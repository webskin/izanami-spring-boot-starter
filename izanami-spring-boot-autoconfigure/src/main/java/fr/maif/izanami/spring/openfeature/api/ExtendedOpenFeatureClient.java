package fr.maif.izanami.spring.openfeature.api;

import dev.openfeature.sdk.*;

/**
 * Extended OpenFeature client that auto-computes default values from flag configuration.
 * <p>
 * The default value is automatically retrieved from {@link FlagConfigService} based on
 * the flag configuration. This requires the flag to be configured with
 * {@link fr.maif.izanami.spring.openfeature.ErrorStrategy#DEFAULT_VALUE}.
 * <p>
 * If a flag is not configured with {@code errorStrategy=DEFAULT_VALUE} and a method that
 * requires auto-computed default value is called, an {@link ExtendedOpenFeatureClientException}
 * is thrown.
 *
 * <h2>Key-based vs Name-based Methods</h2>
 * <p>
 * This client provides two sets of methods for flag evaluation:
 * <ul>
 *   <li><b>Key-based methods</b> (e.g., {@code getBooleanValue(key)}) - Use the Izanami feature
 *       key (UUID) directly. The key must match the {@code openfeature.flags.<name>.key} configuration.</li>
 *   <li><b>Name-based methods</b> (e.g., {@code getBooleanValueByName(name)}) - Use the configured
 *       flag name. The name is looked up in {@code openfeature.flags.<name>.name}, and the corresponding
 *       key is used for evaluation.</li>
 * </ul>
 *
 * <h3>Example Usage</h3>
 * <pre>{@code
 * // Key-based (using UUID)
 * client.getBooleanValue("a4c0d04f-69ac-41aa-a6e4-febcee541d51");
 *
 * // Name-based (using configured name)
 * client.getBooleanValueByName("turbo-mode");
 * }</pre>
 *
 * @see FlagConfigService
 */
public interface ExtendedOpenFeatureClient extends Client {

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

    // --- ByName evaluation methods (auto-computed default value) ---

    // Boolean evaluation methods (auto-computed default value)

    Boolean getBooleanValueByName(String name);

    Boolean getBooleanValueByName(String name, EvaluationContext ctx);

    Boolean getBooleanValueByName(String name, EvaluationContext ctx, FlagEvaluationOptions options);

    FlagEvaluationDetails<Boolean> getBooleanDetailsByName(String name);

    FlagEvaluationDetails<Boolean> getBooleanDetailsByName(String name, EvaluationContext ctx);

    FlagEvaluationDetails<Boolean> getBooleanDetailsByName(String name, EvaluationContext ctx, FlagEvaluationOptions options);

    // String evaluation methods (auto-computed default value)

    String getStringValueByName(String name);

    String getStringValueByName(String name, EvaluationContext ctx);

    String getStringValueByName(String name, EvaluationContext ctx, FlagEvaluationOptions options);

    FlagEvaluationDetails<String> getStringDetailsByName(String name);

    FlagEvaluationDetails<String> getStringDetailsByName(String name, EvaluationContext ctx);

    FlagEvaluationDetails<String> getStringDetailsByName(String name, EvaluationContext ctx, FlagEvaluationOptions options);

    // Integer evaluation methods (auto-computed default value)

    Integer getIntegerValueByName(String name);

    Integer getIntegerValueByName(String name, EvaluationContext ctx);

    Integer getIntegerValueByName(String name, EvaluationContext ctx, FlagEvaluationOptions options);

    FlagEvaluationDetails<Integer> getIntegerDetailsByName(String name);

    FlagEvaluationDetails<Integer> getIntegerDetailsByName(String name, EvaluationContext ctx);

    FlagEvaluationDetails<Integer> getIntegerDetailsByName(String name, EvaluationContext ctx, FlagEvaluationOptions options);

    // Double evaluation methods (auto-computed default value)

    Double getDoubleValueByName(String name);

    Double getDoubleValueByName(String name, EvaluationContext ctx);

    Double getDoubleValueByName(String name, EvaluationContext ctx, FlagEvaluationOptions options);

    FlagEvaluationDetails<Double> getDoubleDetailsByName(String name);

    FlagEvaluationDetails<Double> getDoubleDetailsByName(String name, EvaluationContext ctx);

    FlagEvaluationDetails<Double> getDoubleDetailsByName(String name, EvaluationContext ctx, FlagEvaluationOptions options);

    // Object (Value) evaluation methods (auto-computed default value)

    Value getObjectValueByName(String name);

    Value getObjectValueByName(String name, EvaluationContext ctx);

    Value getObjectValueByName(String name, EvaluationContext ctx, FlagEvaluationOptions options);

    FlagEvaluationDetails<Value> getObjectDetailsByName(String name);

    FlagEvaluationDetails<Value> getObjectDetailsByName(String name, EvaluationContext ctx);

    FlagEvaluationDetails<Value> getObjectDetailsByName(String name, EvaluationContext ctx, FlagEvaluationOptions options);
}
