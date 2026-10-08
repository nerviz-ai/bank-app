2026-10-08 · UC-002 · IdempotencyKeyPort gains deleteExpired; idempotency_keys 24 h retention enforced by an hourly prune (BL-01 closed)
2026-10-08 · UC-003 · CustomerRepository gains findById; CustomerController/CustomerApi gain GET /api/v1/customers/{customerId}; the Location returned by creation now resolves
2026-10-08 · UC-004 · Customer gains status (born KYC_IN_PROGRESS, existing rows ACTIVE via V3); CreateCustomerUseCase records KycVerificationRequested in the transactional outbox (RequestKycVerification port), same transaction
