package fr.maif.izanami.spring.openfeature.internal;

import dev.openfeature.sdk.FlagValueType;
import fr.maif.FeatureClientErrorStrategy;
import fr.maif.izanami.spring.autoconfigure.DefaultValueMap;
import fr.maif.izanami.spring.openfeature.ErrorStrategy;
import fr.maif.izanami.spring.openfeature.FlagConfig;
import fr.maif.izanami.spring.openfeature.FlagsProperties;
import fr.maif.izanami.spring.openfeature.api.ErrorStrategyFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FlagConfigServiceImpl}.
 */
class FlagConfigServiceImplTest {

    private ErrorStrategyFactory errorStrategyFactory;

    @BeforeEach
    void setUp() {
        errorStrategyFactory = mock(ErrorStrategyFactory.class);
        when(errorStrategyFactory.createErrorStrategy(any(), any(), any(), any(), any()))
            .thenAnswer(invocation -> FeatureClientErrorStrategy.defaultValueStrategy(false, "", BigDecimal.ZERO));
    }

    // ========== Helper methods ==========

    private RawFlagConfig createRawConfig(String key, String name, FlagValueType type) {
        RawFlagConfig raw = new RawFlagConfig();
        raw.setKey(key);
        raw.setName(name);
        raw.setValueType(type);
        return raw;
    }

    private RawFlagConfig createRawConfigWithDefault(String key, String name, FlagValueType type, Object defaultValue) {
        RawFlagConfig raw = createRawConfig(key, name, type);
        raw.setErrorStrategy(ErrorStrategy.DEFAULT_VALUE);
        if (defaultValue != null) {
            DefaultValueMap defaultValueMap = new DefaultValueMap();
            defaultValueMap.put("_scalar", defaultValue);
            raw.setDefaultValue(defaultValueMap);
        }
        return raw;
    }

    private FlagConfigServiceImpl createService(RawFlagConfig... configs) {
        return new FlagConfigServiceImpl(new FlagsProperties(Arrays.asList(configs)), errorStrategyFactory);
    }

    private FlagConfigServiceImpl createService(List<RawFlagConfig> configs) {
        return new FlagConfigServiceImpl(new FlagsProperties(configs), errorStrategyFactory);
    }

    // ========== 1. Valid Configuration Tests ==========

    @Test
    void createsFlagConfigWithAllFields() {
        RawFlagConfig raw = new RawFlagConfig();
        raw.setKey("550e8400-e29b-41d4-a716-446655440000");
        raw.setName("my-feature");
        raw.setDescription("My feature description");
        raw.setValueType(FlagValueType.BOOLEAN);
        raw.setErrorStrategy(ErrorStrategy.DEFAULT_VALUE);
        DefaultValueMap defaultValue = new DefaultValueMap();
        defaultValue.put("_scalar", true);
        raw.setDefaultValue(defaultValue);

        FlagConfigServiceImpl service = createService(raw);

        FlagConfig config = service.getFlagConfigByName("my-feature").orElseThrow();
        assertThat(config.key()).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
        assertThat(config.name()).isEqualTo("my-feature");
        assertThat(config.description()).isEqualTo("My feature description");
        assertThat(config.valueType()).isEqualTo(FlagValueType.BOOLEAN);
        assertThat(config.rawErrorStrategy()).isEqualTo(ErrorStrategy.DEFAULT_VALUE);
        assertThat(config.defaultValue()).isEqualTo(true);
    }

    @Test
    void createsBooleanFlagWithDefaultValue() {
        RawFlagConfig raw = createRawConfigWithDefault("key-1", "bool-flag", FlagValueType.BOOLEAN, true);

        FlagConfigServiceImpl service = createService(raw);

        FlagConfig config = service.getFlagConfigByName("bool-flag").orElseThrow();
        assertThat(config.valueType()).isEqualTo(FlagValueType.BOOLEAN);
        assertThat(config.defaultValue()).isEqualTo(true);
    }

