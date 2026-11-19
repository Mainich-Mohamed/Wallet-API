package com.walletapi.service;

import com.walletapi.exception.AccountNotActiveException;
import com.walletapi.exception.AccountNotFoundException;
import com.walletapi.exception.InsufficientFundsException;
import com.walletapi.exception.InvalidAmountException;
import com.walletapi.model.Account;
import com.walletapi.model.AccountStatus;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import com.walletapi.repository.AccountRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AccountService {
    private static final BigDecimal DEFAULT_DAILY_LIMIT = new BigDecimal("5000.00");

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public Account openAccount(UUID ownerId, String currency) {
        if (ownerId == null) {
            throw new IllegalArgumentException("Owner ID is required");
        }
        if (currency == null) {
            throw new IllegalArgumentException("Currency must be a valid 3-letter ISO code");
        }

        String normalizedCurrency = currency.toUpperCase();

        if (accountRepository.existsByOwnerIdAndCurrency(ownerId, normalizedCurrency)) {
            throw new IllegalArgumentException("User already has an account in " + normalizedCurrency);
        }

        Account account = new Account();
        account.setId(UUID.randomUUID());
        account.setOwnerId(ownerId);
        account.setIban(generateAccountNumber());
        account.setCurrency(normalizedCurrency);
        account.setAvailableBalance(BigDecimal.ZERO);
        account.setReservedBalance(BigDecimal.ZERO);
        account.setStatus(AccountStatus.ACTIVE);
        account.setDailyLimit(DEFAULT_DAILY_LIMIT);
        account.setCreatedAt(Instant.now());

        return accountRepository.save(account);
    }

    @Transactional
    public Account deposit(UUID accountId, BigDecimal amount) {
        validateAmount(amount);
        Account account = getActiveAccount(accountId);
        account.setAvailableBalance(account.getAvailableBalance().add(amount));
        return accountRepository.save(account);
    }

    @Transactional
    public Account withdraw(UUID accountId, BigDecimal amount) {
        validateAmount(amount);
        Account account = getActiveAccount(accountId);
        if (account.getAvailableBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException("Not enough balance to withdraw");
        }
        account.setAvailableBalance(account.getAvailableBalance().subtract(amount));
        return accountRepository.save(account);
    }

    @Transactional
    public Account changeStatus(UUID accountId, AccountStatus target) {
        Account account = findAccount(accountId);
        if (!account.getStatus().canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Invalid transition " + account.getStatus() + " -> " + target);
        }
        if (target == AccountStatus.CLOSED
                && account.getAvailableBalance().compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalStateException("Account balance must be zero to close");
        }
        account.setStatus(target);
        return accountRepository.save(account);
    }

    private String generateAccountNumber() {
        long randomDigits = ThreadLocalRandom.current().nextLong(100_000_000_000L, 999_999_999_999L);
        return "ACC-" + randomDigits;
    }

    private Account findAccount(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    private Account getActiveAccount(UUID accountId) {
        Account account = findAccount(accountId);
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException("Account is " + account.getStatus());
        }
        return account;
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidAmountException("Amount must be greater than 0");
        } if (amount.stripTrailingZeros().scale() > 2) {
            throw new InvalidAmountException("Amount must have at most 2 decimals");
        }
    }
}
