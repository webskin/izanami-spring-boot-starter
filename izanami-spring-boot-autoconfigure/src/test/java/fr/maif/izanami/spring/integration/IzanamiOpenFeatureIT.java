package fr.maif.izanami.spring.integration;

import dev.openfeature.sdk.FlagEvaluationDetails;
import fr.maif.izanami.spring.autoconfigure.IzanamiAutoConfiguration;
import fr.maif.izanami.spring.autoconfigure.OpenFeatureAutoConfiguration;
import fr.maif.izanami.spring.openfeature.FlagConfig;
import fr.maif.izanami.spring.openfeature.FlagMetadataKeys;
import fr.maif.izanami.spring.openfeature.api.ExtendedOpenFeatureClient;
import fr.maif.izanami.spring.openfeature.api.FlagConfigService;
import fr.maif.izanami.spring.service.IzanamiService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests running against a real Izanami server.
 * <p>
 * These tests are opt-in:
 * <ul>
 *   <li>They run only with Maven profile {@code -Pintegration-tests}.</li>
 *   <li>They are gated by {@code IZANAMI_INTEGRATION_TEST=true}.</li>
 * </ul>
 * <p>
 * Run {@code ./scripts/seed-izanami.sh} to seed the test data before running these tests.
 */
@EnabledIfEnvironmentVariable(named = "IZANAMI_INTEGRATION_TEST", matches = "true")
class IzanamiOpenFeatureIT {

    // Feature IDs matching seed-izanami.sh
    private static final String TURBO_MODE_ID = "a4c0d04f-69ac-41aa-a6e4-febcee541d51";
    private static final String SECRET_CODENAME_ID = "b5d1e15f-7abd-42bb-b7f5-0cdef6652e62";
    private static final String MAX_POWER_LEVEL_ID = "c6e2f26f-8bce-43cc-c8f6-1def07763f73";
    private static final String DISCOUNT_RATE_ID = "d7f3037f-9cdf-44dd-d9f7-2ef008874084";

