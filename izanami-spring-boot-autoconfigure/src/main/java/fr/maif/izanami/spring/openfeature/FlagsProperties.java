package fr.maif.izanami.spring.openfeature;

import fr.maif.izanami.spring.openfeature.internal.FlagConfigServiceImpl;
import fr.maif.izanami.spring.openfeature.internal.RawFlagConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for OpenFeature flags.
 * <p>
 * Binds to {@code openfeature.flags} as a map keyed by flag name by default.
 * <p>
 * <b>Custom YAML prefix:</b> To use a different YAML prefix, define a bean named
 * {@code flagsProperties}:
 * <pre>{@code
 * @Bean(name = IzanamiAutoConfiguration.FLAGS_PROPERTIES_BEAN)
 * @ConfigurationProperties(prefix = "custom.prefix")
 * public FlagsProperties flagsProperties() {
 *     return new FlagsProperties();
 * }
 * }</pre>
 * <p>
 * <b>Programmatic configuration:</b> Use the fluent builder API with {@link OpenFeatureFlags}:
 * <pre>{@code
 * @Bean
 * @OpenFeatureFlags
 * public FlagsProperties programmaticFlags() {
 *     return FlagsProperties.builder()
 *         .flag("my-feature", flag -> flag
 *             .key("abc-123-uuid")
 *             .valueType(FlagValueType.BOOLEAN)
 *             .defaultValue(false))
 *         .build();
 * }
 * }</pre>
 * <p>
 * Note: This uses {@link RawFlagConfig} for YAML binding. The {@link FlagConfigServiceImpl}
 * transforms these into immutable {@link FlagConfig} instances.
 *
 * @see FlagsPropertiesBuilder
 * @see OpenFeatureFlags
 */
public class FlagsProperties {

    /**
     * Creates a new builder for programmatically configuring flags.
     *
     * @return a new FlagsPropertiesBuilder
     * @see OpenFeatureFlags
     */
    public static FlagsPropertiesBuilder builder() {
        return new FlagsPropertiesBuilder();
    }

    private Map<String, RawFlagConfig> flags = new LinkedHashMap<>();

    public Map<String, RawFlagConfig> getFlags() {
        return flags;
    }

    public void setFlags(Map<String, RawFlagConfig> flags) {
        this.flags = flags == null ? new LinkedHashMap<>() : new LinkedHashMap<>(flags);
    }

    public List<RawFlagConfig> getFlagConfigs() {
        List<RawFlagConfig> normalized = new ArrayList<>();
        for (Map.Entry<String, RawFlagConfig> entry : flags.entrySet()) {
            String name = entry.getKey();
            RawFlagConfig config = entry.getValue();
            if (name == null || name.isBlank() || config == null) {
                continue;
            }
            config.setName(name);
            normalized.add(config);
        }
        return normalized;
    }
}
