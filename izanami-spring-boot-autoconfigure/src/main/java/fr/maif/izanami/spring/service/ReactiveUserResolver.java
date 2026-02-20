package fr.maif.izanami.spring.service;

import fr.maif.izanami.spring.service.api.ReactiveUserProvider;
import fr.maif.izanami.spring.service.api.UserProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.lang.Nullable;
import reactor.core.publisher.Mono;

/**
 * Reactive resolver that provides user identification.
 * <p>
 * Resolution order:
 * <ol>
 *   <li>If explicit user is provided, use it directly</li>
 *   <li>If {@link ReactiveUserProvider} provides a value, use it</li>
 *   <li>If sync {@link UserProvider} provides a value (adapted via fallback), use it</li>
 *   <li>Return {@link Mono#empty()} if no user is available</li>
 * </ol>
 * <p>
 * This class uses {@link ObjectProvider} to safely handle beans that may not be available.
 */
public final class ReactiveUserResolver {
    private static final Logger log = LoggerFactory.getLogger(ReactiveUserResolver.class);

    private final ObjectProvider<ReactiveUserProvider> reactiveProvider;
    private final ObjectProvider<UserProvider> syncProvider;

    public ReactiveUserResolver(
            ObjectProvider<ReactiveUserProvider> reactiveProvider,
            ObjectProvider<UserProvider> syncProvider) {
        this.reactiveProvider = reactiveProvider;
        this.syncProvider = syncProvider;
    }

    /**
     * Resolves the user identifier reactively.
     *
     * @param explicitUser user explicitly set by caller, or null
     * @return a Mono emitting the resolved user, or empty if no user is available
     */
    public Mono<String> resolve(@Nullable String explicitUser) {
        if (explicitUser != null && !explicitUser.isBlank()) {
            return Mono.just(explicitUser.trim());
        }

        return fromReactiveProvider()
                .switchIfEmpty(Mono.defer(this::fromSyncProvider))
                .map(String::trim)
                .filter(s -> !s.isEmpty());
    }

    private Mono<String> fromReactiveProvider() {
        try {
            ReactiveUserProvider provider = reactiveProvider.getIfAvailable();
            if (provider != null) {
                return provider.user()
                        .onErrorResume(e -> {
                            log.debug("Reactive user provider failed: {}", e.getMessage());
                            return Mono.empty();
                        });
            }
        } catch (Exception e) {
            log.debug("Could not access reactive user provider: {}", e.getMessage());
        }
        return Mono.empty();
    }

    private Mono<String> fromSyncProvider() {
        try {
            UserProvider provider = syncProvider.getIfAvailable();
            if (provider != null) {
                return Mono.justOrEmpty(provider.user().orElse(null));
            }
        } catch (Exception e) {
            log.debug("Could not resolve user from sync provider: {}", e.getMessage());
        }
        return Mono.empty();
    }
}
