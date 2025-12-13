package fr.maif.izanami.spring.openfeature;

/**
 * Error strategy applied when Izanami cannot provide a flag value.
 * <p>
 * The strategy is applied at evaluation time, per-flag, by configuring the Izanami client error strategy.
 */
public enum ErrorStrategy {
    /**
     * Return the configured {@code defaultValue}.
     */
    DEFAULT_VALUE,
    /**
     * Fail evaluation by throwing (surfaced through OpenFeature error details).
     */
    FAIL,
    /**
     * Return {@code null}.
     */
    NULL_VALUE,
    /**
     * Compute the value via a callback.
     * <p>
     * This starter does not support wiring custom callbacks from configuration; it uses {@code defaultValue}
     * as a best-effort callback fallback when configured.
     */
    CALLBACK;

    /**
     * Parse a configuration string (case-insensitive).
     *
     * @param value configuration value such as {@code "DEFAULT_VALUE"} or {@code "null-value"}
     * @return the parsed strategy, or {@link #DEFAULT_VALUE} when the value is blank/unknown
     */
    public static ErrorStrategy fromString(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_VALUE;
        }
        String normalized = value.trim().toUpperCase().replace("-", "_");
        try {
            return ErrorStrategy.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return DEFAULT_VALUE;
        }
    }
}

