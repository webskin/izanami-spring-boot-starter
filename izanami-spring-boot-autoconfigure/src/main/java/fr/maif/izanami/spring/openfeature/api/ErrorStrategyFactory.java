package fr.maif.izanami.spring.openfeature.api;

import fr.maif.FeatureClientErrorStrategy;
import fr.maif.izanami.spring.openfeature.FlagConfig;

/**
 * Builds Izanami client error strategies from a {@link FlagConfig}.
 */
public interface ErrorStrategyFactory {
    /**
     * Create an Izanami client error strategy for a given flag configuration.
     *
     * @param config flag configuration
     * @return the Izanami client error strategy (never {@code null})
     */
    FeatureClientErrorStrategy<?> createErrorStrategy(FlagConfig config);
}

