package fr.maif.izanami.spring.openfeature.api;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.FlagEvaluationOptions;
import dev.openfeature.sdk.Value;

/**
 * Extended OpenFeature client that auto-computes default values from flag configuration.
 * <p>
 * The default value is automatically retrieved from {@link FlagConfigService} based on
 * the flag configuration. This requires the flag to be configured with
 * {@link fr.maif.izanami.spring.openfeature.ErrorStrategy#DEFAULT_VALUE}.
 * <p>
 * If a flag is not configured with {@code errorStrategy=DEFAULT_VALUE} and a method that
 * requires auto-computed default value is called, an {@link OpenFeatureClientException}
 * is thrown.
 */
public interface OpenFeatureClient extends Client {

    // Boolean evaluation methods (auto-computed default value)

    Boolean getBooleanValue(String key);

    Boolean getBooleanValue(String key, EvaluationContext ctx);

    Boolean getBooleanValue(String key, EvaluationContext ctx, FlagEvaluationOptions options);

    FlagEvaluationDetails<Boolean> getBooleanDetails(String key);

    FlagEvaluationDetails<Boolean> getBooleanDetails(String key, EvaluationContext ctx);

    FlagEvaluationDetails<Boolean> getBooleanDetails(String key, EvaluationContext ctx, FlagEvaluationOptions options);

    // String evaluation methods (auto-computed default value)

    String getStringValue(String key);

    String getStringValue(String key, EvaluationContext ctx);

    String getStringValue(String key, EvaluationContext ctx, FlagEvaluationOptions options);

    FlagEvaluationDetails<String> getStringDetails(String key);

    FlagEvaluationDetails<String> getStringDetails(String key, EvaluationContext ctx);

    FlagEvaluationDetails<String> getStringDetails(String key, EvaluationContext ctx, FlagEvaluationOptions options);

    // Integer evaluation methods (auto-computed default value)

    Integer getIntegerValue(String key);

    Integer getIntegerValue(String key, EvaluationContext ctx);

    Integer getIntegerValue(String key, EvaluationContext ctx, FlagEvaluationOptions options);

    FlagEvaluationDetails<Integer> getIntegerDetails(String key);

    FlagEvaluationDetails<Integer> getIntegerDetails(String key, EvaluationContext ctx);

    FlagEvaluationDetails<Integer> getIntegerDetails(String key, EvaluationContext ctx, FlagEvaluationOptions options);

    // Double evaluation methods (auto-computed default value)

    Double getDoubleValue(String key);

    Double getDoubleValue(String key, EvaluationContext ctx);

    Double getDoubleValue(String key, EvaluationContext ctx, FlagEvaluationOptions options);

    FlagEvaluationDetails<Double> getDoubleDetails(String key);

    FlagEvaluationDetails<Double> getDoubleDetails(String key, EvaluationContext ctx);

    FlagEvaluationDetails<Double> getDoubleDetails(String key, EvaluationContext ctx, FlagEvaluationOptions options);

    // Object (Value) evaluation methods (auto-computed default value)

    Value getObjectValue(String key);

    Value getObjectValue(String key, EvaluationContext ctx);

    Value getObjectValue(String key, EvaluationContext ctx, FlagEvaluationOptions options);

    FlagEvaluationDetails<Value> getObjectDetails(String key);

    FlagEvaluationDetails<Value> getObjectDetails(String key, EvaluationContext ctx);

    FlagEvaluationDetails<Value> getObjectDetails(String key, EvaluationContext ctx, FlagEvaluationOptions options);
}
