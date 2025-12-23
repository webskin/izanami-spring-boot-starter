package fr.maif.izanami.spring.service.api;

import java.util.Optional;

/**
 * Provider for the root context segment in Izanami feature flag evaluations.
 * <p>
 * The root context represents the primary context segment (e.g., "BUILD", "PRODUCTION")
 * and is combined with optional sub-contexts to form the full context path.
 * <p>
 * If no custom {@code RootContextProvider} bean is registered, a default provider
 * is auto-configured that reads from the {@code izanami.root-context} property.
 *
 * <h2>Example Implementation</h2>
 * <pre>{@code
 * @Component
 * public class BuildRootContextProvider implements RootContextProvider {
 *     @Override
 *     public Optional<String> root() {
 *         return Optional.of("BUILD");
 *     }
 * }
 * }</pre>
 *
 * @see SubContextResolver
 */
@FunctionalInterface
public interface RootContextProvider {

    /**
     * Returns the root context segment.
     *
     * @return an {@link Optional} containing the root context, or empty if not available
     */
    Optional<String> root();
}
