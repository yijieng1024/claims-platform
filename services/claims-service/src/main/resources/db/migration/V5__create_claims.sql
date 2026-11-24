-- Claims domain (design 6.2). Status values follow ClaimStateMachine (design section 5).

-- CLM-<year>-<6-digit number>; one running number, the year is only part of the label
CREATE SEQUENCE claim_number_seq;

CREATE TABLE claims (
    id                    uuid          PRIMARY KEY,
    claim_number          varchar(20)   NOT NULL UNIQUE,
    claimant_id           uuid          NOT NULL,            -- auth user id, no FK (other schema)
    claimant_name         varchar(100)  NOT NULL,
    policy_id             uuid          NOT NULL REFERENCES policies (id),
    policy_number         varchar(30)   NOT NULL,
    type                  varchar(20)   NOT NULL CHECK (type IN ('MOTOR', 'PROPERTY')),
    market                varchar(2)    NOT NULL CHECK (market IN ('MY', 'SG', 'TH', 'ID', 'PH', 'VN')),
    currency              varchar(3)    NOT NULL,
    incident_date         date          NOT NULL,
    incident_location     varchar(255)  NOT NULL,
    description           text          NOT NULL,
    claimed_amount        numeric(15,2) NOT NULL CHECK (claimed_amount > 0),
    status                varchar(20)   NOT NULL CHECK (status IN
                              ('SUBMITTED', 'UNDER_REVIEW', 'INFO_REQUESTED', 'ASSESSED', 'SETTLED', 'REJECTED')),
    assigned_officer_id   uuid,
    assigned_officer_name varchar(100),
    reserve_amount        numeric(15,2) CHECK (reserve_amount > 0),
    settled_amount        numeric(15,2) CHECK (settled_amount >= 0),
    rejection_reason      text,
    created_at            timestamptz   NOT NULL,
    updated_at            timestamptz   NOT NULL,
    closed_at             timestamptz,
    version               bigint        NOT NULL               -- optimistic locking
);

CREATE INDEX claims_market_status_idx    ON claims (market, status);       -- officer queue, manager filters
CREATE INDEX claims_claimant_idx         ON claims (claimant_id);          -- "my claims"
CREATE INDEX claims_assigned_officer_idx ON claims (assigned_officer_id);  -- "assigned to me"

CREATE TABLE claim_status_history (
    id              uuid         PRIMARY KEY,
    claim_id        uuid         NOT NULL REFERENCES claims (id),
    from_status     varchar(20),                                -- null for the first row (submitted)
    to_status       varchar(20)  NOT NULL,
    changed_by_id   uuid         NOT NULL,
    changed_by_name varchar(100) NOT NULL,
    changed_by_role varchar(20)  NOT NULL,
    note            text,
    changed_at      timestamptz  NOT NULL
);

CREATE INDEX claim_status_history_claim_idx ON claim_status_history (claim_id, changed_at);

CREATE TABLE information_requests (
    id              uuid        PRIMARY KEY,
    claim_id        uuid        NOT NULL REFERENCES claims (id),
    requested_by_id uuid        NOT NULL,
    question        text        NOT NULL,
    status          varchar(20) NOT NULL CHECK (status IN ('OPEN', 'RESPONDED')),
    response_text   text,
    created_at      timestamptz NOT NULL,
    responded_at    timestamptz,
    CONSTRAINT information_requests_response CHECK ((status = 'OPEN') = (response_text IS NULL))
);

CREATE INDEX information_requests_claim_idx ON information_requests (claim_id, created_at);
-- at most one OPEN request per claim (matches the single INFO_REQUESTED status)
CREATE UNIQUE INDEX information_requests_one_open_idx ON information_requests (claim_id) WHERE status = 'OPEN';

-- Transactional outbox (design section 7): written with the claim change, published later
CREATE TABLE outbox_events (
    id           uuid        PRIMARY KEY,                       -- = eventId
    aggregate_id uuid        NOT NULL,                          -- claim id, = Kafka key
    event_type   varchar(40) NOT NULL,
    payload      jsonb       NOT NULL,
    status       varchar(10) NOT NULL CHECK (status IN ('PENDING', 'SENT')),
    attempts     int         NOT NULL DEFAULT 0,
    created_at   timestamptz NOT NULL,
    sent_at      timestamptz
);

CREATE INDEX outbox_events_status_created_idx ON outbox_events (status, created_at);
