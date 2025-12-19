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
                    "openfeature.flags[0].key=" + TURBO_MODE_ID,
                    "openfeature.flags[0].name=turbo-mode",
                    "openfeature.flags[0].valueType=boolean",
                    "openfeature.flags[0].errorStrategy=DEFAULT_VALUE",
                    "openfeature.flags[0].defaultValue=false",
                    "openfeature.flags[1].key=" + SECRET_CODENAME_ID,
                    "openfeature.flags[1].name=secret-codename",
                    "openfeature.flags[1].valueType=string",
                    "openfeature.flags[1].errorStrategy=DEFAULT_VALUE",
                    "openfeature.flags[1].defaultValue=default",
                    "openfeature.flags[2].key=" + MAX_POWER_LEVEL_ID,
                    "openfeature.flags[2].name=max-power-level",
                    "openfeature.flags[2].valueType=integer",
                    "openfeature.flags[2].errorStrategy=DEFAULT_VALUE",
                    "openfeature.flags[2].defaultValue=0"
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
                    "openfeature.flags[0].key=" + TURBO_MODE_ID,
                    "openfeature.flags[0].name=turbo-mode",
                    "openfeature.flags[0].valueType=boolean",
                    "openfeature.flags[0].errorStrategy=DEFAULT_VALUE",
                    "openfeature.flags[0].defaultValue=false"
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
    }

    @Nested
    class BatchEvaluationByName {

        @Test
        void evaluatesMultipleFlagsByName() {
            contextRunner
                .withPropertyValues(withFlagConfig(
                    "openfeature.flags[0].key=" + TURBO_MODE_ID,
                    "openfeature.flags[0].name=turbo-mode",
                    "openfeature.flags[0].valueType=boolean",
                    "openfeature.flags[0].errorStrategy=DEFAULT_VALUE",
                    "openfeature.flags[0].defaultValue=false",
                    "openfeature.flags[1].key=" + SECRET_CODENAME_ID,
                    "openfeature.flags[1].name=secret-codename",
                    "openfeature.flags[1].valueType=string",
                    "openfeature.flags[1].errorStrategy=DEFAULT_VALUE",
                    "openfeature.flags[1].defaultValue=default"
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
                    "openfeature.flags[0].key=" + INACTIVE_STRING_ID,
                    "openfeature.flags[0].name=inactive-string",
                    "openfeature.flags[0].valueType=string",
                    "openfeature.flags[0].errorStrategy=DEFAULT_VALUE",
                    "openfeature.flags[0].defaultValue=fallback-value"
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
                    "openfeature.flags[0].key=" + INACTIVE_NUMBER_ID,
                    "openfeature.flags[0].name=inactive-number",
                    "openfeature.flags[0].valueType=integer",
                    "openfeature.flags[0].errorStrategy=DEFAULT_VALUE",
                    "openfeature.flags[0].defaultValue=999"
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
