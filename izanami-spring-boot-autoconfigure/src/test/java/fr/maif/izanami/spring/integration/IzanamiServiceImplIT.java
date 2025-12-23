package fr.maif.izanami.spring.integration;

import dev.openfeature.sdk.FlagValueType;
import fr.maif.izanami.spring.openfeature.FlagMetadataKeys;
import fr.maif.izanami.spring.openfeature.api.IzanamiErrorCallback;
import fr.maif.izanami.spring.service.api.IzanamiService;
import fr.maif.izanami.spring.service.api.ResultValueWithDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link IzanamiService} running against a real Izanami server.
 */
@EnabledIfEnvironmentVariable(named = "IZANAMI_INTEGRATION_TEST", matches = "true")
class IzanamiServiceImplIT extends BaseIzanamiIT {

    // ========== By Key integration tests ==========

    @Test
    void evaluatesBooleanFlagFromServer() {
        contextRunner
            .withPropertyValues(withFlagConfig(
                "openfeature.flags.turbo-mode.key=" + TURBO_MODE_ID,
                                "openfeature.flags.turbo-mode.description=Enable turbo mode",
                "openfeature.flags.turbo-mode.valueType=boolean",
                "openfeature.flags.turbo-mode.errorStrategy=DEFAULT_VALUE",
                "openfeature.flags.turbo-mode.defaultValue=false"
            ))
            .run(context -> {
                waitForIzanami(context);

                IzanamiService service = context.getBean(IzanamiService.class);
                Boolean value = service.forFlagKey(TURBO_MODE_ID).booleanValue().join();

                assertThat(value).isTrue();
            });
    }

    @Test
    void evaluatesStringFlagFromServer() {
        contextRunner
            .withPropertyValues(withFlagConfig(
                "openfeature.flags.secret-codename.key=" + SECRET_CODENAME_ID,
                                "openfeature.flags.secret-codename.description=The secret codename",
                "openfeature.flags.secret-codename.valueType=string",
                "openfeature.flags.secret-codename.errorStrategy=DEFAULT_VALUE",
                "openfeature.flags.secret-codename.defaultValue=classified"
            ))
            .run(context -> {
                waitForIzanami(context);

                IzanamiService service = context.getBean(IzanamiService.class);
                String value = service.forFlagKey(SECRET_CODENAME_ID).stringValue().join();

                assertThat(value).isEqualTo("Operation Thunderbolt");
            });
    }

    @Test
    void evaluatesJsonStringFlagFromServer() {
        contextRunner
            .withPropertyValues(withFlagConfig(
                "openfeature.flags.json-config.key=" + JSON_CONFIG_ID,
                                "openfeature.flags.json-config.description=Configuration stored as JSON string",
                "openfeature.flags.json-config.valueType=string",
                "openfeature.flags.json-config.errorStrategy=DEFAULT_VALUE",
                "openfeature.flags.json-config.defaultValue={}"
            ))
            .run(context -> {
                waitForIzanami(context);

                IzanamiService service = context.getBean(IzanamiService.class);
                String value = service.forFlagKey(JSON_CONFIG_ID).stringValue().get();

                assertThat(value).isEqualTo("""
                    {
                      "enabled": true,
                      "settings": {
                        "theme": "dark",
                        "maxRetries": 3
                      }
                    }""");
            });
    }

    @Test
    void evaluatesIntegerFlagFromServer() {
        contextRunner
            .withPropertyValues(withFlagConfig(
                "openfeature.flags.max-power-level.key=" + MAX_POWER_LEVEL_ID,
                                "openfeature.flags.max-power-level.description=Maximum power level",
                "openfeature.flags.max-power-level.valueType=integer",
                "openfeature.flags.max-power-level.errorStrategy=DEFAULT_VALUE",
                "openfeature.flags.max-power-level.defaultValue=100"
            ))
            .run(context -> {
                waitForIzanami(context);

                IzanamiService service = context.getBean(IzanamiService.class);
                BigDecimal value = service.forFlagKey(MAX_POWER_LEVEL_ID).numberValue().join();

                assertThat(value).isEqualByComparingTo(new BigDecimal("9001"));
            });
    }

    @Test
    void evaluatesDoubleFlagFromServer() {
        contextRunner
            .withPropertyValues(withFlagConfig(
                "openfeature.flags.discount-rate.key=" + DISCOUNT_RATE_ID,
                                "openfeature.flags.discount-rate.description=Current discount rate",
                "openfeature.flags.discount-rate.valueType=double",
                "openfeature.flags.discount-rate.errorStrategy=DEFAULT_VALUE",
                "openfeature.flags.discount-rate.defaultValue=0.0"
            ))
            .run(context -> {
                waitForIzanami(context);

                IzanamiService service = context.getBean(IzanamiService.class);
                BigDecimal value = service.forFlagKey(DISCOUNT_RATE_ID).numberValue().join();

                assertThat(value).isEqualByComparingTo(new BigDecimal("0.15"));
            });
    }

    // ========== ByName integration tests ==========

