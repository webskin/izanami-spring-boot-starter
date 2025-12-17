package fr.maif.izanami.spring.openfeature.internal;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.ClientMetadata;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.EventDetails;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.FlagEvaluationOptions;
import dev.openfeature.sdk.Hook;
import dev.openfeature.sdk.ProviderEvent;
import dev.openfeature.sdk.ProviderState;
import dev.openfeature.sdk.TrackingEventDetails;
import dev.openfeature.sdk.Value;
import fr.maif.FeatureClientErrorStrategy;
import fr.maif.izanami.spring.openfeature.FlagConfig;
import fr.maif.izanami.spring.openfeature.ValueConverter;
import fr.maif.izanami.spring.openfeature.api.ExtendedOpenFeatureClient;
import fr.maif.izanami.spring.openfeature.api.ExtendedOpenFeatureClientException;
import fr.maif.izanami.spring.openfeature.api.FlagConfigService;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Implementation of {@link ExtendedOpenFeatureClient} that delegates to an underlying {@link Client}
 * while providing auto-computed default values for Boolean and Object types.
 */
public final class ExtendedOpenFeatureClientImpl implements ExtendedOpenFeatureClient {

    private final Client delegate;
    private final FlagConfigService flagConfigService;
    private final ValueConverter valueConverter;

