package fr.maif.izanami.spring.openfeature.internal;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.ClientMetadata;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.EventDetails;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.FlagEvaluationOptions;
import dev.openfeature.sdk.Hook;
import dev.openfeature.sdk.MutableStructure;
import dev.openfeature.sdk.Structure;
import dev.openfeature.sdk.ProviderEvent;
import dev.openfeature.sdk.ProviderState;
import dev.openfeature.sdk.TrackingEventDetails;
import dev.openfeature.sdk.Value;
import fr.maif.izanami.spring.openfeature.ErrorStrategy;
import fr.maif.izanami.spring.openfeature.FlagConfig;
import fr.maif.izanami.spring.openfeature.api.FlagConfigService;
import fr.maif.izanami.spring.openfeature.api.ExtendedOpenFeatureClient;
import fr.maif.izanami.spring.openfeature.api.ExtendedOpenFeatureClientException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Implementation of {@link ExtendedOpenFeatureClient} that delegates to an underlying {@link Client}
 * while providing auto-computed default values for Boolean and Object types.
 */
public final class ExtendedOpenFeatureClientImpl implements ExtendedOpenFeatureClient {

    private final Client delegate;
    private final FlagConfigService flagConfigService;

    public ExtendedOpenFeatureClientImpl(Client delegate, FlagConfigService flagConfigService) {
        this.delegate = delegate;
        this.flagConfigService = flagConfigService;
    }

    // ========== Boolean evaluation methods (auto-computed default value) ==========

    @Override
    public Boolean getBooleanValue(String key) {
        Boolean defaultValue = getAutoDefaultValue(key, Boolean.class);
        return delegate.getBooleanValue(key, defaultValue);
    }

    @Override
    public Boolean getBooleanValue(String key, EvaluationContext ctx) {
        Boolean defaultValue = getAutoDefaultValue(key, Boolean.class);
        return delegate.getBooleanValue(key, defaultValue, ctx);
    }

    @Override
    public Boolean getBooleanValue(String key, EvaluationContext ctx, FlagEvaluationOptions options) {
        Boolean defaultValue = getAutoDefaultValue(key, Boolean.class);
        return delegate.getBooleanValue(key, defaultValue, ctx, options);
    }

    @Override
    public FlagEvaluationDetails<Boolean> getBooleanDetails(String key) {
        Boolean defaultValue = getAutoDefaultValue(key, Boolean.class);
        return delegate.getBooleanDetails(key, defaultValue);
    }

    @Override
    public FlagEvaluationDetails<Boolean> getBooleanDetails(String key, EvaluationContext ctx) {
        Boolean defaultValue = getAutoDefaultValue(key, Boolean.class);
        return delegate.getBooleanDetails(key, defaultValue, ctx);
    }

    @Override
    public FlagEvaluationDetails<Boolean> getBooleanDetails(String key, EvaluationContext ctx, FlagEvaluationOptions options) {
        Boolean defaultValue = getAutoDefaultValue(key, Boolean.class);
        return delegate.getBooleanDetails(key, defaultValue, ctx, options);
    }

    // ========== String evaluation methods (auto-computed default value) ==========

    @Override
    public String getStringValue(String key) {
        String defaultValue = getAutoDefaultValue(key, String.class);
        return delegate.getStringValue(key, defaultValue);
    }

    @Override
    public String getStringValue(String key, EvaluationContext ctx) {
        String defaultValue = getAutoDefaultValue(key, String.class);
        return delegate.getStringValue(key, defaultValue, ctx);
    }

    @Override
    public String getStringValue(String key, EvaluationContext ctx, FlagEvaluationOptions options) {
        String defaultValue = getAutoDefaultValue(key, String.class);
        return delegate.getStringValue(key, defaultValue, ctx, options);
    }

    @Override
    public FlagEvaluationDetails<String> getStringDetails(String key) {
        String defaultValue = getAutoDefaultValue(key, String.class);
        return delegate.getStringDetails(key, defaultValue);
    }

