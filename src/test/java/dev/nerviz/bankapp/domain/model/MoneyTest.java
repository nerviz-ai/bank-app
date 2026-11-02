package dev.nerviz.bankapp.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.nerviz.bankapp.domain.exception.ValidationException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyTest {

    @Test
    void rejectsNullAmount() {
        assertThatThrownBy(() -> Money.of(null))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo("MONEY_REQUIRED");
    }

    @ParameterizedTest
    @CsvSource({"-0.01,MONEY_NEGATIVE", "-5,MONEY_NEGATIVE", "1.001,MONEY_SCALE_INVALID", "0.123,MONEY_SCALE_INVALID"})
    void rejectsInvalidAmount(BigDecimal amount, String errorCode) {
        assertThatThrownBy(() -> Money.of(amount))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
    }

    @Test
    void zeroIsZeroWithScaleTwo() {
        BigDecimal amount = Money.zero().amount();

        assertThat(amount).isEqualTo(new BigDecimal("0.00"));
        assertThat(amount.scale()).isEqualTo(2);
    }

    @Test
    void acceptsTwoDecimals() {
        Money money = Money.of(new BigDecimal("10.50"));

        assertThat(money.amount()).isEqualTo(new BigDecimal("10.50"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"10", "10.0", "10.00", "10.000"})
    void normalisesScaleSoEqualAmountsAreEqual(String amount) {
        Money money = Money.of(new BigDecimal(amount));

        assertThat(money).isEqualTo(Money.of(new BigDecimal("10.00")));
        assertThat(money.amount().scale()).isEqualTo(2);
    }
}
