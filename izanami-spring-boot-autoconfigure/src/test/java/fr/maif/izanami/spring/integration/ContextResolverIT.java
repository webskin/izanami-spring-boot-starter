package fr.maif.izanami.spring.integration;

import fr.maif.izanami.spring.service.api.IzanamiService;
import fr.maif.izanami.spring.service.api.RootContextProvider;
import fr.maif.izanami.spring.service.api.SubContextResolver;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for automatic context injection via {@link RootContextProvider}
 * and {@link SubContextResolver}.
 * <p>
 * These tests validate that context resolution works correctly with a real Izanami server.
 * The tests use a feature with context-based overloads:
 * <ul>
 *   <li>Base: disabled</li>
 *   <li>PROD context: enabled for user "prod-user"</li>
 *   <li>PROD/mobile context: enabled for user "mobile-user"</li>
 *   <li>PROD/mobile/EU context: enabled for user "eu-user"</li>
 * </ul>
 */
@EnabledIfEnvironmentVariable(named = "IZANAMI_INTEGRATION_TEST", matches = "true")
class ContextResolverIT extends BaseIzanamiIT {

    // ========== Configuration classes ==========

    @Configuration
    static class ProdRootContextConfig {
        @Bean
        RootContextProvider rootContextProvider() {
            return () -> Optional.of("PROD");
        }
    }

    @Configuration
    static class MobileSubContextConfig {
        @Bean
        SubContextResolver subContextResolver() {
            return () -> Optional.of("mobile");
        }
    }

    @Configuration
    static class EuSubContextConfig {
        @Bean
        SubContextResolver subContextResolver() {
            return () -> Optional.of("mobile/EU");
        }
    }

    @Configuration
    static class ProdRootAndMobileSubConfig {
        @Bean
        RootContextProvider rootContextProvider() {
            return () -> Optional.of("PROD");
        }

        @Bean
        SubContextResolver subContextResolver() {
            return () -> Optional.of("mobile");
        }
    }

    @Configuration
    static class ProdRootAndEuSubConfig {
        @Bean
        RootContextProvider rootContextProvider() {
            return () -> Optional.of("PROD");
        }

        @Bean
        SubContextResolver subContextResolver() {
            return () -> Optional.of("mobile/EU");
        }
    }

    // ========== Helper methods ==========

    private String[] contextTargetedFlagConfig() {
        return withFlagConfig(
            "openfeature.flags.context-targeted.key=" + CONTEXT_TARGETED_ID,
            "openfeature.flags.context-targeted.description=Context-targeted feature",
            "openfeature.flags.context-targeted.valueType=boolean",
            "openfeature.flags.context-targeted.errorStrategy=DEFAULT_VALUE",
            "openfeature.flags.context-targeted.defaultValue=false"
        );
    }

    // ========== Test classes ==========

    @Nested
    class WhenNoContextProvided {

        @Test
        void featureIsDisabled() {
            contextRunner
                .withPropertyValues(contextTargetedFlagConfig())
                .run(context -> {
                    waitForIzanami(context);

                    IzanamiService service = context.getBean(IzanamiService.class);
                    Boolean value = service.forFlagKey(CONTEXT_TARGETED_ID)
                        .booleanValue()
                        .join();

                    assertThat(value)
                        .as("Feature should be disabled when no context is provided (base behavior)")
                        .isFalse();
                });
        }

        @Test
        void featureIsDisabledEvenWithMatchingUser() {
            contextRunner
                .withPropertyValues(contextTargetedFlagConfig())
                .run(context -> {
                    waitForIzanami(context);

                    IzanamiService service = context.getBean(IzanamiService.class);
                    Boolean value = service.forFlagKey(CONTEXT_TARGETED_ID)
                        .withUser("prod-user")
                        .booleanValue()
                        .join();

                    assertThat(value)
                        .as("Feature should be disabled without context even with matching user")
                        .isFalse();
                });
        }
    }

    @Nested
    class WhenRootContextProvided {

        @Test
        void featureEnabledWithMatchingUser() {
            contextRunner
                .withUserConfiguration(ProdRootContextConfig.class)
                .withPropertyValues(contextTargetedFlagConfig())
                .run(context -> {
                    waitForIzanami(context);

                    IzanamiService service = context.getBean(IzanamiService.class);
                    Boolean value = service.forFlagKey(CONTEXT_TARGETED_ID)
                        .withUser("prod-user")
                        .booleanValue()
                        .join();

                    assertThat(value)
                        .as("Feature should be enabled with PROD context and prod-user")
                        .isTrue();
                });
        }

        @Test
        void featureDisabledWithNonMatchingUser() {
            contextRunner
                .withUserConfiguration(ProdRootContextConfig.class)
                .withPropertyValues(contextTargetedFlagConfig())
                .run(context -> {
                    waitForIzanami(context);

                    IzanamiService service = context.getBean(IzanamiService.class);
                    Boolean value = service.forFlagKey(CONTEXT_TARGETED_ID)
                        .withUser("other-user")
                        .booleanValue()
                        .join();

                    assertThat(value)
                        .as("Feature should be disabled with PROD context but non-matching user")
                        .isFalse();
                });
        }
    }

    @Nested
    class WhenRootAndSubContextProvided {

