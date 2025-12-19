package fr.maif.izanami.spring.service.api;

import fr.maif.FeatureClientErrorStrategy;
import fr.maif.features.values.BooleanCastStrategy;
import org.springframework.lang.Nullable;

import java.time.Duration;

/**
 * Base interface for feature request builders providing common configuration methods.
 * <p>
 * This interface defines the shared configuration options available for both single-flag
 * and batch feature evaluations. All configuration methods return the builder type for
 * fluent method chaining.
 *
 * @param <T> the concrete builder type for fluent method chaining
 * @see FeatureRequestBuilder
 * @see BatchFeatureRequestBuilder
 */
public interface BaseFeatureRequestBuilder<T extends BaseFeatureRequestBuilder<T>> {

    /**
     * Set the user identifier for this evaluation.
     * <p>
     * The user identifier is used by Izanami for user-based targeting rules
     * (e.g., percentage rollouts, user allowlists).
     *
     * @param user the user identifier, or {@code null} for anonymous evaluation
     * @return this builder for method chaining
     */
    T withUser(@Nullable String user);

    /**
     * Set the context for this evaluation.
     * <p>
     * The context provides additional targeting information beyond the user
     * (e.g., environment, tenant, feature tier).
     *
     * @param context the evaluation context, or {@code null} for no context
     * @return this builder for method chaining
     */
    T withContext(@Nullable String context);

    /**
     * Set whether to bypass the cache for this evaluation.
     * <p>
     * When set to {@code true}, the Izanami client will fetch fresh feature values
     * from the server, ignoring any cached values. This is useful when you need
     * guaranteed current values despite the performance cost.
     *
     * @param ignoreCache {@code true} to bypass cache, {@code false} to use cache (default)
     * @return this builder for method chaining
     */
    T ignoreCache(boolean ignoreCache);

    /**
     * Set the HTTP request timeout for this evaluation.
     * <p>
     * This overrides the client-level timeout for this specific request.
     * If not set, the client's default timeout is used.
     *
     * @param timeout the request timeout, or {@code null} to use client default
     * @return this builder for method chaining
     */
    T withCallTimeout(@Nullable Duration timeout);

    /**
     * Set an additional payload to send with this evaluation request.
     * <p>
     * The payload is passed through to the Izanami server and can be used
     * for advanced feature evaluation scenarios that require extra context
     * beyond user and context.
     *
     * @param payload the JSON or string payload, or {@code null} for no payload
     * @return this builder for method chaining
     */
    T withPayload(@Nullable String payload);

    /**
     * Set the boolean cast strategy for this evaluation.
     * <p>
     * Controls how non-boolean feature values are cast to boolean:
     * <ul>
     *   <li>{@code STRICT} - Throws exception if attempting to cast non-boolean value</li>
     *   <li>{@code LAX} - Performs lenient casting of non-boolean values (default)</li>
     * </ul>
     *
     * @param strategy the boolean cast strategy
     * @return this builder for method chaining
     */
    T withBooleanCastStrategy(BooleanCastStrategy strategy);

    /**
     * Set the error strategy for this evaluation, overriding the flag's configured strategy.
     * <p>
     * This allows exceptional override of the error handling behavior configured in
     * the flag's {@code FlagConfig}. Useful when a specific request needs different
     * error handling (e.g., fail-fast for critical operations).
     * <p>
     * If not set, the flag's configured {@code clientErrorStrategy()} is used.
     *
     * @param errorStrategy the error strategy to use, or {@code null} to use flag config default
     * @return this builder for method chaining
     * @see fr.maif.izanami.spring.openfeature.FlagConfig#clientErrorStrategy()
     */
    T withErrorStrategy(@Nullable FeatureClientErrorStrategy<?> errorStrategy);
}
