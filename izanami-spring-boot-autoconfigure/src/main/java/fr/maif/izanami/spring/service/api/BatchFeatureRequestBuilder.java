package fr.maif.izanami.spring.service.api;

import org.springframework.lang.Nullable;

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
 *     .values()
 *     .thenAccept(result -> {
 *         Boolean enabled = result.booleanValue("uuid-1");
 *         String value = result.stringValue("uuid-2");
 *     });
 * }</pre>
 *
 * @see IzanamiService#forFlagKeys(String...)
 * @see IzanamiService#forFlagNames(String...)
 */
public interface BatchFeatureRequestBuilder {

    /**
     * Set the user identifier for all flags in this batch evaluation.
     *
     * @param user the user identifier
     * @return this builder for chaining
     */
    BatchFeatureRequestBuilder withUser(@Nullable String user);

    /**
     * Set the context for all flags in this batch evaluation.
     *
     * @param context the evaluation context
     * @return this builder for chaining
     */
    BatchFeatureRequestBuilder withContext(@Nullable String context);

    /**
     * Bypass cache for this evaluation.
     *
     * @param ignoreCache true to bypass cache
     * @return this builder for chaining
     */
    BatchFeatureRequestBuilder ignoreCache(boolean ignoreCache);

    /**
     * Execute the batch evaluation and return results.
     *
     * @return a future containing the batch result with values for all requested flags
     */
    CompletableFuture<BatchResult> values();
}
