package fr.maif.izanami.spring.openfeature.api;

import dev.openfeature.sdk.FlagValueType;
import fr.maif.izanami.spring.openfeature.FlagConfig;

import java.util.concurrent.CompletableFuture;

/**
 * Callback interface for handling Izanami evaluation errors.
 * <p>
 * Implement this interface and register it as a Spring bean, then reference
 * the bean name in your flag configuration:
 * <pre>{@code
 * openfeature:
 *   flags:
 *     - name: my-feature
 *       errorStrategy: CALLBACK
 *       callbackBean: myErrorHandler
 * }</pre>
 * <p>
 * The callback is invoked when the Izanami client encounters an error
 * evaluating a feature flag.
 *
 * <p><b>Example implementation:</b></p>
 * <pre>{@code
 * @Component("myErrorHandler")
 * public class MyErrorHandler implements IzanamiErrorCallback {
 *     @Override
 *     public CompletableFuture<Object> onError(Throwable error, FlagConfig flagConfig, FlagValueType valueType) {
 *         log.warn("Izanami error for flag '{}': {}", flagConfig.name(), error.getMessage());
 *         return switch (valueType) {
 *             case BOOLEAN -> CompletableFuture.completedFuture(false);
 *             case STRING -> CompletableFuture.completedFuture("");
 *             case INTEGER, DOUBLE -> CompletableFuture.completedFuture(0);
 *             case OBJECT -> CompletableFuture.completedFuture(Map.of());
 *         };
 *     }
 * }
 * }</pre>
 *
 * <p><b>Thread safety:</b> Implementations must be thread-safe as callbacks
 * may be invoked concurrently from multiple threads.</p>
 */
@FunctionalInterface
public interface IzanamiErrorCallback {

    /**
     * Handle an Izanami evaluation error and provide a fallback value.
     * <p>
     * The returned value should be compatible with the flag's {@code valueType}:
     * <ul>
     *   <li>{@link FlagValueType#BOOLEAN}: return {@link Boolean}</li>
     *   <li>{@link FlagValueType#STRING}: return {@link String}</li>
     *   <li>{@link FlagValueType#INTEGER} / {@link FlagValueType#DOUBLE}: return {@link Number} (will be converted to {@link java.math.BigDecimal})</li>
     *   <li>{@link FlagValueType#OBJECT}: return {@link String} (JSON) or a serializable object</li>
     * </ul>
     *
     * @param error      the error that occurred during evaluation
     * @param flagConfig the configuration of the flag being evaluated
     * @param valueType  the expected value type
     * @return a {@link CompletableFuture} with the fallback value
     */
    CompletableFuture<Object> onError(Throwable error, FlagConfig flagConfig, FlagValueType valueType);
}
