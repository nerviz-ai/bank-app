package dev.nerviz.bankapp.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AccountStatusTest {

    @Test
    void onlyActiveAcceptsMovements() {
        assertThat(AccountStatus.ACTIVE.acceptsMovements()).isTrue();
        assertThat(AccountStatus.INACTIVE.acceptsMovements()).isFalse();
    }
}