        @Test
        void featureEnabledWithMobileContext() {
            contextRunner
                .withUserConfiguration(ProdRootAndMobileSubConfig.class)
                .withPropertyValues(contextTargetedFlagConfig())
                .run(context -> {
                    waitForIzanami(context);

                    IzanamiService service = context.getBean(IzanamiService.class);
                    Boolean value = service.forFlagKey(CONTEXT_TARGETED_ID)
                        .withUser("mobile-user")
                        .booleanValue()
                        .join();

                    assertThat(value)
                        .as("Feature should be enabled with PROD/mobile context and mobile-user")
                        .isTrue();
                });
        }

        @Test
        void featureEnabledWithNestedEuContext() {
            contextRunner
                .withUserConfiguration(ProdRootAndEuSubConfig.class)
                .withPropertyValues(contextTargetedFlagConfig())
                .run(context -> {
                    waitForIzanami(context);

                    IzanamiService service = context.getBean(IzanamiService.class);
                    Boolean value = service.forFlagKey(CONTEXT_TARGETED_ID)
                        .withUser("eu-user")
                        .booleanValue()
                        .join();

                    assertThat(value)
                        .as("Feature should be enabled with PROD/mobile/EU context and eu-user")
                        .isTrue();
                });
        }

        @Test
        void mobileUserNotMatchingInEuContext() {
            contextRunner
                .withUserConfiguration(ProdRootAndEuSubConfig.class)
                .withPropertyValues(contextTargetedFlagConfig())
                .run(context -> {
                    waitForIzanami(context);

                    IzanamiService service = context.getBean(IzanamiService.class);
                    Boolean value = service.forFlagKey(CONTEXT_TARGETED_ID)
                        .withUser("mobile-user")
                        .booleanValue()
                        .join();

                    assertThat(value)
                        .as("mobile-user should not match in PROD/mobile/EU context (eu-user only)")
                        .isFalse();
                });
        }
    }

    @Nested
    class WhenExplicitContextOverridesProviders {

        @Test
        void explicitContextOverridesRootProvider() {
            contextRunner
                .withUserConfiguration(ProdRootAndMobileSubConfig.class)
                .withPropertyValues(contextTargetedFlagConfig())
                .run(context -> {
                    waitForIzanami(context);

                    IzanamiService service = context.getBean(IzanamiService.class);
                    Boolean value = service.forFlagKey(CONTEXT_TARGETED_ID)
                        .withContext("PROD/mobile/EU")
                        .withUser("eu-user")
                        .booleanValue()
                        .join();

                    assertThat(value)
                        .as("Explicit withContext should override providers and use PROD/mobile/EU")
                        .isTrue();
                });
        }

        @Test
        void explicitContextCanDisableFeature() {
            contextRunner
                .withUserConfiguration(ProdRootAndMobileSubConfig.class)
                .withPropertyValues(contextTargetedFlagConfig())
                .run(context -> {
                    waitForIzanami(context);

                    IzanamiService service = context.getBean(IzanamiService.class);
                    Boolean value = service.forFlagKey(CONTEXT_TARGETED_ID)
                        .withContext("UNKNOWN")
                        .withUser("mobile-user")
                        .booleanValue()
                        .join();

                    assertThat(value)
                        .as("Explicit withContext('UNKNOWN') should override providers and disable feature")
                        .isFalse();
                });
        }
    }

    @Nested
    class WhenSubContextOnlyProvided {

        @Test
        void subContextWithoutRootIsIgnored() {
            contextRunner
                .withUserConfiguration(MobileSubContextConfig.class)
                .withPropertyValues(contextTargetedFlagConfig())
                .run(context -> {
                    waitForIzanami(context);

                    IzanamiService service = context.getBean(IzanamiService.class);
                    Boolean value = service.forFlagKey(CONTEXT_TARGETED_ID)
                        .withUser("mobile-user")
                        .booleanValue()
                        .join();

                    assertThat(value)
                        .as("Feature should be disabled when only SubContextResolver is configured (no root)")
                        .isFalse();
                });
        }
    }

    @Nested
    class WhenPropertyBasedRootContext {

        @Test
        void propertyBasedRootContextWorks() {
            contextRunner
                .withPropertyValues(contextTargetedFlagConfig())
                .withPropertyValues("izanami.root-context=PROD")
                .run(context -> {
                    waitForIzanami(context);

                    IzanamiService service = context.getBean(IzanamiService.class);
                    Boolean value = service.forFlagKey(CONTEXT_TARGETED_ID)
                        .withUser("prod-user")
                        .booleanValue()
                        .join();

                    assertThat(value)
                        .as("Feature should be enabled with property-based root context PROD")
                        .isTrue();
                });
        }

        @Test
        void propertyBasedRootContextCombinesWithSubResolver() {
            contextRunner
                .withUserConfiguration(MobileSubContextConfig.class)
                .withPropertyValues(contextTargetedFlagConfig())
                .withPropertyValues("izanami.root-context=PROD")
                .run(context -> {
                    waitForIzanami(context);

                    IzanamiService service = context.getBean(IzanamiService.class);
                    Boolean value = service.forFlagKey(CONTEXT_TARGETED_ID)
                        .withUser("mobile-user")
                        .booleanValue()
                        .join();

                    assertThat(value)
                        .as("Property-based root context should combine with SubContextResolver")
                        .isTrue();
                });
        }
    }
}
