package fr.maif.izanami.spring.autoconfigure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link NumberToDefaultValueConverter}.
 */
class NumberToDefaultValueConverterTest {

    private NumberToDefaultValueConverter converter;

    @BeforeEach
    void setUp() {
        converter = new NumberToDefaultValueConverter();
    }

    @Test
    void convert_integer_createsMapWithScalarKey() {
        DefaultValueMap result = converter.convert(42);

        assertThat(result).containsEntry("_scalar", 42);
        assertThat(result).hasSize(1);
    }

    @Test
    void convert_double_createsMapWithScalarKey() {
        DefaultValueMap result = converter.convert(3.14);

        assertThat(result).containsEntry("_scalar", 3.14);
        assertThat(result).hasSize(1);
    }

    @Test
    void convert_long_createsMapWithScalarKey() {
        DefaultValueMap result = converter.convert(123456789L);

        assertThat(result).containsEntry("_scalar", 123456789L);
        assertThat(result).hasSize(1);
    }

    @Test
    void convert_bigDecimal_createsMapWithScalarKey() {
        BigDecimal value = new BigDecimal("99.99");
        DefaultValueMap result = converter.convert(value);

        assertThat(result).containsEntry("_scalar", value);
        assertThat(result).hasSize(1);
    }

    @Test
    void convert_zero_createsMapWithScalarKey() {
        DefaultValueMap result = converter.convert(0);

        assertThat(result).containsEntry("_scalar", 0);
        assertThat(result).hasSize(1);
    }

    @Test
    void convert_negative_createsMapWithScalarKey() {
        DefaultValueMap result = converter.convert(-100);

        assertThat(result).containsEntry("_scalar", -100);
        assertThat(result).hasSize(1);
    }

    @Test
    void convert_returnsNewInstanceEachTime() {
        DefaultValueMap result1 = converter.convert(42);
        DefaultValueMap result2 = converter.convert(42);

        assertThat(result1).isNotSameAs(result2);
    }
}
