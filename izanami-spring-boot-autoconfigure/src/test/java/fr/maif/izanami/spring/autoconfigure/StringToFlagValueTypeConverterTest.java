package fr.maif.izanami.spring.autoconfigure;

import dev.openfeature.sdk.FlagValueType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link StringToFlagValueTypeConverter}.
 */
class StringToFlagValueTypeConverterTest {

    private StringToFlagValueTypeConverter converter;

    @BeforeEach
    void setUp() {
        converter = new StringToFlagValueTypeConverter();
    }

    @ParameterizedTest
    @CsvSource({
        "BOOLEAN, BOOLEAN",
        "boolean, BOOLEAN",
        "Boolean, BOOLEAN",
        "STRING, STRING",
        "string, STRING",
        "INTEGER, INTEGER",
        "integer, INTEGER",
        "DOUBLE, DOUBLE",
        "double, DOUBLE",
        "OBJECT, OBJECT",
        "object, OBJECT",
        "NUMBER, DOUBLE",
        "number, DOUBLE",
        "Number, DOUBLE"
    })
    void convert_validValues_returnsExpectedType(String input, FlagValueType expected) {
        FlagValueType result = converter.convert(input);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void convert_unknownValue_returnsBoolean() {
        FlagValueType result = converter.convert("unknown");
        assertThat(result).isEqualTo(FlagValueType.BOOLEAN);
    }

    @Test
    void convert_emptyString_returnsBoolean() {
        FlagValueType result = converter.convert("");
        assertThat(result).isEqualTo(FlagValueType.BOOLEAN);
    }

    @Test
    void convert_whitespaceString_returnsBoolean() {
        FlagValueType result = converter.convert("   ");
        assertThat(result).isEqualTo(FlagValueType.BOOLEAN);
    }

    @Test
    void convert_withLeadingAndTrailingSpaces_parsesCorrectly() {
        FlagValueType result = converter.convert("  STRING  ");
        assertThat(result).isEqualTo(FlagValueType.STRING);
    }
}
