package fr.maif.izanami.spring.integration;

import fr.maif.izanami.spring.service.api.IzanamiService;
import fr.maif.izanami.spring.service.api.UserProvider;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for automatic user injection via {@link UserProvider}.
 * <p>
 * These tests validate that the {@code UserProvider} mechanism correctly injects
 * users into Izanami feature evaluations when no explicit user is provided via
 * {@code withUser()}.
 * <p>
 * The tests use a feature with a UserList activation condition that is enabled
 * only for users "provider-user" and "other-allowed".
 */
@EnabledIfEnvironmentVariable(named = "IZANAMI_INTEGRATION_TEST", matches = "true")
class UserProviderIT extends BaseIzanamiIT {

    @Configuration
    static class AllowedUserProviderConfig {
        @Bean
        UserProvider userProvider() {
            return () -> Optional.of("provider-user");
        }
    }

    @Configuration
    static class UnknownUserProviderConfig {
        @Bean
        UserProvider userProvider() {
            return () -> Optional.of("unknown-user");
        }
    }

    @Nested
    class WhenUserProviderReturnsAllowedUser {

        @Test
        void featureIsEnabled() {
            contextRunner
                .withUserConfiguration(AllowedUserProviderConfig.class)
                .withPropertyValues(withFlagConfig(
                    "openfeature.flags.user-targeted.key=" + USER_TARGETED_ID,
                    "openfeature.flags.user-targeted.description=User-targeted feature",
                    "openfeature.flags.user-targeted.valueType=boolean",
                    "openfeature.flags.user-targeted.errorStrategy=DEFAULT_VALUE",
                    "openfeature.flags.user-targeted.defaultValue=false"
                ))
                .run(context -> {
                    waitForIzanami(context);

                    IzanamiService service = context.getBean(IzanamiService.class);
                    Boolean value = service.forFlagKey(USER_TARGETED_ID)
                        .booleanValue()
                        .join();

                    assertThat(value)
                        .as("Feature should be enabled when UserProvider returns 'provider-user' (in allowlist)")
                        .isTrue();
                });
        }

        @Test
        void featureIsEnabledByName() {
            contextRunner
                .withUserConfiguration(AllowedUserProviderConfig.class)
                .withPropertyValues(withFlagConfig(
                    "openfeature.flags.user-targeted.key=" + USER_TARGETED_ID,
                    "openfeature.flags.user-targeted.description=User-targeted feature",
                    "openfeature.flags.user-targeted.valueType=boolean",
                    "openfeature.flags.user-targeted.errorStrategy=DEFAULT_VALUE",
                    "openfeature.flags.user-targeted.defaultValue=false"
                ))
                .run(context -> {
                    waitForIzanami(context);

                    IzanamiService service = context.getBean(IzanamiService.class);
                    Boolean value = service.forFlagName("user-targeted")
                        .booleanValue()
                        .join();

                    assertThat(value)
                        .as("Feature should be enabled by name when UserProvider returns allowed user")
                        .isTrue();
                });
        }
    }

    @Nested
    class WhenUserProviderReturnsNonAllowedUser {

        @Test
        void featureIsDisabled() {
            contextRunner
                .withUserConfiguration(UnknownUserProviderConfig.class)
                .withPropertyValues(withFlagConfig(
                    "openfeature.flags.user-targeted.key=" + USER_TARGETED_ID,
                    "openfeature.flags.user-targeted.description=User-targeted feature",
                    "openfeature.flags.user-targeted.valueType=boolean",
                    "openfeature.flags.user-targeted.errorStrategy=DEFAULT_VALUE",
                    "openfeature.flags.user-targeted.defaultValue=false"
                ))
                .run(context -> {
                    waitForIzanami(context);

                    IzanamiService service = context.getBean(IzanamiService.class);
                    Boolean value = service.forFlagKey(USER_TARGETED_ID)
                        .booleanValue()
                        .join();

                    assertThat(value)
                        .as("Feature should be disabled when UserProvider returns 'unknown-user' (not in allowlist)")
                        .isFalse();
                });
        }
    }

