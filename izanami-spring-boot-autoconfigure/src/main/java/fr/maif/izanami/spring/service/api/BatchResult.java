package fr.maif.izanami.spring.service.api;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Result of a batch feature flag evaluation.
 * <p>
 * Provides access to individual flag values by their identifiers (keys or names,
 * depending on how the request was built).
 * <p>
 * Example usage:
 * <pre>{@code
 * BatchResult result = izanamiService.forFlagNames("feature-a", "feature-b")
 *     .values()
 *     .join();
 *
 * Boolean enabled = result.booleanValue("feature-a");
 * String value = result.stringValue("feature-b");
 *
 * // With details
 * ResultValueWithDetails<Boolean> details = result.booleanValueDetails("feature-a");
 * }</pre>
 *
 * @see BatchFeatureRequestBuilder
 */
public interface BatchResult {

    /**
     * Get boolean value for a flag by its identifier.
     *
     * @param flagId the flag key (if requested by keys) or name (if requested by names)
     * @return the boolean value, or null if flag not found in result
     */
    Boolean booleanValue(String flagId);

    /**
     * Get string value for a flag by its identifier.
     *
     * @param flagId the flag key (if requested by keys) or name (if requested by names)
     * @return the string value, or null if flag not found in result
     */
    String stringValue(String flagId);

    /**
     * Get number value for a flag by its identifier.
     *
     * @param flagId the flag key (if requested by keys) or name (if requested by names)
     * @return the number value as BigDecimal, or null if flag not found in result
     */
    BigDecimal numberValue(String flagId);

    /**
     * Get boolean value with evaluation details.
     *
     * @param flagId the flag key (if requested by keys) or name (if requested by names)
     * @return the result with value and metadata, or empty result if flag not found
     */
    ResultValueWithDetails<Boolean> booleanValueDetails(String flagId);

    /**
     * Get string value with evaluation details.
     *
     * @param flagId the flag key (if requested by keys) or name (if requested by names)
     * @return the result with value and metadata, or empty result if flag not found
     */
    ResultValueWithDetails<String> stringValueDetails(String flagId);

    /**
     * Get number value with evaluation details.
     *
     * @param flagId the flag key (if requested by keys) or name (if requested by names)
     * @return the result with value and metadata, or empty result if flag not found
     */
    ResultValueWithDetails<BigDecimal> numberValueDetails(String flagId);

    /**
     * Get all flag identifiers in this result.
     *
     * @return set of all flag identifiers (keys or names depending on request)
     */
    Set<String> flagIdentifiers();

    /**
     * Check if a flag is present in this result.
     *
     * @param flagId the flag identifier to check
     * @return true if the flag is present in the result
     */
    boolean hasFlag(String flagId);
}
