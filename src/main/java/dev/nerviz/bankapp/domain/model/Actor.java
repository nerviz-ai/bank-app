package dev.nerviz.bankapp.domain.model;

import dev.nerviz.bankapp.domain.exception.ValidationException;

/**
 * Who is acting, as the use case needs to know it: a customer, for itself, or an operator, for
 * anyone. Built by the inbound adapter from the authenticated principal; carries no security type.
 */
public sealed interface Actor permits Actor.Customer, Actor.Operator {

    /** A customer actor may act only for itself; an operator for anyone. */
    boolean mayActFor(CustomerId owner);

    record Customer(CustomerId id) implements Actor {

        public Customer {
            if (id == null) {
                throw new ValidationException("CUSTOMER_ID_REQUIRED", "customer id is required");
            }
        }

        @Override
        public boolean mayActFor(CustomerId owner) {
            return id.equals(owner);
        }
    }

    record Operator() implements Actor {

        @Override
        public boolean mayActFor(CustomerId owner) {
            return true;
        }
    }
}
