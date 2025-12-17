package fr.maif.izanami.spring.autoconfigure;

import fr.maif.izanami.spring.actuate.IzanamiHealthIndicator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class IzanamiActuatorAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            IzanamiAutoConfiguration.class,
            OpenFeatureAutoConfiguration.class,
            IzanamiActuatorAutoConfiguration.class
        ));

    @Test
    void createsHealthIndicatorWhenActuatorIsPresent() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(IzanamiHealthIndicator.class);
            assertThat(context).hasSingleBean(HealthIndicator.class);
        });
    }

    @Test
    void doesNotCreateHealthIndicatorWhenIzanamiDisabled() {
        contextRunner
            .withPropertyValues("izanami.enabled=false")
            .run(context -> {
                assertThat(context).doesNotHaveBean(IzanamiHealthIndicator.class);
            });
    }
}
