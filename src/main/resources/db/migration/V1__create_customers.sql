-- Personal data: security_number (national identification number) and birth_date.
-- Retention: life of the customer record (UC-001 20-persistencia.md § 1).
CREATE TABLE customers (
    id              uuid          NOT NULL,
    name            varchar(120)  NOT NULL,
    security_number char(11)      NOT NULL,
    birth_date      date          NOT NULL,
    registered_at   timestamptz   NOT NULL,
    version         bigint        NOT NULL DEFAULT 0,
    CONSTRAINT pk_customers PRIMARY KEY (id),
    CONSTRAINT uq_customers_security_number UNIQUE (security_number)
);
