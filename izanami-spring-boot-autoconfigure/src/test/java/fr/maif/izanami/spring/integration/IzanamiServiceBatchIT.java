package fr.maif.izanami.spring.integration;

import fr.maif.izanami.spring.openfeature.FlagMetadataKeys;
import fr.maif.izanami.spring.openfeature.FlagValueSource;
import fr.maif.izanami.spring.service.api.BatchResult;
import fr.maif.izanami.spring.service.api.IzanamiService;
import fr.maif.izanami.spring.service.api.ResultValueWithDetails;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for batch feature evaluation.
 * <p>
 * Requires Izanami server running with seeded data.
 * Run {@code ./scripts/seed-izanami.sh} before these tests.
 */
@EnabledIfEnvironmentVariable(named = "IZANAMI_INTEGRATION_TEST", matches = "true")
class IzanamiServiceBatchIT extends BaseIzanamiIT {

    @Nested
    class BatchEvaluationByKey {

        @Test
        void evaluatesMultipleFlagsInSingleRequest() {
            contextRunner
                .withPropertyValues(withFlagConfig(
                    "openfeature.flags.turbo-mode.key=" + TURBO_MODE_ID,
                                        "openfeature.flags.turbo-mode.valueType=boolean",
                    "openfeature.flags.turbo-mode.errorStrategy=DEFAULT_VALUE",
                    "openfeature.flags.turbo-mode.defaultValue=false",
                    "openfeature.flags.secret-codename.key=" + SECRET_CODENAME_ID,
                                        "openfeature.flags.secret-codename.valueType=string",
                    "openfeature.flags.secret-codename.errorStrategy=DEFAULT_VALUE",
                    "openfeature.flags.secret-codename.defaultValue=default",
                    "openfeature.flags.max-power-level.key=" + MAX_POWER_LEVEL_ID,
                                        "openfeature.flags.max-power-level.valueType=integer",
                    "openfeature.flags.max-power-level.errorStrategy=DEFAULT_VALUE",
                    "openfeature.flags.max-power-level.defaultValue=0"
                ))
                .run(context -> {
                    waitForIzanami(context);
                    IzanamiService service = context.getBean(IzanamiService.class);

                    BatchResult result = service.forFlagKeys(TURBO_MODE_ID, SECRET_CODENAME_ID, MAX_POWER_LEVEL_ID)
                        .values()
                        .join();

                    assertThat(result.booleanValue(TURBO_MODE_ID)).isTrue();
                    assertThat(result.stringValue(SECRET_CODENAME_ID)).isEqualTo("Operation Thunderbolt");
                    assertThat(result.numberValue(MAX_POWER_LEVEL_ID)).isEqualTo(BigDecimal.valueOf(9001));
                });
        }

        @Test
        void includesMetadataInDetails() {
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

                    BatchResult result = service.forFlagKeys(TURBO_MODE_ID)
                        .values()
                        .join();

                    ResultValueWithDetails<Boolean> details = result.booleanValueDetails(TURBO_MODE_ID);
                    assertThat(details.value()).isTrue();
                    assertThat(details.metadata().get(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                        .isEqualTo(FlagValueSource.IZANAMI.name());
                    assertThat(details.metadata().get(FlagMetadataKeys.FLAG_EVALUATION_REASON))
                        .isEqualTo("ORIGIN_OR_CACHE");
                });
        }

        @Test
        void unknownFlagIdReturnsFlagNotFoundDefaults() {
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

                    BatchResult result = service.forFlagKeys(TURBO_MODE_ID)
                        .values()
                        .join();

                    assertThat(result.booleanValue("unknown-flag")).isFalse();
                    assertThat(result.stringValue("unknown-flag")).isEqualTo("");
                    assertThat(result.numberValue("unknown-flag")).isEqualTo(BigDecimal.ZERO);

                    ResultValueWithDetails<Boolean> details = result.booleanValueDetails("unknown-flag");
                    assertThat(details.metadata().get(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                        .isEqualTo(FlagValueSource.APPLICATION_ERROR_STRATEGY.name());
                    assertThat(details.metadata().get(FlagMetadataKeys.FLAG_EVALUATION_REASON))
                        .isEqualTo("FLAG_NOT_FOUND");
                });
        }

