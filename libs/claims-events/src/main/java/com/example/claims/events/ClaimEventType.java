package com.example.claims.events;

/** Constant names are the {@code eventType} values on the wire. */
public enum ClaimEventType {
    ClaimSubmitted,
    ClaimAssigned,
    InformationRequested,
    InformationProvided,
    ClaimAssessed,
    ClaimSettled,
    ClaimRejected
}
