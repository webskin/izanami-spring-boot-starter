package fr.maif.izanami.spring.integration;

import fr.maif.izanami.spring.autoconfigure.IzanamiAutoConfiguration;
import fr.maif.izanami.spring.autoconfigure.OpenFeatureAutoConfiguration;
import fr.maif.izanami.spring.service.api.IzanamiService;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base class for Izanami integration tests running against a real Izanami server.
 * <p>
 * These tests are opt-in:
 * <ul>
 *   <li>They run only with Maven profile {@code -Pintegration-tests}.</li>
 *   <li>They are gated by {@code IZANAMI_INTEGRATION_TEST=true}.</li>
 * </ul>
 * <p>
 * Run {@code ./scripts/seed-izanami.sh} to seed the test data before running these tests.
 */
abstract class BaseIzanamiIT {

    // Feature IDs matching seed-izanami.sh
    protected static final String TURBO_MODE_ID = "a4c0d04f-69ac-41aa-a6e4-febcee541d51";
    protected static final String SECRET_CODENAME_ID = "b5d1e15f-7abd-42bb-b7f5-0cdef6652e62";
    protected static final String MAX_POWER_LEVEL_ID = "c6e2f26f-8bce-43cc-c8f6-1def07763f73";
    protected static final String DISCOUNT_RATE_ID = "d7f3037f-9cdf-44dd-d9f7-2ef008874084";
    protected static final String JSON_CONFIG_ID = "e8f4148f-0def-55ee-eaf8-3f0109985195";

    // Inactive (disabled) feature IDs matching seed-izanami.sh
    protected static final String INACTIVE_BOOL_ID = "f9a5259f-1ef0-66ff-fbf9-4f020aa96206";
    protected static final String INACTIVE_STRING_ID = "0ab6360f-2f01-7700-0c0a-5f131bba7317";
    protected static final String INACTIVE_NUMBER_ID = "1bc7471f-3012-8811-1d1b-6f242ccb8428";

    protected static final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(IzanamiAutoConfiguration.class, OpenFeatureAutoConfiguration.class));

    @BeforeAll
    static void checkEnv() {
        assertThat(env("IZANAMI_CLIENT_ID")).isNotBlank();
        assertThat(env("IZANAMI_CLIENT_SECRET")).isNotBlank();
    }

    protected static String[] izanamiClientProperties() {
        return new String[] {
            "izanami.base-url=" + envOrDefault("IZANAMI_BASE_URL", "http://localhost:9999"),
            "izanami.api-path=" + envOrDefault("IZANAMI_API_PATH", "/api"),
            "izanami.client-id=" + env("IZANAMI_CLIENT_ID"),
            "izanami.client-secret=" + env("IZANAMI_CLIENT_SECRET"),
            "izanami.cache.sse.enabled=false"
        };
    }

    protected static String[] unavailableServerProperties() {
        return new String[] {
            "izanami.base-url=http://unavailable:9999",
            "izanami.api-path=/api",
            "izanami.client-id=test-client",
            "izanami.client-secret=test-secret",
            "izanami.cache.sse.enabled=false"
        };
    }

    protected static String[] withFlagConfig(String... flagProperties) {
        return Stream.concat(
            Stream.of(izanamiClientProperties()),
            Stream.of(flagProperties)
        ).flatMap(Stream::of).toArray(String[]::new);
    }

    protected static String[] withUnavailableServerAndFlagConfig(String... flagProperties) {
        return Stream.concat(
            Stream.of(unavailableServerProperties()),
            Stream.of(flagProperties)
        ).flatMap(Stream::of).toArray(String[]::new);
    }

    protected static void waitForIzanami(AssertableApplicationContext context) {
        context.getBean(IzanamiService.class).whenLoaded().join();
    }

    protected static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }

    protected static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }
}
