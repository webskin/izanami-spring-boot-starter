package fr.maif.izanami.spring.service;

import fr.maif.izanami.spring.autoconfigure.IzanamiProperties;
import fr.maif.izanami.spring.service.api.RootContextProvider;

import java.util.Optional;

/**
 * Default {@link RootContextProvider} that reads from configuration properties.
 * <p>
 * This provider is auto-configured only when no custom {@code RootContextProvider}
 * bean is registered. It reads from the {@code izanami.root-context} property.
 */
public final class PropertyBasedRootContextProvider implements RootContextProvider {

    private final String rootContext;

    public PropertyBasedRootContextProvider(IzanamiProperties properties) {
        this.rootContext = properties.getRootContext();
    }

    @Override
    public Optional<String> root() {
        if (rootContext == null || rootContext.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(rootContext);
    }
}
