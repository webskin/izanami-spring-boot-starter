package fr.maif.izanami.spring.openfeature.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfeature.sdk.FlagValueType;
import fr.maif.FeatureClientErrorStrategy;
import fr.maif.izanami.spring.openfeature.ErrorStrategy;
import fr.maif.izanami.spring.openfeature.FlagConfig;
import fr.maif.izanami.spring.openfeature.api.ErrorStrategyFactory;
import fr.maif.izanami.spring.openfeature.api.IzanamiErrorCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;

import static java.util.concurrent.CompletableFuture.completedFuture;

/**
 * Default {@link ErrorStrategyFactory} implementation.
 */
public final class ErrorStrategyFactoryImpl implements ErrorStrategyFactory {
    private static final Logger log = LoggerFactory.getLogger(ErrorStrategyFactoryImpl.class);

    private final ObjectMapper objectMapper;
    private final BeanFactory beanFactory;

    /**
     * Create a factory using the provided {@link ObjectMapper} and {@link BeanFactory}.
     *
     * @param objectMapper mapper used to serialize object defaults to JSON
     * @param beanFactory  Spring bean factory for looking up callback beans
     */
    public ErrorStrategyFactoryImpl(ObjectMapper objectMapper, BeanFactory beanFactory) {
        this.objectMapper = objectMapper;
        this.beanFactory = beanFactory;
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
            case CALLBACK -> createCallbackStrategy(config);
        };
    }

    private FeatureClientErrorStrategy<?> createCallbackStrategy(FlagConfig config) {
        IzanamiErrorCallback callback = resolveCallback(config);

        if (callback == null) {
            log.debug("No callback bean found for flag '{}', using type-safe defaults", config.name());
            return FeatureClientErrorStrategy.callbackStrategy(
                error -> completedFuture(asBooleanDefault(config)),
                error -> completedFuture(asStringDefault(config)),
                error -> completedFuture(asNumberDefault(config))
            );
        }

        return FeatureClientErrorStrategy.callbackStrategy(
            error -> callback.onError(error, config, FlagValueType.BOOLEAN)
                .thenApply(value -> coerceToBoolean(value, config)),
            error -> callback.onError(error, config, FlagValueType.STRING)
                .thenApply(value -> coerceToString(value, config)),
            error -> callback.onError(error, config, FlagValueType.DOUBLE)
                .thenApply(value -> coerceToNumber(value, config))
        );
    }

    @Nullable
    private IzanamiErrorCallback resolveCallback(FlagConfig config) {
        String beanName = config.callbackBean();
        if (beanName == null || beanName.isBlank()) {
            return null;
        }

        try {
            Object bean = beanFactory.getBean(beanName);
            if (bean instanceof IzanamiErrorCallback callback) {
                return callback;
            }
            log.error("Bean '{}' for flag '{}' does not implement IzanamiErrorCallback, found: {}",
                beanName, config.name(), bean.getClass().getName());
            return null;
        } catch (NoSuchBeanDefinitionException e) {
            log.error("Callback bean '{}' not found for flag '{}'. "
                + "Please ensure a bean with this name implementing IzanamiErrorCallback exists.",
                beanName, config.name());
            return null;
        }
    }

    private boolean coerceToBoolean(@Nullable Object value, FlagConfig config) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.intValue() != 0;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private String coerceToString(@Nullable Object value, FlagConfig config) {
        if (value == null) {
            return "";
        }
        if (value instanceof String s) {
            return s;
        }
        if (!(value instanceof Number) && !(value instanceof Boolean)) {
            return toJson(config, value);
        }
        return value.toString();
    }

    private BigDecimal coerceToNumber(@Nullable Object value, FlagConfig config) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            log.warn("Callback for flag '{}' returned non-numeric value '{}' for NUMBER type",
                config.name(), value);
            return BigDecimal.ZERO;
        }
    }

    private boolean asBooleanDefault(FlagConfig config) {
        if (config.valueType() != FlagValueType.BOOLEAN) {
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
        if (config.valueType() == FlagValueType.OBJECT) {
            return toJson(config, defaultValue);
        }
        return defaultValue.toString();
    }

    private BigDecimal asNumberDefault(FlagConfig config) {
        if (config.valueType() != FlagValueType.INTEGER && config.valueType() != FlagValueType.DOUBLE) {
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
