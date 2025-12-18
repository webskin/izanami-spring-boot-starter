package fr.maif.izanami.spring.openfeature;

/**
 * Keys used in OpenFeature {@code flagMetadata} to expose configuration and evaluation diagnostics.
 */
public final class FlagMetadataKeys {

    private FlagMetadataKeys() {
    }

    /**
     * Metadata key: flag key from configuration.
     */
    public static final String FLAG_CONFIG_KEY = "FLAG_CONFIG_KEY";
    /**
     * Metadata key: flag name from configuration.
     */
    public static final String FLAG_CONFIG_NAME = "FLAG_CONFIG_NAME";
    /**
     * Metadata key: flag description from configuration.
     */
    public static final String FLAG_CONFIG_DESCRIPTION = "FLAG_CONFIG_DESCRIPTION";
    /**
     * Metadata key: configured {@link dev.openfeature.sdk.FlagValueType}.
     */
    public static final String FLAG_CONFIG_VALUE_TYPE = "FLAG_CONFIG_VALUE_TYPE";
    /**
     * Metadata key: configured default value (stringified).
     */
    public static final String FLAG_CONFIG_DEFAULT_VALUE = "FLAG_CONFIG_DEFAULT_VALUE";
    /**
     * Metadata key: configured {@link ErrorStrategy}.
     */
    public static final String FLAG_CONFIG_ERROR_STRATEGY = "FLAG_CONFIG_ERROR_STRATEGY";

    /**
     * Metadata key: {@link FlagValueSource} for the returned value.
     */
    public static final String FLAG_VALUE_SOURCE = "FLAG_VALUE_SOURCE";

    /**
     * Metadata key: evaluation reason (e.g., "DISABLED", ""ORIGIN_OR_CACHE", "ERROR").
     */
    public static final String FLAG_EVALUATION_REASON = "FLAG_EVALUATION_REASON";
}

