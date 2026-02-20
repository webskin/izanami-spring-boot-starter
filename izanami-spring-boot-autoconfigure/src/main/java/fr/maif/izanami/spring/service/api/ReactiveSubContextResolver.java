package fr.maif.izanami.spring.service.api;

import reactor.core.publisher.Mono;

/**
 * Reactive resolver for the sub-context segment in Izanami feature flag evaluations.
 * <p>
 * This is the reactive counterpart of {@link SubContextResolver}, designed for Spring WebFlux
 * applications where {@code @RequestScope} is not available. Implement this interface
 * to resolve the sub-context reactively at subscription time, typically by reading from
 * the Reactor Context populated by a {@code WebFilter}.
 * <p>
 * When both {@code ReactiveSubContextResolver} and {@link SubContextResolver} are registered,
 * the reactive resolver takes precedence. If the reactive resolver returns {@link Mono#empty()},
 * the sync {@link SubContextResolver} is tried as a fallback.
 *
 * <h2>Example: Reactor Context with WebFilter</h2>
 * <pre>{@code
 * // WebFilter populates Reactor Context
 * @Component
 * public class MobileContextWebFilter implements WebFilter {
 *     @Override
 *     public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
 *         String ua = exchange.getRequest().getHeaders().getFirst("User-Agent");
 *         String sub = (ua != null && ua.contains("Mobi")) ? "mobile" : "";
 *         return chain.filter(exchange)
 *             .contextWrite(ctx -> ctx.put("izanami.sub-context", sub));
 *     }
 * }
 *
 * // Resolver reads from Reactor Context
 * @Component
 * public class MobileReactiveSubContextResolver implements ReactiveSubContextResolver {
 *     @Override
 *     public Mono<String> subContext() {
 *         return Mono.deferContextual(ctx ->
 *             Mono.justOrEmpty(ctx.getOrDefault("izanami.sub-context", ""))
 *                 .filter(s -> !s.isEmpty()));
 *     }
 * }
 * }</pre>
 *
 * @see SubContextResolver
 * @see ReactiveUserProvider
 */
@FunctionalInterface
public interface ReactiveSubContextResolver {

    /**
     * Returns the sub-context segment reactively.
     *
     * @return a {@link Mono} emitting the sub-context, or {@link Mono#empty()} if not available
     */
    Mono<String> subContext();
}
