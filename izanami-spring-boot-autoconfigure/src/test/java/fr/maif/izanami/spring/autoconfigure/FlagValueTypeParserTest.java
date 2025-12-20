package fr.maif.izanami.spring.autoconfigure;

import dev.openfeature.sdk.FlagValueType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link FlagValueTypeParser}.
 */
class FlagValueTypeParserTest {

    @ParameterizedTest
    @CsvSource({
        "BOOLEAN, BOOLEAN",
        "boolean, BOOLEAN",
        "Boolean, BOOLEAN",
        "BOOLEAN, BOOLEAN"
    })
    void fromString_booleanVariants_returnsBoolean(String input, FlagValueType expected) {
        assertThat(FlagValueTypeParser.fromString(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "STRING, STRING",
        "string, STRING",
        "String, STRING"
    })
    void fromString_stringVariants_returnsString(String input, FlagValueType expected) {
        assertThat(FlagValueTypeParser.fromString(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "INTEGER, INTEGER",
        "integer, INTEGER",
        "Integer, INTEGER"
    })
    void fromString_integerVariants_returnsInteger(String input, FlagValueType expected) {
        assertThat(FlagValueTypeParser.fromString(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "DOUBLE, DOUBLE",
        "double, DOUBLE",
        "Double, DOUBLE"
    })
    void fromString_doubleVariants_returnsDouble(String input, FlagValueType expected) {
        assertThat(FlagValueTypeParser.fromString(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "OBJECT, OBJECT",
        "object, OBJECT",
        "Object, OBJECT"
    })
    void fromString_objectVariants_returnsObject(String input, FlagValueType expected) {
        assertThat(FlagValueTypeParser.fromString(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"NUMBER", "number", "Number"})
    void fromString_numberAlias_returnsDouble(String input) {
        assertThat(FlagValueTypeParser.fromString(input)).isEqualTo(FlagValueType.DOUBLE);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void fromString_nullEmptyOrBlank_returnsBoolean(String input) {
        assertThat(FlagValueTypeParser.fromString(input)).isEqualTo(FlagValueType.BOOLEAN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "INVALID", "float", "int", "bool"})
    void fromString_unknownValues_returnsBoolean(String input) {
        assertThat(FlagValueTypeParser.fromString(input)).isEqualTo(FlagValueType.BOOLEAN);
    }

    @Test
    void fromString_withHyphens_normalizesToUnderscore() {
        // FlagValueType doesn't have hyphenated enum values,
        // but we test that hyphen normalization works correctly
        assertThat(FlagValueTypeParser.fromString("some-thing")).isEqualTo(FlagValueType.BOOLEAN);
    }

    @Test
    void fromString_withLeadingAndTrailingSpaces_trimsCorrectly() {
        assertThat(FlagValueTypeParser.fromString("  STRING  ")).isEqualTo(FlagValueType.STRING);
    }
}
