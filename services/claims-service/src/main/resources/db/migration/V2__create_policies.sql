-- Simulated reference data (design 6.2): in a real company this comes from the policy admin system.
CREATE TABLE policies (
    id            uuid          PRIMARY KEY,
    policy_number varchar(30)   NOT NULL UNIQUE,
    holder_name   varchar(100)  NOT NULL,
    holder_email  varchar(255)  NOT NULL CHECK (holder_email = lower(holder_email)),
    product_type  varchar(20)   NOT NULL CHECK (product_type IN ('MOTOR', 'PROPERTY')),
    market        varchar(2)    NOT NULL CHECK (market IN ('MY', 'SG', 'TH', 'ID', 'PH', 'VN')),
    currency      varchar(3)    NOT NULL,
    insured_item  varchar(255)  NOT NULL,
    sum_insured   numeric(15,2) NOT NULL CHECK (sum_insured > 0),
    start_date    date          NOT NULL,
    end_date      date          NOT NULL,
    -- expired = ACTIVE with end_date in the past; there is no EXPIRED status
    status        varchar(20)   NOT NULL CHECK (status IN ('ACTIVE', 'CANCELLED')),
    CONSTRAINT policies_cover_period CHECK (end_date >= start_date),
    CONSTRAINT policies_currency_matches_market CHECK (currency = CASE market
        WHEN 'MY' THEN 'MYR' WHEN 'SG' THEN 'SGD' WHEN 'TH' THEN 'THB'
        WHEN 'ID' THEN 'IDR' WHEN 'PH' THEN 'PHP' WHEN 'VN' THEN 'VND' END)
);

CREATE INDEX policies_holder_email_idx ON policies (holder_email);