    @Override
    public FlagEvaluationDetails<String> getStringDetails(String key, EvaluationContext ctx) {
        String defaultValue = getAutoDefaultValue(key, String.class);
        return delegate.getStringDetails(key, defaultValue, ctx);
    }

    @Override
    public FlagEvaluationDetails<String> getStringDetails(String key, EvaluationContext ctx, FlagEvaluationOptions options) {
        String defaultValue = getAutoDefaultValue(key, String.class);
        return delegate.getStringDetails(key, defaultValue, ctx, options);
    }

    // ========== Integer evaluation methods (auto-computed default value) ==========

    @Override
    public Integer getIntegerValue(String key) {
        Integer defaultValue = getAutoDefaultValue(key, Integer.class);
        return delegate.getIntegerValue(key, defaultValue);
    }

    @Override
    public Integer getIntegerValue(String key, EvaluationContext ctx) {
        Integer defaultValue = getAutoDefaultValue(key, Integer.class);
        return delegate.getIntegerValue(key, defaultValue, ctx);
    }

    @Override
    public Integer getIntegerValue(String key, EvaluationContext ctx, FlagEvaluationOptions options) {
        Integer defaultValue = getAutoDefaultValue(key, Integer.class);
        return delegate.getIntegerValue(key, defaultValue, ctx, options);
    }

    @Override
    public FlagEvaluationDetails<Integer> getIntegerDetails(String key) {
        Integer defaultValue = getAutoDefaultValue(key, Integer.class);
        return delegate.getIntegerDetails(key, defaultValue);
    }

    @Override
    public FlagEvaluationDetails<Integer> getIntegerDetails(String key, EvaluationContext ctx) {
        Integer defaultValue = getAutoDefaultValue(key, Integer.class);
        return delegate.getIntegerDetails(key, defaultValue, ctx);
    }

    @Override
    public FlagEvaluationDetails<Integer> getIntegerDetails(String key, EvaluationContext ctx, FlagEvaluationOptions options) {
        Integer defaultValue = getAutoDefaultValue(key, Integer.class);
        return delegate.getIntegerDetails(key, defaultValue, ctx, options);
    }

    // ========== Double evaluation methods (auto-computed default value) ==========

    @Override
    public Double getDoubleValue(String key) {
        Double defaultValue = getAutoDefaultValue(key, Double.class);
        return delegate.getDoubleValue(key, defaultValue);
    }

    @Override
    public Double getDoubleValue(String key, EvaluationContext ctx) {
        Double defaultValue = getAutoDefaultValue(key, Double.class);
        return delegate.getDoubleValue(key, defaultValue, ctx);
    }

    @Override
    public Double getDoubleValue(String key, EvaluationContext ctx, FlagEvaluationOptions options) {
        Double defaultValue = getAutoDefaultValue(key, Double.class);
        return delegate.getDoubleValue(key, defaultValue, ctx, options);
    }

    @Override
    public FlagEvaluationDetails<Double> getDoubleDetails(String key) {
        Double defaultValue = getAutoDefaultValue(key, Double.class);
        return delegate.getDoubleDetails(key, defaultValue);
    }

    @Override
    public FlagEvaluationDetails<Double> getDoubleDetails(String key, EvaluationContext ctx) {
        Double defaultValue = getAutoDefaultValue(key, Double.class);
        return delegate.getDoubleDetails(key, defaultValue, ctx);
    }

    @Override
    public FlagEvaluationDetails<Double> getDoubleDetails(String key, EvaluationContext ctx, FlagEvaluationOptions options) {
        Double defaultValue = getAutoDefaultValue(key, Double.class);
        return delegate.getDoubleDetails(key, defaultValue, ctx, options);
    }

    // ========== Object (Value) evaluation methods (auto-computed default value) ==========

    @Override
    public Value getObjectValue(String key) {
        Value defaultValue = getAutoDefaultValueAsValue(key);
        return delegate.getObjectValue(key, defaultValue);
    }

