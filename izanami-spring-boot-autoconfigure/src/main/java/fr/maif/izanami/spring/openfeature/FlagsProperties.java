package fr.maif.izanami.spring.openfeature;

import fr.maif.izanami.spring.openfeature.internal.RawFlagConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for OpenFeature flags.
 * <p>
 * Binds to {@code openfeature.flags} as a map keyed by flag name by default.
 * Custom prefixes can be used by defining a {@code @Primary} bean with a different
 * {@code @ConfigurationProperties} prefix.
 * <p>
 * Note: This uses {@link RawFlagConfig} for YAML binding. The {@link FlagConfigServiceImpl}
 * transforms these into immutable {@link FlagConfig} instances.
 */
public class FlagsProperties {

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
