package fr.maif.izanami.spring.openfeature;

import dev.openfeature.sdk.FlagValueType;
import fr.maif.izanami.spring.openfeature.internal.RawFlagConfig;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FlagConfigBuilderTest {

    @Nested
    class FluentApiTests {

        @Test
        void setsAllFieldsViaFluentApi() {
            FlagConfigBuilder builder = new FlagConfigBuilder()
                    .key("test-key")
                    .description("Test description")
                    .valueType(FlagValueType.STRING)
                    .errorStrategy(ErrorStrategy.FAIL)
                    .defaultValue("default")
                    .callbackBean("myCallback");

            RawFlagConfig config = builder.toRawFlagConfig("test-flag");

            assertThat(config.getKey()).isEqualTo("test-key");
            assertThat(config.getName()).isEqualTo("test-flag");
            assertThat(config.getDescription()).isEqualTo("Test description");
            assertThat(config.getValueType()).isEqualTo(FlagValueType.STRING);
            assertThat(config.getErrorStrategy()).isEqualTo(ErrorStrategy.FAIL);
            assertThat(config.getCallbackBean()).isEqualTo("myCallback");
        }

        @Test
        void returnsThisForChaining() {
            FlagConfigBuilder builder = new FlagConfigBuilder();

            assertThat(builder.key("k")).isSameAs(builder);
            assertThat(builder.description("d")).isSameAs(builder);
            assertThat(builder.valueType(FlagValueType.BOOLEAN)).isSameAs(builder);
            assertThat(builder.errorStrategy(ErrorStrategy.DEFAULT_VALUE)).isSameAs(builder);
            assertThat(builder.defaultValue(true)).isSameAs(builder);
            assertThat(builder.callbackBean("cb")).isSameAs(builder);
        }

        @Test
        void usesDefaultValueTypeBoolean() {
            FlagConfigBuilder builder = new FlagConfigBuilder()
                    .key("test-key");

            RawFlagConfig config = builder.toRawFlagConfig("test-flag");

            assertThat(config.getValueType()).isEqualTo(FlagValueType.BOOLEAN);
        }
    }

    @Nested
    class ToRawFlagConfigTests {

        @Test
        void createsRawFlagConfigWithAllFields() {
            FlagConfigBuilder builder = new FlagConfigBuilder()
                    .key("uuid-123")
                    .description("desc")
                    .valueType(FlagValueType.INTEGER)
                    .errorStrategy(ErrorStrategy.DEFAULT_VALUE)
                    .defaultValue(42);

            RawFlagConfig config = builder.toRawFlagConfig("my-flag");

            assertThat(config.getKey()).isEqualTo("uuid-123");
            assertThat(config.getName()).isEqualTo("my-flag");
            assertThat(config.getDescription()).isEqualTo("desc");
            assertThat(config.getValueType()).isEqualTo(FlagValueType.INTEGER);
            assertThat(config.getErrorStrategy()).isEqualTo(ErrorStrategy.DEFAULT_VALUE);
            assertThat(config.unwrapRawDefaultValue()).isEqualTo(42);
        }

        @Test
        void setsNameFromParameter() {
            FlagConfigBuilder builder = new FlagConfigBuilder().key("key");

            RawFlagConfig config = builder.toRawFlagConfig("provided-name");

            assertThat(config.getName()).isEqualTo("provided-name");
        }

        @Test
        void handlesNullDescription() {
            FlagConfigBuilder builder = new FlagConfigBuilder()
                    .key("key");

            RawFlagConfig config = builder.toRawFlagConfig("flag");

            assertThat(config.getDescription()).isNull();
        }

        @Test
        void handlesNullErrorStrategy() {
            FlagConfigBuilder builder = new FlagConfigBuilder()
                    .key("key");

            RawFlagConfig config = builder.toRawFlagConfig("flag");

            assertThat(config.getErrorStrategy()).isNull();
        }

        @Test
        void handlesScalarDefaultValue() {
            FlagConfigBuilder builder = new FlagConfigBuilder()
                    .key("key")
                    .defaultValue("scalar-value");

            RawFlagConfig config = builder.toRawFlagConfig("flag");

            assertThat(config.unwrapRawDefaultValue()).isEqualTo("scalar-value");
        }

        @Test
        void handlesBooleanDefaultValue() {
            FlagConfigBuilder builder = new FlagConfigBuilder()
                    .key("key")
                    .valueType(FlagValueType.BOOLEAN)
                    .defaultValue(true);

            RawFlagConfig config = builder.toRawFlagConfig("flag");

            assertThat(config.unwrapRawDefaultValue()).isEqualTo(true);
        }

        @Test
        void handlesObjectDefaultValue() {
            Map<String, Object> objectValue = Map.of(
                    "name", "Izanami",
                    "version", 2
            );

            FlagConfigBuilder builder = new FlagConfigBuilder()
                    .key("key")
                    .valueType(FlagValueType.OBJECT)
                    .defaultValue(objectValue);

            RawFlagConfig config = builder.toRawFlagConfig("flag");

            Object unwrapped = config.unwrapRawDefaultValue();
            assertThat(unwrapped).isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) unwrapped;
            assertThat(result).containsEntry("name", "Izanami");
            assertThat(result).containsEntry("version", 2);
        }

        @Test
        void handlesNullDefaultValue() {
            FlagConfigBuilder builder = new FlagConfigBuilder()
                    .key("key")
                    .defaultValue(null);

            RawFlagConfig config = builder.toRawFlagConfig("flag");

            assertThat(config.getDefaultValue()).isNull();
            assertThat(config.unwrapRawDefaultValue()).isNull();
        }
    }
}
