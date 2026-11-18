package com.walletapi.service;

import com.walletapi.model.Account;
import com.walletapi.model.AccountStatus;
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

    private String generateAccountNumber() {
        long randomDigits = ThreadLocalRandom.current().nextLong(100_000_000_000L, 999_999_999_999L);
        return "ACC-" + randomDigits;
    }
}
