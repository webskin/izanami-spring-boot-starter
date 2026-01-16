package fr.maif.izanami.spring.openfeature;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfeature.sdk.*;
import fr.maif.izanami.spring.openfeature.api.FlagConfigService;
import fr.maif.izanami.spring.service.IzanamiServiceImpl;
import fr.maif.izanami.spring.service.api.FeatureRequestBuilder;
import fr.maif.izanami.spring.service.api.IzanamiService;
import fr.maif.izanami.spring.service.api.ResultValueWithDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.text.MessageFormat;
import java.util.function.Function;

/**
 * OpenFeature {@link FeatureProvider} backed by Izanami.
 * <p>
 * This provider resolves flags based on {@code openfeature.flags} configuration:
 * <ul>
 *   <li>The OpenFeature flag key can be either the configured {@link FlagConfig#name()} or {@link FlagConfig#key()}.</li>
 *   <li>Izanami is always queried by key.</li>
 * </ul>
 * <p>
 * Resilience and fallback rules:
 * <ul>
 *   <li>If the Izanami client is not configured/available, the provider returns the configured default value
 *       ({@link FlagValueSource#APPLICATION_ERROR_STRATEGY}).</li>
 *   <li>If Izanami returns an error, the provider returns the value computed by the Izanami client error strategy
 *       ({@link FlagValueSource#IZANAMI_ERROR_STRATEGY}).</li>
 *   <li>If an object-typed flag returns invalid JSON, the provider returns the configured default value and sets
 *       {@link Reason#ERROR}. The {@link ErrorCode} is intentionally left {@code null} to prevent the OpenFeature SDK
 *       from overwriting the returned value with the caller-provided default.</li>
 * </ul>
 * <p>
 * <b>Note on OpenFeature {@code variant} field:</b>
 * <p>
 * Izanami server does NOT return a "variant" field in its feature flag evaluation responses.
 * The OpenFeature {@code variant} field is always null in evaluations from this provider.
 * <p>
 * Izanami supports activation conditions (user targeting, date ranges, percentage rollout),
 * but these are only returned via the bulk evaluation endpoint {@code GET /api/v2/features}
 * with the response structure:
 * <pre>{@code
 * {
 *   "<context or empty>": {
 *     "name": "feature-name",
 *     "active": <boolean|string|number>,
 *     "project": "project-name",
 *     "conditions": {
 *       "<context>": {
 *         "enabled": true,
 *         "resultType": "boolean|string|number",
 *         "conditions": [
 *           { "period": {...}, "rule": {"users": [...]} }
 *         ]
 *       }
 *     }
 *   }
 * }
 * }</pre>
 * <p>
 * Izanami does indicate which specific condition triggered the evaluation result.
 * <p>
 * To support the OpenFeature {@code variant} field, the {@code izanami-java-client} library
 * would need to be updated to parse and expose condition matching information.
 */
public final class IzanamiFeatureProvider implements FeatureProvider {
    private static final Logger log = LoggerFactory.getLogger(IzanamiFeatureProvider.class);
    private static final String IZANAMI_CONTEXT_ATTRIBUTE = "context";

    private final IzanamiService izanamiService;
    private final EvaluationDependencies evaluationDependencies;

    /**
     * Create a provider.
     *
     * @param flagConfigService access to configured flags
     * @param izanamiService    Izanami client wrapper
     * @param objectMapper      mapper used for JSON parsing/serialization (object flags)
     * @param valueConverter    converter for Java objects to OpenFeature Values
     */
    public IzanamiFeatureProvider(
        FlagConfigService flagConfigService,
        IzanamiService izanamiService,
        ObjectMapper objectMapper,
        ValueConverter valueConverter
    ) {
        this.izanamiService = izanamiService;
        this.evaluationDependencies = new EvaluationDependencies(flagConfigService, izanamiService, objectMapper, valueConverter);
    }

    @Override
    public Metadata getMetadata() {
        return () -> "Izanami (Spring Boot Starter)";
    }

    @Override
    public void initialize(EvaluationContext evaluationContext) {
        log.info("Izanami OpenFeature provider initialized");
        // No-op: the provider is fully configured via Spring and IzanamiService lifecycle.
    }

