package fr.maif.izanami.spring.autoconfigure;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.FlagValueType;
import dev.openfeature.sdk.OpenFeatureAPI;
import fr.maif.FeatureClientErrorStrategy;
import fr.maif.izanami.spring.openfeature.FlagsProperties;
import fr.maif.izanami.spring.openfeature.IzanamiFeatureProvider;
import fr.maif.izanami.spring.openfeature.api.ErrorStrategyFactory;
import fr.maif.izanami.spring.openfeature.api.FlagConfigService;
import fr.maif.izanami.spring.openfeature.api.ExtendedOpenFeatureClient;
import fr.maif.izanami.spring.openfeature.api.ExtendedOpenFeatureClientFactory;
import fr.maif.izanami.spring.service.IzanamiService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for auto-configuration using {@link ApplicationContextRunner}.
 */
class IzanamiAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            IzanamiAutoConfiguration.class,
            OpenFeatureAutoConfiguration.class
        ));

    @Test
    void createsBeansWhenEnabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(IzanamiProperties.class);
            assertThat(context).hasSingleBean(FlagsProperties.class);
            assertThat(context).hasSingleBean(FlagConfigService.class);
            assertThat(context).hasSingleBean(ErrorStrategyFactory.class);
            assertThat(context).hasSingleBean(IzanamiService.class);

            assertThat(context).hasBean("stringToFlagValueTypeConverter");
            assertThat(context).hasBean("stringToErrorStrategyConverter");

            assertThat(context).hasSingleBean(IzanamiFeatureProvider.class);
            assertThat(context).hasSingleBean(OpenFeatureAPI.class);
            assertThat(context).hasSingleBean(ExtendedOpenFeatureClientFactory.class);
            assertThat(context).hasSingleBean(ExtendedOpenFeatureClient.class);
            assertThat(context).hasSingleBean(Client.class);

            OpenFeatureAPI api = context.getBean(OpenFeatureAPI.class);
            assertThat(api.getProviderMetadata().getName()).isEqualTo("Izanami (Spring Boot Starter)");
        });
    }

    @Test
    void doesNotCreateBeansWhenDisabled() {
        contextRunner
            .withPropertyValues("izanami.enabled=false")
            .run(context -> {
                assertThat(context).doesNotHaveBean(IzanamiProperties.class);
                assertThat(context).doesNotHaveBean(FlagsProperties.class);
                assertThat(context).doesNotHaveBean(FlagConfigService.class);
                assertThat(context).doesNotHaveBean(ErrorStrategyFactory.class);
                assertThat(context).doesNotHaveBean(IzanamiService.class);
                assertThat(context).doesNotHaveBean(IzanamiFeatureProvider.class);
                assertThat(context).doesNotHaveBean(OpenFeatureAPI.class);
                assertThat(context).doesNotHaveBean(ExtendedOpenFeatureClientFactory.class);
                assertThat(context).doesNotHaveBean(ExtendedOpenFeatureClient.class);
                assertThat(context).doesNotHaveBean(Client.class);
            });
    }

    @Test
    void bindsValueTypeAndErrorStrategyUsingConverters() {
        contextRunner
            .withPropertyValues(
                "openfeature.flags[0].key=0c1774d1-9a26-4284-b8a6-0179eb7cf2f7",
                "openfeature.flags[0].name=new-dashboard",
                "openfeature.flags[0].description=Test flag",
                "openfeature.flags[0].valueType=object",
                "openfeature.flags[0].errorStrategy=DEFAULT_VALUE",
                "openfeature.flags[0].defaultValue.name=Izanami"
            )
            .run(context -> {
                FlagConfigService configService = context.getBean(FlagConfigService.class);
                assertThat(configService.getAllFlagConfigs()).hasSize(1);
                assertThat(configService.getFlagConfigByName("new-dashboard")).isPresent();
                assertThat(configService.getFlagConfigByName("new-dashboard").get().valueType()).isEqualTo(FlagValueType.OBJECT);
                assertThat(configService.getFlagConfigByName("new-dashboard").get().errorStrategy()).isInstanceOf(FeatureClientErrorStrategy.DefaultValueStrategy.class);
            });
    }

}
