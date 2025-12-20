package fr.maif.izanami.spring.autoconfigure;

import fr.maif.izanami.spring.openfeature.ErrorStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link StringToErrorStrategyConverter}.
 */
class StringToErrorStrategyConverterTest {

    private StringToErrorStrategyConverter converter;

    @BeforeEach
    void setUp() {
        converter = new StringToErrorStrategyConverter();
    }

    @ParameterizedTest
    @CsvSource({
        "DEFAULT_VALUE, DEFAULT_VALUE",
        "default_value, DEFAULT_VALUE",
        "Default_Value, DEFAULT_VALUE",
        "default-value, DEFAULT_VALUE",
        "FAIL, FAIL",
        "fail, FAIL",
        "Fail, FAIL",
        "NULL_VALUE, NULL_VALUE",
        "null_value, NULL_VALUE",
        "null-value, NULL_VALUE",
        "CALLBACK, CALLBACK",
        "callback, CALLBACK"
    })
    void convert_validValues_returnsExpectedStrategy(String input, ErrorStrategy expected) {
        ErrorStrategy result = converter.convert(input);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void convert_unknownValue_returnsDefaultValue() {
        ErrorStrategy result = converter.convert("unknown");
        assertThat(result).isEqualTo(ErrorStrategy.DEFAULT_VALUE);
    }

    @Test
    void convert_emptyString_returnsDefaultValue() {
        ErrorStrategy result = converter.convert("");
        assertThat(result).isEqualTo(ErrorStrategy.DEFAULT_VALUE);
    }

    @Test
    void convert_whitespaceString_returnsDefaultValue() {
        ErrorStrategy result = converter.convert("   ");
        assertThat(result).isEqualTo(ErrorStrategy.DEFAULT_VALUE);
    }

    @Test
    void convert_withLeadingAndTrailingSpaces_parsesCorrectly() {
        ErrorStrategy result = converter.convert("  FAIL  ");
        assertThat(result).isEqualTo(ErrorStrategy.FAIL);
    }
}
