package fr.maif.izanami.spring.openfeature;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.FlagValueType;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.ImmutableMetadata;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.MutableStructure;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Structure;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.GeneralError;
import fr.maif.FeatureClientErrorStrategy;
import fr.maif.features.results.IzanamiResult;
import fr.maif.features.values.BooleanCastStrategy;
import fr.maif.izanami.spring.openfeature.api.ErrorStrategyFactory;
import fr.maif.izanami.spring.openfeature.api.FlagConfigService;
import fr.maif.izanami.spring.service.IzanamiService;
import fr.maif.requests.FeatureRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * OpenFeature {@link FeatureProvider} backed by Izanami.
 * <p>
 * This provider resolves flags based on {@code openfeature.flags} configuration:
 * <ul>
 *   <li>The OpenFeature flag key can be either the configured {@link FlagConfig#name()} or {@link FlagConfig#id()}.</li>
 *   <li>Izanami is always queried by id.</li>
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
    private final ErrorStrategyFactory errorStrategyFactory;
    private final ObjectMapper objectMapper;

    /**
     * Create a provider.
     *
     * @param flagConfigService    access to configured flags
     * @param izanamiService       Izanami client wrapper
     * @param errorStrategyFactory per-flag error strategy factory
     * @param objectMapper         mapper used for JSON parsing/serialization (object flags)
     */
    public IzanamiFeatureProvider(
        FlagConfigService flagConfigService,
        IzanamiService izanamiService,
        ErrorStrategyFactory errorStrategyFactory,
        ObjectMapper objectMapper
    ) {
        this.flagConfigService = flagConfigService;
        this.izanamiService = izanamiService;
        this.errorStrategyFactory = errorStrategyFactory;
        this.objectMapper = objectMapper;
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
    public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean defaultValue, EvaluationContext ctx) {
        return evaluatePrimitive(key, defaultValue, ctx, FlagValueType.BOOLEAN, this::extractBoolean, FlagConfig::booleanDefault);
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue, EvaluationContext ctx) {
        return evaluatePrimitive(key, defaultValue, ctx, FlagValueType.STRING, this::extractString, FlagConfig::stringDefault);
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue, EvaluationContext ctx) {
        return evaluatePrimitive(key, defaultValue, ctx, FlagValueType.INTEGER, this::extractInteger, FlagConfig::integerDefault);
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue, EvaluationContext ctx) {
        return evaluatePrimitive(key, defaultValue, ctx, FlagValueType.DOUBLE, this::extractDouble, FlagConfig::doubleDefault);
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String key, Value defaultValue, EvaluationContext ctx) {
        Optional<FlagConfig> maybeConfig = resolveConfig(key);
        if (maybeConfig.isEmpty()) {
            return notFound(key, defaultValue, FlagMetadataKeys.FLAG_CONFIG_NAME);
        }
        FlagConfig config = maybeConfig.get();
        if (config.valueType() != FlagValueType.OBJECT) {
            return typeMismatch(key, defaultValue, config, FlagValueType.OBJECT);
        }

        Value fallbackValue = toOpenFeatureValueOrNullSafe(config.defaultValue(), defaultValue);
        EvaluationOutcome<Value> outcome = evaluateViaIzanami(config, ctx, fallbackValue, this::extractObject);
        return toProviderEvaluation(outcome, config);
    }

    private <T> ProviderEvaluation<T> evaluatePrimitive(
        String key,
        @Nullable T defaultValue,
        EvaluationContext ctx,
        FlagValueType expectedType,
        PrimitiveExtractor<T> extractor,
        DefaultValueConverter<T> defaultValueConverter
    ) {
        Optional<FlagConfig> maybeConfig = resolveConfig(key);
        if (maybeConfig.isEmpty()) {
            return notFound(key, defaultValue, FlagMetadataKeys.FLAG_CONFIG_NAME);
        }
        FlagConfig config = maybeConfig.get();
        if (config.valueType() != expectedType) {
            return typeMismatch(key, defaultValue, config, expectedType);
        }

        T fallbackValue = defaultValueConverter.convert(config, defaultValue);
        EvaluationOutcome<T> outcome = evaluateViaIzanami(config, ctx, fallbackValue, extractor);
        return toProviderEvaluation(outcome, config);
    }

    private Optional<FlagConfig> resolveConfig(String key) {
        return flagConfigService.getFlagConfigByName(key)
            .or(() -> flagConfigService.getFlagConfigById(key));
    }

    private <T> EvaluationOutcome<T> evaluateViaIzanami(
        FlagConfig config,
        EvaluationContext ctx,
        T fallbackValue,
        PrimitiveExtractor<T> extractor
    ) {
        IzanamiContext izanamiContext = IzanamiContext.from(ctx);

        if (config.id() == null || config.id().isBlank()) {
            return handleApplicationError(config, fallbackValue, "Flag id is blank");
        }

        Optional<IzanamiResult.Result> maybeResult = queryIzanami(config, izanamiContext);
        if (maybeResult.isEmpty()) {
            return handleApplicationError(config, fallbackValue, "Izanami client not available or evaluation failed");
        }

        IzanamiResult.Result result = maybeResult.get();
        FlagValueSource source =
            (result instanceof IzanamiResult.Success) ? FlagValueSource.IZANAMI : FlagValueSource.IZANAMI_ERROR_STRATEGY;

        try {
            T value = extractor.extract(result, config, fallbackValue);
            String reason = (source == FlagValueSource.IZANAMI) ? null : Reason.ERROR.toString();
            return new EvaluationOutcome<>(value, source, null, null, reason);
        } catch (Exception e) {
            return handleApplicationError(config, fallbackValue, e.getMessage());
        }
    }

    private <T> EvaluationOutcome<T> handleApplicationError(FlagConfig config, T fallbackValue, String message) {
        ErrorStrategy strategy = config.errorStrategy();
        return switch (strategy) {
            case FAIL -> throw new GeneralError(message);
            case NULL_VALUE -> new EvaluationOutcome<>(null, FlagValueSource.APPLICATION_ERROR_STRATEGY, null, message, Reason.ERROR.toString());
            case DEFAULT_VALUE, CALLBACK -> EvaluationOutcome.applicationFallback(fallbackValue, message);
        };
    }

    private Optional<IzanamiResult.Result> queryIzanami(FlagConfig config, IzanamiContext context) {
        FeatureClientErrorStrategy<?> errorStrategy = errorStrategyFactory.createErrorStrategy(config);

        FeatureRequest request = FeatureRequest.newFeatureRequest()
            .withFeature(config.id())
            .withErrorStrategy(errorStrategy)
            .withBooleanCastStrategy(BooleanCastStrategy.LAX);

        if (context.user() != null && !context.user().isBlank()) {
            request.withUser(context.user());
        }
        if (context.contextPath() != null && !context.contextPath().isBlank()) {
            request.withContext(context.contextPath());
        }

        boolean propagateErrors = config.errorStrategy() == ErrorStrategy.FAIL;
        return izanamiService.getFeatureResult(request, propagateErrors);
    }

    private Boolean extractBoolean(IzanamiResult.Result result, FlagConfig config, Boolean fallbackValue) {
        return result.booleanValue(BooleanCastStrategy.LAX);
    }

    private String extractString(IzanamiResult.Result result, FlagConfig config, String fallbackValue) {
        return result.stringValue();
    }

    private Integer extractInteger(IzanamiResult.Result result, FlagConfig config, Integer fallbackValue) {
        return Optional.ofNullable(result.numberValue()).map(Number::intValue).orElse(null);
    }

    private Double extractDouble(IzanamiResult.Result result, FlagConfig config, Double fallbackValue) {
        return Optional.ofNullable(result.numberValue()).map(Number::doubleValue).orElse(null);
    }

    private Value extractObject(IzanamiResult.Result result, FlagConfig config, Value fallbackValue) {
        String json = result.stringValue();
        if (json == null) {
            return new Value();
        }
        try {
            Object tree = objectMapper.readValue(json, Object.class);
            return objectToValue(tree);
        } catch (JsonProcessingException e) {
            throw new InvalidObjectJsonException("Flag '" + config.name() + "' returned invalid JSON for valueType=object");
        }
    }

    private Value toOpenFeatureValueOrNullSafe(@Nullable Object configuredDefault, @Nullable Value callerDefault) {
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

    private ProviderEvaluation<Value> notFound(String key, Value defaultValue, String metadataKey) {
        return ProviderEvaluation.<Value>builder()
            .value(defaultValue != null ? defaultValue : new Value())
            .errorCode(ErrorCode.FLAG_NOT_FOUND)
            .errorMessage("Feature flag '" + key + "' not found in openfeature.flags")
            .flagMetadata(ImmutableMetadata.builder().addString(metadataKey, key).build())
            .build();
    }

    private <T> ProviderEvaluation<T> notFound(String key, @Nullable T defaultValue, String metadataKey) {
        return ProviderEvaluation.<T>builder()
            .value(defaultValue)
            .errorCode(ErrorCode.FLAG_NOT_FOUND)
            .errorMessage("Feature flag '" + key + "' not found in openfeature.flags")
            .flagMetadata(ImmutableMetadata.builder().addString(metadataKey, key).build())
            .build();
    }

    private <T> ProviderEvaluation<T> typeMismatch(
        String key,
        @Nullable T defaultValue,
        FlagConfig config,
        FlagValueType expectedType
    ) {
        return ProviderEvaluation.<T>builder()
            .value(defaultValue)
            .errorCode(ErrorCode.TYPE_MISMATCH)
            .errorMessage("Feature flag '" + key + "' is configured as '" + config.valueType().name()
                + "' but evaluated as '" + expectedType.name() + "'")
            .flagMetadata(metadata(config, FlagValueSource.APPLICATION_ERROR_STRATEGY))
            .build();
    }

    private <T> ProviderEvaluation<T> toProviderEvaluation(EvaluationOutcome<T> outcome, FlagConfig config) {
        ProviderEvaluation.ProviderEvaluationBuilder<T> builder = ProviderEvaluation.<T>builder()
            .value(outcome.value())
            .flagMetadata(metadata(config, outcome.source()));

        if (outcome.errorCode() != null) {
            builder.errorCode(outcome.errorCode()).errorMessage(outcome.errorMessage());
            return builder.build();
        }

        if (outcome.source() == FlagValueSource.IZANAMI) {
            return builder.build();
        }

        if (outcome.reason() != null) {
            builder.reason(outcome.reason());
        }
        if (outcome.errorMessage() != null) {
            builder.errorMessage(outcome.errorMessage());
        }
        return builder.build();
    }

    private ImmutableMetadata metadata(FlagConfig config, FlagValueSource source) {
        String defaultValueString = stringifyDefaultValue(config);
        return ImmutableMetadata.builder()
            .addString(FlagMetadataKeys.FLAG_CONFIG_ID, config.id())
            .addString(FlagMetadataKeys.FLAG_CONFIG_NAME, config.name())
            .addString(FlagMetadataKeys.FLAG_CONFIG_DESCRIPTION, config.description())
            .addString(FlagMetadataKeys.FLAG_CONFIG_VALUE_TYPE, config.valueType().name())
            .addString(FlagMetadataKeys.FLAG_CONFIG_DEFAULT_VALUE, defaultValueString)
            .addString(FlagMetadataKeys.FLAG_CONFIG_ERROR_STRATEGY, config.errorStrategy() == null ? null : config.errorStrategy().name())
            .addString(FlagMetadataKeys.FLAG_EVALUATION_VALUE_SOURCE, source.name())
            .build();
    }

    private String stringifyDefaultValue(FlagConfig config) {
        Object defaultValue = config.defaultValue();
        if (defaultValue == null) {
            return null;
        }
        if (config.valueType() == FlagValueType.OBJECT) {
            try {
                return objectMapper.writeValueAsString(defaultValue);
            } catch (JsonProcessingException e) {
                return defaultValue.toString();
            }
        }
        return defaultValue.toString();
    }

    @SuppressWarnings("unchecked")
    private Value objectToValue(Object object) {
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

    /**
     * Lazily extracted Izanami context from an OpenFeature evaluation context.
     */
    private record IzanamiContext(@Nullable String user, @Nullable String contextPath) {
        static IzanamiContext from(@Nullable EvaluationContext ctx) {
            if (ctx == null) {
                return new IzanamiContext(null, null);
            }
            String user = ctx.getTargetingKey();
            Value contextValue = ctx.getValue(IZANAMI_CONTEXT_ATTRIBUTE);
            String contextPath = contextValue != null ? contextValue.asString() : null;
            return new IzanamiContext(user, contextPath);
        }
    }

    private record EvaluationOutcome<T>(
        T value,
        FlagValueSource source,
        @Nullable ErrorCode errorCode,
        @Nullable String errorMessage,
        @Nullable String reason
    ) {
        static <T> EvaluationOutcome<T> applicationFallback(T fallbackValue, @Nullable String message) {
            return new EvaluationOutcome<>(fallbackValue, FlagValueSource.APPLICATION_ERROR_STRATEGY, null, message, Reason.DEFAULT.toString());
        }

        static <T> EvaluationOutcome<T> invalidJsonFallback(T fallbackValue, @Nullable String message) {
            return new EvaluationOutcome<>(fallbackValue, FlagValueSource.APPLICATION_ERROR_STRATEGY, null, message, Reason.ERROR.toString());
        }
    }

    @FunctionalInterface
    private interface PrimitiveExtractor<T> {
        T extract(IzanamiResult.Result result, FlagConfig config, T fallbackValue);
    }

    @FunctionalInterface
    private interface DefaultValueConverter<T> {
        T convert(FlagConfig config, @Nullable T callerDefault);
    }

    private static final class InvalidObjectJsonException extends RuntimeException {
        InvalidObjectJsonException(String message) {
            super(message);
        }
    }
}