    private static final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(IzanamiAutoConfiguration.class, OpenFeatureAutoConfiguration.class));

    @BeforeAll
    static void checkEnv() {
        assertThat(env("IZANAMI_CLIENT_ID")).isNotBlank();
        assertThat(env("IZANAMI_CLIENT_SECRET")).isNotBlank();
    }

    @Test
    void evaluatesBooleanFlagFromServer() {
        contextRunner
            .withPropertyValues(
                "izanami.base-url=" + envOrDefault("IZANAMI_BASE_URL", "http://localhost:9999"),
                "izanami.api-path=" + envOrDefault("IZANAMI_API_PATH", "/api"),
                "izanami.client-id=" + env("IZANAMI_CLIENT_ID"),
                "izanami.client-secret=" + env("IZANAMI_CLIENT_SECRET"),
                "izanami.cache.sse.enabled=false",
                "openfeature.flags[0].key=" + TURBO_MODE_ID,
                "openfeature.flags[0].name=turbo-mode",
                "openfeature.flags[0].description=Enable turbo mode",
                "openfeature.flags[0].valueType=boolean",
                "openfeature.flags[0].errorStrategy=DEFAULT_VALUE",
                "openfeature.flags[0].defaultValue=false"
            )
            .run(context -> {
                context.getBean(IzanamiService.class).whenLoaded().join();

                FlagConfigService configService = context.getBean(FlagConfigService.class);
                FlagConfig flagConfig = configService.getFlagConfigByName("turbo-mode").orElseThrow();

                ExtendedOpenFeatureClient client = context.getBean(ExtendedOpenFeatureClient.class);
                FlagEvaluationDetails<Boolean> details = client.getBooleanDetails(flagConfig.key());

                assertThat(flagConfig.key()).isEqualTo(TURBO_MODE_ID);
                assertThat(details.getValue()).isTrue();
                assertThat(details.getFlagMetadata().getString(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                    .isEqualTo("IZANAMI");
            });
    }

    @Test
    void evaluatesStringFlagFromServer() {
        contextRunner
            .withPropertyValues(
                "izanami.base-url=" + envOrDefault("IZANAMI_BASE_URL", "http://localhost:9999"),
                "izanami.api-path=" + envOrDefault("IZANAMI_API_PATH", "/api"),
                "izanami.client-id=" + env("IZANAMI_CLIENT_ID"),
                "izanami.client-secret=" + env("IZANAMI_CLIENT_SECRET"),
                "izanami.cache.sse.enabled=false",
                "openfeature.flags[0].key=" + SECRET_CODENAME_ID,
                "openfeature.flags[0].name=secret-codename",
                "openfeature.flags[0].description=The secret codename",
                "openfeature.flags[0].valueType=string",
                "openfeature.flags[0].errorStrategy=DEFAULT_VALUE",
                "openfeature.flags[0].defaultValue=classified"
            )
            .run(context -> {
                context.getBean(IzanamiService.class).whenLoaded().join();

                FlagConfigService configService = context.getBean(FlagConfigService.class);
                FlagConfig flagConfig = configService.getFlagConfigByName("secret-codename").orElseThrow();

                ExtendedOpenFeatureClient client = context.getBean(ExtendedOpenFeatureClient.class);
                FlagEvaluationDetails<String> details = client.getStringDetails(flagConfig.key());

                assertThat(flagConfig.key()).isEqualTo(SECRET_CODENAME_ID);
                assertThat(details.getValue()).isEqualTo("Operation Thunderbolt");
                assertThat(details.getFlagMetadata().getString(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                    .isEqualTo("IZANAMI");
            });
    }

    @Test
    void evaluatesIntegerFlagFromServer() {
        contextRunner
            .withPropertyValues(
                "izanami.base-url=" + envOrDefault("IZANAMI_BASE_URL", "http://localhost:9999"),
                "izanami.api-path=" + envOrDefault("IZANAMI_API_PATH", "/api"),
                "izanami.client-id=" + env("IZANAMI_CLIENT_ID"),
                "izanami.client-secret=" + env("IZANAMI_CLIENT_SECRET"),
                "izanami.cache.sse.enabled=false",
                "openfeature.flags[0].key=" + MAX_POWER_LEVEL_ID,
                "openfeature.flags[0].name=max-power-level",
                "openfeature.flags[0].description=Maximum power level",
                "openfeature.flags[0].valueType=integer",
                "openfeature.flags[0].errorStrategy=DEFAULT_VALUE",
                "openfeature.flags[0].defaultValue=100"
            )
            .run(context -> {
                context.getBean(IzanamiService.class).whenLoaded().join();

                FlagConfigService configService = context.getBean(FlagConfigService.class);
                FlagConfig flagConfig = configService.getFlagConfigByName("max-power-level").orElseThrow();

                ExtendedOpenFeatureClient client = context.getBean(ExtendedOpenFeatureClient.class);
                FlagEvaluationDetails<Integer> details = client.getIntegerDetails(flagConfig.key());

                assertThat(flagConfig.key()).isEqualTo(MAX_POWER_LEVEL_ID);
                assertThat(details.getValue()).isEqualTo(9001);
                assertThat(details.getFlagMetadata().getString(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                    .isEqualTo("IZANAMI");
            });
    }

    @Test
    void evaluatesDoubleFlagFromServer() {
        contextRunner
            .withPropertyValues(
                "izanami.base-url=" + envOrDefault("IZANAMI_BASE_URL", "http://localhost:9999"),
                "izanami.api-path=" + envOrDefault("IZANAMI_API_PATH", "/api"),
                "izanami.client-id=" + env("IZANAMI_CLIENT_ID"),
                "izanami.client-secret=" + env("IZANAMI_CLIENT_SECRET"),
                "izanami.cache.sse.enabled=false",
                "openfeature.flags[0].key=" + DISCOUNT_RATE_ID,
                "openfeature.flags[0].name=discount-rate",
                "openfeature.flags[0].description=Current discount rate",
                "openfeature.flags[0].valueType=double",
                "openfeature.flags[0].errorStrategy=DEFAULT_VALUE",
                "openfeature.flags[0].defaultValue=0.0"
            )
            .run(context -> {
                context.getBean(IzanamiService.class).whenLoaded().join();

                FlagConfigService configService = context.getBean(FlagConfigService.class);
                FlagConfig flagConfig = configService.getFlagConfigByName("discount-rate").orElseThrow();

                ExtendedOpenFeatureClient client = context.getBean(ExtendedOpenFeatureClient.class);
                FlagEvaluationDetails<Double> details = client.getDoubleDetails(flagConfig.key());

                assertThat(flagConfig.key()).isEqualTo(DISCOUNT_RATE_ID);
                assertThat(details.getValue()).isEqualTo(0.15);
                assertThat(details.getFlagMetadata().getString(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                    .isEqualTo("IZANAMI");
            });
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }
}
