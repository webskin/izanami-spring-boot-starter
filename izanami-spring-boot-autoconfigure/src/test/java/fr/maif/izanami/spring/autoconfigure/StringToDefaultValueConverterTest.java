package fr.maif.izanami.spring.autoconfigure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link StringToDefaultValueConverter}.
 */
class StringToDefaultValueConverterTest {

    private StringToDefaultValueConverter converter;

    @BeforeEach
    void setUp() {
        converter = new StringToDefaultValueConverter();
    }

    @Test
    void convert_simpleString_createsMapWithScalarKey() {
        DefaultValueMap result = converter.convert("hello");

        assertThat(result).containsEntry("_scalar", "hello");
        assertThat(result).hasSize(1);
    }

    @Test
    void convert_emptyString_createsMapWithScalarKey() {
        DefaultValueMap result = converter.convert("");

        assertThat(result).containsEntry("_scalar", "");
        assertThat(result).hasSize(1);
    }

    @Test
    void convert_whitespaceString_createsMapWithScalarKey() {
        DefaultValueMap result = converter.convert("   ");

        assertThat(result).containsEntry("_scalar", "   ");
        assertThat(result).hasSize(1);
    }

    @Test
    void convert_jsonLikeString_createsMapWithScalarKey() {
        String jsonString = "{\"key\": \"value\"}";
        DefaultValueMap result = converter.convert(jsonString);

        assertThat(result).containsEntry("_scalar", jsonString);
        assertThat(result).hasSize(1);
    }

    @Test
    void convert_numericString_createsMapWithScalarKey() {
        DefaultValueMap result = converter.convert("123");

        assertThat(result).containsEntry("_scalar", "123");
        assertThat(result).hasSize(1);
    }

    @Test
    void convert_returnsNewInstanceEachTime() {
        DefaultValueMap result1 = converter.convert("test");
        DefaultValueMap result2 = converter.convert("test");

        assertThat(result1).isNotSameAs(result2);
    }
}
