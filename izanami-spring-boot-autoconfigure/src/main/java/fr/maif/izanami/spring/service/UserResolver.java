package fr.maif.izanami.spring.service;

import fr.maif.izanami.spring.service.api.UserProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.lang.Nullable;

import java.util.Optional;

/**
 * Resolver that provides default user identification.
 * <p>
 * Resolution order:
 * <ol>
 *   <li>If explicit user is provided, use it directly</li>
 *   <li>If UserProvider provides a value, use it</li>
 *   <li>Return empty if no user is available</li>
 * </ol>
 * <p>
 * This class uses {@link ObjectProvider} to safely handle request-scoped beans
 * that may not be available outside of a request context.
 */
public final class UserResolver {
    private static final Logger log = LoggerFactory.getLogger(UserResolver.class);

    private final ObjectProvider<UserProvider> userProvider;

    public UserResolver(ObjectProvider<UserProvider> userProvider) {
        this.userProvider = userProvider;
    }

    /**
     * Resolves the user identifier.
     *
     * @param explicitUser user explicitly set by caller, or null
     * @return the resolved user, or empty if no user is available
     */
    public Optional<String> resolve(@Nullable String explicitUser) {
        // Priority 1: Explicit user always wins
        if (explicitUser != null && !explicitUser.isBlank()) {
            return Optional.of(explicitUser.trim());
        }

        // Priority 2: UserProvider
        return safeGetUser();
    }

    private Optional<String> safeGetUser() {
        try {
            UserProvider provider = userProvider.getIfAvailable();
            if (provider != null) {
                return provider.user()
                        .map(String::trim)
                        .filter(s -> !s.isEmpty());
            }
        } catch (Exception e) {
            log.warn("Could not resolve user (likely outside request scope): {}", e.getMessage());
        }
        return Optional.empty();
    }
}
