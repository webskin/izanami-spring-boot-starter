package fr.maif.izanami.spring.service.api;

import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

/**
 * Fluent builder for configuring and executing feature flag evaluations.
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
 */
public interface FeatureRequestBuilder {

    /**
     * Set the user identifier for this evaluation.
     * <p>
     * The user identifier is used by Izanami for user-based targeting rules
     * (e.g., percentage rollouts, user allowlists).
     *
     * @param user the user identifier, or {@code null} for anonymous evaluation
     * @return this builder for method chaining
     */
    FeatureRequestBuilder withUser(@Nullable String user);

    /**
     * Set the context for this evaluation.
     * <p>
     * The context provides additional targeting information beyond the user
     * (e.g., environment, tenant, feature tier).
     *
     * @param context the evaluation context, or {@code null} for no context
     * @return this builder for method chaining
     */
    FeatureRequestBuilder withContext(@Nullable String context);

    /**
     * Evaluate the feature flag as a boolean.
     * <p>
     * For boolean features, returns {@code true} if enabled, {@code false} if disabled.
     * For non-boolean features, the value is cast using a lax boolean strategy.
     *
     * @return a future containing the boolean value
     * @throws IzanamiClientNotAvailableException if the Izanami client is not available
     */
    CompletableFuture<Boolean> booleanValue();

    /**
     * Evaluate the feature flag as a string.
     * <p>
     * Returns the string value from Izanami. For disabled features, returns {@code null}
     * unless a default value is configured with {@code DEFAULT_VALUE} error strategy.
     *
     * @return a future containing the string value, or {@code null} if disabled
     * @throws IzanamiClientNotAvailableException if the Izanami client is not available
     */
    CompletableFuture<String> stringValue();

    /**
     * Evaluate the feature flag as a number.
     * <p>
     * Returns the numeric value from Izanami as a {@link BigDecimal}. For disabled features,
     * returns {@code null} unless a default value is configured with {@code DEFAULT_VALUE} error strategy.
     *
     * @return a future containing the number value as BigDecimal, or {@code null} if disabled
     * @throws IzanamiClientNotAvailableException if the Izanami client is not available
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
     * @throws IzanamiClientNotAvailableException if the Izanami client is not available
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
     * @throws IzanamiClientNotAvailableException if the Izanami client is not available
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
     * @throws IzanamiClientNotAvailableException if the Izanami client is not available
     * @see ResultValueWithDetails
     */
    CompletableFuture<ResultValueWithDetails<BigDecimal>> numberValueDetails();
}
