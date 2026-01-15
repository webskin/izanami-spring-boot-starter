package fr.maif.izanami.spring.openfeature;

import dev.openfeature.sdk.FlagValueType;
import fr.maif.izanami.spring.autoconfigure.DefaultValueMap;
import fr.maif.izanami.spring.openfeature.api.IzanamiErrorCallback;
import fr.maif.izanami.spring.openfeature.internal.RawFlagConfig;
import org.springframework.lang.Nullable;

import java.util.Map;

/**
 * Fluent builder for configuring a single feature flag.
 * <p>
 * This builder is used within the lambda passed to {@link FlagsPropertiesBuilder#flag(String, java.util.function.Consumer)}.
 * All setter methods return {@code this} for method chaining.
 *
 * <pre>{@code
 * FlagsProperties.builder()
 *     .flag("my-feature", flag -> flag
 *         .key("abc-123-uuid")
 *         .description("My feature flag")
 *         .valueType(FlagValueType.BOOLEAN)
 *         .errorStrategy(ErrorStrategy.DEFAULT_VALUE)
 *         .defaultValue(false))
 *     .build();
 * }</pre>
 *
 * @see FlagsPropertiesBuilder
 * @see FlagsProperties#builder()
 */
public final class FlagConfigBuilder {

    private static final String SCALAR_KEY = "_scalar";

    private String key;
    private String description;
    private FlagValueType valueType = FlagValueType.BOOLEAN;
    private ErrorStrategy errorStrategy;
    private Object defaultValue;
    private String callbackBean;

    FlagConfigBuilder() {
    }

    /**
     * Sets the Izanami feature key (UUID).
     *
     * @param key the Izanami feature UUID
     * @return this builder
     */
    public FlagConfigBuilder key(String key) {
        this.key = key;
        return this;
    }

    /**
     * Sets a human-readable description for the flag.
     *
     * @param description the flag description
     * @return this builder
     */
    public FlagConfigBuilder description(String description) {
        this.description = description;
        return this;
    }

    /**
     * Sets the value type of the flag.
     * <p>
     * Defaults to {@link FlagValueType#BOOLEAN} if not specified.
     *
     * @param valueType the flag value type
     * @return this builder
     */
    public FlagConfigBuilder valueType(FlagValueType valueType) {
        this.valueType = valueType;
        return this;
    }

    /**
     * Sets the error handling strategy.
     * <p>
     * If not specified and {@code defaultValue} is set, defaults to {@link ErrorStrategy#DEFAULT_VALUE}.
     *
     * @param errorStrategy the error strategy
     * @return this builder
     */
    public FlagConfigBuilder errorStrategy(ErrorStrategy errorStrategy) {
        this.errorStrategy = errorStrategy;
        return this;
    }

    /**
     * Sets the default value to use when the flag cannot be evaluated.
     * <p>
     * The value type should match the configured {@link #valueType(FlagValueType)}:
     * <ul>
     *   <li>{@code BOOLEAN} - {@link Boolean}</li>
     *   <li>{@code STRING} - {@link String}</li>
     *   <li>{@code INTEGER} - {@link Integer} or {@link Long}</li>
     *   <li>{@code DOUBLE} - {@link Double} or other {@link Number}</li>
     *   <li>{@code OBJECT} - {@link Map} or JSON-serializable object</li>
     * </ul>
     *
     * @param defaultValue the default value
     * @return this builder
     */
    public FlagConfigBuilder defaultValue(@Nullable Object defaultValue) {
        this.defaultValue = defaultValue;
        return this;
    }

    /**
     * Sets the Spring bean name for the {@link IzanamiErrorCallback} to invoke
     * when using {@link ErrorStrategy#CALLBACK}.
     *
     * @param callbackBean the callback bean name
     * @return this builder
     */
    public FlagConfigBuilder callbackBean(String callbackBean) {
        this.callbackBean = callbackBean;
        return this;
    }

    /**
     * Creates a {@link RawFlagConfig} from this builder's configuration.
     *
     * @param name the flag name (set by the parent builder)
     * @return a configured RawFlagConfig
     */
    RawFlagConfig toRawFlagConfig(String name) {
        RawFlagConfig raw = new RawFlagConfig();
        raw.setKey(key);
        raw.setName(name);
        raw.setDescription(description);
        raw.setValueType(valueType);
        raw.setErrorStrategy(errorStrategy);
        raw.setDefaultValue(wrapDefaultValue(defaultValue));
        raw.setCallbackBean(callbackBean);
        return raw;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private DefaultValueMap wrapDefaultValue(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        DefaultValueMap map = new DefaultValueMap();
        if (value instanceof Map) {
            map.putAll((Map<String, Object>) value);
        } else {
            map.put(SCALAR_KEY, value);
        }
        return map;
    }
}
