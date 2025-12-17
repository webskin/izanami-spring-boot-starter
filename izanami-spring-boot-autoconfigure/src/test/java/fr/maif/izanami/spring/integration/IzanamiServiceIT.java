package fr.maif.izanami.spring.integration;

import fr.maif.izanami.spring.autoconfigure.IzanamiAutoConfiguration;
import fr.maif.izanami.spring.autoconfigure.OpenFeatureAutoConfiguration;
import fr.maif.izanami.spring.service.IzanamiService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link IzanamiService} running against a real Izanami server.
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
class IzanamiServiceIT {

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

    // ========== By Key integration tests ==========

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
                IzanamiService service = context.getBean(IzanamiService.class);
                service.whenLoaded().join();

                Boolean value = service.forFlagKey(TURBO_MODE_ID).booleanValue().join();

                assertThat(value).isTrue();
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
                IzanamiService service = context.getBean(IzanamiService.class);
                service.whenLoaded().join();

                String value = service.forFlagKey(SECRET_CODENAME_ID).stringValue().join();

                assertThat(value).isEqualTo("Operation Thunderbolt");
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
                IzanamiService service = context.getBean(IzanamiService.class);
                service.whenLoaded().join();

                BigDecimal value = service.forFlagKey(MAX_POWER_LEVEL_ID).numberValue().join();

                assertThat(value).isEqualByComparingTo(new BigDecimal("9001"));
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
                IzanamiService service = context.getBean(IzanamiService.class);
                service.whenLoaded().join();

                BigDecimal value = service.forFlagKey(DISCOUNT_RATE_ID).numberValue().join();

                assertThat(value).isEqualByComparingTo(new BigDecimal("0.15"));
            });
    }

    // ========== ByName integration tests ==========

    @Test
    void evaluatesBooleanFlagByName() {
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
                IzanamiService service = context.getBean(IzanamiService.class);
                service.whenLoaded().join();

                Boolean value = service.forFlagName("turbo-mode").booleanValue().join();

                assertThat(value).isTrue();
            });
    }

    @Test
    void evaluatesStringFlagByName() {
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
                IzanamiService service = context.getBean(IzanamiService.class);
                service.whenLoaded().join();

                String value = service.forFlagName("secret-codename").stringValue().join();

                assertThat(value).isEqualTo("Operation Thunderbolt");
            });
    }

    @Test
    void evaluatesIntegerFlagByName() {
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
                IzanamiService service = context.getBean(IzanamiService.class);
                service.whenLoaded().join();

                BigDecimal value = service.forFlagName("max-power-level").numberValue().join();

                assertThat(value).isEqualByComparingTo(new BigDecimal("9001"));
            });
    }

    @Test
    void evaluatesDoubleFlagByName() {
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
                IzanamiService service = context.getBean(IzanamiService.class);
                service.whenLoaded().join();

                BigDecimal value = service.forFlagName("discount-rate").numberValue().join();

                assertThat(value).isEqualByComparingTo(new BigDecimal("0.15"));
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
