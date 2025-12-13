package fr.maif.izanami.spring.openfeature;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuration properties for OpenFeature flags backed by Izanami.
 * <p>
 * Binds to {@code openfeature.flags}.
 */
@ConfigurationProperties(prefix = "openfeature")
public record FlagsProperties(List<FlagConfig> flags) {
    /**
     * Compact constructor with immutable default.
     */
    public FlagsProperties {
        flags = flags == null ? List.of() : List.copyOf(flags);
    }
}

