package fr.maif.izanami.spring.autoconfigure;

import dev.openfeature.sdk.FlagValueType;

/**
 * Internal parser for flexible configuration binding of {@link FlagValueType}.
 * <p>
 * Supports case-insensitive parsing and aliases (e.g., "number" maps to {@link FlagValueType#DOUBLE}).
 */
final class FlagValueTypeParser {
    private FlagValueTypeParser() {}

    /**
     * Parse a configuration string to {@link FlagValueType}.
     * <p>
     * Parsing is case-insensitive and supports the following values:
     * <ul>
     *   <li>{@code boolean} → {@link FlagValueType#BOOLEAN}</li>
     *   <li>{@code string} → {@link FlagValueType#STRING}</li>
     *   <li>{@code integer} → {@link FlagValueType#INTEGER}</li>
     *   <li>{@code double} → {@link FlagValueType#DOUBLE}</li>
     *   <li>{@code number} → {@link FlagValueType#DOUBLE} (alias for backward compatibility)</li>
     *   <li>{@code object} → {@link FlagValueType#OBJECT}</li>
     * </ul>
     *
     * @param value configuration value
     * @return the parsed type, or {@link FlagValueType#BOOLEAN} when the value is blank/unknown
     */
    public static FlagValueType fromString(String value) {
        if (value == null || value.isBlank()) {
            return FlagValueType.BOOLEAN;
        }
        String normalized = value.trim().toUpperCase().replace("-", "_");

        // Handle "number" alias for backward compatibility
        if ("NUMBER".equals(normalized)) {
            return FlagValueType.DOUBLE;
        }

        try {
            return FlagValueType.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return FlagValueType.BOOLEAN;
        }
    }
}