    @Override
    public void shutdown() {
        log.info("Izanami OpenFeature provider shutting down");
        izanamiService.unwrapClient().ifPresent(client -> {
            try {
                client.close().join();
            } catch (Exception e) {
                log.debug("Error while closing Izanami client from provider shutdown: {}", e.getMessage());
            }
        });
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean callerDefaultValue, EvaluationContext ctx) {
        log.debug("Evaluating boolean flag: key={}", key);
        ProviderEvaluation<Boolean> result = new BooleanEvaluationExecution(evaluationDependencies, key, callerDefaultValue, ctx)
            .evaluate();
        log.debug("Evaluated boolean flag: key={}, value={}, reason={}", key, result.getValue(), result.getReason());
        return result;
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String callerDefaultValue, EvaluationContext ctx) {
        log.debug("Evaluating string flag: key={}", key);
        ProviderEvaluation<String> result = new StringEvaluationExecution(evaluationDependencies, key, callerDefaultValue, ctx)
            .evaluate();
        log.debug("Evaluated string flag: key={}, value={}, reason={}", key, result.getValue(), result.getReason());
        return result;
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer callerDefaultValue, EvaluationContext ctx) {
        log.debug("Evaluating integer flag: key={}", key);
        ProviderEvaluation<Integer> result = new IntegerEvaluationExecution(evaluationDependencies, key, callerDefaultValue, ctx)
            .evaluate();
        log.debug("Evaluated integer flag: key={}, value={}, reason={}", key, result.getValue(), result.getReason());
        return result;
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double callerDefaultValue, EvaluationContext ctx) {
        log.debug("Evaluating double flag: key={}", key);
        ProviderEvaluation<Double> result = new DoubleEvaluationExecution(evaluationDependencies, key, callerDefaultValue, ctx)
            .evaluate();
        log.debug("Evaluated double flag: key={}, value={}, reason={}", key, result.getValue(), result.getReason());
        return result;
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String key, Value callerDefaultValue, EvaluationContext ctx) {
        log.debug("Evaluating object flag: key={}", key);
        ProviderEvaluation<Value> result = new ObjectEvaluationExecution(evaluationDependencies, key, callerDefaultValue, ctx)
            .evaluate();
        log.debug("Evaluated object flag: key={}, value={}, reason={}", key, result.getValue(), result.getReason());
        return result;
    }

    public record EvaluationDependencies(
        FlagConfigService flagConfigService,
        IzanamiService izanamiService,
        ObjectMapper objectMapper,
        ValueConverter valueConverter
    ) {}

    /**
     * Base class for all evaluation executions using *ValueDetails() methods.
     */
    abstract static class EvaluationExecution<T> {
        protected final EvaluationDependencies deps;
        protected final String flagKey;
        protected final T callerDefaultValue;
        protected final EvaluationContext evaluationContext;
        protected final FlagConfig flagConfig;
        protected final boolean flagConfigResolved;

        EvaluationExecution(EvaluationDependencies deps, String flagKey, T callerDefaultValue, EvaluationContext evaluationContext) {
            this.deps = deps;
            this.flagKey = flagKey;
            this.evaluationContext = evaluationContext;
            this.callerDefaultValue = callerDefaultValue;
            this.flagConfig = deps.flagConfigService().getFlagConfigByKey(flagKey).orElse(null);
            this.flagConfigResolved = this.flagConfig != null;
        }

        protected abstract FlagValueType getExpectedType();

        protected abstract ProviderEvaluation<T> evaluateViaIzanami();

        public ProviderEvaluation<T> evaluate() {
            if (!flagConfigResolved) {
                return flagNotFound();
            }
            if (flagConfig.valueType() != getExpectedType()) {
                return typeMismatch();
            }
            return evaluateViaIzanami();
        }

        protected FeatureRequestBuilder buildIzanamiRequest() {
            Value contextValue = evaluationContext.getValue(IZANAMI_CONTEXT_ATTRIBUTE);
            String context = contextValue != null ? contextValue.asString() : null;
            if (log.isTraceEnabled()) {
                log.trace("Izanami query: key={}, user={}, context={}", flagConfig.key(), evaluationContext.getTargetingKey(), context);
            }
            return deps.izanamiService()
                .forFlagKey(flagConfig.key())
                .withUser(evaluationContext.getTargetingKey())
                .withContext(context);
        }

        protected <V> ProviderEvaluation<T> buildProviderEvaluation(V value, ResultValueWithDetails<?> result, Function<V, T> converter) {
            String valueSource = result.metadata().get(FlagMetadataKeys.FLAG_VALUE_SOURCE);
            String reason = result.metadata().get(FlagMetadataKeys.FLAG_EVALUATION_REASON);
            ImmutableMetadata metadata = computeImmutableMetadata(result);
            ErrorCode errorCode = mapValueSourceToErrorCode(reason, valueSource);

            T convertedValue = converter.apply(value);

            if (errorCode != null) {
                return buildErrorProviderEvaluation(
                    convertedValue,
                    metadata,
                    reason,
                    errorCode,
                    "Applying error strategy. Use fallback value: " + convertedValue
                );
            }
            return buildSuccessProviderEvaluation(convertedValue, metadata, reason);
        }