    @Test
    void createsStringFlagWithDefaultValue() {
        RawFlagConfig raw = createRawConfigWithDefault("key-2", "string-flag", FlagValueType.STRING, "hello");

        FlagConfigServiceImpl service = createService(raw);

        FlagConfig config = service.getFlagConfigByName("string-flag").orElseThrow();
        assertThat(config.valueType()).isEqualTo(FlagValueType.STRING);
        assertThat(config.defaultValue()).isEqualTo("hello");
    }

    @Test
    void createsIntegerFlagWithDefaultValue() {
        RawFlagConfig raw = createRawConfigWithDefault("key-3", "int-flag", FlagValueType.INTEGER, 42);

        FlagConfigServiceImpl service = createService(raw);

        FlagConfig config = service.getFlagConfigByName("int-flag").orElseThrow();
        assertThat(config.valueType()).isEqualTo(FlagValueType.INTEGER);
        assertThat(config.defaultValue()).isEqualTo(42);
    }

    @Test
    void createsDoubleFlagWithDefaultValue() {
        RawFlagConfig raw = createRawConfigWithDefault("key-4", "double-flag", FlagValueType.DOUBLE, 3.14);

        FlagConfigServiceImpl service = createService(raw);

        FlagConfig config = service.getFlagConfigByName("double-flag").orElseThrow();
        assertThat(config.valueType()).isEqualTo(FlagValueType.DOUBLE);
        assertThat(config.defaultValue()).isEqualTo(3.14);
    }

