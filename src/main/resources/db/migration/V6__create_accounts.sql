-- UC-007: accounts of a customer. Single currency (USD); balance never negative.
-- No personal data. Retention: life of the customer.
CREATE TABLE accounts (
    id          uuid          NOT NULL,
    customer_id uuid          NOT NULL,
    balance     numeric(19,2) NOT NULL,
    status      varchar(20)   NOT NULL,
    opened_at   timestamptz   NOT NULL,
    version     bigint        NOT NULL DEFAULT 0,
    CONSTRAINT pk_accounts PRIMARY KEY (id),
    CONSTRAINT fk_accounts_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT ck_accounts_balance_non_negative CHECK (balance >= 0),
    CONSTRAINT ck_accounts_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX ix_accounts_customer_id ON accounts (customer_id);
