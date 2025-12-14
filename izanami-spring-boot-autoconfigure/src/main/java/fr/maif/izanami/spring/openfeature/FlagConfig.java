package fr.maif.izanami.spring.openfeature;

import dev.openfeature.sdk.FlagValueType;
import org.springframework.lang.Nullable;

/**
 * Immutable configuration for a single OpenFeature flag backed by Izanami.
 * <p>
 * Instances are created by {@link fr.maif.izanami.spring.openfeature.internal.FlagConfigServiceImpl}
 * from the raw YAML-bound configuration, with default values already coerced to the correct type.
 *
 * @param id            the Izanami feature id
 * @param name          the OpenFeature flag key (human-friendly name)
 * @param description   the flag description
 * @param valueType     the configured value type (never null, defaults to BOOLEAN)
 * @param errorStrategy the configured error strategy (never null, defaults to DEFAULT_VALUE)
 * @param defaultValue  the default value, already coerced to the correct type based on valueType
 * @param callbackBean  the Spring bean name for error callback (used with CALLBACK strategy)
 */
public record FlagConfig(
    String id,
    String name,
    String description,
    FlagValueType valueType,
    ErrorStrategy errorStrategy,
    @Nullable Object defaultValue,
    @Nullable String callbackBean
) {

    /**
     * Get the default value as a Boolean.
     *
     * @param callerDefault fallback if no configured default
     * @return the boolean default value
     */
    @Nullable
    public Boolean booleanDefault(@Nullable Boolean callerDefault) {
        return defaultValue instanceof Boolean b ? b : callerDefault;
    }

    /**
     * Get the default value as a String.
     *
     * @param callerDefault fallback if no configured default
     * @return the string default value
     */
    @Nullable
    public String stringDefault(@Nullable String callerDefault) {
        return defaultValue instanceof String s ? s : callerDefault;
    }

    /**
     * Get the default value as an Integer.
     *
     * @param callerDefault fallback if no configured default
     * @return the integer default value
     */
    @Nullable
    public Integer integerDefault(@Nullable Integer callerDefault) {
        return defaultValue instanceof Integer i ? i : callerDefault;
    }

    /**
     * Get the default value as a Double.
     *
     * @param callerDefault fallback if no configured default
     * @return the double default value
     */
    @Nullable
    public Double doubleDefault(@Nullable Double callerDefault) {
        return defaultValue instanceof Double d ? d : callerDefault;
    }
}
