package fr.maif.izanami.spring.autoconfigure;

import fr.maif.izanami.spring.actuate.IzanamiHealthIndicator;
import fr.maif.izanami.spring.service.api.IzanamiService;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for Izanami Actuator integration.
 * <p>
 * Provides a health indicator for monitoring Izanami connection status.
 * This configuration is only active when Spring Boot Actuator is on the classpath.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnBean(IzanamiService.class)
@ConditionalOnProperty(name = "izanami.enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureAfter(IzanamiAutoConfiguration.class)
public class IzanamiActuatorAutoConfiguration {

    /**
     * Create the Izanami health indicator.
     *
     * @param izanamiService the Izanami service
     * @return an {@link IzanamiHealthIndicator}
     */
    @Bean
    @ConditionalOnMissingBean(name = "izanamiHealthIndicator")
    public IzanamiHealthIndicator izanamiHealthIndicator(IzanamiService izanamiService) {
        return new IzanamiHealthIndicator(izanamiService);
    }
}
