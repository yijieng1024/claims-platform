-- Uploaded documents (design 6.2 / section 9). The file itself is on disk at {root}/{claim_id}/{stored_filename}.
CREATE TABLE claim_documents (
    id                     uuid         PRIMARY KEY,
    claim_id               uuid         NOT NULL REFERENCES claims (id),
    information_request_id uuid         REFERENCES information_requests (id),  -- set when uploaded for a request
    uploaded_by_id         uuid         NOT NULL,
    original_filename      varchar(255) NOT NULL,                              -- display / download name only
    stored_filename        varchar(36)  NOT NULL UNIQUE,                       -- random UUID, never user input
    content_type           varchar(50)  NOT NULL CHECK (content_type IN ('image/jpeg', 'image/png', 'application/pdf')),
    size_bytes             bigint       NOT NULL CHECK (size_bytes > 0 AND size_bytes <= 10485760),  -- 10 MB
    uploaded_at            timestamptz  NOT NULL
);

CREATE INDEX claim_documents_claim_idx ON claim_documents (claim_id, uploaded_at);
