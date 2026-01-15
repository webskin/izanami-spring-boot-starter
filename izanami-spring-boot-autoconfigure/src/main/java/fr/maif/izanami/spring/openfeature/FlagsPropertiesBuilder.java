package fr.maif.izanami.spring.openfeature;

import fr.maif.izanami.spring.openfeature.internal.RawFlagConfig;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Fluent builder for creating {@link FlagsProperties} with multiple flag configurations.
 * <p>
 * Use {@link FlagsProperties#builder()} to obtain an instance. Flags are configured using
 * a lambda-based API where each flag's configuration is scoped within its lambda:
 *
 * <pre>{@code
 * FlagsProperties properties = FlagsProperties.builder()
 *     .flag("my-feature", flag -> flag
 *         .key("abc-123-uuid")
 *         .valueType(FlagValueType.BOOLEAN)
 *         .defaultValue(false))
 *     .flag("another-feature", flag -> flag
 *         .key("def-456-uuid")
 *         .valueType(FlagValueType.STRING)
 *         .defaultValue("default"))
 *     .build();
 * }</pre>
 *
 * <p>Flags are stored in insertion order. If the same flag name is added multiple times,
 * the later configuration overwrites the earlier one.
 *
 * @see FlagsProperties#builder()
 * @see FlagConfigBuilder
 * @see OpenFeatureFlags
 */
public final class FlagsPropertiesBuilder {

    private final Map<String, RawFlagConfig> flags = new LinkedHashMap<>();

    FlagsPropertiesBuilder() {
    }

    /**
     * Configures a flag with the given name.
     * <p>
     * The configurer lambda receives a fresh {@link FlagConfigBuilder} to configure
     * the flag's properties. The flag is added to this builder when the lambda completes.
     *
     * @param name       the flag name (used as the OpenFeature flag key)
     * @param configurer a lambda that configures the flag
     * @return this builder for method chaining
     */
    public FlagsPropertiesBuilder flag(String name, Consumer<FlagConfigBuilder> configurer) {
        FlagConfigBuilder builder = new FlagConfigBuilder();
        configurer.accept(builder);
        flags.put(name, builder.toRawFlagConfig(name));
        return this;
    }

    /**
     * Builds the {@link FlagsProperties} with all configured flags.
     *
     * @return a new FlagsProperties instance containing the configured flags
     */
    public FlagsProperties build() {
        FlagsProperties properties = new FlagsProperties();
        properties.setFlags(new LinkedHashMap<>(flags));
        return properties;
    }
}
