package fr.maif.izanami.spring.service.api;

import java.util.concurrent.CompletableFuture;

/**
 * Fluent builder for configuring and executing batch feature flag evaluations.
 * <p>
 * Obtain an instance via {@link IzanamiService#forFlagKeys(String...)} or
 * {@link IzanamiService#forFlagNames(String...)}.
 * <p>
 * Example usage:
 * <pre>{@code
 * izanamiService.forFlagKeys("uuid-1", "uuid-2", "uuid-3")
 *     .withUser("user-123")
 *     .withContext("production")
 *     .ignoreCache(true)
 *     .values()
 *     .thenAccept(result -> {
 *         Boolean enabled = result.booleanValue("uuid-1");
 *         String value = result.stringValue("uuid-2");
 *     });
 * }</pre>
 *
 * @see IzanamiService#forFlagKeys(String...)
 * @see IzanamiService#forFlagNames(String...)
 * @see BaseFeatureRequestBuilder
 */
public interface BatchFeatureRequestBuilder extends BaseFeatureRequestBuilder<BatchFeatureRequestBuilder> {

    /**
     * Execute the batch evaluation and return results.
     * <p>
     * The returned {@link BatchResult} contains values for all requested flags.
     * Access individual flag values via {@link BatchResult#booleanValue(String)},
     * {@link BatchResult#stringValue(String)}, etc.
     * <p>
     * Note: For flags configured with {@code FAIL} error strategy, exceptions are
     * thrown when accessing the value via the result object, not when calling this method.
     *
     * @return a future containing the batch result with values for all requested flags
     */
    CompletableFuture<BatchResult> values();
}
