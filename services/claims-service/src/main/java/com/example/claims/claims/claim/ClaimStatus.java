package com.example.claims.claims.claim;

public enum ClaimStatus {
    SUBMITTED, UNDER_REVIEW, INFO_REQUESTED, ASSESSED, SETTLED, REJECTED;

    public boolean isClosed() {
        return this == SETTLED || this == REJECTED;
    }
}
