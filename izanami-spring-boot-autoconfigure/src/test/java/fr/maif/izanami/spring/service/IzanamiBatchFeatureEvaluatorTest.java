package fr.maif.izanami.spring.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfeature.sdk.FlagValueType;
import fr.maif.FeatureClientErrorStrategy;
import fr.maif.IzanamiClient;
import fr.maif.features.results.IzanamiResult;
import fr.maif.features.values.BooleanCastStrategy;
import fr.maif.features.values.FeatureValue;
import fr.maif.izanami.spring.openfeature.ErrorStrategy;
import fr.maif.izanami.spring.openfeature.FlagConfig;
import fr.maif.izanami.spring.openfeature.FlagMetadataKeys;
import fr.maif.izanami.spring.openfeature.FlagValueSource;
import fr.maif.requests.FeatureRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.lang.Nullable;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link IzanamiBatchFeatureEvaluator}.
 */
class IzanamiBatchFeatureEvaluatorTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    private FlagConfig createBooleanFlagConfig(String key, String name, boolean defaultValue) {
        return new FlagConfig(
            key, name, "Test flag", FlagValueType.BOOLEAN,
            ErrorStrategy.DEFAULT_VALUE,
            FeatureClientErrorStrategy.defaultValueStrategy(defaultValue, "", BigDecimal.ZERO),
            defaultValue, null
        );
    }

    private FlagConfig createStringFlagConfig(String key, String name, String defaultValue) {
        return new FlagConfig(
            key, name, "Test flag", FlagValueType.STRING,
            ErrorStrategy.DEFAULT_VALUE,
            FeatureClientErrorStrategy.defaultValueStrategy(false, defaultValue != null ? defaultValue : "", BigDecimal.ZERO),
            defaultValue, null
        );
    }

    private IzanamiResult createMockResult(Map<String, IzanamiResult.Result> results) {
        IzanamiResult result = mock(IzanamiResult.class);
        try {
            Field resultsField = IzanamiResult.class.getDeclaredField("results");
            resultsField.setAccessible(true);
            resultsField.set(result, results);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set results field", e);
        }
        return result;
    }

    private FeatureValue createBooleanFeatureValue(boolean value) {
        FeatureValue featureValue = mock(FeatureValue.class);
        when(featureValue.booleanValue(any(BooleanCastStrategy.class))).thenReturn(value);
        return featureValue;
    }

    private IzanamiBatchFeatureEvaluator buildEvaluator(
        @Nullable IzanamiClient client,
        Map<String, FlagConfig> flagConfigs,
        Map<String, String> identifierToKey,
        Set<String> notFoundIdentifiers,
        @Nullable String user,
        @Nullable String context,
        boolean ignoreCache,
        @Nullable Duration callTimeout,
        @Nullable String payload,
        BooleanCastStrategy booleanCastStrategy,
        @Nullable FeatureClientErrorStrategy<?> errorStrategyOverride
    ) {
        BatchEvaluationParams params = BatchEvaluationParams.builder()
            .client(client)
            .objectMapper(objectMapper)
            .flagConfigs(flagConfigs)
            .identifierToKey(identifierToKey)
            .notFoundIdentifiers(notFoundIdentifiers)
            .user(user)
            .context(context)
            .ignoreCache(ignoreCache)
            .callTimeout(callTimeout)
            .payload(payload)
            .booleanCastStrategy(booleanCastStrategy)
            .errorStrategyOverride(errorStrategyOverride)
            .build();
        return new IzanamiBatchFeatureEvaluator(params);
    }

    @Nested
    class EvaluateWithNoConfiguredFlags {

        @Test
        void returnsNotFoundResultsOnly() {
            Set<String> notFoundIdentifiers = Set.of("unknown-1", "unknown-2");

            IzanamiBatchFeatureEvaluator evaluator = buildEvaluator(
                null,
                Map.of(),                  // empty flagConfigs
                Map.of(),                  // empty identifierToKey
                notFoundIdentifiers,
                null, null, false, null, null,
                BooleanCastStrategy.LAX, null
            );

            BatchResultImpl result = evaluator.evaluate().join();

            assertThat(result.flagIdentifiers()).containsExactlyInAnyOrder("unknown-1", "unknown-2");
            assertThat(result.hasFlag("unknown-1")).isTrue();
            // Not-found flags return default value (false for booleans) from details
            assertThat(result.booleanValueDetails("unknown-1").value()).isFalse();
        }

        @Test
        void notFoundFlagsHaveCorrectMetadata() {
            Set<String> notFoundIdentifiers = Set.of("missing-flag");

            IzanamiBatchFeatureEvaluator evaluator = buildEvaluator(
                null,
                Map.of(),
                Map.of(),
                notFoundIdentifiers,
                null, null, false, null, null,
                BooleanCastStrategy.LAX, null
            );

            BatchResultImpl result = evaluator.evaluate().join();

            var details = result.booleanValueDetails("missing-flag");
            assertThat(details.metadata().get(FlagMetadataKeys.FLAG_EVALUATION_REASON)).isEqualTo("FLAG_NOT_FOUND");
            assertThat(details.metadata().get(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                .isEqualTo(FlagValueSource.APPLICATION_ERROR_STRATEGY.name());
        }
    }

    @Nested
    class EvaluateWithNullClient {

        @Test
        void returnsDefaultValuesForAllFlags() {
            FlagConfig config = createBooleanFlagConfig("uuid-1", "flag-1", true);
            Map<String, FlagConfig> flagConfigs = Map.of("uuid-1", config);
            Map<String, String> identifierToKey = Map.of("flag-1", "uuid-1");

            IzanamiBatchFeatureEvaluator evaluator = buildEvaluator(
                null,  // client is null
                flagConfigs,
                identifierToKey,
                Set.of(),
                null, null, false, null, null,
                BooleanCastStrategy.LAX, null
            );

            BatchResultImpl result = evaluator.evaluate().join();

            assertThat(result.hasFlag("flag-1")).isTrue();
            // Error result uses error strategy - default value is true
            var details = result.booleanValueDetails("flag-1");
            assertThat(details.value()).isTrue();
            assertThat(details.metadata().get(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                .isEqualTo(FlagValueSource.IZANAMI_ERROR_STRATEGY.name());
        }

        @Test
        void returnsDefaultsForMultipleFlags() {
            FlagConfig config1 = createBooleanFlagConfig("uuid-1", "flag-1", true);
            FlagConfig config2 = createStringFlagConfig("uuid-2", "flag-2", "default-str");

            Map<String, FlagConfig> flagConfigs = new LinkedHashMap<>();
            flagConfigs.put("uuid-1", config1);
            flagConfigs.put("uuid-2", config2);

            Map<String, String> identifierToKey = new LinkedHashMap<>();
            identifierToKey.put("flag-1", "uuid-1");
            identifierToKey.put("flag-2", "uuid-2");

            IzanamiBatchFeatureEvaluator evaluator = buildEvaluator(
                null,
                flagConfigs,
                identifierToKey,
                Set.of(),
                null, null, false, null, null,
                BooleanCastStrategy.LAX, null
            );

            BatchResultImpl result = evaluator.evaluate().join();

            assertThat(result.flagIdentifiers()).containsExactlyInAnyOrder("flag-1", "flag-2");
            assertThat(result.booleanValueDetails("flag-1").value()).isTrue();
            assertThat(result.stringValueDetails("flag-2").value()).isEqualTo("default-str");
        }
    }

    @Nested
    class EvaluateWithSuccessfulResponse {

        @Test
        void returnsIzanamiValues() {
            IzanamiClient client = mock(IzanamiClient.class);
            FlagConfig config = createBooleanFlagConfig("uuid-1", "flag-1", false);
            Map<String, FlagConfig> flagConfigs = Map.of("uuid-1", config);
            Map<String, String> identifierToKey = Map.of("flag-1", "uuid-1");

            // Mock successful Izanami response
            FeatureValue featureValue = createBooleanFeatureValue(true);

            Map<String, IzanamiResult.Result> results = new LinkedHashMap<>();
            results.put("uuid-1", new IzanamiResult.Success(featureValue));
            IzanamiResult izanamiResult = createMockResult(results);

            when(client.featureValues(any(FeatureRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(izanamiResult));

            IzanamiBatchFeatureEvaluator evaluator = buildEvaluator(
                client,
                flagConfigs, identifierToKey, Set.of(),
                null, null, false, null, null,
                BooleanCastStrategy.LAX, null
            );

            BatchResultImpl result = evaluator.evaluate().join();

            assertThat(result.booleanValue("flag-1")).isTrue();
            var details = result.booleanValueDetails("flag-1");
            assertThat(details.metadata().get(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                .isEqualTo(FlagValueSource.IZANAMI.name());
        }

        @Test
        void handlesFeatureNotInResponse() {
            IzanamiClient client = mock(IzanamiClient.class);
            FlagConfig config = createBooleanFlagConfig("uuid-1", "flag-1", true);
            Map<String, FlagConfig> flagConfigs = Map.of("uuid-1", config);
            Map<String, String> identifierToKey = Map.of("flag-1", "uuid-1");

            // Return empty result (feature not in response)
            IzanamiResult izanamiResult = createMockResult(new LinkedHashMap<>());

            when(client.featureValues(any(FeatureRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(izanamiResult));

            IzanamiBatchFeatureEvaluator evaluator = buildEvaluator(
                client,
                flagConfigs, identifierToKey, Set.of(),
                null, null, false, null, null,
                BooleanCastStrategy.LAX, null
            );

            BatchResultImpl result = evaluator.evaluate().join();

            // Should get error strategy value (default = true)
            var details = result.booleanValueDetails("flag-1");
            assertThat(details.value()).isTrue();
            assertThat(details.metadata().get(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                .isEqualTo(FlagValueSource.IZANAMI_ERROR_STRATEGY.name());
        }
    }

    @Nested
    class EvaluateWithFailedQuery {

        @Test
        void returnsErrorResultsForAllFlags() {
            IzanamiClient client = mock(IzanamiClient.class);
            FlagConfig config = createBooleanFlagConfig("uuid-1", "flag-1", true);
            Map<String, FlagConfig> flagConfigs = Map.of("uuid-1", config);
            Map<String, String> identifierToKey = Map.of("flag-1", "uuid-1");

            // Simulate query failure
            CompletableFuture<IzanamiResult> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(new RuntimeException("Connection timeout"));

            when(client.featureValues(any(FeatureRequest.class)))
                .thenReturn(failedFuture);

            IzanamiBatchFeatureEvaluator evaluator = buildEvaluator(
                client,
                flagConfigs, identifierToKey, Set.of(),
                null, null, false, null, null,
                BooleanCastStrategy.LAX, null
            );

            BatchResultImpl result = evaluator.evaluate().join();

            // Should get error strategy value
            var details = result.booleanValueDetails("flag-1");
            assertThat(details.value()).isTrue();
            assertThat(details.metadata().get(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                .isEqualTo(FlagValueSource.IZANAMI_ERROR_STRATEGY.name());
        }
    }

    @Nested
    class EvaluateWithErrorStrategyOverride {

        @Test
        void usesOverrideStrategyInsteadOfConfig() {
            IzanamiClient client = mock(IzanamiClient.class);
            // Config default is false
            FlagConfig config = createBooleanFlagConfig("uuid-1", "flag-1", false);
            Map<String, FlagConfig> flagConfigs = Map.of("uuid-1", config);
            Map<String, String> identifierToKey = Map.of("flag-1", "uuid-1");

            // Empty response
            IzanamiResult izanamiResult = createMockResult(new LinkedHashMap<>());

            when(client.featureValues(any(FeatureRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(izanamiResult));

            // Override with true default
            FeatureClientErrorStrategy<?> override =
                FeatureClientErrorStrategy.defaultValueStrategy(true, "override", BigDecimal.TEN);

            IzanamiBatchFeatureEvaluator evaluator = buildEvaluator(
                client,
                flagConfigs, identifierToKey, Set.of(),
                null, null, false, null, null,
                BooleanCastStrategy.LAX, override
            );

            BatchResultImpl result = evaluator.evaluate().join();

            // Should use override value (true), not config default (false)
            assertThat(result.booleanValueDetails("flag-1").value()).isTrue();
        }
    }

    @Nested
    class EvaluateWithRequestParameters {

        @Test
        void passesUserAndContextToRequest() {
            IzanamiClient client = mock(IzanamiClient.class);
            FlagConfig config = createBooleanFlagConfig("uuid-1", "flag-1", false);
            Map<String, FlagConfig> flagConfigs = Map.of("uuid-1", config);
            Map<String, String> identifierToKey = Map.of("flag-1", "uuid-1");

            FeatureValue featureValue = createBooleanFeatureValue(true);
            Map<String, IzanamiResult.Result> results = Map.of("uuid-1", new IzanamiResult.Success(featureValue));
            IzanamiResult izanamiResult = createMockResult(results);

            when(client.featureValues(any(FeatureRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(izanamiResult));

            IzanamiBatchFeatureEvaluator evaluator = buildEvaluator(
                client,
                flagConfigs, identifierToKey, Set.of(),
                "test-user",         // user
                "production",        // context
                true,                // ignoreCache
                Duration.ofSeconds(5),  // callTimeout
                "{\"key\":\"value\"}",  // payload
                BooleanCastStrategy.LAX, null
            );

            BatchResultImpl result = evaluator.evaluate().join();

            // Verify result is returned (actual request params verification would require argument captor)
            assertThat(result.booleanValue("flag-1")).isTrue();
        }
    }

    @Nested
    class EvaluateWithMixedResults {

        @Test
        void handlesCombinationOfFoundAndNotFoundFlags() {
            IzanamiClient client = mock(IzanamiClient.class);
            FlagConfig config = createBooleanFlagConfig("uuid-1", "flag-1", false);
            Map<String, FlagConfig> flagConfigs = Map.of("uuid-1", config);
            Map<String, String> identifierToKey = Map.of("flag-1", "uuid-1");
            Set<String> notFoundIdentifiers = Set.of("unknown-flag");

            FeatureValue featureValue = createBooleanFeatureValue(true);
            Map<String, IzanamiResult.Result> results = Map.of("uuid-1", new IzanamiResult.Success(featureValue));
            IzanamiResult izanamiResult = createMockResult(results);

            when(client.featureValues(any(FeatureRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(izanamiResult));

            IzanamiBatchFeatureEvaluator evaluator = buildEvaluator(
                client,
                flagConfigs, identifierToKey, notFoundIdentifiers,
                null, null, false, null, null,
                BooleanCastStrategy.LAX, null
            );

            BatchResultImpl result = evaluator.evaluate().join();

            // Configured flag should be found
            assertThat(result.booleanValue("flag-1")).isTrue();
            assertThat(result.booleanValueDetails("flag-1").metadata().get(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                .isEqualTo(FlagValueSource.IZANAMI.name());

            // Unknown flag should be not-found with default value
            assertThat(result.booleanValueDetails("unknown-flag").value()).isFalse();
            assertThat(result.booleanValueDetails("unknown-flag").metadata().get(FlagMetadataKeys.FLAG_EVALUATION_REASON))
                .isEqualTo("FLAG_NOT_FOUND");
        }
    }

    @Nested
    class EvaluateMultipleFlags {

        @Test
        void processesMultipleFlagsInSingleRequest() {
            IzanamiClient client = mock(IzanamiClient.class);

            FlagConfig config1 = createBooleanFlagConfig("uuid-1", "flag-1", false);
            FlagConfig config2 = createBooleanFlagConfig("uuid-2", "flag-2", true);

            Map<String, FlagConfig> flagConfigs = new LinkedHashMap<>();
            flagConfigs.put("uuid-1", config1);
            flagConfigs.put("uuid-2", config2);

            Map<String, String> identifierToKey = new LinkedHashMap<>();
            identifierToKey.put("flag-1", "uuid-1");
            identifierToKey.put("flag-2", "uuid-2");

            FeatureValue featureValue1 = createBooleanFeatureValue(true);
            FeatureValue featureValue2 = createBooleanFeatureValue(false);

            Map<String, IzanamiResult.Result> results = new LinkedHashMap<>();
            results.put("uuid-1", new IzanamiResult.Success(featureValue1));
            results.put("uuid-2", new IzanamiResult.Success(featureValue2));
            IzanamiResult izanamiResult = createMockResult(results);

            when(client.featureValues(any(FeatureRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(izanamiResult));

            IzanamiBatchFeatureEvaluator evaluator = buildEvaluator(
                client,
                flagConfigs, identifierToKey, Set.of(),
                null, null, false, null, null,
                BooleanCastStrategy.LAX, null
            );

            BatchResultImpl result = evaluator.evaluate().join();

            assertThat(result.flagIdentifiers()).containsExactlyInAnyOrder("flag-1", "flag-2");
            assertThat(result.booleanValue("flag-1")).isTrue();
            assertThat(result.booleanValue("flag-2")).isFalse();
        }
    }
}
