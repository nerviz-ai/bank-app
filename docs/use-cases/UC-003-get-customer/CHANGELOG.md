2026-10-08 · UC-004 · CustomerDetailsResponse gains status (KYC_IN_PROGRESS, ACTIVE, REJECTED_BY_KYC)
2026-10-12 · UC-005 · GetCustomerUseCase throws CustomerNotFoundException (promoted, same CUSTOMER_NOT_FOUND); response unchanged
2026-10-13 · UC-006 · GET /api/v1/customers/{customerId} now requires an authenticated JWT; CustomerDetailsResponse omits securityNumber and birthDate for an anonymized customer (name ANONYMIZED) and masks name in logs
