package fr.maif.izanami.spring.openfeature.api;

/**
 * Exception thrown when a feature client operation cannot be completed.
 * <p>
 * This exception is typically thrown when:
 * <ul>
 *   <li>A flag is not configured in the flag configuration</li>
 *   <li>A method requiring auto-computed default value is called but the flag
 *       is not configured with {@link fr.maif.izanami.spring.openfeature.ErrorStrategy#DEFAULT_VALUE}</li>
 * </ul>
 */
public class OpenFeatureClientException extends RuntimeException {

    public OpenFeatureClientException(String message) {
        super(message);
    }

    public OpenFeatureClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
