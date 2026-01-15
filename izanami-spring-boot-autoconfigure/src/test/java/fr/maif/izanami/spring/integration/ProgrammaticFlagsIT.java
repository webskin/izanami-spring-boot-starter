package fr.maif.izanami.spring.integration;

import dev.openfeature.sdk.FlagValueType;
import fr.maif.izanami.spring.openfeature.ErrorStrategy;
import fr.maif.izanami.spring.openfeature.FlagConfig;
import fr.maif.izanami.spring.openfeature.FlagsProperties;
import fr.maif.izanami.spring.openfeature.OpenFeatureFlags;
import fr.maif.izanami.spring.openfeature.api.FlagConfigService;
import fr.maif.izanami.spring.service.api.IzanamiService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for programmatic flag configuration against a real Izanami server.
 */
@EnabledIfEnvironmentVariable(named = "IZANAMI_INTEGRATION_TEST", matches = "true")
class ProgrammaticFlagsIT extends BaseIzanamiIT {

    @Test
    void evaluatesProgrammaticBooleanFlagFromServer() {
        contextRunner
                .withPropertyValues(izanamiClientProperties())
                .withUserConfiguration(TurboModeProgrammaticConfig.class)
                .run(context -> {
                    waitForIzanami(context);

                    IzanamiService service = context.getBean(IzanamiService.class);
                    Boolean value = service.forFlagKey(TURBO_MODE_ID).booleanValue().join();

                    assertThat(value).isTrue();
                });
    }

    @Test
    void evaluatesProgrammaticStringFlagFromServer() {
        contextRunner
                .withPropertyValues(izanamiClientProperties())
                .withUserConfiguration(SecretCodenameProgrammaticConfig.class)
                .run(context -> {
                    waitForIzanami(context);

                    IzanamiService service = context.getBean(IzanamiService.class);
                    String value = service.forFlagKey(SECRET_CODENAME_ID).stringValue().join();

                    assertThat(value).isEqualTo("Operation Thunderbolt");
                });
    }

    @Test
    void programmaticFlagOverridesYamlFlag() {
        contextRunner
                .withPropertyValues(withFlagConfig(
                        // YAML sets defaultValue to "YAML-default"
                        "openfeature.flags.turbo-mode.key=" + TURBO_MODE_ID,
                        "openfeature.flags.turbo-mode.valueType=boolean",
                        "openfeature.flags.turbo-mode.errorStrategy=DEFAULT_VALUE",
                        "openfeature.flags.turbo-mode.defaultValue=false",
                        "openfeature.flags.turbo-mode.description=From YAML"
                ))
                .withUserConfiguration(TurboModeOverrideConfig.class)
                .run(context -> {
                    FlagConfigService service = context.getBean(FlagConfigService.class);

                    FlagConfig config = service.getFlagConfigByName("turbo-mode").orElseThrow();

                    // Programmatic config should win
                    assertThat(config.description()).isEqualTo("Overridden by programmatic config");
                    assertThat(config.defaultValue()).isEqualTo(true);
                });
    }

    @Test
    void mergesYamlAndProgrammaticFlagsForServer() {
        contextRunner
                .withPropertyValues(withFlagConfig(
                        "openfeature.flags.yaml-string.key=" + SECRET_CODENAME_ID,
                        "openfeature.flags.yaml-string.valueType=string",
                        "openfeature.flags.yaml-string.defaultValue=fallback"
                ))
                .withUserConfiguration(TurboModeProgrammaticConfig.class)
                .run(context -> {
                    waitForIzanami(context);

                    IzanamiService service = context.getBean(IzanamiService.class);

                    // Programmatic flag
                    Boolean boolValue = service.forFlagKey(TURBO_MODE_ID).booleanValue().join();
                    assertThat(boolValue).isTrue();

                    // YAML flag
                    String stringValue = service.forFlagKey(SECRET_CODENAME_ID).stringValue().join();
                    assertThat(stringValue).isEqualTo("Operation Thunderbolt");
                });
    }

    @Test
    void multipleProgrammaticBeansAllLoaded() {
        contextRunner
                .withPropertyValues(izanamiClientProperties())
                .withUserConfiguration(MultipleProgrammaticBeansConfig.class)
                .run(context -> {
                    waitForIzanami(context);

                    FlagConfigService service = context.getBean(FlagConfigService.class);

                    assertThat(service.getFlagConfigByName("turbo-mode")).isPresent();
                    assertThat(service.getFlagConfigByName("secret-codename")).isPresent();
                    assertThat(service.getAllFlagConfigs()).hasSize(2);
                });
    }

    // Test Configuration Classes

    @Configuration
    static class TurboModeProgrammaticConfig {
        @Bean
        @OpenFeatureFlags
        FlagsProperties programmaticFlags() {
            return FlagsProperties.builder()
                    .flag("turbo-mode", flag -> flag
                            .key(TURBO_MODE_ID)
                            .description("Enable turbo mode")
                            .valueType(FlagValueType.BOOLEAN)
                            .errorStrategy(ErrorStrategy.DEFAULT_VALUE)
                            .defaultValue(false))
                    .build();
        }
    }

    @Configuration
    static class SecretCodenameProgrammaticConfig {
        @Bean
        @OpenFeatureFlags
        FlagsProperties programmaticFlags() {
            return FlagsProperties.builder()
                    .flag("secret-codename", flag -> flag
                            .key(SECRET_CODENAME_ID)
                            .description("The secret codename")
                            .valueType(FlagValueType.STRING)
                            .errorStrategy(ErrorStrategy.DEFAULT_VALUE)
                            .defaultValue("classified"))
                    .build();
        }
    }

    @Configuration
    static class TurboModeOverrideConfig {
        @Bean
        @OpenFeatureFlags
        FlagsProperties overrideFlags() {
            return FlagsProperties.builder()
                    .flag("turbo-mode", flag -> flag
                            .key(TURBO_MODE_ID)
                            .description("Overridden by programmatic config")
                            .valueType(FlagValueType.BOOLEAN)
                            .errorStrategy(ErrorStrategy.DEFAULT_VALUE)
                            .defaultValue(true))  // Override YAML's false
                    .build();
        }
    }

    @Configuration
    static class MultipleProgrammaticBeansConfig {
        @Bean
        @OpenFeatureFlags
        FlagsProperties booleanFlags() {
            return FlagsProperties.builder()
                    .flag("turbo-mode", flag -> flag
                            .key(TURBO_MODE_ID)
                            .valueType(FlagValueType.BOOLEAN)
                            .defaultValue(false))
                    .build();
        }

        @Bean
        @OpenFeatureFlags
        FlagsProperties stringFlags() {
            return FlagsProperties.builder()
                    .flag("secret-codename", flag -> flag
                            .key(SECRET_CODENAME_ID)
                            .valueType(FlagValueType.STRING)
                            .defaultValue("default"))
                    .build();
        }
    }
}
