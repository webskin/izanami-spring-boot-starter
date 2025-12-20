package fr.maif.izanami.spring.autoconfigure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BooleanToDefaultValueConverter}.
 */
class BooleanToDefaultValueConverterTest {

    private BooleanToDefaultValueConverter converter;

    @BeforeEach
    void setUp() {
        converter = new BooleanToDefaultValueConverter();
    }

    @Test
    void convert_true_createsMapWithScalarKey() {
        DefaultValueMap result = converter.convert(true);

        assertThat(result).containsEntry("_scalar", true);
        assertThat(result).hasSize(1);
    }

    @Test
    void convert_false_createsMapWithScalarKey() {
        DefaultValueMap result = converter.convert(false);

        assertThat(result).containsEntry("_scalar", false);
        assertThat(result).hasSize(1);
    }

    @Test
    void convert_returnsNewInstanceEachTime() {
        DefaultValueMap result1 = converter.convert(true);
        DefaultValueMap result2 = converter.convert(true);

        assertThat(result1).isNotSameAs(result2);
    }
}
