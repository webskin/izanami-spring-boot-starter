package fr.maif.izanami.spring.openfeature;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfeature.sdk.*;
import fr.maif.features.results.IzanamiResult;
import fr.maif.features.values.BooleanCastStrategy;
import fr.maif.izanami.spring.openfeature.api.FlagConfigService;
import fr.maif.izanami.spring.service.IzanamiClientNotAvailableException;
import fr.maif.izanami.spring.service.IzanamiService;
import fr.maif.izanami.spring.service.ResultWithMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

import java.text.MessageFormat;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    private final FlagConfigService flagConfigService;
    private final IzanamiService izanamiService;
    private final ObjectMapper objectMapper;
    private final EvaluationDependencies evaluationDependencies;
    private final ValueConverter valueConverter;

    /**
     * Create a provider.
     *
     * @param flagConfigService access to configured flags
     * @param izanamiService    Izanami client wrapper
     * @param objectMapper      mapper used for JSON parsing/serialization (object flags)
     */
    public IzanamiFeatureProvider(
        FlagConfigService flagConfigService,
        IzanamiService izanamiService,
        ObjectMapper objectMapper
    ) {
        this.flagConfigService = flagConfigService;
        this.izanamiService = izanamiService;
        this.objectMapper = objectMapper;
        this.evaluationDependencies = new EvaluationDependencies(flagConfigService, izanamiService, objectMapper);
        this.valueConverter = new ValueConverter(objectMapper);
    }

    @Override
    public Metadata getMetadata() {
        return () -> "Izanami (Spring Boot Starter)";
    }

    @Override
    public void initialize(EvaluationContext evaluationContext) {
        // No-op: the provider is fully configured via Spring and IzanamiService lifecycle.
    }

    @Override
    public void shutdown() {
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
        return new PrimitiveEvaluationExecution<>(evaluationDependencies, key, callerDefaultValue, ctx, this::extractBoolean)
            .evaluatePrimitive(FlagValueType.BOOLEAN);
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String callerDefaultValue, EvaluationContext ctx) {
        return new PrimitiveEvaluationExecution<>(evaluationDependencies, key, callerDefaultValue, ctx, this::extractString)
            .evaluatePrimitive(FlagValueType.STRING);
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer callerDefaultValue, EvaluationContext ctx) {
        return new PrimitiveEvaluationExecution<>(evaluationDependencies, key, callerDefaultValue, ctx, this::extractInteger)
            .evaluatePrimitive(FlagValueType.INTEGER);
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double callerDefaultValue, EvaluationContext ctx) {
        return new PrimitiveEvaluationExecution<>(evaluationDependencies, key, callerDefaultValue, ctx, this::extractDouble)
            .evaluatePrimitive(FlagValueType.DOUBLE);
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String key, Value callerDefaultValue, EvaluationContext ctx) {
        return new ObjectEvaluationExecution(evaluationDependencies, key, callerDefaultValue, ctx, this::extractObject)
            .evaluateObject();
    }

    @FunctionalInterface
    interface FlagValueExtractor<T> {
        T extract(IzanamiResult.Result result, FlagConfig flagConfig);
    }

    private record EvaluationDependencies(
        FlagConfigService flagConfigService,
        IzanamiService izanamiService,
        ObjectMapper objectMapper
    ) {}

    static class EvaluationExecution<T> {
        protected final EvaluationDependencies deps;
        protected final String flagKey;
        protected final T callerDefaultValue;
        protected final EvaluationContext evaluationContext;
        protected final FlagValueExtractor<T> flagValueExtractor;
        protected final FlagConfig flagConfig;
        protected final boolean flagConfigResolved;

        EvaluationExecution(EvaluationDependencies deps, String flagKey, T callerDefaultValue, EvaluationContext evaluationContext, FlagValueExtractor<T> flagValueExtractor) {
            this.deps = deps;
            this.flagKey = flagKey;
            this.evaluationContext = evaluationContext;
            this.callerDefaultValue = callerDefaultValue;
            this.flagConfig = deps.flagConfigService().getFlagConfigByKey(flagKey).orElse(null);
            this.flagValueExtractor = flagValueExtractor;
            this.flagConfigResolved = this.flagConfig != null;
        }

        protected ProviderEvaluation<T> evaluateViaIzanami() {
            return evaluateViaIzanami(callerDefaultValue);
        }

        protected ProviderEvaluation<T> evaluateViaIzanami(T callerDefaultValue) {
            Optional<ResultWithMetadata> maybeResult;
            try {
                maybeResult = queryIzanami(flagConfig, evaluationContext);
            } catch (Exception e) {
                return errorStrategyProviderEvaluation(
                    callerDefaultValue,
                    applicationErrorMetadata(flagConfig),
                    Reason.ERROR.name(),
                    ErrorCode.GENERAL,
                    MessageFormat.format("Applying application error strategy. Unable to extract flag value: {0}. Use fallback value: {1}", e.getMessage(), callerDefaultValue)
                );
            }

            if (maybeResult.isEmpty()) {
                // Izanami FAIL strategy
                return errorStrategyProviderEvaluation(
                    callerDefaultValue,
                    applicationErrorMetadata(flagConfig),
                    Reason.ERROR.name(),
                    ErrorCode.GENERAL,
                    "Applying application error strategy. Use fallback value: " + callerDefaultValue
                );
            }
            ResultWithMetadata resultWithMetadata = maybeResult.get();
            IzanamiResult.Result result = resultWithMetadata.result();
            ImmutableMetadata metadata = computeImmutableMetadataFromResultWithMetadata(resultWithMetadata);

            try {
                if (result instanceof IzanamiResult.Success) {
                    T value = flagValueExtractor.extract(result, flagConfig);
                    return successProviderEvaluation(
                        value,
                        metadata,
                        determineSuccessReason(value, flagConfig)
                    );
                } else {
                    // result instanceof IzanamiResult.Error
                    T value = flagValueExtractor.extract(result, flagConfig);
                    return errorStrategyProviderEvaluation(
                        value,
                        metadata,
                        Reason.ERROR.name(),
                        ErrorCode.GENERAL,
                        "Applying Izanami error strategy. Use fallback value: " + value
                    );
                }
            } catch (Exception e) {
                return errorStrategyProviderEvaluation(
                    callerDefaultValue,
                    metadata,
                    Reason.ERROR.name(),
                    ErrorCode.GENERAL,
                    MessageFormat.format("Applying application error strategy. Unable to extract flag value: {0}. Use fallback value: {1}", e.getMessage(), callerDefaultValue)
                );
            }
        }

        private Optional<ResultWithMetadata> queryIzanami(FlagConfig flagConfig, EvaluationContext evaluationContext) throws IzanamiClientNotAvailableException {
            Value contextValue = evaluationContext.getValue(IZANAMI_CONTEXT_ATTRIBUTE);
            String context = contextValue != null ? contextValue.asString() : null;
            return deps.izanamiService()
                .forFlagKey(flagConfig.key())
                .withUser(evaluationContext.getTargetingKey())
                .withContext(context)
                .featureResultWithMetadata();
        }

        protected ProviderEvaluation<T> typeMismatch(FlagValueType expectedType) {
            return errorStrategyProviderEvaluation(
                callerDefaultValue,
                applicationErrorMetadata(flagConfig),
                Reason.ERROR.name(),
                ErrorCode.TYPE_MISMATCH,
                "Feature flag '" + flagKey + "' is configured as '" + flagConfig.valueType().name()
                    + "' but evaluated as '" + expectedType.name() + "'"
            );
        }

        private ImmutableMetadata computeImmutableMetadataFromResultWithMetadata(ResultWithMetadata resultWithMetadata) {
            ImmutableMetadata.ImmutableMetadataBuilder builder = ImmutableMetadata.builder();
            resultWithMetadata.metadata().forEach((key, value) -> {
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
            String defaultValueString = IzanamiService.stringifyDefaultValue(deps.objectMapper(), flagConfig);
            return ImmutableMetadata.builder()
                .addString(FlagMetadataKeys.FLAG_CONFIG_KEY, flagConfig.key())
                .addString(FlagMetadataKeys.FLAG_CONFIG_NAME, flagConfig.name())
                .addString(FlagMetadataKeys.FLAG_CONFIG_DESCRIPTION, flagConfig.description())
                .addString(FlagMetadataKeys.FLAG_CONFIG_VALUE_TYPE, flagConfig.valueType().name())
                .addString(FlagMetadataKeys.FLAG_CONFIG_DEFAULT_VALUE, defaultValueString)
                .addString(FlagMetadataKeys.FLAG_CONFIG_ERROR_STRATEGY, flagConfig.rawErrorStrategy().name())
                .addString(FlagMetadataKeys.FLAG_VALUE_SOURCE, FlagValueSource.APPLICATION_ERROR_STRATEGY.name())
                .build();
        }
    }

    static class PrimitiveEvaluationExecution<T> extends EvaluationExecution<T> {
        PrimitiveEvaluationExecution(EvaluationDependencies deps, String flagKey, T callerDefaultValue, EvaluationContext evaluationContext, FlagValueExtractor<T> flagValueExtractor) {
            super(deps, flagKey, callerDefaultValue, evaluationContext, flagValueExtractor);
        }

        public ProviderEvaluation<T> evaluatePrimitive(FlagValueType expectedType) {
            if (!flagConfigResolved) {
                return flagNotFound();
            }
            if (flagConfig.valueType() != expectedType) {
                return typeMismatch(expectedType);
            }
            return evaluateViaIzanami();
        }
    }

    static class ObjectEvaluationExecution extends EvaluationExecution<Value> {
        private final ValueConverter valueConverter;

        ObjectEvaluationExecution(EvaluationDependencies deps, String flagKey, Value callerDefaultValue, EvaluationContext evaluationContext, FlagValueExtractor<Value> flagValueExtractor) {
            super(deps, flagKey, callerDefaultValue, evaluationContext, flagValueExtractor);
            this.valueConverter = new ValueConverter(deps.objectMapper());
        }

        public ProviderEvaluation<Value> evaluateObject() {
            if (!flagConfigResolved) {
                return flagNotFound();
            }
            if (flagConfig.valueType() != FlagValueType.OBJECT) {
                return typeMismatch(FlagValueType.OBJECT);
            }
            Value coercedCallerDefaultValue = valueConverter.toOpenFeatureValueOrNullSafe(flagConfig.defaultValue(), callerDefaultValue);
            return evaluateViaIzanami(coercedCallerDefaultValue);
        }

        @Override
        protected Value getFlagNotFoundValue() {
            return callerDefaultValue != null ? callerDefaultValue : new Value();
        }
    }

    private Boolean extractBoolean(IzanamiResult.Result result, FlagConfig flagConfig) {
        return result.booleanValue(BooleanCastStrategy.LAX);
    }

    private String extractString(IzanamiResult.Result result, FlagConfig flagConfig) {
        // TODO is result.stringValue can be null ?
        // TODO fallbackValue is not used
        return result.stringValue();
    }

    private Integer extractInteger(IzanamiResult.Result result, FlagConfig flagConfig) {
        // TODO is result.numberValue can be null ?
        // TODO fallbackValue is not used
        return Optional.ofNullable(result.numberValue()).map(Number::intValue).orElse(null);
    }

    private Double extractDouble(IzanamiResult.Result result, FlagConfig flagConfig) {
        // TODO is result.numberValue can be null ?
        // TODO fallbackValue is not used
        return Optional.ofNullable(result.numberValue()).map(Number::doubleValue).orElse(null);
    }

    private Value extractObject(IzanamiResult.Result result, FlagConfig flagConfig) {
        String json = result.stringValue();
        if (json == null) {
            return new Value();
        }
        try {
            Object tree = objectMapper.readValue(json, Object.class);
            return valueConverter.objectToValue(tree);
        } catch (JsonProcessingException e) {
            throw new InvalidObjectJsonException("Flag '" + flagConfig.name() + "' returned invalid JSON for valueType=object");
        }
    }

    static class ValueConverter {
        private final ObjectMapper objectMapper;

        ValueConverter(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        Value toOpenFeatureValueOrNullSafe(@Nullable Object configuredDefault, @Nullable Value callerDefault) {
            if (configuredDefault == null) {
                return callerDefault != null ? callerDefault : new Value();
            }
            if (configuredDefault instanceof Value v) {
                return v;
            }
            if (configuredDefault instanceof String s) {
                try {
                    Object tree = objectMapper.readValue(s, Object.class);
                    return objectToValue(tree);
                } catch (JsonProcessingException e) {
                    return new Value(s);
                }
            }
            try {
                return objectToValue(configuredDefault);
            } catch (Exception e) {
                log.debug("Failed to convert configured default to OpenFeature Value, falling back to caller default: {}", e.getMessage());
                return callerDefault != null ? callerDefault : new Value();
            }
        }

        Value objectToValue(Object object) {
            if (object instanceof Value v) {
                return v;
            }
            if (object == null) {
                return new Value();
            }
            if (object instanceof String s) {
                return new Value(s);
            }
            if (object instanceof Boolean b) {
                return new Value(b);
            }
            if (object instanceof Integer i) {
                return new Value(i);
            }
            if (object instanceof Double d) {
                return new Value(d);
            }
            if (object instanceof Number n) {
                try {
                    return new Value((Object) n);
                } catch (InstantiationException e) {
                    return new Value(n.doubleValue());
                }
            }
            if (object instanceof Structure s) {
                return new Value(s);
            }
            if (object instanceof List<?> list) {
                List<Value> values = list.stream().map(this::objectToValue).toList();
                return new Value(values);
            }
            if (object instanceof Instant instant) {
                return new Value(instant);
            }
            if (object instanceof Map<?, ?> map) {
                Map<String, Value> attributes = new java.util.LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() == null) {
                        continue;
                    }
                    attributes.put(entry.getKey().toString(), objectToValue(entry.getValue()));
                }
                return new Value(new MutableStructure(attributes));
            }
            throw new dev.openfeature.sdk.exceptions.TypeMismatchError(
                "Flag value '" + object + "' had unexpected type " + object.getClass());
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> ProviderEvaluation<T> successProviderEvaluation(
        T value,
        ImmutableMetadata metadata,
        String reason
    ) {
        return (ProviderEvaluation<T>) ProviderEvaluation.builder()
            .value(value)
            .flagMetadata(metadata)
            .reason(reason)
            .build();
    }

    @SuppressWarnings("unchecked")
    private static <T> ProviderEvaluation<T> errorStrategyProviderEvaluation(
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

    /**
     * Determines the appropriate OpenFeature reason for a successful Izanami evaluation.
     *
     * @param value      the evaluated flag value
     * @param flagConfig the flag configuration
     * @return the reason string: DISABLED for false booleans, DEFAULT for null values, UNKNOWN otherwise
     */
    private static String determineSuccessReason(Object value, FlagConfig flagConfig) {
        if (flagConfig.valueType() == FlagValueType.BOOLEAN && Boolean.FALSE.equals(value)) {
            return Reason.DISABLED.name();
        }
        if (value == null) {
            return Reason.DEFAULT.name();
        }
        return Reason.UNKNOWN.name();
    }

    private static final class InvalidObjectJsonException extends RuntimeException {
        InvalidObjectJsonException(String message) {
            super(message);
        }
    }
}