        @Test
        void unknownFlagKeyReturnsFlagNotFoundDefaults() {
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

                    BatchResult result = service.forFlagKeys(TURBO_MODE_ID, "missing-flag-key")
                        .values()
                        .join();

                    assertThat(result.booleanValue("missing-flag-key")).isFalse();
                    assertThat(result.stringValue("missing-flag-key")).isEqualTo("");
                    assertThat(result.numberValue("missing-flag-key")).isEqualTo(BigDecimal.ZERO);

                    ResultValueWithDetails<Boolean> details = result.booleanValueDetails("missing-flag-key");
                    assertThat(details.value()).isFalse();
                    assertThat(details.metadata().get(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                        .isEqualTo(FlagValueSource.APPLICATION_ERROR_STRATEGY.name());
                    assertThat(details.metadata().get(FlagMetadataKeys.FLAG_EVALUATION_REASON))
                        .isEqualTo("FLAG_NOT_FOUND");
                });
        }
    }

    @Nested
    class BatchEvaluationByName {

        @Test
        void evaluatesMultipleFlagsByName() {
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

                    BatchResult result = service.forFlagNames("turbo-mode", "secret-codename")
                        .values()
                        .join();

                    // Results accessible by name
                    assertThat(result.booleanValue("turbo-mode")).isTrue();
                    assertThat(result.stringValue("secret-codename")).isEqualTo("Operation Thunderbolt");
                    assertThat(result.hasFlag("turbo-mode")).isTrue();
                    assertThat(result.hasFlag(TURBO_MODE_ID)).isFalse();  // Key should not work
                });
        }
    }

    @Nested
    class DisabledFeatures {

        @Test
        void disabledStringFeatureReturnsConfiguredDefault() {
            contextRunner
                .withPropertyValues(withFlagConfig(
                    "openfeature.flags.inactive-string.key=" + INACTIVE_STRING_ID,
                                        "openfeature.flags.inactive-string.valueType=string",
                    "openfeature.flags.inactive-string.errorStrategy=DEFAULT_VALUE",
                    "openfeature.flags.inactive-string.defaultValue=fallback-value"
                ))
                .run(context -> {
                    waitForIzanami(context);
                    IzanamiService service = context.getBean(IzanamiService.class);

                    BatchResult result = service.forFlagKeys(INACTIVE_STRING_ID)
                        .values()
                        .join();

                    assertThat(result.stringValue(INACTIVE_STRING_ID)).isEqualTo("fallback-value");

                    ResultValueWithDetails<String> details = result.stringValueDetails(INACTIVE_STRING_ID);
                    assertThat(details.metadata().get(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                        .isEqualTo(FlagValueSource.APPLICATION_ERROR_STRATEGY.name());
                    assertThat(details.metadata().get(FlagMetadataKeys.FLAG_EVALUATION_REASON))
                        .isEqualTo("DISABLED");
                });
        }

        @Test
        void disabledNumberFeatureReturnsConfiguredDefault() {
            contextRunner
                .withPropertyValues(withFlagConfig(
                    "openfeature.flags.inactive-number.key=" + INACTIVE_NUMBER_ID,
                                        "openfeature.flags.inactive-number.valueType=integer",
                    "openfeature.flags.inactive-number.errorStrategy=DEFAULT_VALUE",
                    "openfeature.flags.inactive-number.defaultValue=999"
                ))
                .run(context -> {
                    waitForIzanami(context);
                    IzanamiService service = context.getBean(IzanamiService.class);

                    BatchResult result = service.forFlagKeys(INACTIVE_NUMBER_ID)
                        .values()
                        .join();

                    assertThat(result.numberValue(INACTIVE_NUMBER_ID)).isEqualTo(BigDecimal.valueOf(999));

                    ResultValueWithDetails<BigDecimal> details = result.numberValueDetails(INACTIVE_NUMBER_ID);
                    assertThat(details.metadata().get(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                        .isEqualTo(FlagValueSource.APPLICATION_ERROR_STRATEGY.name());
                    assertThat(details.metadata().get(FlagMetadataKeys.FLAG_EVALUATION_REASON))
                        .isEqualTo("DISABLED");
                });
        }
    }
}
