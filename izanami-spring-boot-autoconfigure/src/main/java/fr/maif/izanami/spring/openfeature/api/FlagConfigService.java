package fr.maif.izanami.spring.openfeature.api;

import fr.maif.izanami.spring.openfeature.FlagConfig;

import java.util.Collection;
import java.util.Optional;

/**
 * Access to flag configuration loaded from {@code openfeature.flags}.
 */
public interface FlagConfigService {
    /**
     * Find a flag id by its configured name.
     *
     * @param name flag name (OpenFeature key)
     * @return the configured id (Izanami feature id)
     */
    Optional<String> findFlagIdByName(String name);

    /**
     * Get a flag configuration by its name.
     *
     * @param name flag name
     * @return flag configuration if present
     */
    Optional<FlagConfig> getFlagConfigByName(String name);

    /**
     * Get a flag configuration by its id.
     *
     * @param id Izanami feature id
     * @return flag configuration if present
     */
    Optional<FlagConfig> getFlagConfigById(String id);

    /**
     * @return all configured flag configurations.
     */
    Collection<FlagConfig> getAllFlagConfigs();
}

