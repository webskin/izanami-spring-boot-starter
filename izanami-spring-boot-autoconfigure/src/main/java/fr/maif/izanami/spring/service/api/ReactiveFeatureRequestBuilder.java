package fr.maif.izanami.spring.service.api;

import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/**
 * Reactive fluent builder for configuring and executing single feature flag evaluations.
 * <p>
 * This is the reactive counterpart of {@link FeatureRequestBuilder}, returning {@link Mono}
 * instead of {@link java.util.concurrent.CompletableFuture} for terminal evaluation methods.
 * <p>
 * Obtain an instance via {@link ReactiveIzanamiService#forFlagKey(String)} or
 * {@link ReactiveIzanamiService#forFlagName(String)}.
 * <p>
 * Example usage:
 * <pre>{@code
 * reactiveIzanamiService.forFlagName("my-feature")
 *     .withUser("user-123")
 *     .withContext("production")
 *     .booleanValue()
 *     .subscribe(enabled -> { ... });
 * }</pre>
 *
 * @see ReactiveIzanamiService#forFlagKey(String)
 * @see ReactiveIzanamiService#forFlagName(String)
 * @see BaseFeatureRequestBuilder
 */
public interface ReactiveFeatureRequestBuilder extends BaseFeatureRequestBuilder<ReactiveFeatureRequestBuilder> {

    /**
     * Evaluate the feature flag as a boolean.
     *
     * @return a Mono emitting the boolean value
     */
    Mono<Boolean> booleanValue();

    /**
     * Evaluate the feature flag as a string.
     *
     * @return a Mono emitting the string value, or null if disabled
     */
    Mono<String> stringValue();

    /**
     * Evaluate the feature flag as a number.
     *
     * @return a Mono emitting the number value as BigDecimal, or null if disabled
     */
    Mono<BigDecimal> numberValue();

    /**
     * Evaluate the feature flag as a boolean with detailed metadata.
     *
     * @return a Mono emitting the boolean value with evaluation metadata
     * @see ResultValueWithDetails
     */
    Mono<ResultValueWithDetails<Boolean>> booleanValueDetails();

    /**
     * Evaluate the feature flag as a string with detailed metadata.
     *
     * @return a Mono emitting the string value with evaluation metadata
     * @see ResultValueWithDetails
     */
    Mono<ResultValueWithDetails<String>> stringValueDetails();

    /**
     * Evaluate the feature flag as a number with detailed metadata.
     *
     * @return a Mono emitting the number value as BigDecimal with evaluation metadata
     * @see ResultValueWithDetails
     */
    Mono<ResultValueWithDetails<BigDecimal>> numberValueDetails();
}