    @Nested
    class WhenExplicitUserOverridesProvider {

        @Test
        void explicitAllowedUserEnablesFeature() {
            contextRunner
                .withUserConfiguration(UnknownUserProviderConfig.class)
                .withPropertyValues(withFlagConfig(
                    "openfeature.flags.user-targeted.key=" + USER_TARGETED_ID,
                    "openfeature.flags.user-targeted.description=User-targeted feature",
                    "openfeature.flags.user-targeted.valueType=boolean",
                    "openfeature.flags.user-targeted.errorStrategy=DEFAULT_VALUE",
                    "openfeature.flags.user-targeted.defaultValue=false"
                ))
                .run(context -> {
                    waitForIzanami(context);

                    IzanamiService service = context.getBean(IzanamiService.class);
                    Boolean value = service.forFlagKey(USER_TARGETED_ID)
                        .withUser("provider-user")
                        .booleanValue()
                        .join();

                    assertThat(value)
                        .as("Explicit withUser('provider-user') should override UserProvider and enable feature")
                        .isTrue();
                });
        }

        @Test
        void explicitNonAllowedUserDisablesFeature() {
            contextRunner
                .withUserConfiguration(AllowedUserProviderConfig.class)
                .withPropertyValues(withFlagConfig(
                    "openfeature.flags.user-targeted.key=" + USER_TARGETED_ID,
                    "openfeature.flags.user-targeted.description=User-targeted feature",
                    "openfeature.flags.user-targeted.valueType=boolean",
                    "openfeature.flags.user-targeted.errorStrategy=DEFAULT_VALUE",
                    "openfeature.flags.user-targeted.defaultValue=false"
                ))
                .run(context -> {
                    waitForIzanami(context);

                    IzanamiService service = context.getBean(IzanamiService.class);
                    Boolean value = service.forFlagKey(USER_TARGETED_ID)
                        .withUser("some-other-user")
                        .booleanValue()
                        .join();

                    assertThat(value)
                        .as("Explicit withUser('some-other-user') should override UserProvider and disable feature")
                        .isFalse();
                });
        }
    }

    @Nested
    class WhenNoUserProviderConfigured {

        @Test
        void featureIsDisabledWithoutUser() {
            contextRunner
                .withPropertyValues(withFlagConfig(
                    "openfeature.flags.user-targeted.key=" + USER_TARGETED_ID,
                    "openfeature.flags.user-targeted.description=User-targeted feature",
                    "openfeature.flags.user-targeted.valueType=boolean",
                    "openfeature.flags.user-targeted.errorStrategy=DEFAULT_VALUE",
                    "openfeature.flags.user-targeted.defaultValue=false"
                ))
                .run(context -> {
                    waitForIzanami(context);

                    IzanamiService service = context.getBean(IzanamiService.class);
                    Boolean value = service.forFlagKey(USER_TARGETED_ID)
                        .booleanValue()
                        .join();

                    assertThat(value)
                        .as("Feature should be disabled when no user is provided (no UserProvider, no explicit user)")
                        .isFalse();
                });
        }

        @Test
        void explicitUserStillWorks() {
            contextRunner
                .withPropertyValues(withFlagConfig(
                    "openfeature.flags.user-targeted.key=" + USER_TARGETED_ID,
                    "openfeature.flags.user-targeted.description=User-targeted feature",
                    "openfeature.flags.user-targeted.valueType=boolean",
                    "openfeature.flags.user-targeted.errorStrategy=DEFAULT_VALUE",
                    "openfeature.flags.user-targeted.defaultValue=false"
                ))
                .run(context -> {
                    waitForIzanami(context);

                    IzanamiService service = context.getBean(IzanamiService.class);
                    Boolean value = service.forFlagKey(USER_TARGETED_ID)
                        .withUser("other-allowed")
                        .booleanValue()
                        .join();

                    assertThat(value)
                        .as("Explicit withUser('other-allowed') should enable feature even without UserProvider")
                        .isTrue();
                });
        }
    }
}
