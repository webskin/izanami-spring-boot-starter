package fr.maif.izanami.spring.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.maif.IzanamiClient;
import fr.maif.errors.IzanamiError;
import fr.maif.features.results.IzanamiResult;
import fr.maif.features.values.BooleanCastStrategy;
import fr.maif.izanami.spring.openfeature.ErrorStrategy;
import fr.maif.izanami.spring.openfeature.FlagConfig;
import fr.maif.izanami.spring.openfeature.FlagMetadataKeys;
import fr.maif.izanami.spring.openfeature.FlagValueSource;
import fr.maif.izanami.spring.service.api.IzanamiClientNotAvailableException;
import fr.maif.izanami.spring.service.api.ResultValueWithDetails;
import fr.maif.requests.FeatureRequest;
import fr.maif.requests.SingleFeatureRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
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
    private final SingleFeatureRequest request;
    private final String user;
    private final String context;

    private record ResultWithMetadata(IzanamiResult.Result result, Map<String, String> metadata) {}

    /**
     * Holds the computed value, source, and reason for a feature evaluation.
     */
    private record EvaluationOutcome<T>(T value, FlagValueSource source, String reason) {}

    IzanamiFeatureEvaluator(@Nullable IzanamiClient client, ObjectMapper objectMapper, FlagConfig flagConfig,
                            SingleFeatureRequest request, String user, String context) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.flagConfig = flagConfig;
        this.request = request;
        this.user = user;
        this.context = context;
    }

    private IzanamiClient requireClient() {
        if (client == null) {
            throw new IzanamiClientNotAvailableException();
        }
        return client;
    }

    /**
     * Evaluate the feature flag as a boolean.
     *
     * @return a future containing the boolean value
     * @throws IzanamiClientNotAvailableException if the Izanami client is not available
     */
    CompletableFuture<Boolean> booleanValue() {
        log.debug("Evaluating flag {} as boolean", flagConfig.key());
        return requireClient().booleanValue(request)
            .whenComplete((result, error) -> {
                if (error == null) {
                    log.debug("Evaluated flag {} as boolean = {}", flagConfig.key(), result);
                }
            });
    }

    /**
     * Evaluate the feature flag as a string.
     *
     * @return a future containing the string value
     * @throws IzanamiClientNotAvailableException if the Izanami client is not available
     */
    CompletableFuture<String> stringValue() {
        log.debug("Evaluating flag {} as string", flagConfig.key());
        return requireClient().stringValue(request)
            .thenApply(value -> applyDefaultIfDisabled(value, flagConfig))
            .whenComplete((result, error) -> {
                if (error == null) {
                    log.debug("Evaluated flag {} as string = {}", flagConfig.key(), result);
                }
            });
    }

    /**
     * Evaluate the feature flag as a number.
     *
     * @return a future containing the number value as BigDecimal
     * @throws IzanamiClientNotAvailableException if the Izanami client is not available
     */
    CompletableFuture<BigDecimal> numberValue() {
        log.debug("Evaluating flag {} as number", flagConfig.key());
        return requireClient().numberValue(request)
            .thenApply(value -> applyDefaultIfDisabled(value, flagConfig))
            .whenComplete((result, error) -> {
                if (error == null) {
                    log.debug("Evaluated flag {} as number = {}", flagConfig.key(), result);
                }
            });
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
            () -> toBigDecimal(flagConfig.defaultValue()),
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
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put(FlagMetadataKeys.FLAG_CONFIG_KEY, flagConfig.key());
        metadata.put(FlagMetadataKeys.FLAG_CONFIG_NAME, flagConfig.name());
        metadata.put(FlagMetadataKeys.FLAG_CONFIG_DESCRIPTION, flagConfig.description());
        metadata.put(FlagMetadataKeys.FLAG_CONFIG_VALUE_TYPE, flagConfig.valueType().name());
        metadata.put(FlagMetadataKeys.FLAG_CONFIG_DEFAULT_VALUE, IzanamiServiceImpl.stringifyDefaultValue(objectMapper, flagConfig));
        metadata.put(FlagMetadataKeys.FLAG_CONFIG_ERROR_STRATEGY, flagConfig.errorStrategy().name());
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
        if (log.isTraceEnabled()) {
            log.trace("Querying Izanami: key={}, user={}, context={}", flagConfig.key(), user, context);
        }
        try {
            IzanamiClient izanamiClient = requireClient();
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
            return izanamiClient
                .featureValues(featureRequest)
                .thenApply(r -> r.results.values().iterator().next());
        } catch (Exception e) {
            log.debug("Izanami evaluation failed; falling back to configured defaults: {}", e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
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
        return featureResultWithMetadata().thenApply(resultWithMetadata -> {
            Map<String, String> metadata = new LinkedHashMap<>(resultWithMetadata.metadata());
            IzanamiResult.Result result = resultWithMetadata.result();
            T rawValue = valueExtractor.apply(result);

            EvaluationOutcome<T> outcome = computeOutcome(result, rawValue, disabledValueResolver, isDisabledCheck);

            metadata.put(FlagMetadataKeys.FLAG_VALUE_SOURCE, outcome.source().name());
            metadata.put(FlagMetadataKeys.FLAG_EVALUATION_REASON, outcome.reason());

            if (outcome.source() == FlagValueSource.APPLICATION_ERROR_STRATEGY) {
                log.warn("Flag {} evaluated using application default value (feature disabled), value={}", flagConfig.key(), outcome.value());
            } else if (outcome.source() == FlagValueSource.IZANAMI_ERROR_STRATEGY) {
                log.warn("Flag {} evaluated using Izanami error strategy (evaluation error), value={}", flagConfig.key(), outcome.value());
            } else {
                log.debug("Evaluated flag {} = {} with details, reason={}", flagConfig.key(), outcome.value(), outcome.reason());
            }
            return new ResultValueWithDetails<>(outcome.value(), unmodifiableMap(metadata));
        });
    }

    /**
     * Computes the evaluation outcome (value, source, reason) based on the Izanami result.
     * <p>
     * For disabled non-boolean features, Izanami returns null (Success with NullValue).
     * In this case, the configured defaultValue is applied if available.
     *
     * @see <a href="https://github.com/MAIF/izanami-java-client/blob/v2.3.7/src/main/java/fr/maif/features/values/NullValue.java">NullValue</a>
     */
    private <T> EvaluationOutcome<T> computeOutcome(
            IzanamiResult.Result result,
            T rawValue,
            Supplier<T> disabledValueResolver,
            Predicate<T> isDisabledCheck
    ) {
        if (result instanceof IzanamiResult.Success) {
            if (isDisabledCheck.test(rawValue)) {
                // Feature is disabled - apply default value if configured
                T resolvedValue = disabledValueResolver.get();
                return new EvaluationOutcome<>(
                    resolvedValue != null ? resolvedValue : rawValue,
                    resolvedValue != null ? FlagValueSource.APPLICATION_ERROR_STRATEGY : FlagValueSource.IZANAMI,
                    "DISABLED"
                );
            }
            return new EvaluationOutcome<>(rawValue, FlagValueSource.IZANAMI, "ORIGIN_OR_CACHE");
        }
        // Error case - value comes from Izanami's error strategy
        return new EvaluationOutcome<>(rawValue, FlagValueSource.IZANAMI_ERROR_STRATEGY, "ERROR");
    }

    /**
     * Apply default value if the feature is disabled (returns null) and DEFAULT_VALUE strategy is configured.
     */
    private String applyDefaultIfDisabled(String value, FlagConfig flagConfig) {
        if (value == null && flagConfig.defaultValue() != null
                && flagConfig.errorStrategy() == ErrorStrategy.DEFAULT_VALUE) {
            String defaultValue = flagConfig.defaultValue().toString();
            log.warn("Flag {} evaluated using application default value (feature disabled), value={}", flagConfig.key(), defaultValue);
            return defaultValue;
        }
        return value;
    }

    /**
     * Apply default value if the feature is disabled (returns null) and DEFAULT_VALUE strategy is configured.
     */
    private BigDecimal applyDefaultIfDisabled(BigDecimal value, FlagConfig flagConfig) {
        if (value == null && flagConfig.defaultValue() != null
                && flagConfig.errorStrategy() == ErrorStrategy.DEFAULT_VALUE) {
            BigDecimal defaultValue = toBigDecimal(flagConfig.defaultValue());
            log.warn("Flag {} evaluated using application default value (feature disabled), value={}", flagConfig.key(), defaultValue);
            return defaultValue;
        }
        return value;
    }

    /**
     * Converts a value to BigDecimal. Returns null if the input is null or not a number.
     */
    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString());
        }
        return null;
    }
}
