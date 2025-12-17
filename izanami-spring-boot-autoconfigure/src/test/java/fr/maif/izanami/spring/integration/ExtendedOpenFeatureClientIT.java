package fr.maif.izanami.spring.integration;

import dev.openfeature.sdk.FlagEvaluationDetails;
import fr.maif.izanami.spring.openfeature.FlagConfig;
import fr.maif.izanami.spring.openfeature.FlagMetadataKeys;
import fr.maif.izanami.spring.openfeature.api.ExtendedOpenFeatureClient;
import fr.maif.izanami.spring.openfeature.api.FlagConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ExtendedOpenFeatureClient} running against a real Izanami server.
 */
@EnabledIfEnvironmentVariable(named = "IZANAMI_INTEGRATION_TEST", matches = "true")
class ExtendedOpenFeatureClientIT extends BaseIzanamiIT {

    @Test
    void evaluatesBooleanFlagFromServer() {
        contextRunner
            .withPropertyValues(withFlagConfig(
                "openfeature.flags[0].key=" + TURBO_MODE_ID,
                "openfeature.flags[0].name=turbo-mode",
                "openfeature.flags[0].description=Enable turbo mode",
                "openfeature.flags[0].valueType=boolean",
                "openfeature.flags[0].errorStrategy=DEFAULT_VALUE",
                "openfeature.flags[0].defaultValue=false"
            ))
            .run(context -> {
                waitForIzanami(context);

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
            .withPropertyValues(withFlagConfig(
                "openfeature.flags[0].key=" + SECRET_CODENAME_ID,
                "openfeature.flags[0].name=secret-codename",
                "openfeature.flags[0].description=The secret codename",
                "openfeature.flags[0].valueType=string",
                "openfeature.flags[0].errorStrategy=DEFAULT_VALUE",
                "openfeature.flags[0].defaultValue=classified"
            ))
            .run(context -> {
                waitForIzanami(context);

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
            .withPropertyValues(withFlagConfig(
                "openfeature.flags[0].key=" + MAX_POWER_LEVEL_ID,
                "openfeature.flags[0].name=max-power-level",
                "openfeature.flags[0].description=Maximum power level",
                "openfeature.flags[0].valueType=integer",
                "openfeature.flags[0].errorStrategy=DEFAULT_VALUE",
                "openfeature.flags[0].defaultValue=100"
            ))
            .run(context -> {
                waitForIzanami(context);

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
            .withPropertyValues(withFlagConfig(
                "openfeature.flags[0].key=" + DISCOUNT_RATE_ID,
                "openfeature.flags[0].name=discount-rate",
                "openfeature.flags[0].description=Current discount rate",
                "openfeature.flags[0].valueType=double",
                "openfeature.flags[0].errorStrategy=DEFAULT_VALUE",
                "openfeature.flags[0].defaultValue=0.0"
            ))
            .run(context -> {
                waitForIzanami(context);

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

    // ========== ByName integration tests ==========

    @Test
    void evaluatesBooleanFlagByName() {
        contextRunner
            .withPropertyValues(withFlagConfig(
                "openfeature.flags[0].key=" + TURBO_MODE_ID,
                "openfeature.flags[0].name=turbo-mode",
                "openfeature.flags[0].description=Enable turbo mode",
                "openfeature.flags[0].valueType=boolean",
                "openfeature.flags[0].errorStrategy=DEFAULT_VALUE",
                "openfeature.flags[0].defaultValue=false"
            ))
            .run(context -> {
                waitForIzanami(context);

                ExtendedOpenFeatureClient client = context.getBean(ExtendedOpenFeatureClient.class);
                FlagEvaluationDetails<Boolean> details = client.getBooleanDetailsByName("turbo-mode");

                assertThat(details.getValue()).isTrue();
                assertThat(details.getFlagMetadata().getString(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                    .isEqualTo("IZANAMI");
            });
    }

    @Test
    void evaluatesStringFlagByName() {
        contextRunner
            .withPropertyValues(withFlagConfig(
                "openfeature.flags[0].key=" + SECRET_CODENAME_ID,
                "openfeature.flags[0].name=secret-codename",
                "openfeature.flags[0].description=The secret codename",
                "openfeature.flags[0].valueType=string",
                "openfeature.flags[0].errorStrategy=DEFAULT_VALUE",
                "openfeature.flags[0].defaultValue=classified"
            ))
            .run(context -> {
                waitForIzanami(context);

                ExtendedOpenFeatureClient client = context.getBean(ExtendedOpenFeatureClient.class);
                FlagEvaluationDetails<String> details = client.getStringDetailsByName("secret-codename");

                assertThat(details.getValue()).isEqualTo("Operation Thunderbolt");
                assertThat(details.getFlagMetadata().getString(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                    .isEqualTo("IZANAMI");
            });
    }

    @Test
    void evaluatesIntegerFlagByName() {
        contextRunner
            .withPropertyValues(withFlagConfig(
                "openfeature.flags[0].key=" + MAX_POWER_LEVEL_ID,
                "openfeature.flags[0].name=max-power-level",
                "openfeature.flags[0].description=Maximum power level",
                "openfeature.flags[0].valueType=integer",
                "openfeature.flags[0].errorStrategy=DEFAULT_VALUE",
                "openfeature.flags[0].defaultValue=100"
            ))
            .run(context -> {
                waitForIzanami(context);

                ExtendedOpenFeatureClient client = context.getBean(ExtendedOpenFeatureClient.class);
                FlagEvaluationDetails<Integer> details = client.getIntegerDetailsByName("max-power-level");

                assertThat(details.getValue()).isEqualTo(9001);
                assertThat(details.getFlagMetadata().getString(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                    .isEqualTo("IZANAMI");
            });
    }

    @Test
    void evaluatesDoubleFlagByName() {
        contextRunner
            .withPropertyValues(withFlagConfig(
                "openfeature.flags[0].key=" + DISCOUNT_RATE_ID,
                "openfeature.flags[0].name=discount-rate",
                "openfeature.flags[0].description=Current discount rate",
                "openfeature.flags[0].valueType=double",
                "openfeature.flags[0].errorStrategy=DEFAULT_VALUE",
                "openfeature.flags[0].defaultValue=0.0"
            ))
            .run(context -> {
                waitForIzanami(context);

                ExtendedOpenFeatureClient client = context.getBean(ExtendedOpenFeatureClient.class);
                FlagEvaluationDetails<Double> details = client.getDoubleDetailsByName("discount-rate");

                assertThat(details.getValue()).isEqualTo(0.15);
                assertThat(details.getFlagMetadata().getString(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                    .isEqualTo("IZANAMI");
            });
    }
}
