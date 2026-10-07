-- Shared by every endpoint that requires Idempotency-Key. No personal data:
-- body_hash is a SHA-256 of the request body; response_body holds the stored response.
CREATE TABLE idempotency_keys (
    idempotency_key  uuid          NOT NULL,
    route            varchar(200)  NOT NULL,
    caller_identity  varchar(200)  NOT NULL,
    body_hash        char(64)      NOT NULL,
    status           varchar(20)   NOT NULL,
    response_status  smallint      NULL,
    response_body    text          NULL,
    response_headers text          NULL,
    claimed_at       timestamptz   NOT NULL,
    created_at       timestamptz   NOT NULL,
    expires_at       timestamptz   NOT NULL,
    version          bigint        NOT NULL,
    CONSTRAINT pk_idempotency_keys PRIMARY KEY (idempotency_key)
);

CREATE INDEX ix_idempotency_keys_expires_at ON idempotency_keys (expires_at);
