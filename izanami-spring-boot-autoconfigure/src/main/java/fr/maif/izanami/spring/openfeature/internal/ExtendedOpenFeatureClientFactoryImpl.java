package fr.maif.izanami.spring.openfeature.internal;

import dev.openfeature.sdk.OpenFeatureAPI;
import fr.maif.izanami.spring.openfeature.api.FlagConfigService;
import fr.maif.izanami.spring.openfeature.api.ExtendedOpenFeatureClient;
import fr.maif.izanami.spring.openfeature.api.ExtendedOpenFeatureClientFactory;

/**
 * Default implementation of {@link ExtendedOpenFeatureClientFactory}.
 * <p>
 * Wraps clients from {@link OpenFeatureAPI} with {@link ExtendedOpenFeatureClientImpl}
 * to provide auto-computed default value functionality.
 */
public final class ExtendedOpenFeatureClientFactoryImpl implements ExtendedOpenFeatureClientFactory {

    private final OpenFeatureAPI openFeatureAPI;
    private final FlagConfigService flagConfigService;

    public ExtendedOpenFeatureClientFactoryImpl(OpenFeatureAPI openFeatureAPI, FlagConfigService flagConfigService) {
        this.openFeatureAPI = openFeatureAPI;
        this.flagConfigService = flagConfigService;
    }

    @Override
    public ExtendedOpenFeatureClient getClient() {
        return new ExtendedOpenFeatureClientImpl(openFeatureAPI.getClient(), flagConfigService);
    }

    @Override
    public ExtendedOpenFeatureClient getClient(String domain) {
        return new ExtendedOpenFeatureClientImpl(openFeatureAPI.getClient(domain), flagConfigService);
    }

    @Override
    public ExtendedOpenFeatureClient getClient(String domain, String version) {
        return new ExtendedOpenFeatureClientImpl(openFeatureAPI.getClient(domain, version), flagConfigService);
    }
}