    @Test
    void evaluatesBooleanFlagByName() {
        contextRunner
            .withPropertyValues(withFlagConfig(
                "openfeature.flags.turbo-mode.key=" + TURBO_MODE_ID,
                                "openfeature.flags.turbo-mode.description=Enable turbo mode",
                "openfeature.flags.turbo-mode.valueType=boolean",
                "openfeature.flags.turbo-mode.errorStrategy=DEFAULT_VALUE",
                "openfeature.flags.turbo-mode.defaultValue=false"
            ))
            .run(context -> {
                waitForIzanami(context);

                IzanamiService service = context.getBean(IzanamiService.class);
                Boolean value = service.forFlagName("turbo-mode").booleanValue().join();

                assertThat(value).isTrue();
            });
    }

    @Test
    void evaluatesStringFlagByName() {
        contextRunner
            .withPropertyValues(withFlagConfig(
                "openfeature.flags.secret-codename.key=" + SECRET_CODENAME_ID,
                                "openfeature.flags.secret-codename.description=The secret codename",
                "openfeature.flags.secret-codename.valueType=string",
                "openfeature.flags.secret-codename.errorStrategy=DEFAULT_VALUE",
                "openfeature.flags.secret-codename.defaultValue=classified"
            ))
            .run(context -> {
                waitForIzanami(context);

                IzanamiService service = context.getBean(IzanamiService.class);
                String value = service.forFlagName("secret-codename").stringValue().join();

                assertThat(value).isEqualTo("Operation Thunderbolt");
            });
    }

    @Test
    void evaluatesIntegerFlagByName() {
        contextRunner
            .withPropertyValues(withFlagConfig(
                "openfeature.flags.max-power-level.key=" + MAX_POWER_LEVEL_ID,
                                "openfeature.flags.max-power-level.description=Maximum power level",
                "openfeature.flags.max-power-level.valueType=integer",
                "openfeature.flags.max-power-level.errorStrategy=DEFAULT_VALUE",
                "openfeature.flags.max-power-level.defaultValue=100"
            ))
            .run(context -> {
                waitForIzanami(context);

                IzanamiService service = context.getBean(IzanamiService.class);
                BigDecimal value = service.forFlagName("max-power-level").numberValue().join();

                assertThat(value).isEqualByComparingTo(new BigDecimal("9001"));
            });
    }

    @Test
    void evaluatesDoubleFlagByName() {
        contextRunner
            .withPropertyValues(withFlagConfig(
                "openfeature.flags.discount-rate.key=" + DISCOUNT_RATE_ID,
                                "openfeature.flags.discount-rate.description=Current discount rate",
                "openfeature.flags.discount-rate.valueType=double",
                "openfeature.flags.discount-rate.errorStrategy=DEFAULT_VALUE",
                "openfeature.flags.discount-rate.defaultValue=0.0"
            ))
            .run(context -> {
                waitForIzanami(context);

                IzanamiService service = context.getBean(IzanamiService.class);
                BigDecimal value = service.forFlagName("discount-rate").numberValue().join();

                assertThat(value).isEqualByComparingTo(new BigDecimal("0.15"));
            });
    }

    // ========== Unavailable server fallback tests ==========

    @Test
    void returnsBooleanDefaultWhenServerUnavailable() {
        contextRunner
            .withPropertyValues(withUnavailableServerAndFlagConfig(
                "openfeature.flags.turbo-mode.key=" + TURBO_MODE_ID,
                                "openfeature.flags.turbo-mode.description=Enable turbo mode",
                "openfeature.flags.turbo-mode.valueType=boolean",
                "openfeature.flags.turbo-mode.errorStrategy=DEFAULT_VALUE",
                "openfeature.flags.turbo-mode.defaultValue=false"
            ))
            .run(context -> {
                IzanamiService service = context.getBean(IzanamiService.class);
                Boolean value = service.forFlagKey(TURBO_MODE_ID).booleanValue().join();

                assertThat(value).isFalse();
            });
    }

    @Test
    void returnsStringDefaultWhenServerUnavailable() {
        contextRunner
            .withPropertyValues(withUnavailableServerAndFlagConfig(
                "openfeature.flags.secret-codename.key=" + SECRET_CODENAME_ID,
                                "openfeature.flags.secret-codename.description=The secret codename",
                "openfeature.flags.secret-codename.valueType=string",
                "openfeature.flags.secret-codename.errorStrategy=DEFAULT_VALUE",
                "openfeature.flags.secret-codename.defaultValue=classified"
            ))
            .run(context -> {
                IzanamiService service = context.getBean(IzanamiService.class);
                String value = service.forFlagKey(SECRET_CODENAME_ID).stringValue().join();

                assertThat(value).isEqualTo("classified");
            });
    }

    @Test
    void returnsIntegerDefaultWhenServerUnavailable() {
        contextRunner
            .withPropertyValues(withUnavailableServerAndFlagConfig(
                "openfeature.flags.max-power-level.key=" + MAX_POWER_LEVEL_ID,
                                "openfeature.flags.max-power-level.description=Maximum power level",
                "openfeature.flags.max-power-level.valueType=integer",
                "openfeature.flags.max-power-level.errorStrategy=DEFAULT_VALUE",
                "openfeature.flags.max-power-level.defaultValue=100"
            ))
            .run(context -> {
                IzanamiService service = context.getBean(IzanamiService.class);
                BigDecimal value = service.forFlagKey(MAX_POWER_LEVEL_ID).numberValue().join();

                assertThat(value).isEqualByComparingTo(new BigDecimal("100"));
            });
    }