        protected ProviderEvaluation<T> handleEvaluationException(Exception e) {
            return buildErrorProviderEvaluation(
                callerDefaultValue,
                applicationErrorMetadata(flagConfig),
                Reason.ERROR.name(),
                ErrorCode.GENERAL,
                MessageFormat.format("Applying application error strategy. Unable to extract flag value: {0}. Use fallback value: {1}", e.getMessage(), callerDefaultValue)
            );
        }

        private ProviderEvaluation<T> typeMismatch() {
            return buildErrorProviderEvaluation(
                callerDefaultValue,
                applicationErrorMetadata(flagConfig),
                Reason.ERROR.name(),
                ErrorCode.TYPE_MISMATCH,
                "Feature flag '" + flagKey + "' is configured as '" + flagConfig.valueType().name()
                    + "' but evaluated as '" + getExpectedType().name() + "'"
            );
        }

        private ImmutableMetadata computeImmutableMetadata(ResultValueWithDetails<?> result) {
            ImmutableMetadata.ImmutableMetadataBuilder builder = ImmutableMetadata.builder();
            result.metadata().forEach((key, value) -> {
                if (value != null) {
                    builder.addString(key, value);
                }
            });
            return builder.build();
        }

        protected ProviderEvaluation<T> flagNotFound() {
            return ProviderEvaluation.<T>builder()
                .value(getFlagNotFoundValue())
                .errorCode(ErrorCode.FLAG_NOT_FOUND)
                .errorMessage("Feature flag '" + flagKey + "' not found in openfeature.flags")
                .flagMetadata(flagNotFoundMetadata())
                .build();
        }

        protected T getFlagNotFoundValue() {
            return callerDefaultValue;
        }

        private ImmutableMetadata flagNotFoundMetadata() {
            return ImmutableMetadata.builder()
                .addString(FlagMetadataKeys.FLAG_CONFIG_KEY, flagKey)
                .addString(FlagMetadataKeys.FLAG_VALUE_SOURCE, FlagValueSource.APPLICATION_ERROR_STRATEGY.name())
                .build();
        }

        private ImmutableMetadata applicationErrorMetadata(FlagConfig flagConfig) {
            String defaultValueString = IzanamiServiceImpl.stringifyDefaultValue(deps.objectMapper(), flagConfig);
            return ImmutableMetadata.builder()
                .addString(FlagMetadataKeys.FLAG_CONFIG_KEY, flagConfig.key())
                .addString(FlagMetadataKeys.FLAG_CONFIG_NAME, flagConfig.name())
                .addString(FlagMetadataKeys.FLAG_CONFIG_DESCRIPTION, flagConfig.description())
                .addString(FlagMetadataKeys.FLAG_CONFIG_VALUE_TYPE, flagConfig.valueType().name())
                .addString(FlagMetadataKeys.FLAG_CONFIG_DEFAULT_VALUE, defaultValueString)
                .addString(FlagMetadataKeys.FLAG_CONFIG_ERROR_STRATEGY, flagConfig.errorStrategy().name())
                .addString(FlagMetadataKeys.FLAG_VALUE_SOURCE, FlagValueSource.APPLICATION_ERROR_STRATEGY.name())
                .build();
        }

        @SuppressWarnings("unchecked")
        protected ProviderEvaluation<T> buildSuccessProviderEvaluation(T value, ImmutableMetadata metadata, String reason) {
            return (ProviderEvaluation<T>) ProviderEvaluation.builder()
                .value(value)
                .flagMetadata(metadata)
                .reason(reason)
                .build();
        }

        @SuppressWarnings("unchecked")
        protected ProviderEvaluation<T> buildErrorProviderEvaluation(
            T fallbackValue,
            ImmutableMetadata metadata,
            String reason,
            ErrorCode errorCode,
            String errorMessage
        ) {
            return (ProviderEvaluation<T>) ProviderEvaluation.builder()
                .value(fallbackValue)
                .flagMetadata(metadata)
                .reason(reason)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
        }

        private static ErrorCode mapValueSourceToErrorCode(String reason, String valueSource) {
            if (FlagValueSource.IZANAMI.name().equals(valueSource) || "DISABLED".equals(reason)) {
                return null;
            }
            return ErrorCode.GENERAL;
        }
    }

