package fr.maif.izanami.spring.openfeature.api;

import dev.openfeature.sdk.FlagValueType;
import fr.maif.FeatureClientErrorStrategy;
import fr.maif.izanami.spring.openfeature.ErrorStrategy;
import org.springframework.lang.Nullable;

/**
 * Factory for creating Izanami client error strategies from flag configuration parameters.
 */
public interface ErrorStrategyFactory {
    /**
     * Create an Izanami client error strategy for the given parameters.
     *
     * @param strategy     the error strategy enum
     * @param valueType    the flag value type
     * @param defaultValue the default value (may be null)
     * @param callbackBean the callback bean name (may be null)
     * @param flagKey      the flag key for logging and callback purposes
     * @return the Izanami client error strategy (never {@code null})
     */
    FeatureClientErrorStrategy<?> createErrorStrategy(
        ErrorStrategy strategy,
        FlagValueType valueType,
        @Nullable Object defaultValue,
        @Nullable String callbackBean,
        String flagKey
    );
}

