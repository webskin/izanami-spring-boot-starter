package fr.maif.izanami.spring.autoconfigure;

import fr.maif.IzanamiClient;
import fr.maif.izanami.spring.service.ReactiveContextResolver;
import fr.maif.izanami.spring.service.ReactiveIzanamiServiceImpl;
import fr.maif.izanami.spring.service.ReactiveUserResolver;
import fr.maif.izanami.spring.service.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Mono;

/**
 * Auto-configuration for reactive Izanami services.
 * <p>
 * This configuration is activated when {@code reactor-core} is on the classpath
 * (typically via {@code spring-boot-starter-webflux}) and the core {@link IzanamiService}
 * bean is available.
 * <p>
 * It provides:
 * <ul>
 *   <li>{@link ReactiveUserResolver} — resolves user reactively (reactive provider first, sync fallback)</li>
 *   <li>{@link ReactiveContextResolver} — resolves context reactively (reactive provider first, sync fallback)</li>
 *   <li>{@link ReactiveIzanamiService} — thin reactive adapter over {@link IzanamiService}</li>
 * </ul>
 */
@AutoConfiguration(after = IzanamiAutoConfiguration.class)
@ConditionalOnClass({ Mono.class, IzanamiClient.class })
@ConditionalOnBean(IzanamiService.class)
@ConditionalOnProperty(name = "izanami.enabled", havingValue = "true", matchIfMissing = true)
public class ReactiveIzanamiAutoConfiguration {

    @Bean
    ReactiveUserResolver reactiveUserResolver(
            ObjectProvider<ReactiveUserProvider> reactiveProvider,
            ObjectProvider<UserProvider> syncProvider) {
        return new ReactiveUserResolver(reactiveProvider, syncProvider);
    }

    @Bean
    ReactiveContextResolver reactiveContextResolver(
            ObjectProvider<ReactiveSubContextResolver> reactiveSubResolver,
            ObjectProvider<SubContextResolver> syncSubResolver,
            ObjectProvider<RootContextProvider> rootProvider) {
        return new ReactiveContextResolver(reactiveSubResolver, syncSubResolver, rootProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    ReactiveIzanamiService reactiveIzanamiService(
            IzanamiService izanamiService,
            ReactiveContextResolver contextResolver,
            ReactiveUserResolver userResolver) {
        return new ReactiveIzanamiServiceImpl(izanamiService, contextResolver, userResolver);
    }
}
