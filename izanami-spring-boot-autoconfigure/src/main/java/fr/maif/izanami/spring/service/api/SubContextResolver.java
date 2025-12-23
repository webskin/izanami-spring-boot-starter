package fr.maif.izanami.spring.service.api;

import java.util.Optional;

/**
 * Resolver for the sub-context segment in Izanami feature flag evaluations.
 * <p>
 * The sub-context represents a secondary context segment (e.g., {@code "mobile"}, {@code "premium"})
 * that is combined with the root context to form the full context path. The sub-context can also
 * be a nested path (e.g., {@code "mobile/android"}, {@code "region/eu/west"}) for more granular
 * context hierarchies.
 * <p>
 * When combined with a root context of {@code "BUILD"}, a sub-context of {@code "mobile/android"}
 * results in the full context path {@code "BUILD/mobile/android"}.
 * <p>
 * Typically implemented as a request-scoped bean to resolve context based on
 * request attributes (headers, parameters, etc.).
 *
 * <h2>Example: Request-Scoped Mobile Detection</h2>
 * <pre>{@code
 * @Component
 * @RequestScope
 * public class MobileSubContextResolver implements SubContextResolver {
 *     private final HttpServletRequest request;
 *
 *     public MobileSubContextResolver(HttpServletRequest request) {
 *         this.request = request;
 *     }
 *
 *     @Override
 *     public Optional<String> subContext() {
 *         String userAgent = request.getHeader("User-Agent");
 *         if (userAgent != null && userAgent.contains("Mobi")) {
 *             return Optional.of("mobile");
 *         }
 *         return Optional.empty();
 *     }
 * }
 * }</pre>
 *
 * @see RootContextProvider
 */
@FunctionalInterface
public interface SubContextResolver {

    /**
     * Returns the sub-context segment.
     *
     * @return an {@link Optional} containing the sub-context, or empty if not available
     */
    Optional<String> subContext();
}
