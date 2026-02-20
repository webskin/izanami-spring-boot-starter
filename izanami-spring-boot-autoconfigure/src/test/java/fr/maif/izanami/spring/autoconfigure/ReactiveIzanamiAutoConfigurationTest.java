package fr.maif.izanami.spring.autoconfigure;

import fr.maif.izanami.spring.service.ReactiveContextResolver;
import fr.maif.izanami.spring.service.ReactiveUserResolver;
import fr.maif.izanami.spring.service.api.*;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ReactiveIzanamiAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    IzanamiAutoConfiguration.class,
                    OpenFeatureAutoConfiguration.class,
                    ReactiveIzanamiAutoConfiguration.class
            ));

    @Test
    void createsReactiveBeansWhenMonoOnClasspath() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ReactiveUserResolver.class);
            assertThat(context).hasSingleBean(ReactiveContextResolver.class);
            assertThat(context).hasSingleBean(ReactiveIzanamiService.class);
        });
    }

    @Test
    void doesNotCreateReactiveBeansWhenDisabled() {
        contextRunner
                .withPropertyValues("izanami.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ReactiveUserResolver.class);
                    assertThat(context).doesNotHaveBean(ReactiveContextResolver.class);
                    assertThat(context).doesNotHaveBean(ReactiveIzanamiService.class);
                });
    }

    @Test
    void doesNotCreateReactiveBeansWhenMonoNotOnClasspath() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(Mono.class))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ReactiveUserResolver.class);
                    assertThat(context).doesNotHaveBean(ReactiveContextResolver.class);
                    assertThat(context).doesNotHaveBean(ReactiveIzanamiService.class);
                    // Sync beans should still be present
                    assertThat(context).hasSingleBean(IzanamiService.class);
                });
    }

    @Nested
    class ProviderWiringTests {

        @Test
        void reactiveUserProvider_wiredToResolver() {
            contextRunner
                    .withUserConfiguration(ReactiveUserProviderConfiguration.class)
                    .run(context -> {
                        assertThat(context).hasSingleBean(ReactiveUserProvider.class);
                        assertThat(context).hasSingleBean(ReactiveUserResolver.class);
                        assertThat(context).hasSingleBean(ReactiveIzanamiService.class);
                    });
        }

        @Test
        void reactiveSubContextResolver_wiredToResolver() {
            contextRunner
                    .withUserConfiguration(ReactiveSubContextResolverConfiguration.class)
                    .run(context -> {
                        assertThat(context).hasSingleBean(ReactiveSubContextResolver.class);
                        assertThat(context).hasSingleBean(ReactiveContextResolver.class);
                        assertThat(context).hasSingleBean(ReactiveIzanamiService.class);
                    });
        }

        @Test
        void syncProviders_wiredAsFallback() {
            contextRunner
                    .withUserConfiguration(SyncUserProviderConfiguration.class)
                    .run(context -> {
                        assertThat(context).hasSingleBean(UserProvider.class);
                        assertThat(context).doesNotHaveBean(ReactiveUserProvider.class);
                        // Reactive resolver is still created — will fallback to sync provider
                        assertThat(context).hasSingleBean(ReactiveUserResolver.class);
                        assertThat(context).hasSingleBean(ReactiveIzanamiService.class);
                    });
        }
    }

    @Nested
    class CustomBeanOverrideTests {

        @Test
        void allowsCustomReactiveIzanamiServiceOverride() {
            contextRunner
                    .withUserConfiguration(CustomReactiveServiceConfiguration.class)
                    .run(context -> {
                        assertThat(context).hasSingleBean(ReactiveIzanamiService.class);
                        // Custom bean should be used
                        assertThat(context.getBean(ReactiveIzanamiService.class))
                                .isInstanceOf(CustomReactiveIzanamiService.class);
                    });
        }
    }

    @Nested
    class CoexistenceTests {

        @Test
        void syncAndReactiveServicesCoexist() {
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(IzanamiService.class);
                assertThat(context).hasSingleBean(ReactiveIzanamiService.class);
            });
        }
    }

    // =====================================================================
    // Test Configurations
    // =====================================================================

    @Configuration(proxyBeanMethods = false)
    static class ReactiveUserProviderConfiguration {
        @Bean
        ReactiveUserProvider reactiveUserProvider() {
            return () -> Mono.just("reactive-user");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ReactiveSubContextResolverConfiguration {
        @Bean
        ReactiveSubContextResolver reactiveSubContextResolver() {
            return () -> Mono.just("mobile");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class SyncUserProviderConfiguration {
        @Bean
        UserProvider userProvider() {
            return () -> Optional.of("sync-user");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomReactiveServiceConfiguration {
        @Bean
        ReactiveIzanamiService reactiveIzanamiService() {
            return new CustomReactiveIzanamiService();
        }
    }

    static class CustomReactiveIzanamiService implements ReactiveIzanamiService {
        @Override public java.util.Optional<fr.maif.IzanamiClient> unwrapClient() { return java.util.Optional.empty(); }
        @Override public Mono<Void> whenLoaded() { return Mono.empty(); }
        @Override public boolean isConnected() { return false; }
        @Override public ReactiveFeatureRequestBuilder forFlagKey(String flagKey) { return null; }
        @Override public ReactiveFeatureRequestBuilder forFlagName(String flagName) { return null; }
        @Override public ReactiveBatchFeatureRequestBuilder forFlagKeys(String... flagKeys) { return null; }
        @Override public ReactiveBatchFeatureRequestBuilder forFlagNames(String... flagNames) { return null; }
    }
}
