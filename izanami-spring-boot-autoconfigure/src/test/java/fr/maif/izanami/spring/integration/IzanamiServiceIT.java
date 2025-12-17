package fr.maif.izanami.spring.integration;

import fr.maif.izanami.spring.service.IzanamiService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link IzanamiService} running against a real Izanami server.
 */
@EnabledIfEnvironmentVariable(named = "IZANAMI_INTEGRATION_TEST", matches = "true")
class IzanamiServiceIT extends BaseIzanamiIT {

    // ========== By Key integration tests ==========

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

                IzanamiService service = context.getBean(IzanamiService.class);
                Boolean value = service.forFlagKey(TURBO_MODE_ID).booleanValue().join();

                assertThat(value).isTrue();
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

                IzanamiService service = context.getBean(IzanamiService.class);
                String value = service.forFlagKey(SECRET_CODENAME_ID).stringValue().join();

                assertThat(value).isEqualTo("Operation Thunderbolt");
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

                IzanamiService service = context.getBean(IzanamiService.class);
                BigDecimal value = service.forFlagKey(MAX_POWER_LEVEL_ID).numberValue().join();

                assertThat(value).isEqualByComparingTo(new BigDecimal("9001"));
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
                "openfeature.flags[0].key=" + TURBO_MODE_ID,
                "openfeature.flags[0].name=turbo-mode",
                "openfeature.flags[0].description=Enable turbo mode",
                "openfeature.flags[0].valueType=boolean",
                "openfeature.flags[0].errorStrategy=DEFAULT_VALUE",
                "openfeature.flags[0].defaultValue=false"
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
                "openfeature.flags[0].key=" + SECRET_CODENAME_ID,
                "openfeature.flags[0].name=secret-codename",
                "openfeature.flags[0].description=The secret codename",
                "openfeature.flags[0].valueType=string",
                "openfeature.flags[0].errorStrategy=DEFAULT_VALUE",
                "openfeature.flags[0].defaultValue=classified"
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
                "openfeature.flags[0].key=" + MAX_POWER_LEVEL_ID,
                "openfeature.flags[0].name=max-power-level",
                "openfeature.flags[0].description=Maximum power level",
                "openfeature.flags[0].valueType=integer",
                "openfeature.flags[0].errorStrategy=DEFAULT_VALUE",
                "openfeature.flags[0].defaultValue=100"
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
                "openfeature.flags[0].key=" + DISCOUNT_RATE_ID,
                "openfeature.flags[0].name=discount-rate",
                "openfeature.flags[0].description=Current discount rate",
                "openfeature.flags[0].valueType=double",
                "openfeature.flags[0].errorStrategy=DEFAULT_VALUE",
                "openfeature.flags[0].defaultValue=0.0"
            ))
            .run(context -> {
                waitForIzanami(context);

                IzanamiService service = context.getBean(IzanamiService.class);
                BigDecimal value = service.forFlagName("discount-rate").numberValue().join();

                assertThat(value).isEqualByComparingTo(new BigDecimal("0.15"));
            });
    }
}
