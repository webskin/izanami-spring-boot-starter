package fr.maif.izanami.spring.service.api;

/**
 * Exception thrown when the Izanami client is not available.
 * <p>
 * This typically occurs when:
 * <ul>
 *   <li>Izanami credentials are not configured</li>
 *   <li>Izanami URL is not configured</li>
 *   <li>The client failed to initialize</li>
 * </ul>
 */
public class IzanamiClientNotAvailableException extends RuntimeException {

    public IzanamiClientNotAvailableException() {
        super("Izanami client is not available");
    }

    public IzanamiClientNotAvailableException(String message) {
        super(message);
    }

    public IzanamiClientNotAvailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
