package fr.maif.izanami.spring.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.OpenFeatureAPI;
import fr.maif.IzanamiClient;
import fr.maif.izanami.spring.openfeature.IzanamiFeatureProvider;
import fr.maif.izanami.spring.openfeature.api.ErrorStrategyFactory;
import fr.maif.izanami.spring.openfeature.api.FlagConfigService;
import fr.maif.izanami.spring.openfeature.api.OpenFeatureClient;
import fr.maif.izanami.spring.openfeature.internal.OpenFeatureClientImpl;
import fr.maif.izanami.spring.service.IzanamiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for OpenFeature integration.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({OpenFeatureAPI.class, IzanamiClient.class})
@ConditionalOnProperty(name = "izanami.enabled", havingValue = "true", matchIfMissing = true)
public class OpenFeatureAutoConfiguration {
    private static final Logger log = LoggerFactory.getLogger(OpenFeatureAutoConfiguration.class);

    /**
     * Create the Izanami-backed OpenFeature provider.
     *
     * @param flagConfigService    flag configuration service
     * @param izanamiService       Izanami service
     * @param errorStrategyFactory error strategy factory
     * @param objectMapperProvider optional Spring {@link ObjectMapper}
     * @return the provider implementation
     */
    @Bean
    @ConditionalOnMissingBean(FeatureProvider.class)
    public IzanamiFeatureProvider izanamiFeatureProvider(
        FlagConfigService flagConfigService,
        IzanamiService izanamiService,
        ErrorStrategyFactory errorStrategyFactory,
        ObjectProvider<ObjectMapper> objectMapperProvider
    ) {
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        return new IzanamiFeatureProvider(flagConfigService, izanamiService, errorStrategyFactory, objectMapper);
    }

    /**
     * Configure the OpenFeature API singleton with the Izanami provider.
     * <p>
     * The provider is registered via {@link OpenFeatureAPI#setProviderAndWait(FeatureProvider)}.
     * Any initialization failure is caught and logged to avoid failing the application startup.
     *
     * @param featureProvider provider to configure
     * @return the OpenFeature API singleton
     */
    @Bean
    @ConditionalOnMissingBean(OpenFeatureAPI.class)
    public OpenFeatureAPI openFeatureAPI(FeatureProvider featureProvider) {
        OpenFeatureAPI api = OpenFeatureAPI.getInstance();
        try {
            api.setProviderAndWait(featureProvider);
        } catch (Exception e) {
            log.warn("Failed to configure OpenFeature provider; OpenFeature will keep its default provider: {}", e.getMessage());
        }
        return api;
    }

    /**
     * Expose the extended {@link OpenFeatureClient} for injection.
     * <p>
     * This client extends the standard OpenFeature {@link Client} with additional methods
     * that auto-compute default values from flag configuration for Boolean and Object types.
     *
     * @param openFeatureAPI    configured OpenFeature API
     * @param flagConfigService flag configuration service
     * @return an extended client instance
     */
    @Bean
    @ConditionalOnMissingBean(OpenFeatureClient.class)
    public OpenFeatureClient openFeatureClient(OpenFeatureAPI openFeatureAPI, FlagConfigService flagConfigService) {
        Client delegate = openFeatureAPI.getClient();
        return new OpenFeatureClientImpl(delegate, flagConfigService);
    }

    /**
     * Ensure the OpenFeature API is shut down when the Spring context is closed.
     * <p>
     * This triggers {@link FeatureProvider#shutdown()} and resets OpenFeature global state.
     *
     * @param openFeatureAPI configured OpenFeature API
     * @return a Spring {@link DisposableBean}
     */
    @Bean
    @ConditionalOnMissingBean(name = "izanamiOpenFeatureApiShutdown")
    public DisposableBean izanamiOpenFeatureApiShutdown(OpenFeatureAPI openFeatureAPI) {
        return () -> {
            try {
                openFeatureAPI.shutdown();
            } catch (Exception e) {
                log.debug("Error while shutting down OpenFeature API: {}", e.getMessage());
            }
        };
    }
}
