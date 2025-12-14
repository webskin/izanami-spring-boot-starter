package fr.maif.izanami.spring.openfeature;

/**
 * Supported OpenFeature value types for Izanami-backed flags.
 * <p>
 * {@link #OBJECT} represents a JSON-compatible object/array value.
 */
public enum EvaluationValueType {
    /**
     * Boolean feature flag value.
     */
    BOOLEAN,
    /**
     * String feature flag value.
     */
    STRING,
    /**
     * Number feature flag value (represented as {@code BigDecimal} internally).
     */
    NUMBER,
    /**
     * JSON object/array feature flag value.
     */
    OBJECT;

    /**
     * Parse a configuration string (case-insensitive).
     *
     * @param value configuration value such as {@code "boolean"} or {@code "OBJECT"}
     * @return the parsed type, or {@link #BOOLEAN} when the value is blank/unknown
     */
    public static EvaluationValueType fromString(String value) {
        if (value == null || value.isBlank()) {
            return BOOLEAN;
        }
        String normalized = value.trim().toUpperCase().replace("-", "_");
        try {
            return EvaluationValueType.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return BOOLEAN;
        }
    }
}

