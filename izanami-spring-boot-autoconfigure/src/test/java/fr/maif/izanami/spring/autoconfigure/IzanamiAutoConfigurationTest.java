package fr.maif.izanami.spring.autoconfigure;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.FlagValueType;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.Value;
import fr.maif.izanami.spring.openfeature.ErrorStrategy;
import fr.maif.izanami.spring.openfeature.FlagMetadataKeys;
import fr.maif.izanami.spring.openfeature.FlagsProperties;
import fr.maif.izanami.spring.openfeature.IzanamiFeatureProvider;
import fr.maif.izanami.spring.openfeature.api.ErrorStrategyFactory;
import fr.maif.izanami.spring.openfeature.api.FlagConfigService;
import fr.maif.izanami.spring.service.IzanamiService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for auto-configuration using {@link ApplicationContextRunner}.
 */
class IzanamiAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            IzanamiAutoConfiguration.class,
            OpenFeatureAutoConfiguration.class
        ));

    @Test
    void createsBeansWhenEnabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(IzanamiProperties.class);
            assertThat(context).hasSingleBean(FlagsProperties.class);
            assertThat(context).hasSingleBean(FlagConfigService.class);
            assertThat(context).hasSingleBean(ErrorStrategyFactory.class);
            assertThat(context).hasSingleBean(IzanamiService.class);

            assertThat(context).hasBean("stringToFlagValueTypeConverter");
            assertThat(context).hasBean("stringToErrorStrategyConverter");

            assertThat(context).hasSingleBean(IzanamiFeatureProvider.class);
            assertThat(context).hasSingleBean(OpenFeatureAPI.class);
            assertThat(context).hasSingleBean(Client.class);

            OpenFeatureAPI api = context.getBean(OpenFeatureAPI.class);
            assertThat(api.getProviderMetadata().getName()).isEqualTo("Izanami (Spring Boot Starter)");
        });
    }

    @Test
    void doesNotCreateBeansWhenDisabled() {
        contextRunner
            .withPropertyValues("izanami.enabled=false")
            .run(context -> {
                assertThat(context).doesNotHaveBean(IzanamiProperties.class);
                assertThat(context).doesNotHaveBean(FlagsProperties.class);
                assertThat(context).doesNotHaveBean(FlagConfigService.class);
                assertThat(context).doesNotHaveBean(ErrorStrategyFactory.class);
                assertThat(context).doesNotHaveBean(IzanamiService.class);
                assertThat(context).doesNotHaveBean(IzanamiFeatureProvider.class);
                assertThat(context).doesNotHaveBean(OpenFeatureAPI.class);
                assertThat(context).doesNotHaveBean(Client.class);
            });
    }

    @Test
    void bindsValueTypeAndErrorStrategyUsingConverters() {
        contextRunner
            .withPropertyValues(
                "openfeature.flags[0].id=0c1774d1-9a26-4284-b8a6-0179eb7cf2f7",
                "openfeature.flags[0].name=new-dashboard",
                "openfeature.flags[0].description=Test flag",
                "openfeature.flags[0].valueType=object",
                "openfeature.flags[0].errorStrategy=DEFAULT_VALUE",
                "openfeature.flags[0].defaultValue.name=Izanami"
            )
            .run(context -> {
                FlagsProperties flagsProperties = context.getBean(FlagsProperties.class);
                assertThat(flagsProperties.flags()).hasSize(1);
                assertThat(flagsProperties.flags().get(0).valueType()).isEqualTo(FlagValueType.OBJECT);
                assertThat(flagsProperties.flags().get(0).errorStrategy()).isEqualTo(ErrorStrategy.DEFAULT_VALUE);
            });
    }

    @Test
    void bindsObjectDefaultValueAndReturnsStructuredOpenFeatureValueOnFallback() {
        contextRunner
            .withPropertyValues(
                "openfeature.flags[0].id=00812ba5-aebc-49e8-959a-4b96a5cebbff",
                "openfeature.flags[0].name=json-content",
                "openfeature.flags[0].description=JSON content",
                "openfeature.flags[0].valueType=object",
                "openfeature.flags[0].errorStrategy=DEFAULT_VALUE",
                "openfeature.flags[0].defaultValue.name=Izanami",
                "openfeature.flags[0].defaultValue.flags[0].id=f1",
                "openfeature.flags[0].defaultValue.flags[0].active=true",
                "openfeature.flags[0].defaultValue.flags[1].id=f2",
                "openfeature.flags[0].defaultValue.flags[1].active=false",
                "openfeature.flags[0].defaultValue.meta.createdBy=unit-test",
                "openfeature.flags[0].defaultValue.meta.version=1"
            )
            .run(context -> {
                FlagsProperties flagsProperties = context.getBean(FlagsProperties.class);
                assertThat(flagsProperties.flags()).hasSize(1);
                assertThat(flagsProperties.flags().get(0).valueType()).isEqualTo(FlagValueType.OBJECT);
                assertThat(flagsProperties.flags().get(0).defaultValue()).isInstanceOfAny(java.util.Map.class, java.util.List.class);

                IzanamiFeatureProvider provider = context.getBean(IzanamiFeatureProvider.class);
                assertThat(provider.getObjectEvaluation("json-content", new Value("caller-default"), null).getValue().isStructure()).isTrue();

                Client client = context.getBean(Client.class);

                FlagEvaluationDetails<Value> details = client.getObjectDetails(
                    "json-content",
                    new Value("caller-default")
                );

                assertThat(details.getValue()).isNotNull();
                assertThat(details.getValue().isStructure()).isTrue();
                assertThat(details.getValue().asStructure().getValue("name").asString()).isEqualTo("Izanami");
                assertThat(details.getValue().asStructure().getValue("flags").asList()).hasSize(2);

                assertThat(details.getFlagMetadata().getString(FlagMetadataKeys.FLAG_EVALUATION_VALUE_SOURCE))
                    .isEqualTo("APPLICATION_ERROR_STRATEGY");
            });
    }
}
