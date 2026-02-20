package fr.maif.izanami.spring.service.api;

import java.util.Optional;

/**
 * Provider for the user identifier in Izanami feature flag evaluations.
 * <p>
 * The user identifier is used for user-based targeting rules in Izanami.
 * Implement this interface to provide a default user when {@code withUser(...)}
 * is not explicitly called on the fluent API.
 * <p>
 * Typically implemented as a request-scoped bean to resolve the authenticated user
 * from Spring Security or other authentication mechanisms.
 *
 * <h2>Example: Static User Provider</h2>
 * <pre>{@code
 * @Component
 * public class SystemUserProvider implements UserProvider {
 *     @Override
 *     public Optional<String> user() {
 *         return Optional.of("system-user");
 *     }
 * }
 * }</pre>
 *
 * <h2>Example: Spring Security Integration</h2>
 * <pre>{@code
 * @Component
 * @RequestScope
 * public class SecurityContextUserProvider implements UserProvider {
 *     @Override
 *     public Optional<String> user() {
 *         Authentication auth = SecurityContextHolder.getContext().getAuthentication();
 *         if (auth == null || !auth.isAuthenticated()) {
 *             return Optional.empty();
 *         }
 *         return Optional.ofNullable(auth.getName());
 *     }
 * }
 * }</pre>
 *
 * @see ReactiveUserProvider
 * @see RootContextProvider
 * @see SubContextResolver
 */
@FunctionalInterface
public interface UserProvider {

    /**
     * Returns the user identifier.
     *
     * @return an {@link Optional} containing the user identifier, or empty if not available
     */
    Optional<String> user();
}
