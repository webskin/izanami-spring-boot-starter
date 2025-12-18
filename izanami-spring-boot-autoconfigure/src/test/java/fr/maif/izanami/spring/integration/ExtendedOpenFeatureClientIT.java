package fr.maif.izanami.spring.integration;

import dev.openfeature.sdk.*;
import fr.maif.izanami.spring.openfeature.FlagConfig;
import fr.maif.izanami.spring.openfeature.FlagMetadataKeys;
import fr.maif.izanami.spring.openfeature.FlagValueSource;
import fr.maif.izanami.spring.openfeature.api.ExtendedOpenFeatureClient;
import fr.maif.izanami.spring.openfeature.api.FlagConfigService;
import fr.maif.izanami.spring.openfeature.api.IzanamiErrorCallback;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.CompletableFuture;

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

    @Test
    void evaluatesObjectFlagFromServer() {
        contextRunner
            .withPropertyValues(withFlagConfig(
                "openfeature.flags[0].key=" + JSON_CONFIG_ID,
                "openfeature.flags[0].name=json-config",
                "openfeature.flags[0].description=Configuration stored as JSON",
                "openfeature.flags[0].valueType=object",
                "openfeature.flags[0].errorStrategy=DEFAULT_VALUE",
                "openfeature.flags[0].defaultValue={}"
            ))
            .run(context -> {
                waitForIzanami(context);

                FlagConfigService configService = context.getBean(FlagConfigService.class);
                FlagConfig flagConfig = configService.getFlagConfigByName("json-config").orElseThrow();

                ExtendedOpenFeatureClient client = context.getBean(ExtendedOpenFeatureClient.class);
                FlagEvaluationDetails<Value> details = client.getObjectDetails(flagConfig.key());

                assertThat(flagConfig.key()).isEqualTo(JSON_CONFIG_ID);
                assertThat(details.getValue().isStructure()).isTrue();
                Structure structure = details.getValue().asStructure();
                assertThat(structure.getValue("enabled").asBoolean()).isTrue();
                assertThat(structure.getValue("settings").isStructure()).isTrue();
                Structure settings = structure.getValue("settings").asStructure();
                assertThat(settings.getValue("theme").asString()).isEqualTo("dark");
                assertThat(settings.getValue("maxRetries").asInteger()).isEqualTo(3);
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

    // ========== Unavailable server fallback tests ==========

    @Test
    void returnsBooleanDefaultWhenServerUnavailable() {
        contextRunner
            .withPropertyValues(withUnavailableServerAndFlagConfig(
                "openfeature.flags[0].key=" + TURBO_MODE_ID,
                "openfeature.flags[0].name=turbo-mode",
                "openfeature.flags[0].description=Enable turbo mode",
                "openfeature.flags[0].valueType=boolean",
                "openfeature.flags[0].errorStrategy=DEFAULT_VALUE",
                "openfeature.flags[0].defaultValue=false"
            ))
            .run(context -> {
                ExtendedOpenFeatureClient client = context.getBean(ExtendedOpenFeatureClient.class);
                FlagEvaluationDetails<Boolean> details = client.getBooleanDetails(TURBO_MODE_ID);

                assertThat(details.getValue()).isFalse();
                assertThat(details.getFlagMetadata().getString(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                    .isEqualTo("IZANAMI_ERROR_STRATEGY");
            });
    }

    @Test
    void returnsStringDefaultWhenServerUnavailable() {
        contextRunner
            .withPropertyValues(withUnavailableServerAndFlagConfig(
                "openfeature.flags[0].key=" + SECRET_CODENAME_ID,
                "openfeature.flags[0].name=secret-codename",
                "openfeature.flags[0].description=The secret codename",
                "openfeature.flags[0].valueType=string",
                "openfeature.flags[0].errorStrategy=DEFAULT_VALUE",
                "openfeature.flags[0].defaultValue=classified"
            ))
            .run(context -> {
                ExtendedOpenFeatureClient client = context.getBean(ExtendedOpenFeatureClient.class);
                FlagEvaluationDetails<String> details = client.getStringDetails(SECRET_CODENAME_ID);

                assertThat(details.getValue()).isEqualTo("classified");
                assertThat(details.getFlagMetadata().getString(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                    .isEqualTo("IZANAMI_ERROR_STRATEGY");
            });
    }

    @Test
    void returnsIntegerDefaultWhenServerUnavailable() {
        contextRunner
            .withPropertyValues(withUnavailableServerAndFlagConfig(
                "openfeature.flags[0].key=" + MAX_POWER_LEVEL_ID,
                "openfeature.flags[0].name=max-power-level",
                "openfeature.flags[0].description=Maximum power level",
                "openfeature.flags[0].valueType=integer",
                "openfeature.flags[0].errorStrategy=DEFAULT_VALUE",
                "openfeature.flags[0].defaultValue=100"
            ))
            .run(context -> {
                ExtendedOpenFeatureClient client = context.getBean(ExtendedOpenFeatureClient.class);
                FlagEvaluationDetails<Integer> details = client.getIntegerDetails(MAX_POWER_LEVEL_ID);

                assertThat(details.getValue()).isEqualTo(100);
                assertThat(details.getFlagMetadata().getString(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                    .isEqualTo("IZANAMI_ERROR_STRATEGY");
            });
    }

    @Test
    void returnsDoubleDefaultWhenServerUnavailable() {
        contextRunner
            .withPropertyValues(withUnavailableServerAndFlagConfig(
                "openfeature.flags[0].key=" + DISCOUNT_RATE_ID,
                "openfeature.flags[0].name=discount-rate",
                "openfeature.flags[0].description=Current discount rate",
                "openfeature.flags[0].valueType=double",
                "openfeature.flags[0].errorStrategy=DEFAULT_VALUE",
                "openfeature.flags[0].defaultValue=1.2"
            ))
            .run(context -> {
                ExtendedOpenFeatureClient client = context.getBean(ExtendedOpenFeatureClient.class);
                FlagEvaluationDetails<Double> details = client.getDoubleDetails(DISCOUNT_RATE_ID);

                assertThat(details.getValue()).isEqualTo(1.2);
                assertThat(details.getFlagMetadata().getString(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                    .isEqualTo("IZANAMI_ERROR_STRATEGY");
            });
    }

    // ========== APPLICATION_ERROR_STRATEGY tests ==========

    @Test
    void returnsCallerDefaultWhenFlagNotConfigured() {
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
                // Request a flag key that is NOT configured in openfeature.flags
                FlagEvaluationDetails<Boolean> details = client.getBooleanDetails("not-configured-flag-key", true);

                assertThat(details.getValue()).isTrue(); // caller default
                assertThat(details.getFlagMetadata().getString(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                    .isEqualTo("APPLICATION_ERROR_STRATEGY");
                assertThat(details.getErrorCode()).isEqualTo(dev.openfeature.sdk.ErrorCode.FLAG_NOT_FOUND);
            });
    }

    @Test
    void returnsCallerDefaultWhenTypeMismatch() {
        contextRunner
            .withPropertyValues(withFlagConfig(
                "openfeature.flags[0].key=" + TURBO_MODE_ID,
                "openfeature.flags[0].name=turbo-mode",
                "openfeature.flags[0].description=Enable turbo mode",
                "openfeature.flags[0].valueType=boolean",  // configured as boolean
                "openfeature.flags[0].errorStrategy=DEFAULT_VALUE",
                "openfeature.flags[0].defaultValue=false"
            ))
            .run(context -> {
                waitForIzanami(context);

                ExtendedOpenFeatureClient client = context.getBean(ExtendedOpenFeatureClient.class);
                // Request as STRING when configured as BOOLEAN -> type mismatch
                FlagEvaluationDetails<String> details = client.getStringDetails(TURBO_MODE_ID, "caller-default");

                assertThat(details.getValue()).isEqualTo("caller-default");
                assertThat(details.getFlagMetadata().getString(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                    .isEqualTo("APPLICATION_ERROR_STRATEGY");
                assertThat(details.getErrorCode()).isEqualTo(dev.openfeature.sdk.ErrorCode.TYPE_MISMATCH);
            });
    }

    // ========== Error strategy tests (FAIL, NULL_VALUE, CALLBACK) ==========
    // Note: Through the OpenFeature layer, these error strategies behave differently than direct IzanamiService calls.
    // When using IzanamiService.forFlagKey().stringValue() directly:
    //   - FAIL throws CompletionException
    //   - NULL_VALUE returns null
    //   - CALLBACK returns the callback value
    //
    // Through OpenFeature, exceptions are caught and the caller-default
    // is returned because the extraction from IzanamiResult.Error fails. The FLAG_VALUE_SOURCE shows
    // IZANAMI_ERROR_STRATEGY because the error originated from the Izanami client, but the value falls back
    // to the OpenFeature caller-default since extraction cannot proceed.
    //
    // For proper error strategy handling with these specific strategies, use IzanamiService directly
    // (see IzanamiServiceImplIT for those tests). The DEFAULT_VALUE strategy works through OpenFeature
    // because it provides an extractable fallback value.

    @Test
    void returnsCallerDefaultWhenServerUnavailableWithFailStrategy() {
        contextRunner
            .withPropertyValues(withUnavailableServerAndFlagConfig(
                "openfeature.flags[0].key=" + SECRET_CODENAME_ID,
                "openfeature.flags[0].name=secret-codename",
                "openfeature.flags[0].description=The secret codename",
                "openfeature.flags[0].valueType=string",
                "openfeature.flags[0].errorStrategy=FAIL"
            ))
            .run(context -> {
                ExtendedOpenFeatureClient client = context.getBean(ExtendedOpenFeatureClient.class);
                FlagEvaluationDetails<String> details = client.getStringDetails(SECRET_CODENAME_ID, "caller-default");

                // IMPORTANT ! FAIL strategy through OpenFeature returns caller-default
                assertThat(details.getValue()).isEqualTo("caller-default");
                assertThat(details.getFlagMetadata().getString(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                    .isEqualTo(FlagValueSource.APPLICATION_ERROR_STRATEGY.name());
                assertThat(details.getErrorCode()).isEqualTo(dev.openfeature.sdk.ErrorCode.GENERAL);
            });
    }

    @Test
    void returnsCallerDefaultWhenServerUnavailableWithNullValueStrategy() {
        contextRunner
            .withPropertyValues(withUnavailableServerAndFlagConfig(
                "openfeature.flags[0].key=" + SECRET_CODENAME_ID,
                "openfeature.flags[0].name=secret-codename",
                "openfeature.flags[0].description=The secret codename",
                "openfeature.flags[0].valueType=string",
                "openfeature.flags[0].errorStrategy=NULL_VALUE"
            ))
            .run(context -> {
                ExtendedOpenFeatureClient client = context.getBean(ExtendedOpenFeatureClient.class);
                FlagEvaluationDetails<String> details = client.getStringDetails(SECRET_CODENAME_ID, "caller-default");

                // IMPORTANT ! NULL_VALUE strategy through OpenFeature returns caller-default
                assertThat(details.getValue()).isEqualTo("caller-default");
                assertThat(details.getFlagMetadata().getString(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                    .isEqualTo("IZANAMI_ERROR_STRATEGY");
            });
    }

    @Test
    void returnsCallerDefaultWhenServerUnavailableWithCallbackStrategy() {
        contextRunner
            .withUserConfiguration(TestCallbackConfiguration.class)
            .withPropertyValues(withUnavailableServerAndFlagConfig(
                "openfeature.flags[0].key=" + SECRET_CODENAME_ID,
                "openfeature.flags[0].name=secret-codename",
                "openfeature.flags[0].description=The secret codename",
                "openfeature.flags[0].valueType=string",
                "openfeature.flags[0].errorStrategy=CALLBACK",
                "openfeature.flags[0].callbackBean=testErrorCallback"
            ))
            .run(context -> {
                ExtendedOpenFeatureClient client = context.getBean(ExtendedOpenFeatureClient.class);
                FlagEvaluationDetails<String> details = client.getStringDetails(SECRET_CODENAME_ID, "caller-default");

                // IMPORTANT ! CALLBACK strategy through OpenFeature returns caller-default
                // (callback cannot be invoked through the featureValues() path)
                assertThat(details.getValue()).isEqualTo("caller-default");
                assertThat(details.getFlagMetadata().getString(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                    .isEqualTo("IZANAMI_ERROR_STRATEGY");
            });
    }

    // ========== Inactive (disabled) feature tests ==========

    @Test
    void evaluatesInactiveBooleanFlagAsFalse() {
        contextRunner
            .withPropertyValues(withFlagConfig(
                "openfeature.flags[0].key=" + INACTIVE_BOOL_ID,
                "openfeature.flags[0].name=inactive-bool",
                "openfeature.flags[0].description=Disabled boolean feature",
                "openfeature.flags[0].valueType=boolean",
                "openfeature.flags[0].errorStrategy=DEFAULT_VALUE",
                "openfeature.flags[0].defaultValue=true"
            ))
            .run(context -> {
                waitForIzanami(context);

                ExtendedOpenFeatureClient client = context.getBean(ExtendedOpenFeatureClient.class);
                FlagEvaluationDetails<Boolean> details = client.getBooleanDetails(INACTIVE_BOOL_ID);

                // Disabled features evaluate to false regardless of defaultValue
                assertThat(details.getValue()).isFalse();
                assertThat(details.getReason()).isEqualTo(Reason.DISABLED.name());
                assertThat(details.getFlagMetadata().getString(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                    .isEqualTo("IZANAMI");
            });
    }

    @Test
    void evaluatesInactiveStringFlagReturnsDefaultValue() {
        contextRunner
            .withPropertyValues(withFlagConfig(
                "openfeature.flags[0].key=" + INACTIVE_STRING_ID,
                "openfeature.flags[0].name=inactive-string",
                "openfeature.flags[0].description=Disabled string feature",
                "openfeature.flags[0].valueType=string",
                "openfeature.flags[0].errorStrategy=DEFAULT_VALUE",
                "openfeature.flags[0].defaultValue=fallback"
            ))
            .run(context -> {
                waitForIzanami(context);

                ExtendedOpenFeatureClient client = context.getBean(ExtendedOpenFeatureClient.class);
                FlagEvaluationDetails<String> details = client.getStringDetails(INACTIVE_STRING_ID);

                // Disabled non-boolean features return the defaultValue when configured
                assertThat(details.getValue()).isEqualTo("fallback");
                assertThat(details.getReason()).isEqualTo(Reason.DISABLED.name());
                assertThat(details.getFlagMetadata().getString(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                    .isEqualTo(FlagValueSource.APPLICATION_ERROR_STRATEGY.name());
            });
    }

    @Test
    void evaluatesInactiveIntegerFlagReturnsDefaultValue() {
        contextRunner
            .withPropertyValues(withFlagConfig(
                "openfeature.flags[0].key=" + INACTIVE_NUMBER_ID,
                "openfeature.flags[0].name=inactive-number",
                "openfeature.flags[0].description=Disabled number feature",
                "openfeature.flags[0].valueType=integer",
                "openfeature.flags[0].errorStrategy=DEFAULT_VALUE",
                "openfeature.flags[0].defaultValue=999"
            ))
            .run(context -> {
                waitForIzanami(context);

                ExtendedOpenFeatureClient client = context.getBean(ExtendedOpenFeatureClient.class);
                FlagEvaluationDetails<Integer> details = client.getIntegerDetails(INACTIVE_NUMBER_ID);

                // Disabled non-boolean features return the defaultValue when configured
                assertThat(details.getValue()).isEqualTo(999);
                assertThat(details.getReason()).isEqualTo(Reason.DISABLED.name());
                assertThat(details.getFlagMetadata().getString(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                    .isEqualTo(FlagValueSource.APPLICATION_ERROR_STRATEGY.name());
            });
    }

    @Test
    void evaluatesInactiveDoubleFlagReturnsDefaultValue() {
        contextRunner
            .withPropertyValues(withFlagConfig(
                "openfeature.flags[0].key=" + INACTIVE_NUMBER_ID,
                "openfeature.flags[0].name=inactive-number",
                "openfeature.flags[0].description=Disabled number feature",
                "openfeature.flags[0].valueType=double",
                "openfeature.flags[0].errorStrategy=DEFAULT_VALUE",
                "openfeature.flags[0].defaultValue=99.9"
            ))
            .run(context -> {
                waitForIzanami(context);

                ExtendedOpenFeatureClient client = context.getBean(ExtendedOpenFeatureClient.class);
                FlagEvaluationDetails<Double> details = client.getDoubleDetails(INACTIVE_NUMBER_ID);

                // Disabled non-boolean features return the defaultValue when configured
                assertThat(details.getValue()).isEqualTo(99.9);
                assertThat(details.getReason()).isEqualTo(Reason.DISABLED.name());
                assertThat(details.getFlagMetadata().getString(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                    .isEqualTo(FlagValueSource.APPLICATION_ERROR_STRATEGY.name());
            });
    }

    /**
     * Test configuration providing a callback bean for error handling tests.
     */
    @Configuration
    static class TestCallbackConfiguration {
        @Bean("testErrorCallback")
        IzanamiErrorCallback testErrorCallback() {
            return (error, flagKey, configuredType, requestedType) -> {
                if (requestedType == FlagValueType.STRING) {
                    return CompletableFuture.completedFuture("callback-fallback-value");
                }
                return CompletableFuture.completedFuture(null);
            };
        }
    }
}
