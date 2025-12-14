package fr.maif.izanami.spring.openfeature.api;

import fr.maif.izanami.spring.openfeature.FlagConfig;

import java.util.Collection;
import java.util.Optional;

/**
 * Access to flag configuration loaded from {@code openfeature.flags}.
 */
public interface FlagConfigService {
    /**
     * Find a flag key by its configured name.
     *
     * @param name flag name (OpenFeature key)
     * @return the configured key (Izanami feature key)
     */
    Optional<String> findFlagKeyByName(String name);

    /**
     * Get a flag configuration by its name.
     *
     * @param name flag name
     * @return flag configuration if present
     */
    Optional<FlagConfig> getFlagConfigByName(String name);

    /**
     * Get a flag configuration by its key.
     *
     * @param key Izanami feature key
     * @return flag configuration if present
     */
    Optional<FlagConfig> getFlagConfigByKey(String key);

    /**
     * @return all configured flag configurations.
     */
    Collection<FlagConfig> getAllFlagConfigs();
}

