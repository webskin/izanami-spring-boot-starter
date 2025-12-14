package fr.maif.izanami.spring.openfeature.internal;

import fr.maif.izanami.spring.openfeature.ErrorStrategy;
import fr.maif.izanami.spring.openfeature.FlagConfig;
import fr.maif.izanami.spring.openfeature.FlagsProperties;
import fr.maif.izanami.spring.openfeature.api.FlagConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Default {@link FlagConfigService} implementation backed by {@link FlagsProperties}.
 */
public final class FlagConfigServiceImpl implements FlagConfigService {
    private static final Logger log = LoggerFactory.getLogger(FlagConfigServiceImpl.class);

    private final Map<String, FlagConfig> configsByName;
    private final Map<String, FlagConfig> configsById;
    private final Map<String, String> nameToId;

    /**
     * Create a service from configured {@code openfeature.flags}.
     *
     * @param flagsProperties properties
     */
    public FlagConfigServiceImpl(FlagsProperties flagsProperties) {
        Map<String, FlagConfig> byName = new LinkedHashMap<>();
        Map<String, FlagConfig> byId = new LinkedHashMap<>();
        Map<String, String> nameToIdMap = new LinkedHashMap<>();

        for (FlagConfig config : flagsProperties.flags()) {
            if (config == null) {
                continue;
            }
            validateConfig(config);
            if (config.name() != null) {
                FlagConfig previous = byName.put(config.name(), config);
                if (previous != null && previous != config) {
                    log.warn("Duplicate flag name '{}' detected; last one wins", config.name());
                }
                if (config.id() != null) {
                    nameToIdMap.put(config.name(), config.id());
                }
            }
            if (config.id() != null) {
                FlagConfig previous = byId.put(config.id(), config);
                if (previous != null && previous != config) {
                    log.warn("Duplicate flag id '{}' detected; last one wins", config.id());
                }
            }
        }

        this.configsByName = Map.copyOf(byName);
        this.configsById = Map.copyOf(byId);
        this.nameToId = Map.copyOf(nameToIdMap);
    }

    @Override
    public Optional<String> findFlagIdByName(String name) {
        return Optional.ofNullable(nameToId.get(name));
    }

    @Override
    public Optional<FlagConfig> getFlagConfigByName(String name) {
        return Optional.ofNullable(configsByName.get(name));
    }

    @Override
    public Optional<FlagConfig> getFlagConfigById(String id) {
        return Optional.ofNullable(configsById.get(id));
    }

    @Override
    public Collection<FlagConfig> getAllFlagConfigs() {
        return configsByName.values();
    }

    private void validateConfig(FlagConfig config) {
        ErrorStrategy strategy = config.errorStrategy();
        if (strategy != ErrorStrategy.DEFAULT_VALUE && config.getDefaultValue() != null) {
            String flagIdentifier = config.name() != null ? config.name() : config.id();
            throw new IllegalArgumentException(
                "Flag '" + flagIdentifier + "' has errorStrategy=" + strategy.name()
                    + " but also defines a defaultValue. "
                    + "The defaultValue property is only valid with errorStrategy=DEFAULT_VALUE."
            );
        }
    }
}

