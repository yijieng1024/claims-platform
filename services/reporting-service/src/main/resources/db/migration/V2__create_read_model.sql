-- Reporting read model (design 6.3), filled only from claims.events.

-- One row per claim, overwritten with the newest snapshot; dashboards are SQL over this table.
CREATE TABLE claim_summary (
    claim_id              uuid          PRIMARY KEY,
    claim_number          varchar(20)   NOT NULL,
    type                  varchar(20)   NOT NULL,
    market                varchar(2)    NOT NULL,
    currency              varchar(3)    NOT NULL,
    status                varchar(20)   NOT NULL,
    claimant_id           uuid          NOT NULL,
    assigned_officer_id   uuid,
    assigned_officer_name varchar(100),
    claimed_amount        numeric(15,2) NOT NULL,
    reserve_amount        numeric(15,2),
    settled_amount        numeric(15,2),
    submitted_at          timestamptz   NOT NULL,
    assigned_at           timestamptz,
    closed_at             timestamptz,
    claim_version         bigint        NOT NULL,              -- events with version <= this are ignored
    updated_at            timestamptz   NOT NULL               -- when reporting last wrote the row (asOf)
);

CREATE INDEX claim_summary_market_status_idx ON claim_summary (market, status);
CREATE INDEX claim_summary_officer_idx       ON claim_summary (assigned_officer_id);

CREATE TABLE notifications (
    id           uuid         PRIMARY KEY,
    user_id      uuid         NOT NULL,
    claim_id     uuid         NOT NULL,
    claim_number varchar(20)  NOT NULL,
    type         varchar(30)  NOT NULL,
    message      text         NOT NULL,
    created_at   timestamptz  NOT NULL,
    read_at      timestamptz
);

CREATE INDEX notifications_user_idx ON notifications (user_id, created_at DESC);

-- Event ids already handled; makes the consumer idempotent (at-least-once delivery)
CREATE TABLE processed_events (
    event_id     uuid        PRIMARY KEY,
    processed_at timestamptz NOT NULL
);
