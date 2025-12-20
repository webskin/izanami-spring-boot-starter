package fr.maif.izanami.spring.openfeature;

import dev.openfeature.sdk.MutableStructure;
import dev.openfeature.sdk.Structure;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.TypeMismatchError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ValueConverter}.
 */
class ValueConverterTest {

    private ValueConverter converter;

    @BeforeEach
    void setUp() {
        converter = new ValueConverter();
    }

    @Nested
    class PassthroughAndNull {

        @Test
        void objectToValue_valueInstance_returnsSameInstance() {
            Value input = new Value("test");
            Value result = converter.objectToValue(input);

            assertThat(result).isSameAs(input);
        }

        @Test
        void objectToValue_null_returnsEmptyValue() {
            Value result = converter.objectToValue(null);

            assertThat(result.isNull()).isTrue();
        }
    }

    @Nested
    class Primitives {

        @Test
        void objectToValue_string_returnsStringValue() {
            Value result = converter.objectToValue("hello");

            assertThat(result.isString()).isTrue();
            assertThat(result.asString()).isEqualTo("hello");
        }

        @Test
        void objectToValue_emptyString_returnsStringValue() {
            Value result = converter.objectToValue("");

            assertThat(result.isString()).isTrue();
            assertThat(result.asString()).isEmpty();
        }

        @Test
        void objectToValue_booleanTrue_returnsBooleanValue() {
            Value result = converter.objectToValue(true);

            assertThat(result.isBoolean()).isTrue();
            assertThat(result.asBoolean()).isTrue();
        }

        @Test
        void objectToValue_booleanFalse_returnsBooleanValue() {
            Value result = converter.objectToValue(false);

            assertThat(result.isBoolean()).isTrue();
            assertThat(result.asBoolean()).isFalse();
        }

        @Test
        void objectToValue_integer_returnsIntegerValue() {
            Value result = converter.objectToValue(42);

            assertThat(result.isNumber()).isTrue();
            assertThat(result.asInteger()).isEqualTo(42);
        }

        @Test
        void objectToValue_negativeInteger_returnsIntegerValue() {
            Value result = converter.objectToValue(-100);

            assertThat(result.isNumber()).isTrue();
            assertThat(result.asInteger()).isEqualTo(-100);
        }

        @Test
        void objectToValue_double_returnsDoubleValue() {
            Value result = converter.objectToValue(3.14);

            assertThat(result.isNumber()).isTrue();
            assertThat(result.asDouble()).isEqualTo(3.14);
        }

        @Test
        void objectToValue_long_convertsToDouble() {
            Value result = converter.objectToValue(123456789L);

            assertThat(result.isNumber()).isTrue();
            assertThat(result.asDouble()).isEqualTo(123456789.0);
        }

        @Test
        void objectToValue_float_convertsToDouble() {
            Value result = converter.objectToValue(2.5f);

            assertThat(result.isNumber()).isTrue();
            assertThat(result.asDouble()).isEqualTo(2.5);
        }
    }

    @Nested
    class StructureTypes {

        @Test
        void objectToValue_structure_returnsStructureValue() {
            MutableStructure structure = new MutableStructure();
            structure.add("key", "value");

            Value result = converter.objectToValue(structure);

            assertThat(result.isStructure()).isTrue();
            assertThat(result.asStructure().getValue("key").asString()).isEqualTo("value");
        }

        @Test
        void objectToValue_emptyStructure_returnsEmptyStructureValue() {
            MutableStructure structure = new MutableStructure();

            Value result = converter.objectToValue(structure);

            assertThat(result.isStructure()).isTrue();
            assertThat(result.asStructure().asMap()).isEmpty();
        }
    }

    @Nested
    class ListTypes {

        @Test
        void objectToValue_listOfStrings_returnsListValue() {
            List<String> input = List.of("a", "b", "c");

            Value result = converter.objectToValue(input);

            assertThat(result.isList()).isTrue();
            List<Value> values = result.asList();
            assertThat(values).hasSize(3);
            assertThat(values.get(0).asString()).isEqualTo("a");
            assertThat(values.get(1).asString()).isEqualTo("b");
            assertThat(values.get(2).asString()).isEqualTo("c");
        }

        @Test
        void objectToValue_listOfMixedTypes_returnsListValue() {
            List<Object> input = List.of("string", 42, true);

            Value result = converter.objectToValue(input);

            assertThat(result.isList()).isTrue();
            List<Value> values = result.asList();
            assertThat(values).hasSize(3);
            assertThat(values.get(0).asString()).isEqualTo("string");
            assertThat(values.get(1).asInteger()).isEqualTo(42);
            assertThat(values.get(2).asBoolean()).isTrue();
        }

