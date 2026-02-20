package fr.maif.izanami.spring.service.api;

import reactor.core.publisher.Mono;

/**
 * Reactive fluent builder for configuring and executing batch feature flag evaluations.
 * <p>
 * This is the reactive counterpart of {@link BatchFeatureRequestBuilder}, returning {@link Mono}
 * instead of {@link java.util.concurrent.CompletableFuture} for the terminal evaluation method.
 * <p>
 * Obtain an instance via {@link ReactiveIzanamiService#forFlagKeys(String...)} or
 * {@link ReactiveIzanamiService#forFlagNames(String...)}.
 * <p>
 * Example usage:
 * <pre>{@code
 * reactiveIzanamiService.forFlagNames("feature-a", "feature-b")
 *     .withUser("user-123")
 *     .values()
 *     .subscribe(result -> {
 *         Boolean enabled = result.booleanValue("feature-a");
 *         String value = result.stringValue("feature-b");
 *     });
 * }</pre>
 *
 * @see ReactiveIzanamiService#forFlagKeys(String...)
 * @see ReactiveIzanamiService#forFlagNames(String...)
 * @see BaseFeatureRequestBuilder
 */
public interface ReactiveBatchFeatureRequestBuilder extends BaseFeatureRequestBuilder<ReactiveBatchFeatureRequestBuilder> {

    /**
     * Execute the batch evaluation and return results.
     *
     * @return a Mono emitting the batch result with values for all requested flags
     */
    Mono<BatchResult> values();
}
