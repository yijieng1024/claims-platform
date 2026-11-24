CREATE TABLE users (
    id            uuid         PRIMARY KEY,
    email         varchar(255) NOT NULL UNIQUE,  -- stored lower-cased by the application
    password_hash varchar(100) NOT NULL,         -- BCrypt
    full_name     varchar(100) NOT NULL,
    role          varchar(20)  NOT NULL CHECK (role IN ('CLAIMANT', 'OFFICER', 'MANAGER')),
    market        varchar(2)   CHECK (market IN ('MY', 'SG', 'TH', 'ID', 'PH', 'VN')),
    created_at    timestamptz  NOT NULL DEFAULT now(),
    -- officers belong to exactly one market; nobody else has one (design Q1)
    CONSTRAINT users_market_only_for_officers CHECK ((role = 'OFFICER') = (market IS NOT NULL))
);
