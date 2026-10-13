-- UC-006: a customer can be anonymized. security_number and birth_date (personal data) become
-- nullable and are erased on anonymization; name becomes the placeholder 'ANONYMIZED';
-- anonymized_at records when (UC-006 20-persistencia.md § 1). The row and its id stay.
ALTER TABLE customers
    ALTER COLUMN security_number DROP NOT NULL,
    ALTER COLUMN birth_date DROP NOT NULL,
    ADD COLUMN anonymized_at timestamptz NULL;

ALTER TABLE customers
    ADD CONSTRAINT ck_customers_identity CHECK (
        (anonymized_at IS NULL
            AND security_number IS NOT NULL
            AND birth_date IS NOT NULL)
        OR
        (anonymized_at IS NOT NULL
            AND security_number IS NULL
            AND birth_date IS NULL
            AND name = 'ANONYMIZED')
    ) NOT VALID;

ALTER TABLE customers
    VALIDATE CONSTRAINT ck_customers_identity;
