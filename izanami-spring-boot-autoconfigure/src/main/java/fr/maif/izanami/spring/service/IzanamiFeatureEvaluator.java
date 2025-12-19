package fr.maif.izanami.spring.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.maif.IzanamiClient;
import fr.maif.errors.IzanamiError;
import fr.maif.features.results.IzanamiResult;
import fr.maif.features.values.BooleanCastStrategy;
import fr.maif.izanami.spring.openfeature.ErrorStrategy;
import fr.maif.izanami.spring.openfeature.FlagConfig;
import fr.maif.izanami.spring.service.api.ResultValueWithDetails;
import fr.maif.requests.FeatureRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static java.util.Collections.unmodifiableMap;

/**
 * Performs feature flag evaluation given a configured request.
 * <p>
 * This class is package-private and not part of the public API.
 * Use {@link fr.maif.izanami.spring.service.api.FeatureRequestBuilder} to access evaluation methods.
 */
final class IzanamiFeatureEvaluator {
    private static final Logger log = LoggerFactory.getLogger(IzanamiFeatureEvaluator.class);

    @Nullable
    private final IzanamiClient client;
    private final ObjectMapper objectMapper;
    private final FlagConfig flagConfig;
    private final String user;
    private final String context;

    private record ResultWithMetadata(IzanamiResult.Result result, Map<String, String> metadata) {}

    IzanamiFeatureEvaluator(@Nullable IzanamiClient client, ObjectMapper objectMapper, FlagConfig flagConfig,
                            String user, String context) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.flagConfig = flagConfig;
        this.user = user;
        this.context = context;
    }

    /**
     * Evaluate the feature flag as a boolean and return detailed result with metadata.
     *
     * @return a future containing the boolean value with evaluation metadata
     */
    CompletableFuture<ResultValueWithDetails<Boolean>> booleanValueDetails() {
        log.debug("Evaluating flag {} as boolean with details", flagConfig.key());
        return evaluateWithDetails(
            result -> result.booleanValue(BooleanCastStrategy.LAX),
            () -> null,  // Boolean doesn't use default for disabled - false is the disabled value
            Boolean.FALSE::equals  // false means disabled for boolean features
        );
    }

    /**
     * Evaluate the feature flag as a string and return detailed result with metadata.
     *
     * @return a future containing the string value with evaluation metadata
     */
    CompletableFuture<ResultValueWithDetails<String>> stringValueDetails() {
        log.debug("Evaluating flag {} as string with details", flagConfig.key());
        return evaluateWithDetails(
            IzanamiResult.Result::stringValue,
            () -> flagConfig.defaultValue() != null ? flagConfig.defaultValue().toString() : null,
            Objects::isNull  // null means disabled for string features
        );
    }

    /**
     * Evaluate the feature flag as a number and return detailed result with metadata.
     *
     * @return a future containing the number value as BigDecimal with evaluation metadata
     */
    CompletableFuture<ResultValueWithDetails<BigDecimal>> numberValueDetails() {
        log.debug("Evaluating flag {} as number with details", flagConfig.key());
        return evaluateWithDetails(
            IzanamiResult.Result::numberValue,
            () -> IzanamiEvaluationHelper.toBigDecimal(flagConfig.defaultValue()),
            Objects::isNull  // null means disabled for number features
        );
    }

    // =========================================================================
    // Internal evaluation helpers
    // =========================================================================

    /**
     * Retrieve the raw feature result with metadata asynchronously.
     */
    private CompletableFuture<ResultWithMetadata> featureResultWithMetadata() {
        Map<String, String> metadata = IzanamiEvaluationHelper.buildBaseMetadata(flagConfig, objectMapper);
        try {
            return evaluateFeatureResult()
                .handle((result, error) -> {
                    if (error != null) {
                        // Handle both sync and async errors
                        if (flagConfig.errorStrategy() == ErrorStrategy.FAIL) {
                            throw error instanceof RuntimeException ? (RuntimeException) error : new RuntimeException(error);
                        }
                        return new ResultWithMetadata(
                            new IzanamiResult.Error(flagConfig.clientErrorStrategy(), new IzanamiError(error.getMessage())),
                            unmodifiableMap(metadata)
                        );
                    }
                    return new ResultWithMetadata(result, unmodifiableMap(metadata));
                });
        } catch (Exception e) {
            // Handle synchronous exceptions from evaluateFeatureResult()
            if (flagConfig.errorStrategy() == ErrorStrategy.FAIL) {
                return CompletableFuture.failedFuture(e);
            }
            return CompletableFuture.completedFuture(new ResultWithMetadata(
                new IzanamiResult.Error(flagConfig.clientErrorStrategy(), new IzanamiError(e.getMessage())),
                unmodifiableMap(metadata)
            ));
        }
    }

    /**
     * Evaluate the feature and return the raw result.
     */
    private CompletableFuture<IzanamiResult.Result> evaluateFeatureResult() {
        if (client == null) {
            log.debug("Izanami client not available; returning error result");
            return CompletableFuture.completedFuture(
                new IzanamiResult.Error(
                    flagConfig.clientErrorStrategy(),
                    new IzanamiError("Izanami client not available")
                )
            );
        }
        if (log.isTraceEnabled()) {
            log.trace("Querying Izanami: key={}, user={}, context={}", flagConfig.key(), user, context);
        }
        FeatureRequest featureRequest = FeatureRequest.newFeatureRequest()
            .withFeature(flagConfig.key())
            .withErrorStrategy(flagConfig.clientErrorStrategy())
            .withBooleanCastStrategy(BooleanCastStrategy.LAX);
        if (user != null) {
            featureRequest = featureRequest.withUser(user);
        }
        if (context != null) {
            featureRequest = featureRequest.withContext(context);
        }
        return client
            .featureValues(featureRequest)
            .thenApply(r -> r.results.values().iterator().next());
    }

    /**
     * Generic evaluation method that handles the common flow for all *ValueDetails methods.
     *
     * @param valueExtractor       extracts the raw value from IzanamiResult.Result
     * @param disabledValueResolver resolves the value when feature is disabled (null from Izanami)
     * @param isDisabledCheck      checks if the value indicates a disabled feature
     */
    private <T> CompletableFuture<ResultValueWithDetails<T>> evaluateWithDetails(
            Function<IzanamiResult.Result, T> valueExtractor,
            Supplier<T> disabledValueResolver,
            Predicate<T> isDisabledCheck
    ) {
        return featureResultWithMetadata().thenApply(resultWithMetadata ->
            IzanamiEvaluationHelper.buildResultWithDetails(
                resultWithMetadata.result(),
                valueExtractor,
                resultWithMetadata.metadata(),
                disabledValueResolver,
                isDisabledCheck,
                flagConfig.key()
            )
        );
    }
}
