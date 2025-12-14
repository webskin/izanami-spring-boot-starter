package fr.maif.izanami.spring.openfeature;

import fr.maif.izanami.spring.openfeature.internal.RawFlagConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuration properties for OpenFeature flags.
 * <p>
 * Binds to {@code openfeature.flags}.
 * <p>
 * Note: This uses {@link RawFlagConfig} for YAML binding. The {@link FlagConfigServiceImpl}
 * transforms these into immutable {@link FlagConfig} instances.
 */
@ConfigurationProperties(prefix = "openfeature")
public record FlagsProperties(List<RawFlagConfig> flags) {
    /**
     * Compact constructor with immutable default.
     */
    public FlagsProperties {
        flags = flags == null ? List.of() : List.copyOf(flags);
    }
}