    @Test
    void returnsDoubleDefaultWhenServerUnavailable() {
        contextRunner
            .withPropertyValues(withUnavailableServerAndFlagConfig(
                "openfeature.flags.discount-rate.key=" + DISCOUNT_RATE_ID,
                                "openfeature.flags.discount-rate.description=Current discount rate",
                "openfeature.flags.discount-rate.valueType=double",
                "openfeature.flags.discount-rate.errorStrategy=DEFAULT_VALUE",
                "openfeature.flags.discount-rate.defaultValue=0.0"
            ))
            .run(context -> {
                IzanamiService service = context.getBean(IzanamiService.class);
                BigDecimal value = service.forFlagKey(DISCOUNT_RATE_ID).numberValue().join();

                assertThat(value).isEqualByComparingTo(new BigDecimal("0.0"));
            });
    }


    @Test
    void evaluatesDoubleResultValueWithDetailsFromServer() {
        contextRunner
            .withPropertyValues(withFlagConfig(
                "openfeature.flags.discount-rate.key=" + DISCOUNT_RATE_ID,
                                "openfeature.flags.discount-rate.description=Current discount rate",
                "openfeature.flags.discount-rate.valueType=double",
                "openfeature.flags.discount-rate.errorStrategy=DEFAULT_VALUE",
                "openfeature.flags.discount-rate.defaultValue=0.0"
            ))
            .run(context -> {
                waitForIzanami(context);

                IzanamiService service = context.getBean(IzanamiService.class);
                ResultValueWithDetails<BigDecimal> result = service.forFlagKey(DISCOUNT_RATE_ID).numberValueDetails().join();

                assertThat(result.value().doubleValue()).isEqualTo(0.15);
                assertThat(result.metadata().get(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                    .isEqualTo("IZANAMI");
            });
    }

    // ========== Non-existent flag tests ==========

    private static final String NON_EXISTENT_FLAG_ID = "00000000-0000-0000-0000-000000000000";

    @Test
    void returnsDefaultWhenFlagDoesNotExistOnServer() {
        contextRunner
            .withPropertyValues(withFlagConfig(
                "openfeature.flags.non-existent-flag.key=" + NON_EXISTENT_FLAG_ID,
                                "openfeature.flags.non-existent-flag.description=A flag that does not exist on the server",
                "openfeature.flags.non-existent-flag.valueType=boolean",
                "openfeature.flags.non-existent-flag.errorStrategy=DEFAULT_VALUE",
                "openfeature.flags.non-existent-flag.defaultValue=true"
            ))
            .run(context -> {
                waitForIzanami(context);

                IzanamiService service = context.getBean(IzanamiService.class);
                ResultValueWithDetails<Boolean> result = service.forFlagKey(NON_EXISTENT_FLAG_ID).booleanValueDetails().join();

                assertThat(result.value()).isTrue();
                assertThat(result.metadata().get(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                    .isEqualTo("IZANAMI_ERROR_STRATEGY");
            });
    }

    // ========== FLAG_NOT_FOUND tests (flag not in configuration) ==========

    @Test
    void forFlagKey_notConfigured_returnsDefaultsWithFlagNotFound() {
        contextRunner
            .withPropertyValues(withFlagConfig(
                "openfeature.flags.turbo-mode.key=" + TURBO_MODE_ID,
                                "openfeature.flags.turbo-mode.description=Enable turbo mode",
                "openfeature.flags.turbo-mode.valueType=boolean",
                "openfeature.flags.turbo-mode.errorStrategy=DEFAULT_VALUE",
                "openfeature.flags.turbo-mode.defaultValue=false"
            ))
            .run(context -> {
                waitForIzanami(context);

                IzanamiService service = context.getBean(IzanamiService.class);
                // Request a flag key that is NOT in openfeature.flags configuration
                ResultValueWithDetails<Boolean> result = service.forFlagKey("not-configured-key").booleanValueDetails().join();

                assertThat(result.value()).isFalse();  // default for boolean
                assertThat(result.metadata().get(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                    .isEqualTo("APPLICATION_ERROR_STRATEGY");
                assertThat(result.metadata().get(FlagMetadataKeys.FLAG_EVALUATION_REASON))
                    .isEqualTo("FLAG_NOT_FOUND");
            });
    }

    @Test
    void forFlagName_notConfigured_returnsDefaultsWithFlagNotFound() {
        contextRunner
            .withPropertyValues(withFlagConfig(
                "openfeature.flags.turbo-mode.key=" + TURBO_MODE_ID,
                                "openfeature.flags.turbo-mode.description=Enable turbo mode",
                "openfeature.flags.turbo-mode.valueType=boolean",
                "openfeature.flags.turbo-mode.errorStrategy=DEFAULT_VALUE",
                "openfeature.flags.turbo-mode.defaultValue=false"
            ))
            .run(context -> {
                waitForIzanami(context);

                IzanamiService service = context.getBean(IzanamiService.class);
                // Request a flag name that is NOT in openfeature.flags configuration
                ResultValueWithDetails<String> result = service.forFlagName("not-configured-name").stringValueDetails().join();

                assertThat(result.value()).isEqualTo("");  // default for string
                assertThat(result.metadata().get(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                    .isEqualTo("APPLICATION_ERROR_STRATEGY");
                assertThat(result.metadata().get(FlagMetadataKeys.FLAG_EVALUATION_REASON))
                    .isEqualTo("FLAG_NOT_FOUND");
            });
    }

    @Test
    void forFlagKeys_withMissingFlag_includesMissingWithFlagNotFound() {
        contextRunner
            .withPropertyValues(withFlagConfig(
                "openfeature.flags.turbo-mode.key=" + TURBO_MODE_ID,
                                "openfeature.flags.turbo-mode.description=Enable turbo mode",
                "openfeature.flags.turbo-mode.valueType=boolean",
                "openfeature.flags.turbo-mode.errorStrategy=DEFAULT_VALUE",
                "openfeature.flags.turbo-mode.defaultValue=false"
            ))
            .run(context -> {
                waitForIzanami(context);

                IzanamiService service = context.getBean(IzanamiService.class);
                // Request both configured and non-configured flag keys
                var batchResult = service.forFlagKeys(TURBO_MODE_ID, "not-configured-key").values().join();

                // Configured flag should work normally
                assertThat(batchResult.booleanValue(TURBO_MODE_ID)).isTrue();
                assertThat(batchResult.booleanValueDetails(TURBO_MODE_ID).metadata().get(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                    .isEqualTo("IZANAMI");

                // Missing flag should return defaults with FLAG_NOT_FOUND
                assertThat(batchResult.hasFlag("not-configured-key")).isTrue();
                ResultValueWithDetails<Boolean> missingResult = batchResult.booleanValueDetails("not-configured-key");
                assertThat(missingResult.value()).isFalse();
                assertThat(missingResult.metadata().get(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                    .isEqualTo("APPLICATION_ERROR_STRATEGY");
                assertThat(missingResult.metadata().get(FlagMetadataKeys.FLAG_EVALUATION_REASON))
                    .isEqualTo("FLAG_NOT_FOUND");
            });
    }

    @Test
    void forFlagNames_withMissingFlag_includesMissingWithFlagNotFound() {
        contextRunner
            .withPropertyValues(withFlagConfig(
                "openfeature.flags.secret-codename.key=" + SECRET_CODENAME_ID,
                                "openfeature.flags.secret-codename.description=The secret codename",
                "openfeature.flags.secret-codename.valueType=string",
                "openfeature.flags.secret-codename.errorStrategy=DEFAULT_VALUE",
                "openfeature.flags.secret-codename.defaultValue=classified"
            ))
            .run(context -> {
                waitForIzanami(context);

                IzanamiService service = context.getBean(IzanamiService.class);
                // Request both configured and non-configured flag names
                var batchResult = service.forFlagNames("secret-codename", "not-configured-name").values().join();

                // Configured flag should work normally
                assertThat(batchResult.stringValue("secret-codename")).isEqualTo("Operation Thunderbolt");
                assertThat(batchResult.stringValueDetails("secret-codename").metadata().get(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                    .isEqualTo("IZANAMI");

                // Missing flag should return defaults with FLAG_NOT_FOUND
                assertThat(batchResult.hasFlag("not-configured-name")).isTrue();
                ResultValueWithDetails<String> missingResult = batchResult.stringValueDetails("not-configured-name");
                assertThat(missingResult.value()).isEqualTo("");
                assertThat(missingResult.metadata().get(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                    .isEqualTo("APPLICATION_ERROR_STRATEGY");
                assertThat(missingResult.metadata().get(FlagMetadataKeys.FLAG_EVALUATION_REASON))
                    .isEqualTo("FLAG_NOT_FOUND");
            });
    }

    // ========== Error strategy tests (FAIL, NULL_VALUE, CALLBACK) ==========

    @Test
    void throwsWhenServerUnavailableWithFailStrategy() {
        contextRunner
            .withPropertyValues(withUnavailableServerAndFlagConfig(
                "openfeature.flags.secret-codename.key=" + SECRET_CODENAME_ID,
                                "openfeature.flags.secret-codename.description=The secret codename",
                "openfeature.flags.secret-codename.valueType=string",
                "openfeature.flags.secret-codename.errorStrategy=FAIL"
            ))
            .run(context -> {
                IzanamiService service = context.getBean(IzanamiService.class);

                assertThatThrownBy(() -> service.forFlagKey(SECRET_CODENAME_ID).stringValue().join())
                    .isInstanceOf(CompletionException.class);
            });
    }

    @Test
    void returnsNullWhenServerUnavailableWithNullValueStrategy() {
        contextRunner
            .withPropertyValues(withUnavailableServerAndFlagConfig(
                "openfeature.flags.secret-codename.key=" + SECRET_CODENAME_ID,
                                "openfeature.flags.secret-codename.description=The secret codename",
                "openfeature.flags.secret-codename.valueType=string",
                "openfeature.flags.secret-codename.errorStrategy=NULL_VALUE"
            ))
            .run(context -> {
                IzanamiService service = context.getBean(IzanamiService.class);
                String value = service.forFlagKey(SECRET_CODENAME_ID).stringValue().join();

                assertThat(value).isNull();
            });
    }

    @Test
    void returnsCallbackValueWhenServerUnavailableWithCallbackStrategy() {
        contextRunner
            .withUserConfiguration(TestCallbackConfiguration.class)
            .withPropertyValues(withUnavailableServerAndFlagConfig(
                "openfeature.flags.secret-codename.key=" + SECRET_CODENAME_ID,
                                "openfeature.flags.secret-codename.description=The secret codename",
                "openfeature.flags.secret-codename.valueType=string",
                "openfeature.flags.secret-codename.errorStrategy=CALLBACK",
                "openfeature.flags.secret-codename.callbackBean=testErrorCallback"
            ))
            .run(context -> {
                IzanamiService service = context.getBean(IzanamiService.class);
                String value = service.forFlagKey(SECRET_CODENAME_ID).stringValue().join();

                assertThat(value).isEqualTo("callback-fallback-value");
            });
    }

    // ========== Inactive (disabled) feature tests ==========

    @Test
    void evaluatesInactiveBooleanFlagAsFalse() {
        contextRunner
            .withPropertyValues(withFlagConfig(
                "openfeature.flags.inactive-bool.key=" + INACTIVE_BOOL_ID,
                                "openfeature.flags.inactive-bool.description=Disabled boolean feature",
                "openfeature.flags.inactive-bool.valueType=boolean",
                "openfeature.flags.inactive-bool.errorStrategy=DEFAULT_VALUE",
                "openfeature.flags.inactive-bool.defaultValue=true"
            ))
            .run(context -> {
                waitForIzanami(context);

                IzanamiService service = context.getBean(IzanamiService.class);
                Boolean value = service.forFlagKey(INACTIVE_BOOL_ID).booleanValue().join();

                // Disabled features evaluate to false regardless of defaultValue
                assertThat(value).isFalse();
            });
    }

    @Test
    void evaluatesInactiveStringFlagReturnsDefaultValue() {
        contextRunner
            .withPropertyValues(withFlagConfig(
                "openfeature.flags.inactive-string.key=" + INACTIVE_STRING_ID,
                                "openfeature.flags.inactive-string.description=Disabled string feature",
                "openfeature.flags.inactive-string.valueType=string",
                "openfeature.flags.inactive-string.errorStrategy=DEFAULT_VALUE",
                "openfeature.flags.inactive-string.defaultValue=fallback"
            ))
            .run(context -> {
                waitForIzanami(context);

                IzanamiService service = context.getBean(IzanamiService.class);
                String value = service.forFlagKey(INACTIVE_STRING_ID).stringValue().join();

                // Disabled non-boolean features return the defaultValue when configured
                assertThat(value).isEqualTo("fallback");
            });
    }

    @Test
    void evaluatesInactiveNumberFlagReturnsDefaultValue() {
        contextRunner
            .withPropertyValues(withFlagConfig(
                "openfeature.flags.inactive-number.key=" + INACTIVE_NUMBER_ID,
                                "openfeature.flags.inactive-number.description=Disabled number feature",
                "openfeature.flags.inactive-number.valueType=integer",
                "openfeature.flags.inactive-number.errorStrategy=DEFAULT_VALUE",
                "openfeature.flags.inactive-number.defaultValue=999"
            ))
            .run(context -> {
                waitForIzanami(context);

                IzanamiService service = context.getBean(IzanamiService.class);
                BigDecimal value = service.forFlagKey(INACTIVE_NUMBER_ID).numberValue().join();

                // Disabled non-boolean features return the defaultValue when configured
                assertThat(value).isEqualByComparingTo(new BigDecimal("999"));
            });
    }

    // ========== ValueDetails integration tests (active features) ==========

    @Test
    void booleanValueDetails_activeFeature_returnsValueWithIzanamiSourceAndUnknownReason() {
        contextRunner
            .withPropertyValues(withFlagConfig(
                "openfeature.flags.turbo-mode.key=" + TURBO_MODE_ID,
                                "openfeature.flags.turbo-mode.description=Enable turbo mode",
                "openfeature.flags.turbo-mode.valueType=boolean",
                "openfeature.flags.turbo-mode.errorStrategy=DEFAULT_VALUE",
                "openfeature.flags.turbo-mode.defaultValue=false"
            ))
            .run(context -> {
                waitForIzanami(context);

                IzanamiService service = context.getBean(IzanamiService.class);
                ResultValueWithDetails<Boolean> result = service.forFlagKey(TURBO_MODE_ID).booleanValueDetails().join();

                assertThat(result.value()).isTrue();
                assertThat(result.metadata().get(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                    .isEqualTo("IZANAMI");
                assertThat(result.metadata().get(FlagMetadataKeys.FLAG_EVALUATION_REASON))
                    .isEqualTo("ORIGIN_OR_CACHE");
            });
    }

    @Test
    void stringValueDetails_activeFeature_returnsValueWithIzanamiSourceAndUnknownReason() {
        contextRunner
            .withPropertyValues(withFlagConfig(
                "openfeature.flags.secret-codename.key=" + SECRET_CODENAME_ID,
                                "openfeature.flags.secret-codename.description=The secret codename",
                "openfeature.flags.secret-codename.valueType=string",
                "openfeature.flags.secret-codename.errorStrategy=DEFAULT_VALUE",
                "openfeature.flags.secret-codename.defaultValue=classified"
            ))
            .run(context -> {
                waitForIzanami(context);

                IzanamiService service = context.getBean(IzanamiService.class);
                ResultValueWithDetails<String> result = service.forFlagKey(SECRET_CODENAME_ID).stringValueDetails().join();

                assertThat(result.value()).isEqualTo("Operation Thunderbolt");
                assertThat(result.metadata().get(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                    .isEqualTo("IZANAMI");
                assertThat(result.metadata().get(FlagMetadataKeys.FLAG_EVALUATION_REASON))
                    .isEqualTo("ORIGIN_OR_CACHE");
            });
    }

    @Test
    void numberValueDetails_activeFeature_returnsValueWithIzanamiSourceAndUnknownReason() {
        contextRunner
            .withPropertyValues(withFlagConfig(
                "openfeature.flags.max-power-level.key=" + MAX_POWER_LEVEL_ID,
                                "openfeature.flags.max-power-level.description=Maximum power level",
                "openfeature.flags.max-power-level.valueType=integer",
                "openfeature.flags.max-power-level.errorStrategy=DEFAULT_VALUE",
                "openfeature.flags.max-power-level.defaultValue=100"
            ))
            .run(context -> {
                waitForIzanami(context);

                IzanamiService service = context.getBean(IzanamiService.class);
                ResultValueWithDetails<BigDecimal> result = service.forFlagKey(MAX_POWER_LEVEL_ID).numberValueDetails().join();

                assertThat(result.value()).isEqualByComparingTo(new BigDecimal("9001"));
                assertThat(result.metadata().get(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                    .isEqualTo("IZANAMI");
                assertThat(result.metadata().get(FlagMetadataKeys.FLAG_EVALUATION_REASON))
                    .isEqualTo("ORIGIN_OR_CACHE");
            });
    }

    // ========== ValueDetails integration tests (inactive/disabled features) ==========

    @Test
    void booleanValueDetails_inactiveFeature_returnsFalseWithIzanamiSourceAndDisabledReason() {
        contextRunner
            .withPropertyValues(withFlagConfig(
                "openfeature.flags.inactive-bool.key=" + INACTIVE_BOOL_ID,
                                "openfeature.flags.inactive-bool.description=Disabled boolean feature",
                "openfeature.flags.inactive-bool.valueType=boolean",
                "openfeature.flags.inactive-bool.errorStrategy=DEFAULT_VALUE",
                "openfeature.flags.inactive-bool.defaultValue=true"
            ))
            .run(context -> {
                waitForIzanami(context);

                IzanamiService service = context.getBean(IzanamiService.class);
                ResultValueWithDetails<Boolean> result = service.forFlagKey(INACTIVE_BOOL_ID).booleanValueDetails().join();

                // Disabled boolean features evaluate to false regardless of defaultValue
                assertThat(result.value()).isFalse();
                assertThat(result.metadata().get(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                    .isEqualTo("IZANAMI");
                assertThat(result.metadata().get(FlagMetadataKeys.FLAG_EVALUATION_REASON))
                    .isEqualTo("DISABLED");
            });
    }

    @Test
    void stringValueDetails_inactiveFeature_returnsDefaultValueWithAppErrorStrategySourceAndDisabledReason() {
        contextRunner
            .withPropertyValues(withFlagConfig(
                "openfeature.flags.inactive-string.key=" + INACTIVE_STRING_ID,
                                "openfeature.flags.inactive-string.description=Disabled string feature",
                "openfeature.flags.inactive-string.valueType=string",
                "openfeature.flags.inactive-string.errorStrategy=DEFAULT_VALUE",
                "openfeature.flags.inactive-string.defaultValue=fallback"
            ))
            .run(context -> {
                waitForIzanami(context);

                IzanamiService service = context.getBean(IzanamiService.class);
                ResultValueWithDetails<String> result = service.forFlagKey(INACTIVE_STRING_ID).stringValueDetails().join();

                // Disabled non-boolean features return the configured defaultValue (NOT null)
                assertThat(result.value()).isEqualTo("fallback");
                assertThat(result.metadata().get(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                    .isEqualTo("APPLICATION_ERROR_STRATEGY");
                assertThat(result.metadata().get(FlagMetadataKeys.FLAG_EVALUATION_REASON))
                    .isEqualTo("DISABLED");
            });
    }

    @Test
    void numberValueDetails_inactiveFeature_returnsDefaultValueWithAppErrorStrategySourceAndDisabledReason() {
        contextRunner
            .withPropertyValues(withFlagConfig(
                "openfeature.flags.inactive-number.key=" + INACTIVE_NUMBER_ID,
                                "openfeature.flags.inactive-number.description=Disabled number feature",
                "openfeature.flags.inactive-number.valueType=integer",
                "openfeature.flags.inactive-number.errorStrategy=DEFAULT_VALUE",
                "openfeature.flags.inactive-number.defaultValue=999"
            ))
            .run(context -> {
                waitForIzanami(context);

                IzanamiService service = context.getBean(IzanamiService.class);
                ResultValueWithDetails<BigDecimal> result = service.forFlagKey(INACTIVE_NUMBER_ID).numberValueDetails().join();

                // Disabled non-boolean features return the configured defaultValue (NOT null)
                assertThat(result.value()).isEqualByComparingTo(new BigDecimal("999"));
                assertThat(result.metadata().get(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                    .isEqualTo("APPLICATION_ERROR_STRATEGY");
                assertThat(result.metadata().get(FlagMetadataKeys.FLAG_EVALUATION_REASON))
                    .isEqualTo("DISABLED");
            });
    }

    // ========== ValueDetails unavailable server fallback tests ==========

    @Test
    void booleanValueDetails_serverUnavailable_returnsDefaultWithErrorStrategySourceAndErrorReason() {
        contextRunner
            .withPropertyValues(withUnavailableServerAndFlagConfig(
                "openfeature.flags.turbo-mode.key=" + TURBO_MODE_ID,
                                "openfeature.flags.turbo-mode.description=Enable turbo mode",
                "openfeature.flags.turbo-mode.valueType=boolean",
                "openfeature.flags.turbo-mode.errorStrategy=DEFAULT_VALUE",
                "openfeature.flags.turbo-mode.defaultValue=false"
            ))
            .run(context -> {
                IzanamiService service = context.getBean(IzanamiService.class);
                ResultValueWithDetails<Boolean> result = service.forFlagKey(TURBO_MODE_ID).booleanValueDetails().join();

                assertThat(result.value()).isFalse();
                assertThat(result.metadata().get(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                    .isEqualTo("IZANAMI_ERROR_STRATEGY");
                assertThat(result.metadata().get(FlagMetadataKeys.FLAG_EVALUATION_REASON))
                    .isEqualTo("ERROR");
            });
    }

    @Test
    void stringValueDetails_serverUnavailable_returnsDefaultWithErrorStrategySourceAndErrorReason() {
        contextRunner
            .withPropertyValues(withUnavailableServerAndFlagConfig(
                "openfeature.flags.secret-codename.key=" + SECRET_CODENAME_ID,
                                "openfeature.flags.secret-codename.description=The secret codename",
                "openfeature.flags.secret-codename.valueType=string",
                "openfeature.flags.secret-codename.errorStrategy=DEFAULT_VALUE",
                "openfeature.flags.secret-codename.defaultValue=classified"
            ))
            .run(context -> {
                IzanamiService service = context.getBean(IzanamiService.class);
                ResultValueWithDetails<String> result = service.forFlagKey(SECRET_CODENAME_ID).stringValueDetails().join();

                assertThat(result.value()).isEqualTo("classified");
                assertThat(result.metadata().get(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                    .isEqualTo("IZANAMI_ERROR_STRATEGY");
                assertThat(result.metadata().get(FlagMetadataKeys.FLAG_EVALUATION_REASON))
                    .isEqualTo("ERROR");
            });
    }

    @Test
    void numberValueDetails_serverUnavailable_returnsDefaultWithErrorStrategySourceAndErrorReason() {
        contextRunner
            .withPropertyValues(withUnavailableServerAndFlagConfig(
                "openfeature.flags.max-power-level.key=" + MAX_POWER_LEVEL_ID,
                                "openfeature.flags.max-power-level.description=Maximum power level",
                "openfeature.flags.max-power-level.valueType=integer",
                "openfeature.flags.max-power-level.errorStrategy=DEFAULT_VALUE",
                "openfeature.flags.max-power-level.defaultValue=100"
            ))
            .run(context -> {
                IzanamiService service = context.getBean(IzanamiService.class);
                ResultValueWithDetails<BigDecimal> result = service.forFlagKey(MAX_POWER_LEVEL_ID).numberValueDetails().join();

                assertThat(result.value()).isEqualByComparingTo(new BigDecimal("100"));
                assertThat(result.metadata().get(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                    .isEqualTo("IZANAMI_ERROR_STRATEGY");
                assertThat(result.metadata().get(FlagMetadataKeys.FLAG_EVALUATION_REASON))
                    .isEqualTo("ERROR");
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

    // ========== Fluent API with user and context parameters ==========

    @Test
    void evaluatesFlagWithUserContext() {
        contextRunner
            .withPropertyValues(withFlagConfig(
                "openfeature.flags.turbo-mode.key=" + TURBO_MODE_ID,
                                "openfeature.flags.turbo-mode.description=Enable turbo mode",
                "openfeature.flags.turbo-mode.valueType=boolean",
                "openfeature.flags.turbo-mode.errorStrategy=DEFAULT_VALUE",
                "openfeature.flags.turbo-mode.defaultValue=false"
            ))
            .run(context -> {
                waitForIzanami(context);

                IzanamiService service = context.getBean(IzanamiService.class);
                Boolean value = service.forFlagKey(TURBO_MODE_ID)
                    .withUser("test-user-123")
                    .booleanValue()
                    .join();

                // The flag value is still true (user context is passed but doesn't affect this simple flag)
                assertThat(value).isTrue();
            });
    }

    @Test
    void evaluatesFlagWithEnvironmentContext() {
        contextRunner
            .withPropertyValues(withFlagConfig(
                "openfeature.flags.turbo-mode.key=" + TURBO_MODE_ID,
                                "openfeature.flags.turbo-mode.description=Enable turbo mode",
                "openfeature.flags.turbo-mode.valueType=boolean",
                "openfeature.flags.turbo-mode.errorStrategy=DEFAULT_VALUE",
                "openfeature.flags.turbo-mode.defaultValue=false"
            ))
            .run(context -> {
                waitForIzanami(context);

                IzanamiService service = context.getBean(IzanamiService.class);
                Boolean value = service.forFlagKey(TURBO_MODE_ID)
                    .withContext("production")
                    .booleanValue()
                    .join();

                assertThat(value).isTrue();
            });
    }

    @Test
    void evaluatesFlagWithUserAndContext() {
        contextRunner
            .withPropertyValues(withFlagConfig(
                "openfeature.flags.turbo-mode.key=" + TURBO_MODE_ID,
                                "openfeature.flags.turbo-mode.description=Enable turbo mode",
                "openfeature.flags.turbo-mode.valueType=boolean",
                "openfeature.flags.turbo-mode.errorStrategy=DEFAULT_VALUE",
                "openfeature.flags.turbo-mode.defaultValue=false"
            ))
            .run(context -> {
                waitForIzanami(context);

                IzanamiService service = context.getBean(IzanamiService.class);
                Boolean value = service.forFlagKey(TURBO_MODE_ID)
                    .withUser("test-user-456")
                    .withContext("staging")
                    .booleanValue()
                    .join();

                assertThat(value).isTrue();
            });
    }

    @Test
    void evaluatesFlagWithPayload() {
        contextRunner
            .withPropertyValues(withFlagConfig(
                "openfeature.flags.turbo-mode.key=" + TURBO_MODE_ID,
                                "openfeature.flags.turbo-mode.description=Enable turbo mode",
                "openfeature.flags.turbo-mode.valueType=boolean",
                "openfeature.flags.turbo-mode.errorStrategy=DEFAULT_VALUE",
                "openfeature.flags.turbo-mode.defaultValue=false"
            ))
            .run(context -> {
                waitForIzanami(context);

                IzanamiService service = context.getBean(IzanamiService.class);
                Boolean value = service.forFlagKey(TURBO_MODE_ID)
                    .withPayload("{\"version\": \"1.0\"}")
                    .booleanValue()
                    .join();

                assertThat(value).isTrue();
            });
    }

    @Test
    void evaluatesFlagWithAllBuilderOptions() {
        contextRunner
            .withPropertyValues(withFlagConfig(
                "openfeature.flags.secret-codename.key=" + SECRET_CODENAME_ID,
                                "openfeature.flags.secret-codename.description=The secret codename",
                "openfeature.flags.secret-codename.valueType=string",
                "openfeature.flags.secret-codename.errorStrategy=DEFAULT_VALUE",
                "openfeature.flags.secret-codename.defaultValue=classified"
            ))
            .run(context -> {
                waitForIzanami(context);

                IzanamiService service = context.getBean(IzanamiService.class);
                ResultValueWithDetails<String> result = service.forFlagKey(SECRET_CODENAME_ID)
                    .withUser("test-user")
                    .withContext("production")
                    .withPayload("{\"region\": \"EU\"}")
                    .ignoreCache(true)
                    .stringValueDetails()
                    .join();

                assertThat(result.value()).isEqualTo("Operation Thunderbolt");
                assertThat(result.metadata().get(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                    .isEqualTo("IZANAMI");
            });
    }

    // ========== Batch API with user and context parameters ==========

    @Test
    void evaluatesBatchFlagsWithUserContext() {
        contextRunner
            .withPropertyValues(withFlagConfig(
                "openfeature.flags.turbo-mode.key=" + TURBO_MODE_ID,
                                "openfeature.flags.turbo-mode.valueType=boolean",
                "openfeature.flags.turbo-mode.errorStrategy=DEFAULT_VALUE",
                "openfeature.flags.turbo-mode.defaultValue=false",
                "openfeature.flags.secret-codename.key=" + SECRET_CODENAME_ID,
                                "openfeature.flags.secret-codename.valueType=string",
                "openfeature.flags.secret-codename.errorStrategy=DEFAULT_VALUE",
                "openfeature.flags.secret-codename.defaultValue=default"
            ))
            .run(context -> {
                waitForIzanami(context);

                IzanamiService service = context.getBean(IzanamiService.class);
                var result = service.forFlagKeys(TURBO_MODE_ID, SECRET_CODENAME_ID)
                    .withUser("batch-test-user")
                    .withContext("testing")
                    .values()
                    .join();

                assertThat(result.booleanValue(TURBO_MODE_ID)).isTrue();
                assertThat(result.stringValue(SECRET_CODENAME_ID)).isEqualTo("Operation Thunderbolt");
            });
    }

    @Test
    void evaluatesBatchFlagsWithIgnoreCache() {
        contextRunner
            .withPropertyValues(withFlagConfig(
                "openfeature.flags.turbo-mode.key=" + TURBO_MODE_ID,
                                "openfeature.flags.turbo-mode.valueType=boolean",
                "openfeature.flags.turbo-mode.errorStrategy=DEFAULT_VALUE",
                "openfeature.flags.turbo-mode.defaultValue=false"
            ))
            .run(context -> {
                waitForIzanami(context);

                IzanamiService service = context.getBean(IzanamiService.class);
                var result = service.forFlagKeys(TURBO_MODE_ID)
                    .ignoreCache(true)
                    .values()
                    .join();

                // Should still return the correct value when ignoring cache
                assertThat(result.booleanValue(TURBO_MODE_ID)).isTrue();
                assertThat(result.booleanValueDetails(TURBO_MODE_ID).metadata().get(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                    .isEqualTo("IZANAMI");
            });
    }
}
