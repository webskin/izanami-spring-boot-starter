package fr.maif.izanami.spring.openfeature.internal;

import dev.openfeature.sdk.OpenFeatureAPI;
import fr.maif.izanami.spring.openfeature.api.FlagConfigService;
import fr.maif.izanami.spring.openfeature.api.OpenFeatureClient;
import fr.maif.izanami.spring.openfeature.api.OpenFeatureClientFactory;

/**
 * Default implementation of {@link OpenFeatureClientFactory}.
 * <p>
 * Wraps clients from {@link OpenFeatureAPI} with {@link OpenFeatureClientImpl}
 * to provide auto-computed default value functionality.
 */
public final class OpenFeatureClientFactoryImpl implements OpenFeatureClientFactory {

    private final OpenFeatureAPI openFeatureAPI;
    private final FlagConfigService flagConfigService;

    public OpenFeatureClientFactoryImpl(OpenFeatureAPI openFeatureAPI, FlagConfigService flagConfigService) {
        this.openFeatureAPI = openFeatureAPI;
        this.flagConfigService = flagConfigService;
    }

    @Override
    public OpenFeatureClient getClient() {
        return new OpenFeatureClientImpl(openFeatureAPI.getClient(), flagConfigService);
    }

    @Override
    public OpenFeatureClient getClient(String domain) {
        return new OpenFeatureClientImpl(openFeatureAPI.getClient(domain), flagConfigService);
    }

    @Override
    public OpenFeatureClient getClient(String domain, String version) {
        return new OpenFeatureClientImpl(openFeatureAPI.getClient(domain, version), flagConfigService);
    }
}