    @Override
    public Value getObjectValue(String key, EvaluationContext ctx) {
        Value defaultValue = getAutoDefaultValueAsValue(key);
        return delegate.getObjectValue(key, defaultValue, ctx);
    }

    @Override
    public Value getObjectValue(String key, EvaluationContext ctx, FlagEvaluationOptions options) {
        Value defaultValue = getAutoDefaultValueAsValue(key);
        return delegate.getObjectValue(key, defaultValue, ctx, options);
    }

    @Override
    public FlagEvaluationDetails<Value> getObjectDetails(String key) {
        Value defaultValue = getAutoDefaultValueAsValue(key);
        return delegate.getObjectDetails(key, defaultValue);
    }

    @Override
    public FlagEvaluationDetails<Value> getObjectDetails(String key, EvaluationContext ctx) {
        Value defaultValue = getAutoDefaultValueAsValue(key);
        return delegate.getObjectDetails(key, defaultValue, ctx);
    }

    @Override
    public FlagEvaluationDetails<Value> getObjectDetails(String key, EvaluationContext ctx, FlagEvaluationOptions options) {
        Value defaultValue = getAutoDefaultValueAsValue(key);
        return delegate.getObjectDetails(key, defaultValue, ctx, options);
    }

    // ========== Delegated methods from Client interface ==========

    @Override
    public Boolean getBooleanValue(String key, Boolean defaultValue) {
        return delegate.getBooleanValue(key, defaultValue);
    }

    @Override
    public Boolean getBooleanValue(String key, Boolean defaultValue, EvaluationContext ctx) {
        return delegate.getBooleanValue(key, defaultValue, ctx);
    }

    @Override
    public Boolean getBooleanValue(String key, Boolean defaultValue, EvaluationContext ctx, FlagEvaluationOptions options) {
        return delegate.getBooleanValue(key, defaultValue, ctx, options);
    }

    @Override
    public FlagEvaluationDetails<Boolean> getBooleanDetails(String key, Boolean defaultValue) {
        return delegate.getBooleanDetails(key, defaultValue);
    }

    @Override
    public FlagEvaluationDetails<Boolean> getBooleanDetails(String key, Boolean defaultValue, EvaluationContext ctx) {
        return delegate.getBooleanDetails(key, defaultValue, ctx);
    }

    @Override
    public FlagEvaluationDetails<Boolean> getBooleanDetails(String key, Boolean defaultValue, EvaluationContext ctx, FlagEvaluationOptions options) {
        return delegate.getBooleanDetails(key, defaultValue, ctx, options);
    }

    @Override
    public String getStringValue(String key, String defaultValue) {
        return delegate.getStringValue(key, defaultValue);
    }

    @Override
    public String getStringValue(String key, String defaultValue, EvaluationContext ctx) {
        return delegate.getStringValue(key, defaultValue, ctx);
    }

    @Override
    public String getStringValue(String key, String defaultValue, EvaluationContext ctx, FlagEvaluationOptions options) {
        return delegate.getStringValue(key, defaultValue, ctx, options);
    }

    @Override
    public FlagEvaluationDetails<String> getStringDetails(String key, String defaultValue) {
        return delegate.getStringDetails(key, defaultValue);
    }

    @Override
    public FlagEvaluationDetails<String> getStringDetails(String key, String defaultValue, EvaluationContext ctx) {
        return delegate.getStringDetails(key, defaultValue, ctx);
    }

    @Override
    public FlagEvaluationDetails<String> getStringDetails(String key, String defaultValue, EvaluationContext ctx, FlagEvaluationOptions options) {
        return delegate.getStringDetails(key, defaultValue, ctx, options);
    }

    @Override
    public Integer getIntegerValue(String key, Integer defaultValue) {
        return delegate.getIntegerValue(key, defaultValue);
    }

