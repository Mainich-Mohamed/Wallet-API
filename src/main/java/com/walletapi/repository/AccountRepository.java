package com.walletapi.repository;

import com.walletapi.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    boolean existsByOwnerIdAndCurrency(UUID ownerId, String currency);
}
