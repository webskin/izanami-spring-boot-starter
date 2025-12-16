package fr.maif.izanami.spring.openfeature.internal;

import dev.openfeature.sdk.OpenFeatureAPI;
import fr.maif.izanami.spring.openfeature.ValueConverter;
import fr.maif.izanami.spring.openfeature.api.ExtendedOpenFeatureClient;
import fr.maif.izanami.spring.openfeature.api.ExtendedOpenFeatureClientFactory;
import fr.maif.izanami.spring.openfeature.api.FlagConfigService;

/**
 * Default implementation of {@link ExtendedOpenFeatureClientFactory}.
 * <p>
 * Wraps clients from {@link OpenFeatureAPI} with {@link ExtendedOpenFeatureClientImpl}
 * to provide auto-computed default value functionality.
 */
public final class ExtendedOpenFeatureClientFactoryImpl implements ExtendedOpenFeatureClientFactory {

    private final OpenFeatureAPI openFeatureAPI;
    private final FlagConfigService flagConfigService;
    private final ValueConverter valueConverter;

    public ExtendedOpenFeatureClientFactoryImpl(OpenFeatureAPI openFeatureAPI, FlagConfigService flagConfigService, ValueConverter valueConverter) {
        this.openFeatureAPI = openFeatureAPI;
        this.flagConfigService = flagConfigService;
        this.valueConverter = valueConverter;
    }

    @Override
    public ExtendedOpenFeatureClient getClient() {
        return new ExtendedOpenFeatureClientImpl(openFeatureAPI.getClient(), flagConfigService, valueConverter);
    }

    @Override
    public ExtendedOpenFeatureClient getClient(String domain) {
        return new ExtendedOpenFeatureClientImpl(openFeatureAPI.getClient(domain), flagConfigService, valueConverter);
    }

    @Override
    public ExtendedOpenFeatureClient getClient(String domain, String version) {
        return new ExtendedOpenFeatureClientImpl(openFeatureAPI.getClient(domain, version), flagConfigService, valueConverter);
    }
}
