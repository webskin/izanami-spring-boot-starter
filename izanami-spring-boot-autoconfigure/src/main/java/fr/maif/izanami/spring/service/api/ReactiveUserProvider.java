package fr.maif.izanami.spring.service.api;

import reactor.core.publisher.Mono;

/**
 * Reactive provider for the user identifier in Izanami feature flag evaluations.
 * <p>
 * This is the reactive counterpart of {@link UserProvider}, designed for Spring WebFlux
 * applications where {@code @RequestScope} is not available. Implement this interface
 * to resolve the user identifier reactively at subscription time.
 * <p>
 * When both {@code ReactiveUserProvider} and {@link UserProvider} are registered,
 * the reactive provider takes precedence. If the reactive provider returns {@link Mono#empty()},
 * the sync {@link UserProvider} is tried as a fallback.
 *
 * <h2>Example: Spring Security Reactive Integration</h2>
 * <pre>{@code
 * @Component
 * public class SecurityReactiveUserProvider implements ReactiveUserProvider {
 *     @Override
 *     public Mono<String> user() {
 *         return ReactiveSecurityContextHolder.getContext()
 *             .map(ctx -> ctx.getAuthentication().getName());
 *     }
 * }
 * }</pre>
 *
 * @see UserProvider
 * @see ReactiveSubContextResolver
 */
@FunctionalInterface
public interface ReactiveUserProvider {

    /**
     * Returns the user identifier reactively.
     *
     * @return a {@link Mono} emitting the user identifier, or {@link Mono#empty()} if not available
     */
    Mono<String> user();
}
