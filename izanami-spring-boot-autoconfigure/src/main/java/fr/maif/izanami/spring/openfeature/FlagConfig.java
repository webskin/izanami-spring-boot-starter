package fr.maif.izanami.spring.openfeature;

import dev.openfeature.sdk.FlagValueType;
import fr.maif.FeatureClientErrorStrategy;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * Immutable configuration for a single OpenFeature flag backed by Izanami.
 * <p>
 * Instances are created by {@link fr.maif.izanami.spring.openfeature.internal.FlagConfigServiceImpl}
 * from the raw YAML-bound configuration, with default values already coerced to the correct type.
 *
 * @param key              the feature key
 * @param name             the OpenFeature flag key (human-friendly name)
 * @param description      the flag description
 * @param valueType        the configured value type (never null, defaults to BOOLEAN)
 * @param rawErrorStrategy the configured error strategy enum (never null, defaults to DEFAULT_VALUE)
 * @param errorStrategy    the computed Izanami client error strategy
 * @param defaultValue     the default value, already coerced to the correct type based on valueType
 * @param callbackBean     the Spring bean name for error callback (used with CALLBACK strategy)
 */
public record FlagConfig(
    @NonNull
    String key,
    @NonNull
    String name,
    @NonNull
    String description,
    @NonNull
    FlagValueType valueType,
    @NonNull
    ErrorStrategy rawErrorStrategy,
    @NonNull
    FeatureClientErrorStrategy<?> errorStrategy,
    @Nullable Object defaultValue,
    @Nullable String callbackBean
) {

}
