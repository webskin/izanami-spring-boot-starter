package fr.maif.izanami.spring.service.api;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

/**
 * Fluent builder for configuring and executing single feature flag evaluations.
 * <p>
 * Obtain an instance via {@link IzanamiService#forFlagKey(String)} or {@link IzanamiService#forFlagName(String)}.
 * <p>
 * Example usage:
 * <pre>{@code
 * izanamiService.forFlagName("my-feature")
 *     .withUser("user-123")
 *     .withContext("production")
 *     .booleanValue()
 *     .thenAccept(enabled -> { ... });
 * }</pre>
 *
 * @see IzanamiService#forFlagKey(String)
 * @see IzanamiService#forFlagName(String)
 * @see BaseFeatureRequestBuilder
 */
public interface FeatureRequestBuilder extends BaseFeatureRequestBuilder<FeatureRequestBuilder> {

    /**
     * Evaluate the feature flag as a boolean.
     * <p>
     * For boolean features, returns {@code true} if enabled, {@code false} if disabled.
     * For non-boolean features, the value is cast using the configured boolean cast strategy
     * (default: LAX).
     *
     * @return a future containing the boolean value
     */
    CompletableFuture<Boolean> booleanValue();

    /**
     * Evaluate the feature flag as a string.
     * <p>
     * Returns the string value from Izanami. For disabled features, returns {@code null}
     * unless a default value is configured with {@code DEFAULT_VALUE} error strategy.
     *
     * @return a future containing the string value, or {@code null} if disabled
     */
    CompletableFuture<String> stringValue();

    /**
     * Evaluate the feature flag as a number.
     * <p>
     * Returns the numeric value from Izanami as a {@link BigDecimal}. For disabled features,
     * returns {@code null} unless a default value is configured with {@code DEFAULT_VALUE} error strategy.
     *
     * @return a future containing the number value as BigDecimal, or {@code null} if disabled
     */
    CompletableFuture<BigDecimal> numberValue();

    /**
     * Evaluate the feature flag as a boolean with detailed metadata.
     * <p>
     * Returns both the value and evaluation metadata including the value source
     * ({@code IZANAMI}, {@code APPLICATION_ERROR_STRATEGY}, {@code IZANAMI_ERROR_STRATEGY})
     * and evaluation reason ({@code ORIGIN_OR_CACHE}, {@code DISABLED}, {@code ERROR}).
     *
     * @return a future containing the boolean value with evaluation metadata
     * @see ResultValueWithDetails
     */
    CompletableFuture<ResultValueWithDetails<Boolean>> booleanValueDetails();

    /**
     * Evaluate the feature flag as a string with detailed metadata.
     * <p>
     * Returns both the value and evaluation metadata including the value source
     * and evaluation reason.
     *
     * @return a future containing the string value with evaluation metadata
     * @see ResultValueWithDetails
     */
    CompletableFuture<ResultValueWithDetails<String>> stringValueDetails();

    /**
     * Evaluate the feature flag as a number with detailed metadata.
     * <p>
     * Returns both the value and evaluation metadata including the value source
     * and evaluation reason.
     *
     * @return a future containing the number value as BigDecimal with evaluation metadata
     * @see ResultValueWithDetails
     */
    CompletableFuture<ResultValueWithDetails<BigDecimal>> numberValueDetails();
}
