package fr.maif.izanami.spring.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.FlagValueType;
import dev.openfeature.sdk.OpenFeatureAPI;
import fr.maif.FeatureClientErrorStrategy;
import fr.maif.izanami.spring.openfeature.ErrorStrategy;
import fr.maif.izanami.spring.openfeature.FlagsProperties;
import fr.maif.izanami.spring.openfeature.IzanamiFeatureProvider;
import fr.maif.izanami.spring.openfeature.api.ErrorStrategyFactory;
import fr.maif.izanami.spring.openfeature.api.ExtendedOpenFeatureClient;
import fr.maif.izanami.spring.openfeature.api.ExtendedOpenFeatureClientFactory;
import fr.maif.izanami.spring.openfeature.api.FlagConfigService;
import fr.maif.izanami.spring.service.api.IzanamiService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
                "openfeature.flags.new-dashboard.key=0c1774d1-9a26-4284-b8a6-0179eb7cf2f7",
                "openfeature.flags.new-dashboard.description=Test flag",
                "openfeature.flags.new-dashboard.valueType=object",
                "openfeature.flags.new-dashboard.errorStrategy=DEFAULT_VALUE",
                "openfeature.flags.new-dashboard.defaultValue.name=Izanami"
            )
            .run(context -> {
                FlagConfigService configService = context.getBean(FlagConfigService.class);
                assertThat(configService.getAllFlagConfigs()).hasSize(1);
                assertThat(configService.getFlagConfigByName("new-dashboard")).isPresent();
                assertThat(configService.getFlagConfigByName("new-dashboard").get().valueType()).isEqualTo(FlagValueType.OBJECT);
                assertThat(configService.getFlagConfigByName("new-dashboard").get().clientErrorStrategy()).isInstanceOf(FeatureClientErrorStrategy.DefaultValueStrategy.class);
            });
    }

    @Nested
    class IzanamiPropertiesTests {

        @Test
        void bindsIzanamiProperties() {
            contextRunner
                .withPropertyValues(
                    "izanami.base-url=http://izanami-test:9999",
                    "izanami.api-path=/api/v2",
                    "izanami.client-id=test-client",
                    "izanami.client-secret=secret-123"
                )
                .run(context -> {
                    IzanamiProperties props = context.getBean(IzanamiProperties.class);
                    assertThat(props.getBaseUrl()).isEqualTo("http://izanami-test:9999");
                    assertThat(props.getApiPath()).isEqualTo("/api/v2");
                    assertThat(props.getClientId()).isEqualTo("test-client");
                    assertThat(props.getClientSecret()).isEqualTo("secret-123");
                });
        }

        @Test
        void bindsCacheProperties() {
            contextRunner
                .withPropertyValues(
                    "izanami.cache.enabled=true",
                    "izanami.cache.refresh-interval=PT5M",
                    "izanami.cache.sse.enabled=true",
                    "izanami.cache.sse.keep-alive-interval=PT30S"
                )
                .run(context -> {
                    IzanamiProperties props = context.getBean(IzanamiProperties.class);
                    assertThat(props.getCache().getEnabled()).isTrue();
                    assertThat(props.getCache().getRefreshInterval().toMinutes()).isEqualTo(5);
                    assertThat(props.getCache().getSse().getEnabled()).isTrue();
                    assertThat(props.getCache().getSse().getKeepAliveInterval().toSeconds()).isEqualTo(30);
                });
        }

        @Test
        void handlesPartialConfiguration() {
            contextRunner
                .withPropertyValues(
                    "izanami.base-url=http://localhost:9999"
                    // No client-id, client-secret
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(IzanamiService.class);
                    IzanamiProperties props = context.getBean(IzanamiProperties.class);
                    assertThat(props.getBaseUrl()).isEqualTo("http://localhost:9999");
                    // Service should be created but remain inactive due to missing credentials
                });
        }
    }

    @Nested
    class FlagConfigurationTests {

        @Test
        void bindsMultipleFlags() {
            contextRunner
                .withPropertyValues(
                    "openfeature.flags.feature-1.key=uuid-1",
                    "openfeature.flags.feature-1.valueType=boolean",
                    "openfeature.flags.feature-1.errorStrategy=DEFAULT_VALUE",
                    "openfeature.flags.feature-1.defaultValue=true",
                    "openfeature.flags.feature-2.key=uuid-2",
                    "openfeature.flags.feature-2.valueType=string",
                    "openfeature.flags.feature-2.errorStrategy=DEFAULT_VALUE",
                    "openfeature.flags.feature-2.defaultValue=hello"
                )
                .run(context -> {
                    FlagConfigService configService = context.getBean(FlagConfigService.class);
                    assertThat(configService.getAllFlagConfigs()).hasSize(2);
                    assertThat(configService.getFlagConfigByName("feature-1")).isPresent();
                    assertThat(configService.getFlagConfigByName("feature-2")).isPresent();
                });
        }

        @Test
        void bindsFailErrorStrategy() {
            contextRunner
                .withPropertyValues(
                    "openfeature.flags.critical-feature.key=uuid-1",
                    "openfeature.flags.critical-feature.valueType=boolean",
                    "openfeature.flags.critical-feature.errorStrategy=FAIL"
                )
                .run(context -> {
                    FlagConfigService configService = context.getBean(FlagConfigService.class);
                    var config = configService.getFlagConfigByName("critical-feature");
                    assertThat(config).isPresent();
                    assertThat(config.get().errorStrategy()).isEqualTo(ErrorStrategy.FAIL);
                    assertThat(config.get().clientErrorStrategy()).isInstanceOf(FeatureClientErrorStrategy.FailStrategy.class);
                });
        }

        @Test
        void bindsIntegerValueType() {
            contextRunner
                .withPropertyValues(
                    "openfeature.flags.max-retries.key=uuid-1",
                    "openfeature.flags.max-retries.valueType=integer",
                    "openfeature.flags.max-retries.errorStrategy=DEFAULT_VALUE",
                    "openfeature.flags.max-retries.defaultValue=3"
                )
                .run(context -> {
                    FlagConfigService configService = context.getBean(FlagConfigService.class);
                    var config = configService.getFlagConfigByName("max-retries");
                    assertThat(config).isPresent();
                    assertThat(config.get().valueType()).isEqualTo(FlagValueType.INTEGER);
                });
        }

        @Test
        void bindsDoubleValueType() {
            contextRunner
                .withPropertyValues(
                    "openfeature.flags.rate-limit.key=uuid-1",
                    "openfeature.flags.rate-limit.valueType=double",
                    "openfeature.flags.rate-limit.errorStrategy=DEFAULT_VALUE",
                    "openfeature.flags.rate-limit.defaultValue=1.5"
                )
                .run(context -> {
                    FlagConfigService configService = context.getBean(FlagConfigService.class);
                    var config = configService.getFlagConfigByName("rate-limit");
                    assertThat(config).isPresent();
                    assertThat(config.get().valueType()).isEqualTo(FlagValueType.DOUBLE);
                });
        }

        @Test
        void lookupByKeyAndName() {
            contextRunner
                .withPropertyValues(
                    "openfeature.flags.my-feature.key=my-uuid",
                    "openfeature.flags.my-feature.valueType=boolean",
                    "openfeature.flags.my-feature.errorStrategy=DEFAULT_VALUE",
                    "openfeature.flags.my-feature.defaultValue=true"
                )
                .run(context -> {
                    FlagConfigService configService = context.getBean(FlagConfigService.class);
                    assertThat(configService.getFlagConfigByKey("my-uuid")).isPresent();
                    assertThat(configService.getFlagConfigByName("my-feature")).isPresent();
                    // Key and name should return the same config
                    assertThat(configService.getFlagConfigByKey("my-uuid").get())
                        .isEqualTo(configService.getFlagConfigByName("my-feature").get());
                });
        }
    }

    @Nested
    class ObjectMapperTests {

        @Test
        void usesCustomObjectMapperFromContext() {
            contextRunner
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(ObjectMapper.class);
                    assertThat(context).hasSingleBean(IzanamiService.class);
                });
        }

        @Test
        void createsDefaultObjectMapperWhenNotProvided() {
            contextRunner
                .run(context -> {
                    // Should work without Jackson auto-configuration
                    assertThat(context).hasSingleBean(IzanamiService.class);
                });
        }
    }

    @Nested
    class OpenFeatureConditionTests {

        @Test
        void createsOpenFeatureBeansWhenIzanamiEnabled() {
            contextRunner
                .run(context -> {
                    // OpenFeature beans are created when Izanami is enabled (default)
                    assertThat(context).hasSingleBean(OpenFeatureAPI.class);
                    assertThat(context).hasSingleBean(IzanamiFeatureProvider.class);
                    assertThat(context).hasSingleBean(ExtendedOpenFeatureClient.class);
                    assertThat(context).hasSingleBean(Client.class);
                });
        }

        @Test
        void openFeatureProviderHasCorrectName() {
            contextRunner
                .run(context -> {
                    IzanamiFeatureProvider provider = context.getBean(IzanamiFeatureProvider.class);
                    assertThat(provider.getMetadata().getName()).isEqualTo("Izanami (Spring Boot Starter)");
                });
        }
    }

    @Nested
    class CustomBeanOverrideTests {

        @Test
        void allowsCustomObjectMapperOverride() {
            contextRunner
                .withUserConfiguration(CustomObjectMapperConfiguration.class)
                .run(context -> {
                    ObjectMapper mapper = context.getBean(ObjectMapper.class);
                    assertThat(mapper).isNotNull();
                    assertThat(context).hasSingleBean(IzanamiService.class);
                });
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomObjectMapperConfiguration {
        @Bean
        ObjectMapper customObjectMapper() {
            return new ObjectMapper();
        }
    }

}