    @Test
    void createsObjectFlagWithMapDefaultValue() {
        RawFlagConfig raw = createRawConfig("key-5", "object-flag", FlagValueType.OBJECT);
        raw.setErrorStrategy(ErrorStrategy.DEFAULT_VALUE);
        DefaultValueMap defaultValue = new DefaultValueMap();
        defaultValue.put("name", "Izanami");
        defaultValue.put("version", 2);
        raw.setDefaultValue(defaultValue);

        FlagConfigServiceImpl service = createService(raw);

        FlagConfig config = service.getFlagConfigByName("object-flag").orElseThrow();
        assertThat(config.valueType()).isEqualTo(FlagValueType.OBJECT);
        assertThat(config.defaultValue()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> defaultMap = (Map<String, Object>) config.defaultValue();
        assertThat(defaultMap).containsEntry("name", "Izanami");
        assertThat(defaultMap).containsEntry("version", 2);
    }

    // ========== 2. Validation Error Tests ==========

    @Test
    void throwsWhenKeyIsNull() {
        RawFlagConfig raw = new RawFlagConfig();
        raw.setName("my-flag");
        raw.setValueType(FlagValueType.BOOLEAN);

        assertThatThrownBy(() -> createService(raw))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("missing required 'key' property");
    }

    @Test
    void throwsWhenKeyIsBlank() {
        RawFlagConfig raw = new RawFlagConfig();
        raw.setKey("   ");
        raw.setName("my-flag");
        raw.setValueType(FlagValueType.BOOLEAN);

        assertThatThrownBy(() -> createService(raw))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("missing required 'key' property");
    }

    @Test
    void throwsWhenNameIsNull() {
        RawFlagConfig raw = new RawFlagConfig();
        raw.setKey("key-1");
        raw.setValueType(FlagValueType.BOOLEAN);

        assertThatThrownBy(() -> createService(raw))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("missing required 'name' property");
    }

    @Test
    void throwsWhenNameIsBlank() {
        RawFlagConfig raw = new RawFlagConfig();
        raw.setKey("key-1");
        raw.setName("");
        raw.setValueType(FlagValueType.BOOLEAN);

        assertThatThrownBy(() -> createService(raw))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("missing required 'name' property");
    }

    @Test
    void usesDefaultBooleanValueTypeWhenNotExplicitlySet() {
        // RawFlagConfig has default valueType=BOOLEAN, so null means BOOLEAN
        RawFlagConfig raw = new RawFlagConfig();
        raw.setKey("key-1");
        raw.setName("my-flag");
        raw.setValueType(null); // Will use default BOOLEAN

        FlagConfigServiceImpl service = createService(raw);

        FlagConfig config = service.getFlagConfigByName("my-flag").orElseThrow();
        assertThat(config.valueType()).isEqualTo(FlagValueType.BOOLEAN);
    }

    @Test
    void throwsWhenDefaultValueWithFailStrategy() {
        RawFlagConfig raw = createRawConfig("key-1", "my-flag", FlagValueType.BOOLEAN);
        raw.setErrorStrategy(ErrorStrategy.FAIL);
        DefaultValueMap defaultValue = new DefaultValueMap();
        defaultValue.put("_scalar", true);
        raw.setDefaultValue(defaultValue);

        assertThatThrownBy(() -> createService(raw))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("errorStrategy=FAIL")
            .hasMessageContaining("defaultValue");
    }

    @Test
    void throwsWhenCallbackBeanWithDefaultValueStrategy() {
        RawFlagConfig raw = createRawConfig("key-1", "my-flag", FlagValueType.BOOLEAN);
        raw.setErrorStrategy(ErrorStrategy.DEFAULT_VALUE);
        raw.setCallbackBean("myCallbackBean");

        assertThatThrownBy(() -> createService(raw))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("callbackBean")
            .hasMessageContaining("errorStrategy=DEFAULT_VALUE");
    }

    @Test
    void throwsWhenInvalidNumberDefaultValue() {
        RawFlagConfig raw = createRawConfig("key-1", "my-flag", FlagValueType.INTEGER);
        raw.setErrorStrategy(ErrorStrategy.DEFAULT_VALUE);
        DefaultValueMap defaultValue = new DefaultValueMap();
        defaultValue.put("_scalar", "not-a-number");
        raw.setDefaultValue(defaultValue);

        assertThatThrownBy(() -> createService(raw))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not a valid number");
    }

    // ========== 3. Error Strategy Tests ==========

    @Test
    void usesDefaultValueStrategyWhenExplicitlySet() {
        RawFlagConfig raw = createRawConfig("key-1", "my-flag", FlagValueType.BOOLEAN);
        raw.setErrorStrategy(ErrorStrategy.DEFAULT_VALUE);
        DefaultValueMap defaultValue = new DefaultValueMap();
        defaultValue.put("_scalar", true);
        raw.setDefaultValue(defaultValue);

        FlagConfigServiceImpl service = createService(raw);

        FlagConfig config = service.getFlagConfigByName("my-flag").orElseThrow();
        assertThat(config.rawErrorStrategy()).isEqualTo(ErrorStrategy.DEFAULT_VALUE);
    }

    @Test
    void usesCallbackStrategyWhenExplicitlySet() {
        RawFlagConfig raw = createRawConfig("key-1", "my-flag", FlagValueType.BOOLEAN);
        raw.setErrorStrategy(ErrorStrategy.CALLBACK);
        raw.setCallbackBean("myCallbackBean");

        FlagConfigServiceImpl service = createService(raw);

        FlagConfig config = service.getFlagConfigByName("my-flag").orElseThrow();
        assertThat(config.rawErrorStrategy()).isEqualTo(ErrorStrategy.CALLBACK);
    }

    @Test
    void usesFailStrategyWhenExplicitlySet() {
        RawFlagConfig raw = createRawConfig("key-1", "my-flag", FlagValueType.BOOLEAN);
        raw.setErrorStrategy(ErrorStrategy.FAIL);

        FlagConfigServiceImpl service = createService(raw);

        FlagConfig config = service.getFlagConfigByName("my-flag").orElseThrow();
        assertThat(config.rawErrorStrategy()).isEqualTo(ErrorStrategy.FAIL);
    }

    @Test
    void usesNullValueStrategyWhenExplicitlySet() {
        RawFlagConfig raw = createRawConfig("key-1", "my-flag", FlagValueType.BOOLEAN);
        raw.setErrorStrategy(ErrorStrategy.NULL_VALUE);

        FlagConfigServiceImpl service = createService(raw);

        FlagConfig config = service.getFlagConfigByName("my-flag").orElseThrow();
        assertThat(config.rawErrorStrategy()).isEqualTo(ErrorStrategy.NULL_VALUE);
    }

    @Test
    void defaultsToDefaultValueStrategyWhenNotExplicitlySet() {
        // RawFlagConfig has default errorStrategy=DEFAULT_VALUE
        RawFlagConfig raw = createRawConfig("key-1", "my-flag", FlagValueType.BOOLEAN);

        FlagConfigServiceImpl service = createService(raw);

        FlagConfig config = service.getFlagConfigByName("my-flag").orElseThrow();
        assertThat(config.rawErrorStrategy()).isEqualTo(ErrorStrategy.DEFAULT_VALUE);
    }

    // ========== 4. Default Value Coercion Tests ==========

    @Test
    void coercesBooleanFromStringTrue() {
        RawFlagConfig raw = createRawConfigWithDefault("key-1", "bool-flag", FlagValueType.BOOLEAN, "true");

        FlagConfigServiceImpl service = createService(raw);

        FlagConfig config = service.getFlagConfigByName("bool-flag").orElseThrow();
        assertThat(config.defaultValue()).isEqualTo(true);
    }

    @Test
    void coercesBooleanFromStringFalse() {
        RawFlagConfig raw = createRawConfigWithDefault("key-1", "bool-flag", FlagValueType.BOOLEAN, "false");

        FlagConfigServiceImpl service = createService(raw);

        FlagConfig config = service.getFlagConfigByName("bool-flag").orElseThrow();
        assertThat(config.defaultValue()).isEqualTo(false);
    }

    @Test
    void coercesBooleanFromNumberOne() {
        RawFlagConfig raw = createRawConfigWithDefault("key-1", "bool-flag", FlagValueType.BOOLEAN, 1);

        FlagConfigServiceImpl service = createService(raw);

        FlagConfig config = service.getFlagConfigByName("bool-flag").orElseThrow();
        assertThat(config.defaultValue()).isEqualTo(true);
    }

    @Test
    void coercesBooleanFromNumberZero() {
        RawFlagConfig raw = createRawConfigWithDefault("key-1", "bool-flag", FlagValueType.BOOLEAN, 0);

        FlagConfigServiceImpl service = createService(raw);

        FlagConfig config = service.getFlagConfigByName("bool-flag").orElseThrow();
        assertThat(config.defaultValue()).isEqualTo(false);
    }

    @Test
    void coercesIntegerFromDouble() {
        RawFlagConfig raw = createRawConfigWithDefault("key-1", "int-flag", FlagValueType.INTEGER, 3.14);

        FlagConfigServiceImpl service = createService(raw);

        FlagConfig config = service.getFlagConfigByName("int-flag").orElseThrow();
        assertThat(config.defaultValue()).isEqualTo(3);
    }

    @Test
    void coercesIntegerFromString() {
        RawFlagConfig raw = createRawConfigWithDefault("key-1", "int-flag", FlagValueType.INTEGER, "42");

        FlagConfigServiceImpl service = createService(raw);

        FlagConfig config = service.getFlagConfigByName("int-flag").orElseThrow();
        assertThat(config.defaultValue()).isEqualTo(42);
    }

    @Test
    void coercesDoubleFromInteger() {
        RawFlagConfig raw = createRawConfigWithDefault("key-1", "double-flag", FlagValueType.DOUBLE, 42);

        FlagConfigServiceImpl service = createService(raw);

        FlagConfig config = service.getFlagConfigByName("double-flag").orElseThrow();
        assertThat(config.defaultValue()).isEqualTo(42.0);
    }

    @Test
    void coercesDoubleFromString() {
        RawFlagConfig raw = createRawConfigWithDefault("key-1", "double-flag", FlagValueType.DOUBLE, "3.14");

        FlagConfigServiceImpl service = createService(raw);

        FlagConfig config = service.getFlagConfigByName("double-flag").orElseThrow();
        assertThat(config.defaultValue()).isEqualTo(3.14);
    }

    @Test
    void providesTypeSafeDefaultForBooleanWhenNoDefaultValue() {
        RawFlagConfig raw = createRawConfig("key-1", "bool-flag", FlagValueType.BOOLEAN);

        FlagConfigServiceImpl service = createService(raw);

        FlagConfig config = service.getFlagConfigByName("bool-flag").orElseThrow();
        assertThat(config.defaultValue()).isEqualTo(false);
    }

    @Test
    void providesTypeSafeDefaultForStringWhenNoDefaultValue() {
        RawFlagConfig raw = createRawConfig("key-1", "string-flag", FlagValueType.STRING);

        FlagConfigServiceImpl service = createService(raw);

        FlagConfig config = service.getFlagConfigByName("string-flag").orElseThrow();
        assertThat(config.defaultValue()).isEqualTo("");
    }

    @Test
    void providesTypeSafeDefaultForIntegerWhenNoDefaultValue() {
        RawFlagConfig raw = createRawConfig("key-1", "int-flag", FlagValueType.INTEGER);

        FlagConfigServiceImpl service = createService(raw);

        FlagConfig config = service.getFlagConfigByName("int-flag").orElseThrow();
        assertThat(config.defaultValue()).isEqualTo(0);
    }

    @Test
    void providesTypeSafeDefaultForDoubleWhenNoDefaultValue() {
        RawFlagConfig raw = createRawConfig("key-1", "double-flag", FlagValueType.DOUBLE);

        FlagConfigServiceImpl service = createService(raw);

        FlagConfig config = service.getFlagConfigByName("double-flag").orElseThrow();
        assertThat(config.defaultValue()).isEqualTo(0.0);
    }

    @Test
    void providesTypeSafeDefaultForObjectWhenNoDefaultValue() {
        RawFlagConfig raw = createRawConfig("key-1", "object-flag", FlagValueType.OBJECT);

        FlagConfigServiceImpl service = createService(raw);

        FlagConfig config = service.getFlagConfigByName("object-flag").orElseThrow();
        assertThat(config.defaultValue()).isEqualTo(Map.of());
    }

    // ========== 5. Lookup Method Tests ==========

    @Test
    void findsFlagConfigByName() {
        RawFlagConfig raw = createRawConfig("key-1", "my-flag", FlagValueType.BOOLEAN);

        FlagConfigServiceImpl service = createService(raw);

        assertThat(service.getFlagConfigByName("my-flag")).isPresent();
        assertThat(service.getFlagConfigByName("my-flag").get().name()).isEqualTo("my-flag");
    }

    @Test
    void findsFlagConfigByKey() {
        RawFlagConfig raw = createRawConfig("key-1", "my-flag", FlagValueType.BOOLEAN);

        FlagConfigServiceImpl service = createService(raw);

        assertThat(service.getFlagConfigByKey("key-1")).isPresent();
        assertThat(service.getFlagConfigByKey("key-1").get().key()).isEqualTo("key-1");
    }

    @Test
    void findsFlagKeyByName() {
        RawFlagConfig raw = createRawConfig("key-1", "my-flag", FlagValueType.BOOLEAN);

        FlagConfigServiceImpl service = createService(raw);

        assertThat(service.findFlagKeyByName("my-flag")).isPresent();
        assertThat(service.findFlagKeyByName("my-flag").get()).isEqualTo("key-1");
    }

    @Test
    void returnsEmptyWhenNameNotFound() {
        RawFlagConfig raw = createRawConfig("key-1", "my-flag", FlagValueType.BOOLEAN);

        FlagConfigServiceImpl service = createService(raw);

        assertThat(service.getFlagConfigByName("unknown-flag")).isEmpty();
        assertThat(service.findFlagKeyByName("unknown-flag")).isEmpty();
    }

    @Test
    void returnsEmptyWhenKeyNotFound() {
        RawFlagConfig raw = createRawConfig("key-1", "my-flag", FlagValueType.BOOLEAN);

        FlagConfigServiceImpl service = createService(raw);

        assertThat(service.getFlagConfigByKey("unknown-key")).isEmpty();
    }

    @Test
    void getAllFlagConfigsReturnsAllConfigs() {
        RawFlagConfig raw1 = createRawConfig("key-1", "flag-1", FlagValueType.BOOLEAN);
        RawFlagConfig raw2 = createRawConfig("key-2", "flag-2", FlagValueType.STRING);
        RawFlagConfig raw3 = createRawConfig("key-3", "flag-3", FlagValueType.INTEGER);

        FlagConfigServiceImpl service = createService(raw1, raw2, raw3);

        assertThat(service.getAllFlagConfigs()).hasSize(3);
        assertThat(service.getAllFlagConfigs())
            .extracting(FlagConfig::name)
            .containsExactlyInAnyOrder("flag-1", "flag-2", "flag-3");
    }

    // ========== 6. Edge Case Tests ==========

    @Test
    void handlesEmptyFlagsList() {
        FlagConfigServiceImpl service = createService(List.of());

        assertThat(service.getAllFlagConfigs()).isEmpty();
        assertThat(service.getFlagConfigByName("any")).isEmpty();
        assertThat(service.getFlagConfigByKey("any")).isEmpty();
    }

    @Test
    void handlesNullFlagsPropertyGracefully() {
        // FlagsProperties constructor handles null by returning empty list
        FlagConfigServiceImpl service = new FlagConfigServiceImpl(new FlagsProperties(null), errorStrategyFactory);

        assertThat(service.getAllFlagConfigs()).isEmpty();
    }

    @Test
    void lastConfigWinsOnDuplicateName() {
        RawFlagConfig raw1 = createRawConfig("key-1", "same-name", FlagValueType.BOOLEAN);
        RawFlagConfig raw2 = createRawConfig("key-2", "same-name", FlagValueType.STRING);

        FlagConfigServiceImpl service = createService(raw1, raw2);

        FlagConfig config = service.getFlagConfigByName("same-name").orElseThrow();
        assertThat(config.key()).isEqualTo("key-2");
        assertThat(config.valueType()).isEqualTo(FlagValueType.STRING);
    }

    @Test
    void lastConfigWinsOnDuplicateKey() {
        RawFlagConfig raw1 = createRawConfig("same-key", "flag-1", FlagValueType.BOOLEAN);
        RawFlagConfig raw2 = createRawConfig("same-key", "flag-2", FlagValueType.STRING);

        FlagConfigServiceImpl service = createService(raw1, raw2);

        FlagConfig config = service.getFlagConfigByKey("same-key").orElseThrow();
        assertThat(config.name()).isEqualTo("flag-2");
        assertThat(config.valueType()).isEqualTo(FlagValueType.STRING);
    }

    @Test
    void usesNameAsDescriptionWhenDescriptionNull() {
        RawFlagConfig raw = createRawConfig("key-1", "my-flag", FlagValueType.BOOLEAN);
        raw.setDescription(null);

        FlagConfigServiceImpl service = createService(raw);

        FlagConfig config = service.getFlagConfigByName("my-flag").orElseThrow();
        assertThat(config.description()).isEqualTo("my-flag");
    }
}
