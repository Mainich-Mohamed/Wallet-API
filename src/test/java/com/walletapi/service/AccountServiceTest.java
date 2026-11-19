package com.walletapi.service;

import com.walletapi.exception.AccountNotActiveException;
import com.walletapi.exception.AccountNotFoundException;
import com.walletapi.exception.InsufficientFundsException;
import com.walletapi.exception.InvalidAmountException;
import com.walletapi.model.Account;
import com.walletapi.model.AccountStatus;
import com.walletapi.repository.AccountRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountService accountService;

    private Account buildAccount(UUID id, AccountStatus status, String balance) {
        Account account = new Account();
        account.setId(id);
        account.setOwnerId(UUID.randomUUID());
        account.setIban("ACC-123456789012");
        account.setCurrency("MAD");
        account.setAvailableBalance(new BigDecimal(balance));
        account.setReservedBalance(BigDecimal.ZERO);
        account.setStatus(status);
        account.setDailyLimit(new BigDecimal("5000.00"));
        return account;
    }

    private Account givenAccount(AccountStatus status, String balance) {
        UUID id = UUID.randomUUID();
        Account account = buildAccount(id, status, balance);
        when(accountRepository.findById(id)).thenReturn(Optional.of(account));
        return account;
    }

    private void saveReturnsItsArgument() {
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static BigDecimal amountOf(String raw) {
        return raw == null ? null : new BigDecimal(raw);
    }

    @Nested
    class OpenAccountTests {

        @Test
        void shouldOpenAccountWithDefaultValues() {
            UUID ownerId = UUID.randomUUID();
            when(accountRepository.existsByOwnerIdAndCurrency(ownerId, "MAD")).thenReturn(false);
            saveReturnsItsArgument();

            Account result = accountService.openAccount(ownerId, "mad");

            assertThat(result.getId()).isNotNull();
            assertThat(result.getOwnerId()).isEqualTo(ownerId);
            assertThat(result.getIban()).startsWith("ACC-");
            assertThat(result.getCurrency()).isEqualTo("MAD");
            assertThat(result.getAvailableBalance()).isEqualByComparingTo("0");
            assertThat(result.getReservedBalance()).isEqualByComparingTo("0");
            assertThat(result.getStatus()).isEqualTo(AccountStatus.ACTIVE);
            assertThat(result.getDailyLimit()).isEqualByComparingTo("5000.00");
            assertThat(result.getCreatedAt()).isNotNull();
            verify(accountRepository).save(any(Account.class));
        }

        @Test
        void shouldThrowWhenOwnerIdIsNull() {
            assertThatThrownBy(() -> accountService.openAccount(null, "MAD"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Owner ID");
            verifyNoInteractions(accountRepository);
        }

        @Test
        void shouldThrowWhenCurrencyIsNull() {
            assertThatThrownBy(() -> accountService.openAccount(UUID.randomUUID(), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Currency");
            verifyNoInteractions(accountRepository);
        }

        @Test
        void shouldThrowWhenDuplicateAccountExists() {
            UUID ownerId = UUID.randomUUID();
            when(accountRepository.existsByOwnerIdAndCurrency(ownerId, "MAD")).thenReturn(true);

            assertThatThrownBy(() -> accountService.openAccount(ownerId, "MAD"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already has an account");
            verify(accountRepository, never()).save(any());
        }
    }

    @Nested
    class DepositTests {

        @Test
        void shouldAddAmountToAvailableBalance() {
            Account account = givenAccount(AccountStatus.ACTIVE, "100.00");
            saveReturnsItsArgument();

            Account updated = accountService.deposit(account.getId(), new BigDecimal("50.25"));

            assertThat(updated.getAvailableBalance()).isEqualByComparingTo("150.25");
            verify(accountRepository).save(account);
        }

        @ParameterizedTest(name = "deposit {0} on 100.00 -> {1}")
        @CsvSource({
                "0.01,   100.01",
                "10.12,  110.12",
                "1.500,  101.50"
        })
        void shouldAcceptValidAmounts(String amount, String expectedBalance) {
            Account account = givenAccount(AccountStatus.ACTIVE, "100.00");
            saveReturnsItsArgument();

            Account updated = accountService.deposit(account.getId(), amountOf(amount));

            assertThat(updated.getAvailableBalance()).isEqualByComparingTo(expectedBalance);
        }
        @ParameterizedTest(name = "deposit [{0}] -> \"{1}\"")
        @CsvSource({
                ",        greater than 0",
                "0,       greater than 0",
                "0.00,    greater than 0",
                "-0.01,   greater than 0",
                "-10.00,  greater than 0",
                "0.001,   2 decimals",
                "10.123,  2 decimals"
        })
        void shouldRejectInvalidAmountsBeforeTouchingTheRepository(String amount, String message) {
            assertThatThrownBy(() -> accountService.deposit(UUID.randomUUID(), amountOf(amount)))
                    .isInstanceOf(InvalidAmountException.class)
                    .hasMessageContaining(message);
            verifyNoInteractions(accountRepository);
        }

        @Test
        void shouldThrowWhenAccountNotFound() {
            UUID accountId = UUID.randomUUID();
            when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.deposit(accountId, new BigDecimal("20.00")))
                    .isInstanceOf(AccountNotFoundException.class);
            verify(accountRepository, never()).save(any());
        }

        @ParameterizedTest
        @EnumSource(value = AccountStatus.class, names = {"FROZEN", "CLOSED"})
        void shouldRejectWhenAccountIsNotActive(AccountStatus status) {
            Account account = givenAccount(status, "100.00");

            assertThatThrownBy(() -> accountService.deposit(account.getId(), new BigDecimal("20.00")))
                    .isInstanceOf(AccountNotActiveException.class);
            assertThat(account.getAvailableBalance()).isEqualByComparingTo("100.00");
            verify(accountRepository, never()).save(any());
        }
    }

    @Nested
    class WithdrawTests {

        @Test
        void shouldSubtractAmountFromAvailableBalance() {
            Account account = givenAccount(AccountStatus.ACTIVE, "100.00");
            saveReturnsItsArgument();

            Account updated = accountService.withdraw(account.getId(), new BigDecimal("40.50"));

            assertThat(updated.getAvailableBalance()).isEqualByComparingTo("59.50");
            verify(accountRepository).save(account);
        }
        @ParameterizedTest(name = "withdraw {1} from {0} -> {2}")
        @CsvSource({
                "100.00, 99.99,  0.01",
                "100.00, 100.00, 0.00"
        })
        void shouldAllowWithdrawingUpToTheExactBalance(String balance, String amount, String expected) {
            Account account = givenAccount(AccountStatus.ACTIVE, balance);
            saveReturnsItsArgument();

            Account updated = accountService.withdraw(account.getId(), amountOf(amount));

            assertThat(updated.getAvailableBalance()).isEqualByComparingTo(expected);
        }
        @ParameterizedTest(name = "withdraw {1} from {0} -> insufficient funds")
        @CsvSource({
                "100.00, 100.01",
                "49.99,  50.00",
                "0.00,   0.01"
        })
        void shouldRejectOverdraftAndLeaveBalanceUnchanged(String balance, String amount) {
            Account account = givenAccount(AccountStatus.ACTIVE, balance);

            assertThatThrownBy(() -> accountService.withdraw(account.getId(), amountOf(amount)))
                    .isInstanceOf(InsufficientFundsException.class);assertThat(account.getAvailableBalance()).isEqualByComparingTo(balance);
            verify(accountRepository, never()).save(any());
        }

        @ParameterizedTest(name = "withdraw [{0}] -> \"{1}\"")
        @CsvSource({
                ",        greater than 0",
                "0,       greater than 0",
                "0.00,    greater than 0",
                "-0.01,   greater than 0",
                "-10.00,  greater than 0",
                "0.001,   2 decimals",
                "10.123,  2 decimals"
        })
        void shouldRejectInvalidAmountsBeforeTouchingTheRepository(String amount, String message) {
            assertThatThrownBy(() -> accountService.withdraw(UUID.randomUUID(), amountOf(amount)))
                    .isInstanceOf(InvalidAmountException.class)
                    .hasMessageContaining(message);
            verifyNoInteractions(accountRepository);
        }

        @Test
        void shouldThrowWhenAccountNotFound() {
            UUID accountId = UUID.randomUUID();
            when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.withdraw(accountId, new BigDecimal("10.00")))
                    .isInstanceOf(AccountNotFoundException.class);
            verify(accountRepository, never()).save(any());
        }

        @ParameterizedTest
        @EnumSource(value = AccountStatus.class, names = {"FROZEN", "CLOSED"})
        void shouldRejectWhenAccountIsNotActive(AccountStatus status) {
            Account account = givenAccount(status, "100.00");

            assertThatThrownBy(() -> accountService.withdraw(account.getId(), new BigDecimal("10.00")))
                    .isInstanceOf(AccountNotActiveException.class);
            assertThat(account.getAvailableBalance()).isEqualByComparingTo("100.00");
            verify(accountRepository, never()).save(any());
        }
    }

    @Nested
    class ChangeStatusTests {

        @ParameterizedTest(name = "{0} -> {1} is allowed")
        @CsvSource({
                "ACTIVE, FROZEN",
                "FROZEN, ACTIVE",
                "ACTIVE, CLOSED",
                "FROZEN, CLOSED"
        })
        void shouldApplyAllowedTransitions(AccountStatus from, AccountStatus to) {
            Account account = givenAccount(from, "0.00");
            saveReturnsItsArgument();

            Account updated = accountService.changeStatus(account.getId(), to);

            assertThat(updated.getStatus()).isEqualTo(to);
            verify(accountRepository).save(account);
        }

        @ParameterizedTest(name = "{0} -> {1} is rejected")
        @CsvSource({
                "ACTIVE, ACTIVE",
                "FROZEN, FROZEN",
                "CLOSED, CLOSED",
                "CLOSED, ACTIVE",
                "CLOSED, FROZEN"
        })
        void shouldRejectInvalidTransitions(AccountStatus from, AccountStatus to) {
            Account account = givenAccount(from, "0.00");

            assertThatThrownBy(() -> accountService.changeStatus(account.getId(), to))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Invalid transition");
            assertThat(account.getStatus()).isEqualTo(from);
            verify(accountRepository, never()).save(any());
        }

        @ParameterizedTest(name = "closing from {0} with balance 0.01 is rejected")
        @EnumSource(value = AccountStatus.class, names = {"ACTIVE", "FROZEN"})
        void shouldRejectClosingWithNonZeroBalance(AccountStatus from) {
            Account account = givenAccount(from, "0.01");

            assertThatThrownBy(() -> accountService.changeStatus(account.getId(), AccountStatus.CLOSED))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("zero");
            assertThat(account.getStatus()).isEqualTo(from);
            verify(accountRepository, never()).save(any());
        }

        @Test
        void shouldTreatZeroAtScaleFourAsZeroWhenClosing() {
            Account account = givenAccount(AccountStatus.ACTIVE, "0.0000");
            saveReturnsItsArgument();

            Account updated = accountService.changeStatus(account.getId(), AccountStatus.CLOSED);

            assertThat(updated.getStatus()).isEqualTo(AccountStatus.CLOSED);
        }

        @Test
        void shouldThrowWhenAccountNotFound() {
            UUID accountId = UUID.randomUUID();
            when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> accountService.changeStatus(accountId, AccountStatus.FROZEN))
                    .isInstanceOf(AccountNotFoundException.class);
            verify(accountRepository, never()).save(any());
        }
    }
}
