package fr.maif.izanami.spring.openfeature.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.maif.FeatureClientErrorStrategy;
import fr.maif.izanami.spring.openfeature.ErrorStrategy;
import fr.maif.izanami.spring.openfeature.EvaluationValueType;
import fr.maif.izanami.spring.openfeature.FlagConfig;
import fr.maif.izanami.spring.openfeature.api.ErrorStrategyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

import static java.util.concurrent.CompletableFuture.completedFuture;

/**
 * Default {@link ErrorStrategyFactory} implementation.
 */
public final class ErrorStrategyFactoryImpl implements ErrorStrategyFactory {
    private static final Logger log = LoggerFactory.getLogger(ErrorStrategyFactoryImpl.class);

    private final ObjectMapper objectMapper;

    /**
     * Create a factory using the provided {@link ObjectMapper}.
     *
     * @param objectMapper mapper used to serialize object defaults to JSON
     */
    public ErrorStrategyFactoryImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public FeatureClientErrorStrategy<?> createErrorStrategy(FlagConfig config) {
        ErrorStrategy strategy = config.errorStrategy() == null ? ErrorStrategy.DEFAULT_VALUE : config.errorStrategy();
        return switch (strategy) {
            case DEFAULT_VALUE -> FeatureClientErrorStrategy.defaultValueStrategy(
                asBooleanDefault(config),
                asStringDefault(config),
                asNumberDefault(config)
            );
            case NULL_VALUE -> FeatureClientErrorStrategy.nullValueStrategy();
            case FAIL -> FeatureClientErrorStrategy.failStrategy();
            case CALLBACK -> FeatureClientErrorStrategy.callbackStrategy(
                error -> completedFuture(asBooleanDefault(config)),
                error -> completedFuture(asStringDefault(config)),
                error -> completedFuture(asNumberDefault(config))
            );
        };
    }

    private boolean asBooleanDefault(FlagConfig config) {
        if (config.valueType() != EvaluationValueType.BOOLEAN) {
            return false;
        }
        Object defaultValue = config.defaultValue();
        if (defaultValue == null) {
            return false;
        }
        if (defaultValue instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(defaultValue.toString());
    }

    private String asStringDefault(FlagConfig config) {
        Object defaultValue = config.defaultValue();
        if (defaultValue == null) {
            return "";
        }
        if (config.valueType() == EvaluationValueType.OBJECT) {
            return toJson(config, defaultValue);
        }
        return defaultValue.toString();
    }

    private BigDecimal asNumberDefault(FlagConfig config) {
        if (config.valueType() != EvaluationValueType.NUMBER) {
            return BigDecimal.ZERO;
        }
        Object defaultValue = config.defaultValue();
        if (defaultValue == null) {
            return BigDecimal.ZERO;
        }
        if (defaultValue instanceof BigDecimal bd) {
            return bd;
        }
        if (defaultValue instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(defaultValue.toString());
        } catch (NumberFormatException e) {
            log.warn("Invalid number default for flag '{}': '{}'", config.name(), defaultValue);
            return BigDecimal.ZERO;
        }
    }

    private String toJson(FlagConfig config, Object value) {
        if (value instanceof String s) {
            return s;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize object default for flag '{}', falling back to empty object: {}", config.name(), e.getMessage());
            return "{}";
        }
    }
}
