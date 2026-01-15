package fr.maif.izanami.spring.openfeature;

import dev.openfeature.sdk.FlagValueType;
import fr.maif.izanami.spring.openfeature.internal.RawFlagConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FlagsPropertiesBuilderTest {

    @Test
    void buildsEmptyFlagsProperties() {
        FlagsProperties props = FlagsProperties.builder().build();

        assertThat(props.getFlags()).isEmpty();
        assertThat(props.getFlagConfigs()).isEmpty();
    }

    @Test
    void buildsSingleFlag() {
        FlagsProperties props = FlagsProperties.builder()
                .flag("my-feature", flag -> flag
                        .key("uuid-123")
                        .description("My feature")
                        .valueType(FlagValueType.BOOLEAN)
                        .defaultValue(false))
                .build();

        assertThat(props.getFlags()).hasSize(1);
        assertThat(props.getFlags()).containsKey("my-feature");

        RawFlagConfig config = props.getFlags().get("my-feature");
        assertThat(config.getKey()).isEqualTo("uuid-123");
        assertThat(config.getDescription()).isEqualTo("My feature");
        assertThat(config.getValueType()).isEqualTo(FlagValueType.BOOLEAN);
    }

    @Test
    void buildsMultipleFlags() {
        FlagsProperties props = FlagsProperties.builder()
                .flag("feature-a", flag -> flag
                        .key("uuid-a")
                        .valueType(FlagValueType.BOOLEAN))
                .flag("feature-b", flag -> flag
                        .key("uuid-b")
                        .valueType(FlagValueType.STRING)
                        .defaultValue("default"))
                .flag("feature-c", flag -> flag
                        .key("uuid-c")
                        .valueType(FlagValueType.INTEGER)
                        .defaultValue(42))
                .build();

        assertThat(props.getFlags()).hasSize(3);
        assertThat(props.getFlags()).containsKeys("feature-a", "feature-b", "feature-c");
    }

    @Test
    void preservesFlagOrder() {
        FlagsProperties props = FlagsProperties.builder()
                .flag("zebra", flag -> flag.key("z"))
                .flag("apple", flag -> flag.key("a"))
                .flag("mango", flag -> flag.key("m"))
                .build();

        List<String> keys = new ArrayList<>(props.getFlags().keySet());
        assertThat(keys).containsExactly("zebra", "apple", "mango");
    }

    @Test
    void laterFlagOverwritesEarlierWithSameName() {
        FlagsProperties props = FlagsProperties.builder()
                .flag("duplicate", flag -> flag
                        .key("old-key")
                        .description("Original"))
                .flag("duplicate", flag -> flag
                        .key("new-key")
                        .description("Replacement"))
                .build();

        assertThat(props.getFlags()).hasSize(1);

        RawFlagConfig config = props.getFlags().get("duplicate");
        assertThat(config.getKey()).isEqualTo("new-key");
        assertThat(config.getDescription()).isEqualTo("Replacement");
    }

    @Test
    void lambdaReceivesFreshBuilder() {
        List<FlagConfigBuilder> capturedBuilders = new ArrayList<>();

        FlagsProperties.builder()
                .flag("flag-1", builder -> {
                    capturedBuilders.add(builder);
                    builder.key("key-1");
                })
                .flag("flag-2", builder -> {
                    capturedBuilders.add(builder);
                    builder.key("key-2");
                })
                .build();

        assertThat(capturedBuilders).hasSize(2);
        assertThat(capturedBuilders.get(0)).isNotSameAs(capturedBuilders.get(1));
    }

    @Test
    void getFlagConfigsNormalizesNames() {
        FlagsProperties props = FlagsProperties.builder()
                .flag("my-feature", flag -> flag.key("uuid"))
                .build();

        List<RawFlagConfig> configs = props.getFlagConfigs();

        assertThat(configs).hasSize(1);
        assertThat(configs.get(0).getName()).isEqualTo("my-feature");
    }

    @Test
    void builderMethodReturnsNewInstance() {
        FlagsPropertiesBuilder builder1 = FlagsProperties.builder();
        FlagsPropertiesBuilder builder2 = FlagsProperties.builder();

        assertThat(builder1).isNotSameAs(builder2);
    }

    @Test
    void supportsAllValueTypes() {
        FlagsProperties props = FlagsProperties.builder()
                .flag("bool", flag -> flag.key("k1").valueType(FlagValueType.BOOLEAN).defaultValue(true))
                .flag("string", flag -> flag.key("k2").valueType(FlagValueType.STRING).defaultValue("text"))
                .flag("integer", flag -> flag.key("k3").valueType(FlagValueType.INTEGER).defaultValue(123))
                .flag("double", flag -> flag.key("k4").valueType(FlagValueType.DOUBLE).defaultValue(3.14))
                .flag("object", flag -> flag.key("k5").valueType(FlagValueType.OBJECT).defaultValue(Map.of("a", 1)))
                .build();

        assertThat(props.getFlags()).hasSize(5);

        assertThat(props.getFlags().get("bool").getValueType()).isEqualTo(FlagValueType.BOOLEAN);
        assertThat(props.getFlags().get("string").getValueType()).isEqualTo(FlagValueType.STRING);
        assertThat(props.getFlags().get("integer").getValueType()).isEqualTo(FlagValueType.INTEGER);
        assertThat(props.getFlags().get("double").getValueType()).isEqualTo(FlagValueType.DOUBLE);
        assertThat(props.getFlags().get("object").getValueType()).isEqualTo(FlagValueType.OBJECT);
    }

    @Test
    void supportsAllErrorStrategies() {
        FlagsProperties props = FlagsProperties.builder()
                .flag("default", flag -> flag.key("k1").errorStrategy(ErrorStrategy.DEFAULT_VALUE).defaultValue(false))
                .flag("fail", flag -> flag.key("k2").errorStrategy(ErrorStrategy.FAIL))
                .flag("null", flag -> flag.key("k3").errorStrategy(ErrorStrategy.NULL_VALUE))
                .flag("callback", flag -> flag.key("k4").errorStrategy(ErrorStrategy.CALLBACK).callbackBean("myBean"))
                .build();

        assertThat(props.getFlags().get("default").getErrorStrategy()).isEqualTo(ErrorStrategy.DEFAULT_VALUE);
        assertThat(props.getFlags().get("fail").getErrorStrategy()).isEqualTo(ErrorStrategy.FAIL);
        assertThat(props.getFlags().get("null").getErrorStrategy()).isEqualTo(ErrorStrategy.NULL_VALUE);
        assertThat(props.getFlags().get("callback").getErrorStrategy()).isEqualTo(ErrorStrategy.CALLBACK);
        assertThat(props.getFlags().get("callback").getCallbackBean()).isEqualTo("myBean");
    }
}
