package fr.maif.izanami.spring.integration;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.Value;
import fr.maif.izanami.spring.autoconfigure.IzanamiAutoConfiguration;
import fr.maif.izanami.spring.autoconfigure.OpenFeatureAutoConfiguration;
import fr.maif.izanami.spring.openfeature.FlagMetadataKeys;
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
 */
@EnabledIfEnvironmentVariable(named = "IZANAMI_INTEGRATION_TEST", matches = "true")
class IzanamiOpenFeatureIT {

    private static final String PERFORMANCE_MODE_ID = "a4c0d04f-69ac-41aa-a6e4-febcee541d51";
    private static final String JSON_CONTENT_ID = "00812ba5-aebc-49e8-959a-4b96a5cebbff";

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
                "openfeature.flags[0].key=" + PERFORMANCE_MODE_ID,
                "openfeature.flags[0].name=performance-mode",
                "openfeature.flags[0].description=Performance mode",
                "openfeature.flags[0].valueType=boolean",
                "openfeature.flags[0].errorStrategy=DEFAULT_VALUE",
                "openfeature.flags[0].defaultValue=false"
            )
            .run(context -> {
                Client client = context.getBean(Client.class);
                FlagEvaluationDetails<Boolean> details = client.getBooleanDetails("performance-mode", false);

                assertThat(details.getValue()).isTrue();
                assertThat(details.getFlagMetadata().getString(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                    .isEqualTo("IZANAMI");
            });
    }

    @Test
    void evaluatesBooleanFlagFromServer_withNoDefaultParameter() {
        contextRunner
            .withPropertyValues(
                "izanami.base-url=" + envOrDefault("IZANAMI_BASE_URL", "http://localhost:9999"),
                "izanami.api-path=" + envOrDefault("IZANAMI_API_PATH", "/api"),
                "izanami.client-id=" + env("IZANAMI_CLIENT_ID"),
                "izanami.client-secret=" + env("IZANAMI_CLIENT_SECRET"),
                "izanami.cache.sse.enabled=false",
                "openfeature.flags[0].key=" + PERFORMANCE_MODE_ID,
                "openfeature.flags[0].name=performance-mode",
                "openfeature.flags[0].description=Performance mode",
                "openfeature.flags[0].valueType=boolean",
                "openfeature.flags[0].errorStrategy=DEFAULT_VALUE",
                "openfeature.flags[0].defaultValue=false"
            )
            .run(context -> {
                Client client = context.getBean(Client.class);
                FlagEvaluationDetails<Boolean> details = client.getBooleanDetails("performance-mode", null);

                assertThat(details.getValue()).isTrue();
                assertThat(details.getFlagMetadata().getString(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                    .isEqualTo("IZANAMI");
            });
    }

    //@Test
    void evaluatesObjectFlagFromServer() {
        contextRunner
            .withPropertyValues(
                "izanami.base-url=" + envOrDefault("IZANAMI_BASE_URL", "http://localhost:9999"),
                "izanami.api-path=" + envOrDefault("IZANAMI_API_PATH", "/api"),
                "izanami.client-id=" + env("IZANAMI_CLIENT_ID"),
                "izanami.client-secret=" + env("IZANAMI_CLIENT_SECRET"),
                "izanami.cache.sse.enabled=false",
                "openfeature.flags[0].key=" + JSON_CONTENT_ID,
                "openfeature.flags[0].name=json-content",
                "openfeature.flags[0].description=JSON content",
                "openfeature.flags[0].valueType=object",
                "openfeature.flags[0].errorStrategy=DEFAULT_VALUE",
                "openfeature.flags[0].defaultValue.name=fallback"
            )
            .run(context -> {
                Client client = context.getBean(Client.class);
                FlagEvaluationDetails<Value> details = client.getObjectDetails("json-content", new Value());

                assertThat(details.getFlagMetadata().getString(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                    .isEqualTo("IZANAMI");

                assertThat(details.getValue().isStructure()).isTrue();
                assertThat(details.getValue().asStructure().getValue("name").asString()).isEqualTo("Izanami");
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