        @Test
        void objectToValue_emptyList_returnsEmptyListValue() {
            List<Object> input = List.of();

            Value result = converter.objectToValue(input);

            assertThat(result.isList()).isTrue();
            assertThat(result.asList()).isEmpty();
        }

        @Test
        void objectToValue_nestedList_convertsRecursively() {
            List<Object> input = List.of(List.of(1, 2), List.of(3, 4));

            Value result = converter.objectToValue(input);

            assertThat(result.isList()).isTrue();
            List<Value> outer = result.asList();
            assertThat(outer).hasSize(2);
            assertThat(outer.get(0).isList()).isTrue();
            assertThat(outer.get(0).asList().get(0).asInteger()).isEqualTo(1);
        }
    }

    @Nested
    class InstantTypes {

        @Test
        void objectToValue_instant_returnsInstantValue() {
            Instant instant = Instant.parse("2024-01-15T10:30:00Z");

            Value result = converter.objectToValue(instant);

            assertThat(result.isInstant()).isTrue();
            assertThat(result.asInstant()).isEqualTo(instant);
        }

        @Test
        void objectToValue_epochInstant_returnsInstantValue() {
            Instant instant = Instant.EPOCH;

            Value result = converter.objectToValue(instant);

            assertThat(result.isInstant()).isTrue();
            assertThat(result.asInstant()).isEqualTo(Instant.EPOCH);
        }
    }

    @Nested
    class MapTypes {

        @Test
        void objectToValue_mapOfStrings_returnsStructureValue() {
            Map<String, String> input = Map.of("key1", "value1", "key2", "value2");

            Value result = converter.objectToValue(input);

            assertThat(result.isStructure()).isTrue();
            Structure structure = result.asStructure();
            assertThat(structure.getValue("key1").asString()).isEqualTo("value1");
            assertThat(structure.getValue("key2").asString()).isEqualTo("value2");
        }

        @Test
        void objectToValue_mapOfMixedTypes_convertsValuesRecursively() {
            Map<String, Object> input = Map.of(
                "string", "value",
                "number", 42,
                "boolean", true
            );

            Value result = converter.objectToValue(input);

            assertThat(result.isStructure()).isTrue();
            Structure structure = result.asStructure();
            assertThat(structure.getValue("string").asString()).isEqualTo("value");
            assertThat(structure.getValue("number").asInteger()).isEqualTo(42);
            assertThat(structure.getValue("boolean").asBoolean()).isTrue();
        }

        @Test
        void objectToValue_nestedMap_convertsRecursively() {
            Map<String, Object> inner = Map.of("innerKey", "innerValue");
            Map<String, Object> input = Map.of("outer", inner);

            Value result = converter.objectToValue(input);

            assertThat(result.isStructure()).isTrue();
            Value outerValue = result.asStructure().getValue("outer");
            assertThat(outerValue.isStructure()).isTrue();
            assertThat(outerValue.asStructure().getValue("innerKey").asString()).isEqualTo("innerValue");
        }

        @Test
        void objectToValue_emptyMap_returnsEmptyStructureValue() {
            Map<String, Object> input = Map.of();

            Value result = converter.objectToValue(input);

            assertThat(result.isStructure()).isTrue();
            assertThat(result.asStructure().asMap()).isEmpty();
        }

        @Test
        void objectToValue_mapWithNullKey_skipsNullKey() {
            Map<String, Object> input = new java.util.HashMap<>();
            input.put(null, "nullKeyValue");
            input.put("validKey", "validValue");

            Value result = converter.objectToValue(input);

            assertThat(result.isStructure()).isTrue();
            Structure structure = result.asStructure();
            assertThat(structure.asMap()).hasSize(1);
            assertThat(structure.getValue("validKey").asString()).isEqualTo("validValue");
        }

        @Test
        void objectToValue_mapWithNonStringKey_convertsKeyToString() {
            Map<Object, Object> input = Map.of(123, "numericKey");

            Value result = converter.objectToValue(input);

            assertThat(result.isStructure()).isTrue();
            assertThat(result.asStructure().getValue("123").asString()).isEqualTo("numericKey");
        }
    }

    @Nested
    class UnsupportedTypes {

        @Test
        void objectToValue_customObject_throwsTypeMismatchError() {
            Object customObject = new Object() {
                @Override
                public String toString() {
                    return "CustomObject";
                }
            };

            assertThatThrownBy(() -> converter.objectToValue(customObject))
                .isInstanceOf(TypeMismatchError.class)
                .hasMessageContaining("CustomObject")
                .hasMessageContaining("unexpected type");
        }

        @Test
        void objectToValue_class_throwsTypeMismatchError() {
            assertThatThrownBy(() -> converter.objectToValue(String.class))
                .isInstanceOf(TypeMismatchError.class)
                .hasMessageContaining("unexpected type");
        }
    }
}
