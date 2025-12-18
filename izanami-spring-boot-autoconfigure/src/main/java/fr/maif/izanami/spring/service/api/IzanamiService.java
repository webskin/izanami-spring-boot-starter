package fr.maif.izanami.spring.service.api;

import fr.maif.IzanamiClient;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Main service for evaluating Izanami feature flags.
 * <p>
 * This service provides a fluent async API for feature flag evaluation with user targeting
 * and context support. All evaluation methods return {@link CompletableFuture} for non-blocking
 * async operations.
 * <p>
 * The service is resilient by design:
 * <ul>
 *   <li>It never throws from Spring lifecycle callbacks</li>
 *   <li>If credentials or URL are missing, it stays inactive</li>
 *   <li>If Izanami is unreachable, evaluations fall back via configured error strategies</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 * <pre>{@code
 * // Simple evaluation
 * Boolean enabled = izanamiService.forFlagName("my-feature")
 *     .booleanValue()
 *     .join();
 *
 * // With user targeting
 * String value = izanamiService.forFlagKey("feature-uuid")
 *     .withUser("user-123")
 *     .stringValue()
 *     .join();
 *
 * // Async with callback
 * izanamiService.forFlagName("my-feature")
 *     .withUser("user-123")
 *     .withContext("production")
 *     .booleanValue()
 *     .thenAccept(enabled -> { ... });
 * }</pre>
 *
 * @see FeatureRequestBuilder
 */
public interface IzanamiService {

    /**
     * Access the underlying {@link IzanamiClient} for advanced use cases.
     * <p>
     * Use this escape hatch when you need direct access to the native Izanami client API,
     * such as batch evaluation of multiple flags in a single request.
     * <p>
     * Example:
     * <pre>{@code
     * izanamiService.unwrapClient().ifPresent(client -> {
     *     client.featureValues(FeatureRequest.newFeatureRequest()
     *             .withFeatures("flag-1", "flag-2", "flag-3"))
     *         .thenAccept(results -> { ... });
     * });
     * }</pre>
     *
     * @return an {@link Optional} containing the client if available, empty if not configured or failed to initialize
     */
    Optional<IzanamiClient> unwrapClient();

    /**
     * Returns a future that completes when the initial flag preload has finished.
     * <p>
     * Use this to wait for flags to be available at application startup:
     * <pre>{@code
     * izanamiService.whenLoaded().join();
     * // Flags are now preloaded and ready
     * }</pre>
     * <p>
     * The future completes successfully even if preloading failed; use {@link #isConnected()}
     * to check if the connection was successful.
     *
     * @return a future that completes when preloading finishes (successfully or not)
     */
    CompletableFuture<Void> whenLoaded();

    /**
     * Check if the Izanami client is connected and has successfully preloaded flags.
     *
     * @return {@code true} if connected and flags are preloaded, {@code false} otherwise
     */
    boolean isConnected();

    /**
     * Start building a feature request for the given flag key (UUID).
     * <p>
     * The flag key is the Izanami feature UUID as configured in the {@code openfeature.flags[].key} property.
     * <p>
     * Example:
     * <pre>{@code
     * izanamiService.forFlagKey("a4c0d04f-69ac-41aa-a6e4-febcee541d51")
     *     .withUser("user-123")
     *     .booleanValue()
     *     .thenAccept(enabled -> { ... });
     * }</pre>
     *
     * @param flagKey the flag key (UUID) as configured in Izanami
     * @return a builder for configuring and executing the feature request
     * @throws FlagNotFoundException if the flag key is not found in configuration
     */
    FeatureRequestBuilder forFlagKey(String flagKey);

    /**
     * Start building a feature request for the given flag name.
     * <p>
     * The flag name is the human-friendly identifier as configured in the {@code openfeature.flags[].name} property.
     * <p>
     * Example:
     * <pre>{@code
     * izanamiService.forFlagName("turbo-mode")
     *     .withUser("user-123")
     *     .booleanValue()
     *     .thenAccept(enabled -> { ... });
     * }</pre>
     *
     * @param flagName the flag name as configured in openfeature.flags
     * @return a builder for configuring and executing the feature request
     * @throws FlagNotFoundException if the flag name is not found in configuration
     */
    FeatureRequestBuilder forFlagName(String flagName);
}
