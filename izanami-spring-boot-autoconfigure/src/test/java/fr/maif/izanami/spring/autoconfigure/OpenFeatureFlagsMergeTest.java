package fr.maif.izanami.spring.autoconfigure;

import dev.openfeature.sdk.FlagValueType;
import fr.maif.izanami.spring.openfeature.FlagsProperties;
import fr.maif.izanami.spring.openfeature.OpenFeatureFlags;
import fr.maif.izanami.spring.openfeature.api.FlagConfigService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class OpenFeatureFlagsMergeTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IzanamiAutoConfiguration.class));

    @Nested
    class YamlOnlyTests {

        @Test
        void loadsYamlFlagsWhenNoProgrammaticBeans() {
            contextRunner
                    .withPropertyValues(
                            "openfeature.flags.yaml-flag.key=yaml-key-123",
                            "openfeature.flags.yaml-flag.valueType=BOOLEAN",
                            "openfeature.flags.yaml-flag.defaultValue=true"
                    )
                    .run(context -> {
                        FlagsProperties props = context.getBean(FlagsProperties.class);
                        assertThat(props.getFlags()).hasSize(1);
                        assertThat(props.getFlags()).containsKey("yaml-flag");
                        assertThat(props.getFlags().get("yaml-flag").getKey()).isEqualTo("yaml-key-123");
                    });
        }

        @Test
        void loadsMultipleYamlFlags() {
            contextRunner
                    .withPropertyValues(
                            "openfeature.flags.flag-a.key=key-a",
                            "openfeature.flags.flag-b.key=key-b",
                            "openfeature.flags.flag-c.key=key-c"
                    )
                    .run(context -> {
                        FlagsProperties props = context.getBean(FlagsProperties.class);
                        assertThat(props.getFlags()).hasSize(3);
                        assertThat(props.getFlags()).containsKeys("flag-a", "flag-b", "flag-c");
                    });
        }
    }

    @Nested
    class ProgrammaticOnlyTests {

        @Test
        void loadsProgrammaticFlagsWhenNoYaml() {
            contextRunner
                    .withUserConfiguration(SingleFlagConfig.class)
                    .run(context -> {
                        FlagsProperties props = context.getBean(FlagsProperties.class);
                        assertThat(props.getFlags()).hasSize(1);
                        assertThat(props.getFlags()).containsKey("programmatic-flag");
                        assertThat(props.getFlags().get("programmatic-flag").getKey()).isEqualTo("prog-key-123");
                    });
        }

        @Test
        void mergesMultipleProgrammaticBeans() {
            contextRunner
                    .withUserConfiguration(MultipleProgrammaticBeansConfig.class)
                    .run(context -> {
                        FlagsProperties props = context.getBean(FlagsProperties.class);
                        assertThat(props.getFlags()).hasSize(3);
                        assertThat(props.getFlags()).containsKeys("flag-from-bean-a", "flag-from-bean-b", "flag-from-bean-c");
                    });
        }
    }

    @Nested
    class MergePrecedenceTests {

        @Test
        void programmaticOverridesYamlForSameFlagName() {
            contextRunner
                    .withPropertyValues(
                            "openfeature.flags.shared-flag.key=yaml-key",
                            "openfeature.flags.shared-flag.description=YAML description",
                            "openfeature.flags.shared-flag.defaultValue=false"
                    )
                    .withUserConfiguration(OverrideFlagConfig.class)
                    .run(context -> {
                        FlagsProperties props = context.getBean(FlagsProperties.class);
                        assertThat(props.getFlags()).hasSize(1);

                        var config = props.getFlags().get("shared-flag");
                        assertThat(config.getKey()).isEqualTo("programmatic-key");
                        assertThat(config.getDescription()).isEqualTo("Programmatic description");
                        assertThat(config.unwrapRawDefaultValue()).isEqualTo(true);
                    });
        }

        @Test
        void yamlFlagsPreservedWhenNoConflict() {
            contextRunner
                    .withPropertyValues(
                            "openfeature.flags.yaml-only.key=yaml-key",
                            "openfeature.flags.yaml-only.valueType=STRING"
                    )
                    .withUserConfiguration(NonConflictingFlagConfig.class)
                    .run(context -> {
                        FlagsProperties props = context.getBean(FlagsProperties.class);
                        assertThat(props.getFlags()).hasSize(2);
                        assertThat(props.getFlags()).containsKeys("yaml-only", "programmatic-only");

                        assertThat(props.getFlags().get("yaml-only").getKey()).isEqualTo("yaml-key");
                        assertThat(props.getFlags().get("programmatic-only").getKey()).isEqualTo("prog-key");
                    });
        }

        @Test
        void mergedFlagsPassedToFlagConfigService() {
            contextRunner
                    .withPropertyValues(
                            "openfeature.flags.yaml-flag.key=yaml-uuid",
                            "openfeature.flags.yaml-flag.valueType=BOOLEAN",
                            "openfeature.flags.yaml-flag.defaultValue=false"
                    )
                    .withUserConfiguration(SingleFlagConfig.class)
                    .run(context -> {
                        FlagConfigService service = context.getBean(FlagConfigService.class);

                        assertThat(service.getFlagConfigByName("yaml-flag")).isPresent();
                        assertThat(service.getFlagConfigByName("programmatic-flag")).isPresent();
                        assertThat(service.getAllFlagConfigs()).hasSize(2);
                    });
        }
    }

    @Nested
    class EdgeCaseTests {

        @Test
        void handlesEmptyYamlAndNoProgrammatic() {
            contextRunner
                    .run(context -> {
                        FlagsProperties props = context.getBean(FlagsProperties.class);
                        assertThat(props.getFlags()).isEmpty();
                    });
        }

        @Test
        void handlesProgrammaticWithNullFlags() {
            contextRunner
                    .withUserConfiguration(EmptyProgrammaticConfig.class)
                    .run(context -> {
                        FlagsProperties props = context.getBean(FlagsProperties.class);
                        assertThat(props.getFlags()).isEmpty();
                    });
        }
    }

    @Nested
    class BackwardCompatibilityTests {

        @Test
        void allowsUserToOverrideWithCustomPrefixBean() {
            // User-defined bean named "flagsProperties" replaces the auto-configured YAML-bound bean
            contextRunner
                    .withPropertyValues(
                            // Default prefix flags (should be ignored)
                            "openfeature.flags.default-flag.key=default-key",
                            // Custom prefix flags (should be used)
                            "custom.prefix.flags.custom-flag.key=custom-key"
                    )
                    .withUserConfiguration(CustomPrefixOverrideConfig.class)
                    .run(context -> {
                        FlagsProperties props = context.getBean(FlagsProperties.class);

                        // Should have only the custom prefix flag, not the default one
                        assertThat(props.getFlags()).hasSize(1);
                        assertThat(props.getFlags()).containsKey("custom-flag");
                        assertThat(props.getFlags().get("custom-flag").getKey()).isEqualTo("custom-key");
                    });
        }

        @Test
        void allowsCustomYamlPrefixWhileMergingProgrammaticFlags() {
            // User defines flagsProperties with custom prefix
            // AND defines @OpenFeatureFlags programmatic beans
            // Both should be merged
            contextRunner
                    .withPropertyValues(
                            "custom.yaml.flags.custom-yaml-flag.key=custom-yaml-key",
                            "openfeature.flags.ignored-flag.key=ignored-key"  // Should be ignored
                    )
                    .withUserConfiguration(CustomYamlPrefixWithProgrammaticConfig.class)
                    .run(context -> {
                        FlagsProperties props = context.getBean(FlagsProperties.class);

                        // Should have custom yaml flag + programmatic flag, NOT openfeature.flags
                        assertThat(props.getFlags()).hasSize(2);
                        assertThat(props.getFlags()).containsKeys("custom-yaml-flag", "programmatic-flag");
                        assertThat(props.getFlags()).doesNotContainKey("ignored-flag");
                    });
        }
    }

    // Test Configuration Classes

    @Configuration
    static class SingleFlagConfig {
        @Bean
        @OpenFeatureFlags
        FlagsProperties programmaticFlags() {
            return FlagsProperties.builder()
                    .flag("programmatic-flag", flag -> flag
                            .key("prog-key-123")
                            .valueType(FlagValueType.BOOLEAN)
                            .defaultValue(true))
                    .build();
        }
    }

    @Configuration
    static class MultipleProgrammaticBeansConfig {
        @Bean
        @OpenFeatureFlags
        FlagsProperties flagsA() {
            return FlagsProperties.builder()
                    .flag("flag-from-bean-a", f -> f.key("key-a"))
                    .build();
        }

        @Bean
        @OpenFeatureFlags
        FlagsProperties flagsB() {
            return FlagsProperties.builder()
                    .flag("flag-from-bean-b", f -> f.key("key-b"))
                    .flag("flag-from-bean-c", f -> f.key("key-c"))
                    .build();
        }
    }

    @Configuration
    static class OverrideFlagConfig {
        @Bean
        @OpenFeatureFlags
        FlagsProperties overrideFlags() {
            return FlagsProperties.builder()
                    .flag("shared-flag", flag -> flag
                            .key("programmatic-key")
                            .description("Programmatic description")
                            .defaultValue(true))
                    .build();
        }
    }

    @Configuration
    static class NonConflictingFlagConfig {
        @Bean
        @OpenFeatureFlags
        FlagsProperties nonConflicting() {
            return FlagsProperties.builder()
                    .flag("programmatic-only", f -> f.key("prog-key"))
                    .build();
        }
    }

    @Configuration
    static class EmptyProgrammaticConfig {
        @Bean
        @OpenFeatureFlags
        FlagsProperties emptyFlags() {
            return FlagsProperties.builder().build();
        }
    }

    @Configuration
    static class CustomPrefixOverrideConfig {
        @Bean(name = IzanamiAutoConfiguration.FLAGS_PROPERTIES_BEAN)
        @ConfigurationProperties(prefix = "custom.prefix")
        FlagsProperties flagsProperties() {
            return new FlagsProperties();
        }
    }

    @Configuration
    static class CustomYamlPrefixWithProgrammaticConfig {
        @Bean(name = IzanamiAutoConfiguration.FLAGS_PROPERTIES_BEAN)
        @ConfigurationProperties(prefix = "custom.yaml")
        FlagsProperties flagsProperties() {
            return new FlagsProperties();
        }

        @Bean
        @OpenFeatureFlags
        FlagsProperties programmaticFlags() {
            return FlagsProperties.builder()
                    .flag("programmatic-flag", f -> f.key("prog-key"))
                    .build();
        }
    }
}
