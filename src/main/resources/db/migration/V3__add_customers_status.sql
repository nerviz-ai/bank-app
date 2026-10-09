-- UC-004: customers gain a KYC status. Existing rows are grandfathered as ACTIVE
-- (user's decision); the default is dropped so every later insert states its status.
ALTER TABLE customers
    ADD COLUMN status varchar(20) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE customers
    ALTER COLUMN status DROP DEFAULT;

ALTER TABLE customers
    ADD CONSTRAINT ck_customers_status
        CHECK (status IN ('KYC_IN_PROGRESS', 'ACTIVE', 'REJECTED_BY_KYC'));