    static class BooleanEvaluationExecution extends EvaluationExecution<Boolean> {
        BooleanEvaluationExecution(EvaluationDependencies deps, String flagKey, Boolean callerDefaultValue, EvaluationContext evaluationContext) {
            super(deps, flagKey, callerDefaultValue, evaluationContext);
        }

        @Override
        protected FlagValueType getExpectedType() {
            return FlagValueType.BOOLEAN;
        }

        @Override
        protected ProviderEvaluation<Boolean> evaluateViaIzanami() {
            try {
                ResultValueWithDetails<Boolean> result = buildIzanamiRequest().booleanValueDetails().join();
                return buildProviderEvaluation(result.value(), result, Function.identity());
            } catch (Exception e) {
                return handleEvaluationException(e);
            }
        }
    }

    static class StringEvaluationExecution extends EvaluationExecution<String> {
        StringEvaluationExecution(EvaluationDependencies deps, String flagKey, String callerDefaultValue, EvaluationContext evaluationContext) {
            super(deps, flagKey, callerDefaultValue, evaluationContext);
        }

        @Override
        protected FlagValueType getExpectedType() {
            return FlagValueType.STRING;
        }

        @Override
        protected ProviderEvaluation<String> evaluateViaIzanami() {
            try {
                ResultValueWithDetails<String> result = buildIzanamiRequest().stringValueDetails().join();
                return buildProviderEvaluation(result.value(), result, Function.identity());
            } catch (Exception e) {
                return handleEvaluationException(e);
            }
        }
    }

    static class IntegerEvaluationExecution extends EvaluationExecution<Integer> {
        IntegerEvaluationExecution(EvaluationDependencies deps, String flagKey, Integer callerDefaultValue, EvaluationContext evaluationContext) {
            super(deps, flagKey, callerDefaultValue, evaluationContext);
        }

        @Override
        protected FlagValueType getExpectedType() {
            return FlagValueType.INTEGER;
        }

        @Override
        protected ProviderEvaluation<Integer> evaluateViaIzanami() {
            try {
                ResultValueWithDetails<BigDecimal> result = buildIzanamiRequest().numberValueDetails().join();
                return buildProviderEvaluation(result.value(), result, v -> v != null ? v.intValue() : null);
            } catch (Exception e) {
                return handleEvaluationException(e);
            }
        }
    }

    static class DoubleEvaluationExecution extends EvaluationExecution<Double> {
        DoubleEvaluationExecution(EvaluationDependencies deps, String flagKey, Double callerDefaultValue, EvaluationContext evaluationContext) {
            super(deps, flagKey, callerDefaultValue, evaluationContext);
        }

        @Override
        protected FlagValueType getExpectedType() {
            return FlagValueType.DOUBLE;
        }

        @Override
        protected ProviderEvaluation<Double> evaluateViaIzanami() {
            try {
                ResultValueWithDetails<BigDecimal> result = buildIzanamiRequest().numberValueDetails().join();
                return buildProviderEvaluation(result.value(), result, v -> v != null ? v.doubleValue() : null);
            } catch (Exception e) {
                return handleEvaluationException(e);
            }
        }
    }

    static class ObjectEvaluationExecution extends EvaluationExecution<Value> {
        ObjectEvaluationExecution(EvaluationDependencies deps, String flagKey, Value callerDefaultValue, EvaluationContext evaluationContext) {
            super(deps, flagKey, callerDefaultValue, evaluationContext);
        }

        @Override
        protected FlagValueType getExpectedType() {
            return FlagValueType.OBJECT;
        }

        @Override
        protected ProviderEvaluation<Value> evaluateViaIzanami() {
            try {
                ResultValueWithDetails<String> result = buildIzanamiRequest().stringValueDetails().join();
                return buildProviderEvaluation(result.value(), result, this::parseJsonToValue);
            } catch (Exception e) {
                return handleEvaluationException(e);
            }
        }

        private Value parseJsonToValue(String json) {
            if (json == null || json.isEmpty()) {
                return new Value();
            }
            try {
                Object parsed = deps.objectMapper().readValue(json, Object.class);
                return deps.valueConverter().objectToValue(parsed);
            } catch (JsonProcessingException e) {
                throw new InvalidObjectJsonException("Flag '" + flagConfig.name() + "' returned invalid JSON for valueType=object: " + e.getMessage());
            }
        }

        @Override
        protected Value getFlagNotFoundValue() {
            return callerDefaultValue != null ? callerDefaultValue : new Value();
        }
    }

    private static final class InvalidObjectJsonException extends RuntimeException {
        InvalidObjectJsonException(String message) {
            super(message);
        }
    }
}