    public ExtendedOpenFeatureClientImpl(Client delegate, FlagConfigService flagConfigService, ValueConverter valueConverter) {
        this.delegate = delegate;
        this.flagConfigService = flagConfigService;
        this.valueConverter = valueConverter;
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

    private FlagConfig getValidatedFlagConfig(
            String identifier,
            Function<String, Optional<FlagConfig>> lookup,
            String identifierType) {
        FlagConfig config = lookup.apply(identifier)
            .orElseThrow(() -> new ExtendedOpenFeatureClientException(
                "Flag with " + identifierType + " '" + identifier + "' is not configured. "
                    + "Please add it to openfeature.flags configuration or use a method with explicit defaultValue."
            ));

        if (!(config.errorStrategy() instanceof FeatureClientErrorStrategy.DefaultValueStrategy)) {
            throw new ExtendedOpenFeatureClientException(
                "Flag '" + identifier + "' has errorStrategy=" + config.errorStrategy().getClass().getSimpleName()
                    + " but a method requiring auto-computed defaultValue was called. "
                    + "Either configure the flag with errorStrategy=DEFAULT_VALUE or use a method with explicit defaultValue."
            );
        }

        return config;
    }

    private FlagConfig getValidatedFlagConfigByKey(String key) {
        return getValidatedFlagConfig(key, flagConfigService::getFlagConfigByKey, "key");
    }

    private FlagConfig getValidatedFlagConfigByName(String name) {
        return getValidatedFlagConfig(name, flagConfigService::getFlagConfigByName, "name");
    }

    private <T> T getTypedDefaultValue(FlagConfig config, String identifier, Class<T> type) {
        Object defaultValue = config.defaultValue();

        if (defaultValue == null) {
            throw new ExtendedOpenFeatureClientException(
                "Flag '" + identifier + "' has no defaultValue configured. "
                    + "OpenFeature requires a non-null defaultValue. "
                    + "Please configure a defaultValue or use a method with explicit defaultValue."
            );
        }

        if (!type.isInstance(defaultValue)) {
            throw new ExtendedOpenFeatureClientException(
                "Flag '" + identifier + "' has defaultValue of type " + defaultValue.getClass().getSimpleName()
                    + " but expected " + type.getSimpleName() + ". Check your flag configuration."
            );
        }

        return type.cast(defaultValue);
    }

    private <T> T getAutoDefaultValue(String key, Class<T> type) {
        FlagConfig config = getValidatedFlagConfigByKey(key);
        return getTypedDefaultValue(config, key, type);
    }

    private Value getAutoDefaultValueAsValue(String key) {
        FlagConfig config = getValidatedFlagConfigByKey(key);
        return valueConverter.objectToValue(config.defaultValue());
    }

    // ========== ByName Boolean evaluation methods ==========

    @Override
    public Boolean getBooleanValueByName(String name) {
        FlagConfig config = getValidatedFlagConfigByName(name);
        Boolean defaultValue = getTypedDefaultValue(config, name, Boolean.class);
        return delegate.getBooleanValue(config.key(), defaultValue);
    }

    @Override
    public Boolean getBooleanValueByName(String name, EvaluationContext ctx) {
        FlagConfig config = getValidatedFlagConfigByName(name);
        Boolean defaultValue = getTypedDefaultValue(config, name, Boolean.class);
        return delegate.getBooleanValue(config.key(), defaultValue, ctx);
    }

    @Override
    public Boolean getBooleanValueByName(String name, EvaluationContext ctx, FlagEvaluationOptions options) {
        FlagConfig config = getValidatedFlagConfigByName(name);
        Boolean defaultValue = getTypedDefaultValue(config, name, Boolean.class);
        return delegate.getBooleanValue(config.key(), defaultValue, ctx, options);
    }

    @Override
    public FlagEvaluationDetails<Boolean> getBooleanDetailsByName(String name) {
        FlagConfig config = getValidatedFlagConfigByName(name);
        Boolean defaultValue = getTypedDefaultValue(config, name, Boolean.class);
        return delegate.getBooleanDetails(config.key(), defaultValue);
    }

    @Override
    public FlagEvaluationDetails<Boolean> getBooleanDetailsByName(String name, EvaluationContext ctx) {
        FlagConfig config = getValidatedFlagConfigByName(name);
        Boolean defaultValue = getTypedDefaultValue(config, name, Boolean.class);
        return delegate.getBooleanDetails(config.key(), defaultValue, ctx);
    }

    @Override
    public FlagEvaluationDetails<Boolean> getBooleanDetailsByName(String name, EvaluationContext ctx, FlagEvaluationOptions options) {
        FlagConfig config = getValidatedFlagConfigByName(name);
        Boolean defaultValue = getTypedDefaultValue(config, name, Boolean.class);
        return delegate.getBooleanDetails(config.key(), defaultValue, ctx, options);
    }

    // ========== ByName String evaluation methods ==========

    @Override
    public String getStringValueByName(String name) {
        FlagConfig config = getValidatedFlagConfigByName(name);
        String defaultValue = getTypedDefaultValue(config, name, String.class);
        return delegate.getStringValue(config.key(), defaultValue);
    }

    @Override
    public String getStringValueByName(String name, EvaluationContext ctx) {
        FlagConfig config = getValidatedFlagConfigByName(name);
        String defaultValue = getTypedDefaultValue(config, name, String.class);
        return delegate.getStringValue(config.key(), defaultValue, ctx);
    }

    @Override
    public String getStringValueByName(String name, EvaluationContext ctx, FlagEvaluationOptions options) {
        FlagConfig config = getValidatedFlagConfigByName(name);
        String defaultValue = getTypedDefaultValue(config, name, String.class);
        return delegate.getStringValue(config.key(), defaultValue, ctx, options);
    }

    @Override
    public FlagEvaluationDetails<String> getStringDetailsByName(String name) {
        FlagConfig config = getValidatedFlagConfigByName(name);
        String defaultValue = getTypedDefaultValue(config, name, String.class);
        return delegate.getStringDetails(config.key(), defaultValue);
    }

    @Override
    public FlagEvaluationDetails<String> getStringDetailsByName(String name, EvaluationContext ctx) {
        FlagConfig config = getValidatedFlagConfigByName(name);
        String defaultValue = getTypedDefaultValue(config, name, String.class);
        return delegate.getStringDetails(config.key(), defaultValue, ctx);
    }

    @Override
    public FlagEvaluationDetails<String> getStringDetailsByName(String name, EvaluationContext ctx, FlagEvaluationOptions options) {
        FlagConfig config = getValidatedFlagConfigByName(name);
        String defaultValue = getTypedDefaultValue(config, name, String.class);
        return delegate.getStringDetails(config.key(), defaultValue, ctx, options);
    }

    // ========== ByName Integer evaluation methods ==========

    @Override
    public Integer getIntegerValueByName(String name) {
        FlagConfig config = getValidatedFlagConfigByName(name);
        Integer defaultValue = getTypedDefaultValue(config, name, Integer.class);
        return delegate.getIntegerValue(config.key(), defaultValue);
    }

    @Override
    public Integer getIntegerValueByName(String name, EvaluationContext ctx) {
        FlagConfig config = getValidatedFlagConfigByName(name);
        Integer defaultValue = getTypedDefaultValue(config, name, Integer.class);
        return delegate.getIntegerValue(config.key(), defaultValue, ctx);
    }

    @Override
    public Integer getIntegerValueByName(String name, EvaluationContext ctx, FlagEvaluationOptions options) {
        FlagConfig config = getValidatedFlagConfigByName(name);
        Integer defaultValue = getTypedDefaultValue(config, name, Integer.class);
        return delegate.getIntegerValue(config.key(), defaultValue, ctx, options);
    }

    @Override
    public FlagEvaluationDetails<Integer> getIntegerDetailsByName(String name) {
        FlagConfig config = getValidatedFlagConfigByName(name);
        Integer defaultValue = getTypedDefaultValue(config, name, Integer.class);
        return delegate.getIntegerDetails(config.key(), defaultValue);
    }

    @Override
    public FlagEvaluationDetails<Integer> getIntegerDetailsByName(String name, EvaluationContext ctx) {
        FlagConfig config = getValidatedFlagConfigByName(name);
        Integer defaultValue = getTypedDefaultValue(config, name, Integer.class);
        return delegate.getIntegerDetails(config.key(), defaultValue, ctx);
    }

    @Override
    public FlagEvaluationDetails<Integer> getIntegerDetailsByName(String name, EvaluationContext ctx, FlagEvaluationOptions options) {
        FlagConfig config = getValidatedFlagConfigByName(name);
        Integer defaultValue = getTypedDefaultValue(config, name, Integer.class);
        return delegate.getIntegerDetails(config.key(), defaultValue, ctx, options);
    }

    // ========== ByName Double evaluation methods ==========

    @Override
    public Double getDoubleValueByName(String name) {
        FlagConfig config = getValidatedFlagConfigByName(name);
        Double defaultValue = getTypedDefaultValue(config, name, Double.class);
        return delegate.getDoubleValue(config.key(), defaultValue);
    }

    @Override
    public Double getDoubleValueByName(String name, EvaluationContext ctx) {
        FlagConfig config = getValidatedFlagConfigByName(name);
        Double defaultValue = getTypedDefaultValue(config, name, Double.class);
        return delegate.getDoubleValue(config.key(), defaultValue, ctx);
    }

    @Override
    public Double getDoubleValueByName(String name, EvaluationContext ctx, FlagEvaluationOptions options) {
        FlagConfig config = getValidatedFlagConfigByName(name);
        Double defaultValue = getTypedDefaultValue(config, name, Double.class);
        return delegate.getDoubleValue(config.key(), defaultValue, ctx, options);
    }

    @Override
    public FlagEvaluationDetails<Double> getDoubleDetailsByName(String name) {
        FlagConfig config = getValidatedFlagConfigByName(name);
        Double defaultValue = getTypedDefaultValue(config, name, Double.class);
        return delegate.getDoubleDetails(config.key(), defaultValue);
    }

    @Override
    public FlagEvaluationDetails<Double> getDoubleDetailsByName(String name, EvaluationContext ctx) {
        FlagConfig config = getValidatedFlagConfigByName(name);
        Double defaultValue = getTypedDefaultValue(config, name, Double.class);
        return delegate.getDoubleDetails(config.key(), defaultValue, ctx);
    }

    @Override
    public FlagEvaluationDetails<Double> getDoubleDetailsByName(String name, EvaluationContext ctx, FlagEvaluationOptions options) {
        FlagConfig config = getValidatedFlagConfigByName(name);
        Double defaultValue = getTypedDefaultValue(config, name, Double.class);
        return delegate.getDoubleDetails(config.key(), defaultValue, ctx, options);
    }

    // ========== ByName Object (Value) evaluation methods ==========

    @Override
    public Value getObjectValueByName(String name) {
        FlagConfig config = getValidatedFlagConfigByName(name);
        Value defaultValue = valueConverter.objectToValue(config.defaultValue());
        return delegate.getObjectValue(config.key(), defaultValue);
    }

    @Override
    public Value getObjectValueByName(String name, EvaluationContext ctx) {
        FlagConfig config = getValidatedFlagConfigByName(name);
        Value defaultValue = valueConverter.objectToValue(config.defaultValue());
        return delegate.getObjectValue(config.key(), defaultValue, ctx);
    }

    @Override
    public Value getObjectValueByName(String name, EvaluationContext ctx, FlagEvaluationOptions options) {
        FlagConfig config = getValidatedFlagConfigByName(name);
        Value defaultValue = valueConverter.objectToValue(config.defaultValue());
        return delegate.getObjectValue(config.key(), defaultValue, ctx, options);
    }

    @Override
    public FlagEvaluationDetails<Value> getObjectDetailsByName(String name) {
        FlagConfig config = getValidatedFlagConfigByName(name);
        Value defaultValue = valueConverter.objectToValue(config.defaultValue());
        return delegate.getObjectDetails(config.key(), defaultValue);
    }

    @Override
    public FlagEvaluationDetails<Value> getObjectDetailsByName(String name, EvaluationContext ctx) {
        FlagConfig config = getValidatedFlagConfigByName(name);
        Value defaultValue = valueConverter.objectToValue(config.defaultValue());
        return delegate.getObjectDetails(config.key(), defaultValue, ctx);
    }

    @Override
    public FlagEvaluationDetails<Value> getObjectDetailsByName(String name, EvaluationContext ctx, FlagEvaluationOptions options) {
        FlagConfig config = getValidatedFlagConfigByName(name);
        Value defaultValue = valueConverter.objectToValue(config.defaultValue());
        return delegate.getObjectDetails(config.key(), defaultValue, ctx, options);
    }
}
