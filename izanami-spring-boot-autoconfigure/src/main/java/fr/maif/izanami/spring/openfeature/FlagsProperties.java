package fr.maif.izanami.spring.openfeature;

import fr.maif.izanami.spring.openfeature.internal.RawFlagConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
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
public class FlagsProperties {

    private List<RawFlagConfig> flags = new ArrayList<>();

    public List<RawFlagConfig> getFlags() {
        return flags;
    }

    public void setFlags(List<RawFlagConfig> flags) {
        this.flags = flags == null ? new ArrayList<>() : new ArrayList<>(flags);
    }
}
