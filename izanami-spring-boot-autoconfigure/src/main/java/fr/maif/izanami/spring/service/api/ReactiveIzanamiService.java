package fr.maif.izanami.spring.service.api;

import fr.maif.IzanamiClient;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Reactive service for evaluating Izanami feature flags.
 * <p>
 * This is the reactive counterpart of {@link IzanamiService}, designed for Spring WebFlux
 * applications. All evaluation methods return {@link Mono} for non-blocking reactive operations.
 * <p>
 * The reactive service resolves user and context reactively at subscription time (via
 * {@link ReactiveUserProvider} and {@link ReactiveSubContextResolver}), then delegates
 * to the underlying {@link IzanamiService} for evaluation.
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * @RestController
 * public class FeatureController {
 *     private final ReactiveIzanamiService izanamiService;
 *
 *     @GetMapping("/feature")
 *     public Mono<Boolean> isEnabled() {
 *         return izanamiService.forFlagName("turbo-mode").booleanValue();
 *     }
 *
 *     @GetMapping("/features")
 *     public Mono<BatchResult> getFeatures() {
 *         return izanamiService.forFlagNames("feature-a", "feature-b")
 *             .withUser("user-123")
 *             .values();
 *     }
 * }
 * }</pre>
 *
 * @see IzanamiService
 * @see ReactiveFeatureRequestBuilder
 * @see ReactiveBatchFeatureRequestBuilder
 */
public interface ReactiveIzanamiService {

    /**
     * Access the underlying {@link IzanamiClient} for advanced use cases.
     *
     * @return an {@link Optional} containing the client if available, empty if not configured or failed to initialize
     */
    Optional<IzanamiClient> unwrapClient();

    /**
     * Returns a Mono that completes when the initial flag preload has finished.
     * <p>
     * Use this to wait for flags to be available at application startup:
     * <pre>{@code
     * reactiveIzanamiService.whenLoaded()
     *     .then(reactiveIzanamiService.forFlagName("my-feature").booleanValue())
     *     .subscribe(enabled -> { ... });
     * }</pre>
     *
     * @return a Mono that completes when preloading finishes (successfully or not)
     */
    Mono<Void> whenLoaded();

    /**
     * Check if the Izanami client is connected and has successfully preloaded flags.
     *
     * @return {@code true} if connected and flags are preloaded, {@code false} otherwise
     */
    boolean isConnected();

    /**
     * Start building a reactive feature request for the given flag key (UUID).
     *
     * @param flagKey the flag key (UUID) as configured in Izanami
     * @return a reactive builder for configuring and executing the feature request
     */
    ReactiveFeatureRequestBuilder forFlagKey(String flagKey);

    /**
     * Start building a reactive feature request for the given flag name.
     *
     * @param flagName the flag name as configured in openfeature.flags
     * @return a reactive builder for configuring and executing the feature request
     */
    ReactiveFeatureRequestBuilder forFlagName(String flagName);

    /**
     * Start building a reactive batch feature request for multiple flag keys (UUIDs).
     *
     * @param flagKeys the flag keys (UUIDs) as configured in Izanami
     * @return a reactive builder for configuring and executing the batch feature request
     */
    ReactiveBatchFeatureRequestBuilder forFlagKeys(String... flagKeys);

    /**
     * Start building a reactive batch feature request for multiple flag names.
     *
     * @param flagNames the flag names as configured in openfeature.flags
     * @return a reactive builder for configuring and executing the batch feature request
     */
    ReactiveBatchFeatureRequestBuilder forFlagNames(String... flagNames);
}
