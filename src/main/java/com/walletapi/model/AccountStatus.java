package com.walletapi.model;

public enum AccountStatus {
    ACTIVE, FROZEN, CLOSED;

    public boolean canTransitionTo(AccountStatus target) {
        return switch (this) {
            case ACTIVE -> target == FROZEN || target == CLOSED;
            case FROZEN -> target == ACTIVE || target == CLOSED;
            case CLOSED -> false;
        };
    }
}