    @Override
    public Integer getIntegerValue(String key, Integer defaultValue, EvaluationContext ctx) {
        return delegate.getIntegerValue(key, defaultValue, ctx);
    }

    @Override
    public Integer getIntegerValue(String key, Integer defaultValue, EvaluationContext ctx, FlagEvaluationOptions options) {
        return delegate.getIntegerValue(key, defaultValue, ctx, options);
    }

    @Override
    public FlagEvaluationDetails<Integer> getIntegerDetails(String key, Integer defaultValue) {
        return delegate.getIntegerDetails(key, defaultValue);
    }

    @Override
    public FlagEvaluationDetails<Integer> getIntegerDetails(String key, Integer defaultValue, EvaluationContext ctx) {
        return delegate.getIntegerDetails(key, defaultValue, ctx);
    }

    @Override
    public FlagEvaluationDetails<Integer> getIntegerDetails(String key, Integer defaultValue, EvaluationContext ctx, FlagEvaluationOptions options) {
        return delegate.getIntegerDetails(key, defaultValue, ctx, options);
    }

    @Override
    public Double getDoubleValue(String key, Double defaultValue) {
        return delegate.getDoubleValue(key, defaultValue);
    }

    @Override
    public Double getDoubleValue(String key, Double defaultValue, EvaluationContext ctx) {
        return delegate.getDoubleValue(key, defaultValue, ctx);
    }

    @Override
    public Double getDoubleValue(String key, Double defaultValue, EvaluationContext ctx, FlagEvaluationOptions options) {
        return delegate.getDoubleValue(key, defaultValue, ctx, options);
    }

    @Override
    public FlagEvaluationDetails<Double> getDoubleDetails(String key, Double defaultValue) {
        return delegate.getDoubleDetails(key, defaultValue);
    }

    @Override
    public FlagEvaluationDetails<Double> getDoubleDetails(String key, Double defaultValue, EvaluationContext ctx) {
        return delegate.getDoubleDetails(key, defaultValue, ctx);
    }

    @Override
    public FlagEvaluationDetails<Double> getDoubleDetails(String key, Double defaultValue, EvaluationContext ctx, FlagEvaluationOptions options) {
        return delegate.getDoubleDetails(key, defaultValue, ctx, options);
    }

    @Override
    public Value getObjectValue(String key, Value defaultValue) {
        return delegate.getObjectValue(key, defaultValue);
    }

    @Override
    public Value getObjectValue(String key, Value defaultValue, EvaluationContext ctx) {
        return delegate.getObjectValue(key, defaultValue, ctx);
    }

    @Override
    public Value getObjectValue(String key, Value defaultValue, EvaluationContext ctx, FlagEvaluationOptions options) {
        return delegate.getObjectValue(key, defaultValue, ctx, options);
    }

    @Override
    public FlagEvaluationDetails<Value> getObjectDetails(String key, Value defaultValue) {
        return delegate.getObjectDetails(key, defaultValue);
    }

    @Override
    public FlagEvaluationDetails<Value> getObjectDetails(String key, Value defaultValue, EvaluationContext ctx) {
        return delegate.getObjectDetails(key, defaultValue, ctx);
    }

    @Override
    public FlagEvaluationDetails<Value> getObjectDetails(String key, Value defaultValue, EvaluationContext ctx, FlagEvaluationOptions options) {
        return delegate.getObjectDetails(key, defaultValue, ctx, options);
    }

    @Override
    public ClientMetadata getMetadata() {
        return delegate.getMetadata();
    }

    @Override
    public EvaluationContext getEvaluationContext() {
        return delegate.getEvaluationContext();
    }

    @Override
    public Client setEvaluationContext(EvaluationContext evaluationContext) {
        delegate.setEvaluationContext(evaluationContext);
        return this;
    }

    @Override
    public List<Hook> getHooks() {
        return delegate.getHooks();
    }

    @Override
    public Client addHooks(Hook... hooks) {
        delegate.addHooks(hooks);
        return this;
    }

