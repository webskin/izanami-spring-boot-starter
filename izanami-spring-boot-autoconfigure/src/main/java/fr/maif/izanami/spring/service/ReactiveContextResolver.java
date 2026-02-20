package fr.maif.izanami.spring.service;

import fr.maif.izanami.spring.service.api.ReactiveSubContextResolver;
import fr.maif.izanami.spring.service.api.RootContextProvider;
import fr.maif.izanami.spring.service.api.SubContextResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.lang.Nullable;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Reactive resolver that combines root and sub-context segments.
 * <p>
 * Resolution order:
 * <ol>
 *   <li>If explicit context is provided, use it directly</li>
 *   <li>If sub-context is resolved (reactive or sync fallback), combine with root</li>
 *   <li>If only root context is present, use it alone</li>
 *   <li>Return {@link Mono#empty()} if no context is available</li>
 * </ol>
 * <p>
 * This class reproduces the same semantics as {@link CompositeContextResolver},
 * including normalization and the sub-only warning behavior.
 */
public final class ReactiveContextResolver {
    private static final Logger log = LoggerFactory.getLogger(ReactiveContextResolver.class);

    private final ObjectProvider<ReactiveSubContextResolver> reactiveSubResolver;
    private final ObjectProvider<SubContextResolver> syncSubResolver;
    private final ObjectProvider<RootContextProvider> rootProvider;

    public ReactiveContextResolver(
            ObjectProvider<ReactiveSubContextResolver> reactiveSubResolver,
            ObjectProvider<SubContextResolver> syncSubResolver,
            ObjectProvider<RootContextProvider> rootProvider) {
        this.reactiveSubResolver = reactiveSubResolver;
        this.syncSubResolver = syncSubResolver;
        this.rootProvider = rootProvider;
    }

    /**
     * Resolves the full context path reactively.
     *
     * @param explicitContext context explicitly set by caller, or null
     * @return a Mono emitting the resolved context, or empty if no context is available
     */
    public Mono<String> resolve(@Nullable String explicitContext) {
        if (explicitContext != null && !explicitContext.isBlank()) {
            return Mono.just(CompositeContextResolver.normalize(explicitContext));
        }

        return resolveSubContext()
                .map(sub -> {
                    Optional<String> root = safeGetRoot();
                    if (root.isPresent()) {
                        return root.get() + "/" + sub;
                    }
                    log.warn("SubContextResolver returned '{}' but no RootContextProvider is available. "
                            + "Using sub-context as full context path.", sub);
                    return sub;
                })
                .switchIfEmpty(Mono.defer(() -> Mono.justOrEmpty(safeGetRoot().orElse(null))))
                .map(CompositeContextResolver::normalize)
                .filter(s -> !s.isEmpty());
    }

    private Mono<String> resolveSubContext() {
        return fromReactiveSubProvider()
                .switchIfEmpty(Mono.defer(this::fromSyncSubProvider));
    }

    private Mono<String> fromReactiveSubProvider() {
        try {
            ReactiveSubContextResolver resolver = reactiveSubResolver.getIfAvailable();
            if (resolver != null) {
                return resolver.subContext()
                        .onErrorResume(e -> {
                            log.debug("Reactive sub-context resolver failed: {}", e.getMessage());
                            return Mono.empty();
                        });
            }
        } catch (Exception e) {
            log.debug("Could not access reactive sub-context resolver: {}", e.getMessage());
        }
        return Mono.empty();
    }

    private Mono<String> fromSyncSubProvider() {
        try {
            SubContextResolver resolver = syncSubResolver.getIfAvailable();
            if (resolver != null) {
                return Mono.justOrEmpty(resolver.subContext().orElse(null));
            }
        } catch (Exception e) {
            log.debug("Could not resolve sub-context from sync provider: {}", e.getMessage());
        }
        return Mono.empty();
    }

    private Optional<String> safeGetRoot() {
        try {
            RootContextProvider provider = rootProvider.getIfAvailable();
            if (provider != null) {
                return provider.root()
                        .map(CompositeContextResolver::normalize)
                        .filter(s -> !s.isEmpty());
            }
        } catch (Exception e) {
            log.debug("Could not resolve root context: {}", e.getMessage());
        }
        return Optional.empty();
    }
}
