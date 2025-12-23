package fr.maif.izanami.spring.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfeature.sdk.FlagValueType;
import fr.maif.FeatureClientErrorStrategy;
import fr.maif.features.results.IzanamiResult;
import fr.maif.features.values.BooleanCastStrategy;
import fr.maif.features.values.FeatureValue;
import fr.maif.izanami.spring.openfeature.ErrorStrategy;
import fr.maif.izanami.spring.openfeature.FlagConfig;
import fr.maif.izanami.spring.openfeature.FlagMetadataKeys;
import fr.maif.izanami.spring.openfeature.FlagValueSource;
import fr.maif.izanami.spring.service.api.ResultValueWithDetails;
import fr.maif.requests.FeatureRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link IzanamiEvaluationHelper}.
 */
class IzanamiEvaluationHelperTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Nested
    class ComputeOutcome {

        @Test
        void computeOutcome_success_notDisabled_returnsIzanamiSource() {
            FeatureValue featureValue = mock(FeatureValue.class);
            when(featureValue.booleanValue(any(BooleanCastStrategy.class))).thenReturn(true);
            IzanamiResult.Success success = new IzanamiResult.Success(featureValue);

            IzanamiEvaluationHelper.EvaluationOutcome<Boolean> outcome =
                IzanamiEvaluationHelper.computeOutcome(success, true, () -> false, Boolean.FALSE::equals);

            assertThat(outcome.value()).isTrue();
            assertThat(outcome.source()).isEqualTo(FlagValueSource.IZANAMI);
            assertThat(outcome.reason()).isEqualTo("ORIGIN_OR_CACHE");
        }

        @Test
        void computeOutcome_success_disabled_withDefaultValue_returnsAppErrorStrategy() {
            FeatureValue featureValue = mock(FeatureValue.class);
            when(featureValue.booleanValue(any(BooleanCastStrategy.class))).thenReturn(false);
            IzanamiResult.Success success = new IzanamiResult.Success(featureValue);

            IzanamiEvaluationHelper.EvaluationOutcome<Boolean> outcome =
                IzanamiEvaluationHelper.computeOutcome(success, false, () -> true, Boolean.FALSE::equals);

            assertThat(outcome.value()).isTrue();
            assertThat(outcome.source()).isEqualTo(FlagValueSource.APPLICATION_ERROR_STRATEGY);
            assertThat(outcome.reason()).isEqualTo("DISABLED");
        }

        @Test
        void computeOutcome_success_disabled_noDefaultValue_returnsRawValue() {
            FeatureValue featureValue = mock(FeatureValue.class);
            IzanamiResult.Success success = new IzanamiResult.Success(featureValue);

            IzanamiEvaluationHelper.EvaluationOutcome<String> outcome =
                IzanamiEvaluationHelper.computeOutcome(success, null, () -> null, str -> str == null);

            assertThat(outcome.value()).isNull();
            assertThat(outcome.source()).isEqualTo(FlagValueSource.IZANAMI);
            assertThat(outcome.reason()).isEqualTo("DISABLED");
        }

        @Test
        void computeOutcome_error_returnsErrorSource() {
            FeatureClientErrorStrategy<?> strategy = FeatureClientErrorStrategy.defaultValueStrategy(true, "", BigDecimal.ZERO);
            IzanamiResult.Error error = new IzanamiResult.Error(strategy, new fr.maif.errors.IzanamiError("Test error"));

            IzanamiEvaluationHelper.EvaluationOutcome<Boolean> outcome =
                IzanamiEvaluationHelper.computeOutcome(error, true, () -> false, Boolean.FALSE::equals);

            assertThat(outcome.value()).isTrue();
            assertThat(outcome.source()).isEqualTo(FlagValueSource.IZANAMI_ERROR_STRATEGY);
            assertThat(outcome.reason()).isEqualTo("ERROR");
        }
    }

    @Nested
    class BuildBaseMetadata {

        @Test
        void buildBaseMetadata_includesAllFlagConfigFields() {
            FlagConfig config = new FlagConfig(
                "uuid-123",
                "my-feature",
                "Test description",
                FlagValueType.BOOLEAN,
                ErrorStrategy.DEFAULT_VALUE,
                FeatureClientErrorStrategy.defaultValueStrategy(false, "", BigDecimal.ZERO),
                true,
                null
            );

            Map<String, String> metadata = IzanamiEvaluationHelper.buildBaseMetadata(config, objectMapper);

            assertThat(metadata)
                .containsEntry(FlagMetadataKeys.FLAG_CONFIG_KEY, "uuid-123")
                .containsEntry(FlagMetadataKeys.FLAG_CONFIG_NAME, "my-feature")
                .containsEntry(FlagMetadataKeys.FLAG_CONFIG_DESCRIPTION, "Test description")
                .containsEntry(FlagMetadataKeys.FLAG_CONFIG_VALUE_TYPE, "BOOLEAN")
                .containsEntry(FlagMetadataKeys.FLAG_CONFIG_ERROR_STRATEGY, "DEFAULT_VALUE")
                .containsEntry(FlagMetadataKeys.FLAG_CONFIG_DEFAULT_VALUE, "true");
        }

        @Test
        void buildBaseMetadata_handlesNullDefaultValue() {
            FlagConfig config = new FlagConfig(
                "uuid-123",
                "my-feature",
                "Test description",
                FlagValueType.STRING,
                ErrorStrategy.DEFAULT_VALUE,
                FeatureClientErrorStrategy.defaultValueStrategy(false, "", BigDecimal.ZERO),
                null,
                null
            );

            Map<String, String> metadata = IzanamiEvaluationHelper.buildBaseMetadata(config, objectMapper);

            assertThat(metadata.get(FlagMetadataKeys.FLAG_CONFIG_DEFAULT_VALUE)).isNull();
        }

        @Test
        void buildBaseMetadata_serializesObjectDefaultValue() {
            Map<String, Object> objectDefault = Map.of("key", "value", "count", 42);
            FlagConfig config = new FlagConfig(
                "uuid-123",
                "my-feature",
                "Test description",
                FlagValueType.OBJECT,
                ErrorStrategy.DEFAULT_VALUE,
                FeatureClientErrorStrategy.defaultValueStrategy(false, "", BigDecimal.ZERO),
                objectDefault,
                null
            );

            Map<String, String> metadata = IzanamiEvaluationHelper.buildBaseMetadata(config, objectMapper);

            String defaultValue = metadata.get(FlagMetadataKeys.FLAG_CONFIG_DEFAULT_VALUE);
            assertThat(defaultValue).contains("\"key\"").contains("\"value\"").contains("42");
        }
    }

    @Nested
    class BuildFlagNotFoundMetadata {

        @Test
        void buildFlagNotFoundMetadata_setsCorrectReasonAndSource() {
            Map<String, String> metadata = IzanamiEvaluationHelper.buildFlagNotFoundMetadata("missing-flag");

            assertThat(metadata)
                .containsEntry(FlagMetadataKeys.FLAG_CONFIG_KEY, "missing-flag")
                .containsEntry(FlagMetadataKeys.FLAG_VALUE_SOURCE, FlagValueSource.APPLICATION_ERROR_STRATEGY.name())
                .containsEntry(FlagMetadataKeys.FLAG_EVALUATION_REASON, "FLAG_NOT_FOUND");
        }
    }

    @Nested
    class ComputeEffectiveErrorStrategy {

        @Test
        void computeEffectiveErrorStrategy_prefersOverride_whenProvided() {
            FeatureClientErrorStrategy<?> override = FeatureClientErrorStrategy.failStrategy();
            FeatureClientErrorStrategy<?> configDefault = FeatureClientErrorStrategy.defaultValueStrategy(false, "", BigDecimal.ZERO);

            FeatureClientErrorStrategy<?> result = IzanamiEvaluationHelper.computeEffectiveErrorStrategy(override, configDefault);

            assertThat(result).isSameAs(override);
        }

        @Test
        void computeEffectiveErrorStrategy_fallsBackToConfig_whenNoOverride() {
            FeatureClientErrorStrategy<?> configDefault = FeatureClientErrorStrategy.defaultValueStrategy(false, "", BigDecimal.ZERO);

            FeatureClientErrorStrategy<?> result = IzanamiEvaluationHelper.computeEffectiveErrorStrategy(null, configDefault);

            assertThat(result).isSameAs(configDefault);
        }
    }

    @Nested
    class ApplyCommonConfiguration {

        @Test
        void applyCommonConfiguration_withAllNulls_returnsOriginalRequest() {
            FeatureRequest request = FeatureRequest.newFeatureRequest();

            FeatureRequest result = IzanamiEvaluationHelper.applyCommonConfiguration(
                request, null, null, false, null, null
            );

            assertThat(result).isNotNull();
        }

        @Test
        void applyCommonConfiguration_withUser_setsUser() {
            FeatureRequest request = FeatureRequest.newFeatureRequest();

            FeatureRequest result = IzanamiEvaluationHelper.applyCommonConfiguration(
                request, "user-123", null, false, null, null
            );

            assertThat(result).isNotNull();
        }

        @Test
        void applyCommonConfiguration_withContext_setsContext() {
            FeatureRequest request = FeatureRequest.newFeatureRequest();

            FeatureRequest result = IzanamiEvaluationHelper.applyCommonConfiguration(
                request, null, "production", false, null, null
            );

            assertThat(result).isNotNull();
        }

        @Test
        void applyCommonConfiguration_withIgnoreCache_setsIgnoreCache() {
            FeatureRequest request = FeatureRequest.newFeatureRequest();

            FeatureRequest result = IzanamiEvaluationHelper.applyCommonConfiguration(
                request, null, null, true, null, null
            );

            assertThat(result).isNotNull();
        }

        @Test
        void applyCommonConfiguration_withTimeout_setsTimeout() {
            FeatureRequest request = FeatureRequest.newFeatureRequest();

            FeatureRequest result = IzanamiEvaluationHelper.applyCommonConfiguration(
                request, null, null, false, Duration.ofSeconds(5), null
            );

            assertThat(result).isNotNull();
        }

        @Test
        void applyCommonConfiguration_withPayload_setsPayload() {
            FeatureRequest request = FeatureRequest.newFeatureRequest();

            FeatureRequest result = IzanamiEvaluationHelper.applyCommonConfiguration(
                request, null, null, false, null, "{\"key\":\"value\"}"
            );

            assertThat(result).isNotNull();
        }

        @Test
        void applyCommonConfiguration_withAllOptions_setsAll() {
            FeatureRequest request = FeatureRequest.newFeatureRequest();

            FeatureRequest result = IzanamiEvaluationHelper.applyCommonConfiguration(
                request, "user-123", "staging", true, Duration.ofSeconds(10), "{\"data\":true}"
            );

            assertThat(result).isNotNull();
        }
    }

    @Nested
    class ToBigDecimal {

        @Test
        void toBigDecimal_null_returnsNull() {
            assertThat(IzanamiEvaluationHelper.toBigDecimal(null)).isNull();
        }

        @Test
        void toBigDecimal_bigDecimal_returnsSameInstance() {
            BigDecimal input = new BigDecimal("123.45");
            assertThat(IzanamiEvaluationHelper.toBigDecimal(input)).isSameAs(input);
        }

        @Test
        void toBigDecimal_integer_convertsToBigDecimal() {
            assertThat(IzanamiEvaluationHelper.toBigDecimal(42)).isEqualByComparingTo(new BigDecimal("42"));
        }

        @Test
        void toBigDecimal_double_convertsToBigDecimal() {
            assertThat(IzanamiEvaluationHelper.toBigDecimal(3.14)).isEqualByComparingTo(new BigDecimal("3.14"));
        }

        @Test
        void toBigDecimal_long_convertsToBigDecimal() {
            assertThat(IzanamiEvaluationHelper.toBigDecimal(123456789L)).isEqualByComparingTo(new BigDecimal("123456789"));
        }

        @Test
        void toBigDecimal_string_returnsNull() {
            assertThat(IzanamiEvaluationHelper.toBigDecimal("not a number")).isNull();
        }
    }

    @Nested
    class BuildResultWithDetails {

        @Test
        void buildResultWithDetails_success_setsCorrectSourceAndReason() {
            FeatureValue featureValue = mock(FeatureValue.class);
            IzanamiResult.Success success = new IzanamiResult.Success(featureValue);
            Map<String, String> baseMetadata = Map.of("key", "value");

            ResultValueWithDetails<String> result = IzanamiEvaluationHelper.buildResultWithDetails(
                success,
                r -> "test-value",
                baseMetadata,
                () -> null,
                str -> str == null,
                "test-flag"
            );

            assertThat(result.value()).isEqualTo("test-value");
            assertThat(result.metadata().get(FlagMetadataKeys.FLAG_VALUE_SOURCE)).isEqualTo("IZANAMI");
            assertThat(result.metadata().get(FlagMetadataKeys.FLAG_EVALUATION_REASON)).isEqualTo("ORIGIN_OR_CACHE");
            assertThat(result.metadata().get("key")).isEqualTo("value");
        }

        @Test
        void buildResultWithDetails_error_setsErrorSourceAndReason() {
            FeatureClientErrorStrategy<?> strategy = FeatureClientErrorStrategy.defaultValueStrategy(true, "", BigDecimal.ZERO);
            IzanamiResult.Error error = new IzanamiResult.Error(strategy, new fr.maif.errors.IzanamiError("Test error"));
            Map<String, String> baseMetadata = Map.of();

            ResultValueWithDetails<Boolean> result = IzanamiEvaluationHelper.buildResultWithDetails(
                error,
                r -> true,
                baseMetadata,
                () -> false,
                Boolean.FALSE::equals,
                "test-flag"
            );

            assertThat(result.value()).isTrue();
            assertThat(result.metadata().get(FlagMetadataKeys.FLAG_VALUE_SOURCE)).isEqualTo("IZANAMI_ERROR_STRATEGY");
            assertThat(result.metadata().get(FlagMetadataKeys.FLAG_EVALUATION_REASON)).isEqualTo("ERROR");
        }
    }
}
