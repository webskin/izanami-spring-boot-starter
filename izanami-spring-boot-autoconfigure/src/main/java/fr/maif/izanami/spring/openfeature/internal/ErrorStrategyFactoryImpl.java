package fr.maif.izanami.spring.openfeature.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfeature.sdk.FlagValueType;
import fr.maif.FeatureClientErrorStrategy;
import fr.maif.izanami.spring.openfeature.ErrorStrategy;
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
    public FeatureClientErrorStrategy<?> createErrorStrategy(
            ErrorStrategy strategy,
            FlagValueType valueType,
            @Nullable Object defaultValue,
            @Nullable String callbackBean,
            String flagKey
    ) {
        return switch (strategy) {
            case DEFAULT_VALUE -> FeatureClientErrorStrategy.defaultValueStrategy(
                asBooleanDefault(valueType, defaultValue),
                asStringDefault(valueType, defaultValue, flagKey),
                asNumberDefault(valueType, defaultValue, flagKey)
            );
            case NULL_VALUE -> FeatureClientErrorStrategy.nullValueStrategy();
            case FAIL -> FeatureClientErrorStrategy.failStrategy();
            case CALLBACK -> createCallbackStrategy(valueType, defaultValue, callbackBean, flagKey);
        };
    }

    private FeatureClientErrorStrategy<?> createCallbackStrategy(
            FlagValueType valueType,
            @Nullable Object defaultValue,
            @Nullable String callbackBean,
            String flagKey
    ) {
        IzanamiErrorCallback callback = resolveCallback(callbackBean, flagKey);

        if (callback == null) {
            log.debug("No callback bean found for flag '{}', using type-safe defaults", flagKey);
            return FeatureClientErrorStrategy.callbackStrategy(
                error -> completedFuture(asBooleanDefault(valueType, defaultValue)),
                error -> completedFuture(asStringDefault(valueType, defaultValue, flagKey)),
                error -> completedFuture(asNumberDefault(valueType, defaultValue, flagKey))
            );
        }

        return FeatureClientErrorStrategy.callbackStrategy(
            error -> callback.onError(error, flagKey, valueType, FlagValueType.BOOLEAN)
                .thenApply(this::coerceToBoolean),
            error -> callback.onError(error, flagKey, valueType, FlagValueType.STRING)
                .thenApply(value -> coerceToString(value, flagKey)),
            error -> callback.onError(error, flagKey, valueType, FlagValueType.DOUBLE)
                .thenApply(value -> coerceToNumber(value, flagKey))
        );
    }

    @Nullable
    private IzanamiErrorCallback resolveCallback(@Nullable String callbackBean, String flagKey) {
        if (callbackBean == null || callbackBean.isBlank()) {
            return null;
        }

        try {
            Object bean = beanFactory.getBean(callbackBean);
            if (bean instanceof IzanamiErrorCallback callback) {
                return callback;
            }
            log.error("Bean '{}' for flag '{}' does not implement IzanamiErrorCallback, found: {}",
                callbackBean, flagKey, bean.getClass().getName());
            return null;
        } catch (NoSuchBeanDefinitionException e) {
            log.error("Callback bean '{}' not found for flag '{}'. "
                + "Please ensure a bean with this name implementing IzanamiErrorCallback exists.",
                callbackBean, flagKey);
            return null;
        }
    }

    private boolean coerceToBoolean(@Nullable Object value) {
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

    private String coerceToString(@Nullable Object value, String flagKey) {
        if (value == null) {
            return "";
        }
        if (value instanceof String s) {
            return s;
        }
        if (!(value instanceof Number) && !(value instanceof Boolean)) {
            return toJson(value, flagKey);
        }
        return value.toString();
    }

    private BigDecimal coerceToNumber(@Nullable Object value, String flagKey) {
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
                flagKey, value);
            return BigDecimal.ZERO;
        }
    }

    private boolean asBooleanDefault(FlagValueType valueType, @Nullable Object defaultValue) {
        if (valueType != FlagValueType.BOOLEAN) {
            return false;
        }
        if (defaultValue == null) {
            return false;
        }
        if (defaultValue instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(defaultValue.toString());
    }

    private String asStringDefault(FlagValueType valueType, @Nullable Object defaultValue, String flagKey) {
        if (defaultValue == null) {
            return "";
        }
        if (valueType == FlagValueType.OBJECT) {
            return toJson(defaultValue, flagKey);
        }
        return defaultValue.toString();
    }

    private BigDecimal asNumberDefault(FlagValueType valueType, @Nullable Object defaultValue, String flagKey) {
        if (valueType != FlagValueType.INTEGER && valueType != FlagValueType.DOUBLE) {
            return BigDecimal.ZERO;
        }
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
            log.warn("Invalid number default for flag '{}': '{}'", flagKey, defaultValue);
            return BigDecimal.ZERO;
        }
    }

    private String toJson(Object value, String flagKey) {
        if (value instanceof String s) {
            return s;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize object default for flag '{}', falling back to empty object: {}", flagKey, e.getMessage());
            return "{}";
        }
    }
}
