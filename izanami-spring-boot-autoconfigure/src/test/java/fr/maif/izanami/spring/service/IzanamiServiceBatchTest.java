package fr.maif.izanami.spring.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfeature.sdk.FlagValueType;
import fr.maif.FeatureClientErrorStrategy;
import fr.maif.IzanamiClient;
import fr.maif.features.results.IzanamiResult;
import fr.maif.features.values.BooleanCastStrategy;
import fr.maif.features.values.FeatureValue;
import fr.maif.izanami.spring.autoconfigure.IzanamiProperties;
import fr.maif.izanami.spring.openfeature.ErrorStrategy;
import fr.maif.izanami.spring.openfeature.FlagConfig;
import fr.maif.izanami.spring.openfeature.FlagMetadataKeys;
import fr.maif.izanami.spring.openfeature.FlagValueSource;
import fr.maif.izanami.spring.openfeature.api.FlagConfigService;
import fr.maif.izanami.spring.service.api.BatchResult;
import fr.maif.izanami.spring.service.api.ResultValueWithDetails;
import fr.maif.requests.FeatureRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import fr.maif.izanami.spring.service.api.RootContextProvider;
import fr.maif.izanami.spring.service.api.SubContextResolver;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IzanamiServiceBatchTest {

    private FlagConfigService flagConfigService;
    private ObjectMapper objectMapper;
    private IzanamiClient mockClient;
    private IzanamiServiceImpl.IzanamiClientFactory mockFactory;

    @BeforeEach
    void setUp() {
        flagConfigService = mock(FlagConfigService.class);
        objectMapper = new ObjectMapper();
        mockClient = mock(IzanamiClient.class);
        mockFactory = mock(IzanamiServiceImpl.IzanamiClientFactory.class);

        when(flagConfigService.getAllFlagConfigs()).thenReturn(List.of());
        when(mockClient.isLoaded()).thenReturn(CompletableFuture.completedFuture(null));
    }

    // =====================================================================
    // Test Helpers
    // =====================================================================

    private static IzanamiProperties validProperties() {
        IzanamiProperties props = new IzanamiProperties();
        props.setBaseUrl("http://localhost:9999");
        props.setApiPath("/api");
        props.setClientId("test-client-id");
        props.setClientSecret("test-client-secret");
        IzanamiProperties.Cache cache = new IzanamiProperties.Cache();
        cache.setEnabled(true);
        cache.setRefreshInterval(Duration.ofMinutes(5));
        IzanamiProperties.Cache.Sse sse = new IzanamiProperties.Cache.Sse();
        sse.setEnabled(false);
        sse.setKeepAliveInterval(Duration.ofSeconds(25));
        cache.setSse(sse);
        props.setCache(cache);
        return props;
    }

    private static IzanamiProperties blankUrlProperties() {
        IzanamiProperties props = new IzanamiProperties();
        props.setBaseUrl("");
        props.setApiPath("/api");
        props.setClientId("test-client-id");
        props.setClientSecret("test-client-secret");
        return props;
    }

    private static FlagConfig testDefaultBooleanFlagConfig(String key, String name, boolean defaultValue) {
        return new FlagConfig(
            key,
            name,
            "Test flag description",
            FlagValueType.BOOLEAN,
            ErrorStrategy.DEFAULT_VALUE,
            FeatureClientErrorStrategy.defaultValueStrategy(defaultValue, "", BigDecimal.ZERO),
            defaultValue,
            null
        );
    }

    private static FlagConfig testDefaultStringFlagConfig(String key, String name, String defaultValue) {
        return new FlagConfig(
            key,
            name,
            "Test flag description",
            FlagValueType.STRING,
            ErrorStrategy.DEFAULT_VALUE,
            FeatureClientErrorStrategy.defaultValueStrategy(false, defaultValue, BigDecimal.ZERO),
            defaultValue,
            null
        );
    }

    private static FlagConfig testDefaultIntegerFlagConfig(String key, String name, BigDecimal defaultValue) {
        return new FlagConfig(
            key,
            name,
            "Test flag description",
            FlagValueType.INTEGER,
            ErrorStrategy.DEFAULT_VALUE,
            FeatureClientErrorStrategy.defaultValueStrategy(false, "", defaultValue),
            defaultValue,
            null
        );
    }

    /**
     * Creates a no-op CompositeContextResolver that returns empty (no default context).
     */
    @SuppressWarnings("unchecked")
    private static CompositeContextResolver noOpContextResolver() {
        ObjectProvider<RootContextProvider> rootProvider = mock(ObjectProvider.class);
        ObjectProvider<SubContextResolver> subResolver = mock(ObjectProvider.class);
        when(rootProvider.getIfAvailable()).thenReturn(null);
        when(subResolver.getIfAvailable()).thenReturn(null);
        return new CompositeContextResolver(rootProvider, subResolver);
    }

    private IzanamiServiceImpl createServiceWithMockFactory(IzanamiProperties properties) {
        when(mockFactory.create(any(), any(), any(), any())).thenReturn(mockClient);
        return new IzanamiServiceImpl(properties, flagConfigService, objectMapper, noOpContextResolver(), mockFactory);
    }

    private IzanamiResult createMockSuccessResult(Map<String, FeatureValue> featureValues) {
        IzanamiResult result = mock(IzanamiResult.class);
        try {
            Map<String, IzanamiResult.Result> results = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, FeatureValue> entry : featureValues.entrySet()) {
                results.put(entry.getKey(), new IzanamiResult.Success(entry.getValue()));
            }
            java.lang.reflect.Field resultsField = IzanamiResult.class.getDeclaredField("results");
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

    private FeatureValue createStringFeatureValue(String value) {
        FeatureValue featureValue = mock(FeatureValue.class);
        when(featureValue.stringValue()).thenReturn(value);
        when(featureValue.booleanValue(any(BooleanCastStrategy.class))).thenReturn(value != null);
        return featureValue;
    }

    private FeatureValue createNumberFeatureValue(BigDecimal value) {
        FeatureValue featureValue = mock(FeatureValue.class);
        when(featureValue.numberValue()).thenReturn(value);
        when(featureValue.booleanValue(any(BooleanCastStrategy.class))).thenReturn(value != null && value.compareTo(BigDecimal.ZERO) != 0);
        return featureValue;
    }

    // =====================================================================
    // Fluent API Entry Points Tests
    // =====================================================================

    @Nested
    class ForFlagKeysTests {

        @Test
        void forFlagKeys_whenAllFlagsExist_returnsBuilder() {
            FlagConfig config1 = testDefaultBooleanFlagConfig("uuid-1", "flag-1", false);
            FlagConfig config2 = testDefaultBooleanFlagConfig("uuid-2", "flag-2", true);

            when(flagConfigService.getFlagConfigByKey("uuid-1")).thenReturn(Optional.of(config1));
            when(flagConfigService.getFlagConfigByKey("uuid-2")).thenReturn(Optional.of(config2));

            IzanamiServiceImpl service = createServiceWithMockFactory(validProperties());
            service.afterPropertiesSet();

            var builder = service.forFlagKeys("uuid-1", "uuid-2");

            assertThat(builder).isNotNull();
        }

        @Test
        void forFlagKeys_whenAnyFlagMissing_includesMissingFlagWithDefaults() {
            FlagConfig config1 = testDefaultBooleanFlagConfig("uuid-1", "flag-1", false);

            when(flagConfigService.getFlagConfigByKey("uuid-1")).thenReturn(Optional.of(config1));
            when(flagConfigService.getFlagConfigByKey("uuid-missing")).thenReturn(Optional.empty());

            IzanamiResult mockResult = createMockSuccessResult(Map.of(
                "uuid-1", createBooleanFeatureValue(true)
            ));
            when(mockClient.featureValues(any(FeatureRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResult));

            IzanamiServiceImpl service = createServiceWithMockFactory(validProperties());
            service.afterPropertiesSet();

            // Should not throw - missing flag is included with defaults
            BatchResult batchResult = service.forFlagKeys("uuid-1", "uuid-missing")
                .values()
                .join();

            // Existing flag should work normally
            assertThat(batchResult.booleanValue("uuid-1")).isTrue();

            // Missing flag should return defaults with FLAG_NOT_FOUND
            assertThat(batchResult.hasFlag("uuid-missing")).isTrue();
            ResultValueWithDetails<Boolean> missingResult = batchResult.booleanValueDetails("uuid-missing");
            assertThat(missingResult.value()).isFalse();
            assertThat(missingResult.metadata().get(FlagMetadataKeys.FLAG_EVALUATION_REASON)).isEqualTo("FLAG_NOT_FOUND");
            assertThat(missingResult.metadata().get(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                .isEqualTo(FlagValueSource.APPLICATION_ERROR_STRATEGY.name());
        }
    }

    @Nested
    class ForFlagNamesTests {

        @Test
        void forFlagNames_whenAllFlagsExist_returnsBuilder() {
            FlagConfig config1 = testDefaultBooleanFlagConfig("uuid-1", "flag-1", false);
            FlagConfig config2 = testDefaultBooleanFlagConfig("uuid-2", "flag-2", true);

            when(flagConfigService.getFlagConfigByName("flag-1")).thenReturn(Optional.of(config1));
            when(flagConfigService.getFlagConfigByName("flag-2")).thenReturn(Optional.of(config2));

            IzanamiServiceImpl service = createServiceWithMockFactory(validProperties());
            service.afterPropertiesSet();

            var builder = service.forFlagNames("flag-1", "flag-2");

            assertThat(builder).isNotNull();
        }

        @Test
        void forFlagNames_whenAnyFlagMissing_includesMissingFlagWithDefaults() {
            FlagConfig config1 = testDefaultBooleanFlagConfig("uuid-1", "flag-1", false);

            when(flagConfigService.getFlagConfigByName("flag-1")).thenReturn(Optional.of(config1));
            when(flagConfigService.getFlagConfigByName("missing-flag")).thenReturn(Optional.empty());

            IzanamiResult mockResult = createMockSuccessResult(Map.of(
                "uuid-1", createBooleanFeatureValue(true)
            ));
            when(mockClient.featureValues(any(FeatureRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResult));

            IzanamiServiceImpl service = createServiceWithMockFactory(validProperties());
            service.afterPropertiesSet();

            // Should not throw - missing flag is included with defaults
            BatchResult batchResult = service.forFlagNames("flag-1", "missing-flag")
                .values()
                .join();

            // Existing flag should work normally (by name)
            assertThat(batchResult.booleanValue("flag-1")).isTrue();

            // Missing flag should return defaults with FLAG_NOT_FOUND
            assertThat(batchResult.hasFlag("missing-flag")).isTrue();
            ResultValueWithDetails<String> missingResult = batchResult.stringValueDetails("missing-flag");
            assertThat(missingResult.value()).isEqualTo("");
            assertThat(missingResult.metadata().get(FlagMetadataKeys.FLAG_EVALUATION_REASON)).isEqualTo("FLAG_NOT_FOUND");
            assertThat(missingResult.metadata().get(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                .isEqualTo(FlagValueSource.APPLICATION_ERROR_STRATEGY.name());
        }
    }

    // =====================================================================
    // Batch Evaluation Tests
    // =====================================================================

    @Nested
    class BatchEvaluationTests {

        @Test
        void values_multipleFlags_allSucceed() {
            FlagConfig config1 = testDefaultBooleanFlagConfig("uuid-1", "flag-1", false);
            FlagConfig config2 = testDefaultStringFlagConfig("uuid-2", "flag-2", "default");
            FlagConfig config3 = testDefaultIntegerFlagConfig("uuid-3", "flag-3", BigDecimal.TEN);

            when(flagConfigService.getFlagConfigByKey("uuid-1")).thenReturn(Optional.of(config1));
            when(flagConfigService.getFlagConfigByKey("uuid-2")).thenReturn(Optional.of(config2));
            when(flagConfigService.getFlagConfigByKey("uuid-3")).thenReturn(Optional.of(config3));

            IzanamiResult mockResult = createMockSuccessResult(Map.of(
                "uuid-1", createBooleanFeatureValue(true),
                "uuid-2", createStringFeatureValue("hello"),
                "uuid-3", createNumberFeatureValue(BigDecimal.valueOf(42))
            ));
            when(mockClient.featureValues(any(FeatureRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResult));

            IzanamiServiceImpl service = createServiceWithMockFactory(validProperties());
            service.afterPropertiesSet();

            BatchResult result = service.forFlagKeys("uuid-1", "uuid-2", "uuid-3")
                .values()
                .join();

            assertThat(result.booleanValue("uuid-1")).isTrue();
            assertThat(result.stringValue("uuid-2")).isEqualTo("hello");
            assertThat(result.numberValue("uuid-3")).isEqualTo(BigDecimal.valueOf(42));
        }

        @Test
        void values_withUserAndContext_passedToRequest() {
            FlagConfig config1 = testDefaultBooleanFlagConfig("uuid-1", "flag-1", false);

            when(flagConfigService.getFlagConfigByKey("uuid-1")).thenReturn(Optional.of(config1));

            IzanamiResult mockResult = createMockSuccessResult(Map.of(
                "uuid-1", createBooleanFeatureValue(true)
            ));

            ArgumentCaptor<FeatureRequest> requestCaptor = ArgumentCaptor.forClass(FeatureRequest.class);
            when(mockClient.featureValues(requestCaptor.capture()))
                .thenReturn(CompletableFuture.completedFuture(mockResult));

            IzanamiServiceImpl service = createServiceWithMockFactory(validProperties());
            service.afterPropertiesSet();

            service.forFlagKeys("uuid-1")
                .withUser("test-user")
                .withContext("test-context")
                .values()
                .join();

            FeatureRequest capturedRequest = requestCaptor.getValue();
            assertThat(capturedRequest.getUser()).isEqualTo("test-user");
            assertThat(capturedRequest.getContext()).hasValue("test-context");
        }
    }

    // =====================================================================
    // Client Not Available Tests
    // =====================================================================

    @Nested
    class ClientNotAvailableTests {

        @Test
        void values_whenClientNull_returnsDefaultsForAllFlags() {
            FlagConfig config1 = testDefaultBooleanFlagConfig("uuid-1", "flag-1", true);
            FlagConfig config2 = testDefaultStringFlagConfig("uuid-2", "flag-2", "fallback");

            when(flagConfigService.getFlagConfigByKey("uuid-1")).thenReturn(Optional.of(config1));
            when(flagConfigService.getFlagConfigByKey("uuid-2")).thenReturn(Optional.of(config2));

            // Create service without initializing client (blank URL)
            IzanamiServiceImpl service = new IzanamiServiceImpl(blankUrlProperties(), flagConfigService, objectMapper, noOpContextResolver());
            service.afterPropertiesSet();

            BatchResult result = service.forFlagKeys("uuid-1", "uuid-2")
                .values()
                .join();

            // When client is not available, error strategy values are used
            assertThat(result.booleanValue("uuid-1")).isEqualTo(true);  // From error strategy default
            assertThat(result.stringValue("uuid-2")).isEqualTo("fallback");  // From error strategy default
        }

        @Test
        void values_whenClientNull_detailsShowIzanamiErrorStrategy() {
            FlagConfig config1 = testDefaultBooleanFlagConfig("uuid-1", "flag-1", true);

            when(flagConfigService.getFlagConfigByKey("uuid-1")).thenReturn(Optional.of(config1));

            // Create service without initializing client (blank URL)
            IzanamiServiceImpl service = new IzanamiServiceImpl(blankUrlProperties(), flagConfigService, objectMapper, noOpContextResolver());
            service.afterPropertiesSet();

            BatchResult result = service.forFlagKeys("uuid-1")
                .values()
                .join();

            ResultValueWithDetails<Boolean> details = result.booleanValueDetails("uuid-1");
            assertThat(details.metadata().get(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                .isEqualTo(FlagValueSource.IZANAMI_ERROR_STRATEGY.name());
            assertThat(details.metadata().get(FlagMetadataKeys.FLAG_EVALUATION_REASON))
                .isEqualTo("ERROR");
        }
    }

    // =====================================================================
    // Disabled Features Tests
    // =====================================================================

    @Nested
    class DisabledFeaturesTests {

        @Test
        void booleanValue_disabledFeature_returnsFalse() {
            FlagConfig config1 = testDefaultBooleanFlagConfig("uuid-1", "flag-1", true);

            when(flagConfigService.getFlagConfigByKey("uuid-1")).thenReturn(Optional.of(config1));

            // Disabled boolean feature returns false
            IzanamiResult mockResult = createMockSuccessResult(Map.of(
                "uuid-1", createBooleanFeatureValue(false)
            ));
            when(mockClient.featureValues(any(FeatureRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResult));

            IzanamiServiceImpl service = createServiceWithMockFactory(validProperties());
            service.afterPropertiesSet();

            BatchResult result = service.forFlagKeys("uuid-1")
                .values()
                .join();

            assertThat(result.booleanValue("uuid-1")).isFalse();
        }

        @Test
        void stringValue_disabledFeature_returnsConfiguredDefault() {
            FlagConfig config1 = testDefaultStringFlagConfig("uuid-1", "flag-1", "fallback-value");

            when(flagConfigService.getFlagConfigByKey("uuid-1")).thenReturn(Optional.of(config1));

            // Disabled string feature returns null
            IzanamiResult mockResult = createMockSuccessResult(Map.of(
                "uuid-1", createStringFeatureValue(null)
            ));
            when(mockClient.featureValues(any(FeatureRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResult));

            IzanamiServiceImpl service = createServiceWithMockFactory(validProperties());
            service.afterPropertiesSet();

            BatchResult result = service.forFlagKeys("uuid-1")
                .values()
                .join();

            assertThat(result.stringValue("uuid-1")).isEqualTo("fallback-value");
        }

        @Test
        void stringValueDetails_disabledFeature_hasApplicationErrorStrategySource() {
            FlagConfig config1 = testDefaultStringFlagConfig("uuid-1", "flag-1", "fallback-value");

            when(flagConfigService.getFlagConfigByKey("uuid-1")).thenReturn(Optional.of(config1));

            // Disabled string feature returns null
            IzanamiResult mockResult = createMockSuccessResult(Map.of(
                "uuid-1", createStringFeatureValue(null)
            ));
            when(mockClient.featureValues(any(FeatureRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResult));

            IzanamiServiceImpl service = createServiceWithMockFactory(validProperties());
            service.afterPropertiesSet();

            BatchResult result = service.forFlagKeys("uuid-1")
                .values()
                .join();

            ResultValueWithDetails<String> details = result.stringValueDetails("uuid-1");
            assertThat(details.metadata().get(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                .isEqualTo(FlagValueSource.APPLICATION_ERROR_STRATEGY.name());
            assertThat(details.metadata().get(FlagMetadataKeys.FLAG_EVALUATION_REASON))
                .isEqualTo("DISABLED");
        }

        @Test
        void numberValue_disabledFeature_returnsConfiguredDefault() {
            FlagConfig config1 = testDefaultIntegerFlagConfig("uuid-1", "flag-1", BigDecimal.valueOf(999));

            when(flagConfigService.getFlagConfigByKey("uuid-1")).thenReturn(Optional.of(config1));

            // Disabled number feature returns null
            IzanamiResult mockResult = createMockSuccessResult(Map.of(
                "uuid-1", createNumberFeatureValue(null)
            ));
            when(mockClient.featureValues(any(FeatureRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResult));

            IzanamiServiceImpl service = createServiceWithMockFactory(validProperties());
            service.afterPropertiesSet();

            BatchResult result = service.forFlagKeys("uuid-1")
                .values()
                .join();

            assertThat(result.numberValue("uuid-1")).isEqualTo(BigDecimal.valueOf(999));
        }
    }

    // =====================================================================
    // Result Mapping Tests
    // =====================================================================

    @Nested
    class ResultMappingTests {

        @Test
        void forFlagNames_resultsAccessibleByName() {
            FlagConfig config1 = testDefaultBooleanFlagConfig("uuid-1", "my-feature", false);
            FlagConfig config2 = testDefaultStringFlagConfig("uuid-2", "other-feature", "default");

            when(flagConfigService.getFlagConfigByName("my-feature")).thenReturn(Optional.of(config1));
            when(flagConfigService.getFlagConfigByName("other-feature")).thenReturn(Optional.of(config2));

            IzanamiResult mockResult = createMockSuccessResult(Map.of(
                "uuid-1", createBooleanFeatureValue(true),
                "uuid-2", createStringFeatureValue("hello")
            ));
            when(mockClient.featureValues(any(FeatureRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResult));

            IzanamiServiceImpl service = createServiceWithMockFactory(validProperties());
            service.afterPropertiesSet();

            BatchResult result = service.forFlagNames("my-feature", "other-feature")
                .values()
                .join();

            // Results are accessible by name, not by key
            assertThat(result.booleanValue("my-feature")).isTrue();
            assertThat(result.stringValue("other-feature")).isEqualTo("hello");
            assertThat(result.hasFlag("my-feature")).isTrue();
            assertThat(result.hasFlag("uuid-1")).isFalse();  // Key should not work
        }

        @Test
        void forFlagKeys_resultsAccessibleByKey() {
            FlagConfig config1 = testDefaultBooleanFlagConfig("uuid-1", "my-feature", false);

            when(flagConfigService.getFlagConfigByKey("uuid-1")).thenReturn(Optional.of(config1));

            IzanamiResult mockResult = createMockSuccessResult(Map.of(
                "uuid-1", createBooleanFeatureValue(true)
            ));
            when(mockClient.featureValues(any(FeatureRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResult));

            IzanamiServiceImpl service = createServiceWithMockFactory(validProperties());
            service.afterPropertiesSet();

            BatchResult result = service.forFlagKeys("uuid-1")
                .values()
                .join();

            // Results are accessible by key, not by name
            assertThat(result.booleanValue("uuid-1")).isTrue();
            assertThat(result.hasFlag("uuid-1")).isTrue();
            assertThat(result.hasFlag("my-feature")).isFalse();  // Name should not work
        }

        @Test
        void flagIdentifiers_returnsAllRequestedIdentifiers() {
            FlagConfig config1 = testDefaultBooleanFlagConfig("uuid-1", "flag-1", false);
            FlagConfig config2 = testDefaultBooleanFlagConfig("uuid-2", "flag-2", true);

            when(flagConfigService.getFlagConfigByKey("uuid-1")).thenReturn(Optional.of(config1));
            when(flagConfigService.getFlagConfigByKey("uuid-2")).thenReturn(Optional.of(config2));

            IzanamiResult mockResult = createMockSuccessResult(Map.of(
                "uuid-1", createBooleanFeatureValue(true),
                "uuid-2", createBooleanFeatureValue(false)
            ));
            when(mockClient.featureValues(any(FeatureRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResult));

            IzanamiServiceImpl service = createServiceWithMockFactory(validProperties());
            service.afterPropertiesSet();

            BatchResult result = service.forFlagKeys("uuid-1", "uuid-2")
                .values()
                .join();

            assertThat(result.flagIdentifiers()).containsExactlyInAnyOrder("uuid-1", "uuid-2");
        }

        @Test
        void hasFlag_returnsTrueForPresentFlags() {
            FlagConfig config1 = testDefaultBooleanFlagConfig("uuid-1", "flag-1", false);

            when(flagConfigService.getFlagConfigByKey("uuid-1")).thenReturn(Optional.of(config1));

            IzanamiResult mockResult = createMockSuccessResult(Map.of(
                "uuid-1", createBooleanFeatureValue(true)
            ));
            when(mockClient.featureValues(any(FeatureRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResult));

            IzanamiServiceImpl service = createServiceWithMockFactory(validProperties());
            service.afterPropertiesSet();

            BatchResult result = service.forFlagKeys("uuid-1")
                .values()
                .join();

            assertThat(result.hasFlag("uuid-1")).isTrue();
            assertThat(result.hasFlag("non-existent")).isFalse();
        }

        @Test
        void booleanValue_forNonExistentFlag_returnsDefault() {
            FlagConfig config1 = testDefaultBooleanFlagConfig("uuid-1", "flag-1", false);

            when(flagConfigService.getFlagConfigByKey("uuid-1")).thenReturn(Optional.of(config1));

            IzanamiResult mockResult = createMockSuccessResult(Map.of(
                "uuid-1", createBooleanFeatureValue(true)
            ));
            when(mockClient.featureValues(any(FeatureRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResult));

            IzanamiServiceImpl service = createServiceWithMockFactory(validProperties());
            service.afterPropertiesSet();

            BatchResult result = service.forFlagKeys("uuid-1")
                .values()
                .join();

            assertThat(result.booleanValue("non-existent")).isFalse();
        }
    }

    // =====================================================================
    // Metadata Tests
    // =====================================================================

    @Nested
    class MetadataTests {

        @Test
        void booleanValueDetails_includesAllMetadata() {
            FlagConfig config1 = testDefaultBooleanFlagConfig("uuid-1", "flag-1", false);

            when(flagConfigService.getFlagConfigByKey("uuid-1")).thenReturn(Optional.of(config1));

            IzanamiResult mockResult = createMockSuccessResult(Map.of(
                "uuid-1", createBooleanFeatureValue(true)
            ));
            when(mockClient.featureValues(any(FeatureRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResult));

            IzanamiServiceImpl service = createServiceWithMockFactory(validProperties());
            service.afterPropertiesSet();

            BatchResult result = service.forFlagKeys("uuid-1")
                .values()
                .join();

            ResultValueWithDetails<Boolean> details = result.booleanValueDetails("uuid-1");

            assertThat(details.value()).isTrue();
            assertThat(details.metadata().get(FlagMetadataKeys.FLAG_CONFIG_KEY)).isEqualTo("uuid-1");
            assertThat(details.metadata().get(FlagMetadataKeys.FLAG_CONFIG_NAME)).isEqualTo("flag-1");
            assertThat(details.metadata().get(FlagMetadataKeys.FLAG_CONFIG_VALUE_TYPE)).isEqualTo("BOOLEAN");
            assertThat(details.metadata().get(FlagMetadataKeys.FLAG_VALUE_SOURCE)).isEqualTo(FlagValueSource.IZANAMI.name());
            assertThat(details.metadata().get(FlagMetadataKeys.FLAG_EVALUATION_REASON)).isEqualTo("ORIGIN_OR_CACHE");
        }
    }

    // =====================================================================
    // FAIL Strategy Tests
    // =====================================================================

    @Nested
    class FailStrategyTests {

        @Test
        void booleanValue_withFailStrategyAndError_throwsException() {
            FlagConfig config1 = testFailBooleanFlagConfig("uuid-1", "flag-1");

            when(flagConfigService.getFlagConfigByKey("uuid-1")).thenReturn(Optional.of(config1));

            // Create service without initializing client (blank URL) to simulate error
            IzanamiServiceImpl service = new IzanamiServiceImpl(blankUrlProperties(), flagConfigService, objectMapper, noOpContextResolver());
            service.afterPropertiesSet();

            BatchResult result = service.forFlagKeys("uuid-1")
                .values()
                .join();  // values() succeeds

            // Accessing value with FAIL strategy should throw
            org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> {
                result.booleanValue("uuid-1");
            });
        }

        @Test
        void stringValue_withFailStrategyAndError_throwsException() {
            FlagConfig config1 = testFailStringFlagConfig("uuid-1", "flag-1");

            when(flagConfigService.getFlagConfigByKey("uuid-1")).thenReturn(Optional.of(config1));

            // Create service without initializing client (blank URL) to simulate error
            IzanamiServiceImpl service = new IzanamiServiceImpl(blankUrlProperties(), flagConfigService, objectMapper, noOpContextResolver());
            service.afterPropertiesSet();

            BatchResult result = service.forFlagKeys("uuid-1")
                .values()
                .join();  // values() succeeds

            // Accessing value with FAIL strategy should throw
            org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> {
                result.stringValue("uuid-1");
            });
        }

        @Test
        void numberValue_withFailStrategyAndError_throwsException() {
            FlagConfig config1 = testFailNumberFlagConfig("uuid-1", "flag-1");

            when(flagConfigService.getFlagConfigByKey("uuid-1")).thenReturn(Optional.of(config1));

            // Create service without initializing client (blank URL) to simulate error
            IzanamiServiceImpl service = new IzanamiServiceImpl(blankUrlProperties(), flagConfigService, objectMapper, noOpContextResolver());
            service.afterPropertiesSet();

            BatchResult result = service.forFlagKeys("uuid-1")
                .values()
                .join();  // values() succeeds

            // Accessing value with FAIL strategy should throw
            org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> {
                result.numberValue("uuid-1");
            });
        }

        @Test
        void withPerRequestFailStrategy_overridesFlagConfigDefault() {
            FlagConfig config1 = testDefaultBooleanFlagConfig("uuid-1", "flag-1", true);

            when(flagConfigService.getFlagConfigByKey("uuid-1")).thenReturn(Optional.of(config1));

            // Create service without initializing client (blank URL) to simulate error
            IzanamiServiceImpl service = new IzanamiServiceImpl(blankUrlProperties(), flagConfigService, objectMapper, noOpContextResolver());
            service.afterPropertiesSet();

            // Override with FAIL strategy per-request
            BatchResult result = service.forFlagKeys("uuid-1")
                .withErrorStrategy(FeatureClientErrorStrategy.failStrategy())
                .values()
                .join();  // values() succeeds

            // Accessing value should throw despite FlagConfig having DEFAULT_VALUE strategy
            org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> {
                result.booleanValue("uuid-1");
            });
        }

        private static FlagConfig testFailBooleanFlagConfig(String key, String name) {
            return new FlagConfig(
                key,
                name,
                "Test flag description",
                FlagValueType.BOOLEAN,
                ErrorStrategy.FAIL,
                FeatureClientErrorStrategy.failStrategy(),
                null,
                null
            );
        }

        private static FlagConfig testFailStringFlagConfig(String key, String name) {
            return new FlagConfig(
                key,
                name,
                "Test flag description",
                FlagValueType.STRING,
                ErrorStrategy.FAIL,
                FeatureClientErrorStrategy.failStrategy(),
                null,
                null
            );
        }

        private static FlagConfig testFailNumberFlagConfig(String key, String name) {
            return new FlagConfig(
                key,
                name,
                "Test flag description",
                FlagValueType.INTEGER,
                ErrorStrategy.FAIL,
                FeatureClientErrorStrategy.failStrategy(),
                null,
                null
            );
        }
    }
}
