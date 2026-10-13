package dev.nerviz.bankapp.infrastructure.config.security;

/** Every role an endpoint or the actuator names, without the {@code ROLE_} prefix. */
public final class Roles {

    public static final String DATA_PROTECTION_OFFICER = "DATA_PROTECTION_OFFICER";
    public static final String OPERATOR = "OPERATOR";

    private Roles() {}
}