    @Override
    public ProviderState getProviderState() {
        return delegate.getProviderState();
    }

    @Override
    public ExtendedOpenFeatureClient onProviderReady(Consumer<EventDetails> handler) {
        delegate.onProviderReady(handler);
        return this;
    }

    @Override
    public ExtendedOpenFeatureClient onProviderConfigurationChanged(Consumer<EventDetails> handler) {
        delegate.onProviderConfigurationChanged(handler);
        return this;
    }

    @Override
    public ExtendedOpenFeatureClient onProviderStale(Consumer<EventDetails> handler) {
        delegate.onProviderStale(handler);
        return this;
    }

    @Override
    public ExtendedOpenFeatureClient onProviderError(Consumer<EventDetails> handler) {
        delegate.onProviderError(handler);
        return this;
    }

    @Override
    public ExtendedOpenFeatureClient on(ProviderEvent event, Consumer<EventDetails> handler) {
        delegate.on(event, handler);
        return this;
    }

    @Override
    public ExtendedOpenFeatureClient removeHandler(ProviderEvent event, Consumer<EventDetails> handler) {
        delegate.removeHandler(event, handler);
        return this;
    }

    @Override
    public void track(String trackingEventName) {
        delegate.track(trackingEventName);
    }

    @Override
    public void track(String trackingEventName, EvaluationContext evaluationContext) {
        delegate.track(trackingEventName, evaluationContext);
    }

    @Override
    public void track(String trackingEventName, TrackingEventDetails trackingEventDetails) {
        delegate.track(trackingEventName, trackingEventDetails);
    }

    @Override
    public void track(String trackingEventName, EvaluationContext evaluationContext, TrackingEventDetails trackingEventDetails) {
        delegate.track(trackingEventName, evaluationContext, trackingEventDetails);
    }

    // ========== Private helper methods ==========

    private FlagConfig getValidatedFlagConfig(String key) {
        FlagConfig config = flagConfigService.getFlagConfigByKey(key)
            .orElseThrow(() -> new ExtendedOpenFeatureClientException(
                "Flag '" + key + "' is not configured. "
                    + "Please add it to openfeature.flags configuration or use a method with explicit defaultValue."
            ));

        if (config.errorStrategy() != ErrorStrategy.DEFAULT_VALUE) {
            throw new ExtendedOpenFeatureClientException(
                "Flag '" + key + "' has errorStrategy=" + config.errorStrategy().name()
                    + " but a method requiring auto-computed defaultValue was called. "
                    + "Either configure the flag with errorStrategy=DEFAULT_VALUE or use a method with explicit defaultValue."
            );
        }

        return config;
    }

    private <T> T getAutoDefaultValue(String key, Class<T> type) {
        FlagConfig config = getValidatedFlagConfig(key);
        Object defaultValue = config.defaultValue();

        if (defaultValue == null) {
            throw new ExtendedOpenFeatureClientException(
                "Flag '" + key + "' has no defaultValue configured. "
                    + "OpenFeature requires a non-null defaultValue. "
                    + "Please configure a defaultValue or use a method with explicit defaultValue."
            );
        }

        if (!type.isInstance(defaultValue)) {
            throw new ExtendedOpenFeatureClientException(
                "Flag '" + key + "' has defaultValue of type " + defaultValue.getClass().getSimpleName()
                    + " but expected " + type.getSimpleName() + ". Check your flag configuration."
            );
        }

        return type.cast(defaultValue);
    }

    private Value getAutoDefaultValueAsValue(String key) {
        FlagConfig config = getValidatedFlagConfig(key);
        return objectToValue(config.defaultValue());
    }

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
            return new Value(n.doubleValue());
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
            Map<String, Value> attributes = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                attributes.put(entry.getKey().toString(), objectToValue(entry.getValue()));
            }
            return new Value(new MutableStructure(attributes));
        }
        return new Value(object.toString());
    }
}
