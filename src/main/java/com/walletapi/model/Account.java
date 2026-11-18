package com.walletapi.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
public class Account {
    @Id
    private UUID id;

    @Column(updatable = false, nullable = false)
    private UUID ownerId;

    @Column(unique = true, updatable = false, nullable = false)
    private String iban;

    @Column(nullable = false, length = 3)
    private String currency = "MAD";

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal availableBalance;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal reservedBalance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal dailyLimit;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
