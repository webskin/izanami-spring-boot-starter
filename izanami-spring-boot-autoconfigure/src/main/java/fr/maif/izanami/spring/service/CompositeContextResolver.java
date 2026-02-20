package fr.maif.izanami.spring.service;

import fr.maif.izanami.spring.service.api.RootContextProvider;
import fr.maif.izanami.spring.service.api.SubContextResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.lang.Nullable;

import java.util.Optional;

/**
 * Composite resolver that combines root and sub-context segments.
 * <p>
 * Resolution order:
 * <ol>
 *   <li>If explicit context is provided, use it directly</li>
 *   <li>If SubContextResolver provides a value, combine with RootContextProvider</li>
 *   <li>If only RootContextProvider provides a value, use it alone</li>
 *   <li>Return empty if no context is available</li>
 * </ol>
 * <p>
 * This class uses {@link ObjectProvider} to safely handle request-scoped beans
 * that may not be available outside of a request context.
 */
public final class CompositeContextResolver {
    private static final Logger log = LoggerFactory.getLogger(CompositeContextResolver.class);

    private final ObjectProvider<RootContextProvider> rootContextProvider;
    private final ObjectProvider<SubContextResolver> subContextResolver;

    public CompositeContextResolver(
            ObjectProvider<RootContextProvider> rootContextProvider,
            ObjectProvider<SubContextResolver> subContextResolver
    ) {
        this.rootContextProvider = rootContextProvider;
        this.subContextResolver = subContextResolver;
    }

    /**
     * Resolves the full context path.
     *
     * @param explicitContext context explicitly set by caller, or null
     * @return the resolved context, or empty if no context is available
     */
    public Optional<String> resolve(@Nullable String explicitContext) {
        // Priority 1: Explicit context always wins
        if (explicitContext != null && !explicitContext.isBlank()) {
            return Optional.of(normalize(explicitContext));
        }

        // Try to get sub-context (may fail if not in request scope)
        Optional<String> subContext = safeGetSubContext();

        // Try to get root context
        Optional<String> rootContext = safeGetRootContext();

        // Priority 2: SubContext with root
        if (subContext.isPresent()) {
            if (rootContext.isPresent()) {
                String combined = rootContext.get() + "/" + subContext.get();
                return Optional.of(normalize(combined));
            } else {
                // Resilience: sub-only, treat as full context with warning
                log.warn("SubContextResolver returned '{}' but no RootContextProvider is available. "
                        + "Using sub-context as full context path.", subContext.get());
                return Optional.of(normalize(subContext.get()));
            }
        }

        // Priority 3: Root context only
        if (rootContext.isPresent()) {
            return Optional.of(normalize(rootContext.get()));
        }

        // Priority 4: No context
        return Optional.empty();
    }

    private Optional<String> safeGetRootContext() {
        try {
            RootContextProvider provider = rootContextProvider.getIfAvailable();
            if (provider != null) {
                return provider.root().map(CompositeContextResolver::normalize).filter(s -> !s.isEmpty());
            }
        } catch (Exception e) {
            log.debug("Could not resolve root context: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private Optional<String> safeGetSubContext() {
        try {
            SubContextResolver resolver = subContextResolver.getIfAvailable();
            if (resolver != null) {
                return resolver.subContext().map(CompositeContextResolver::normalize).filter(s -> !s.isEmpty());
            }
        } catch (Exception e) {
            log.debug("Could not resolve sub-context (likely outside request scope): {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Normalizes context path by:
     * <ul>
     *   <li>Trimming whitespace</li>
     *   <li>Removing leading and trailing slashes</li>
     *   <li>Collapsing multiple adjacent slashes to single slash</li>
     * </ul>
     */
    static String normalize(String context) {
        if (context == null) {
            return "";
        }
        return context
            .trim()
            .replaceAll("/+", "/")  // Collapse multiple slashes
            .replaceAll("(^/)|(/$)", "");  // Remove leading/trailing slashes
    }
}
