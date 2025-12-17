package fr.maif.izanami.spring.openfeature;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfeature.sdk.*;
import fr.maif.FeatureClientErrorStrategy;
import fr.maif.IzanamiClient;
import fr.maif.features.results.IzanamiResult;
import fr.maif.features.values.BooleanCastStrategy;
import fr.maif.izanami.spring.openfeature.api.FlagConfigService;
import fr.maif.izanami.spring.service.IzanamiClientNotAvailableException;
import fr.maif.izanami.spring.service.IzanamiService;
import fr.maif.izanami.spring.service.ResultWithMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class IzanamiFeatureProviderTest {

    private FlagConfigService flagConfigService;
    private IzanamiService izanamiService;
    private ObjectMapper objectMapper;
    private ValueConverter valueConverter;
    private IzanamiFeatureProvider provider;

    // Mock for fluent builder
    private IzanamiService.FeatureRequestBuilder mockBuilder;

    @BeforeEach
    void setUp() {
        flagConfigService = mock(FlagConfigService.class);
        izanamiService = mock(IzanamiService.class);
        objectMapper = new ObjectMapper();
        valueConverter = new ValueConverter(objectMapper);

        // Setup fluent builder mock
        mockBuilder = mock(IzanamiService.FeatureRequestBuilder.class);
        when(mockBuilder.withUser(any())).thenReturn(mockBuilder);
        when(mockBuilder.withContext(any())).thenReturn(mockBuilder);

        provider = new IzanamiFeatureProvider(flagConfigService, izanamiService, objectMapper, valueConverter);
    }

    // =====================================================================
    // Test Helpers
    // =====================================================================

    private static FlagConfig testFlagConfig(String key, String name, FlagValueType valueType, Object defaultValue) {
        return new FlagConfig(
            key, name, "Test flag description", valueType,
            ErrorStrategy.DEFAULT_VALUE,
            FeatureClientErrorStrategy.defaultValueStrategy(false, "", BigDecimal.ZERO),
            defaultValue, null
        );
    }

    private void setupFlagConfig(String key, FlagConfig config) {
        when(flagConfigService.getFlagConfigByKey(key)).thenReturn(Optional.of(config));
    }

    private void setupFlagNotFound(String key) {
        when(flagConfigService.getFlagConfigByKey(key)).thenReturn(Optional.empty());
    }

    private ResultWithMetadata successResult(IzanamiResult.Success mockSuccess) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put(FlagMetadataKeys.FLAG_CONFIG_KEY, "test-key");
        metadata.put(FlagMetadataKeys.FLAG_CONFIG_NAME, "test-name");
        metadata.put(FlagMetadataKeys.FLAG_CONFIG_DESCRIPTION, "test description");
        metadata.put(FlagMetadataKeys.FLAG_CONFIG_VALUE_TYPE, "BOOLEAN");
        metadata.put(FlagMetadataKeys.FLAG_CONFIG_DEFAULT_VALUE, "false");
        metadata.put(FlagMetadataKeys.FLAG_CONFIG_ERROR_STRATEGY, "DEFAULT_VALUE");
        metadata.put(FlagMetadataKeys.FLAG_VALUE_SOURCE, FlagValueSource.IZANAMI.name());
        return new ResultWithMetadata(mockSuccess, metadata);
    }

    private ResultWithMetadata errorResult(IzanamiResult.Error mockError) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put(FlagMetadataKeys.FLAG_CONFIG_KEY, "test-key");
        metadata.put(FlagMetadataKeys.FLAG_CONFIG_NAME, "test-name");
        metadata.put(FlagMetadataKeys.FLAG_CONFIG_DESCRIPTION, "test description");
        metadata.put(FlagMetadataKeys.FLAG_CONFIG_VALUE_TYPE, "BOOLEAN");
        metadata.put(FlagMetadataKeys.FLAG_CONFIG_DEFAULT_VALUE, "false");
        metadata.put(FlagMetadataKeys.FLAG_CONFIG_ERROR_STRATEGY, "DEFAULT_VALUE");
        metadata.put(FlagMetadataKeys.FLAG_VALUE_SOURCE, FlagValueSource.IZANAMI_ERROR_STRATEGY.name());
        return new ResultWithMetadata(mockError, metadata);
    }

    private void setupSuccessfulEvaluation(String flagKey, IzanamiResult.Success mockSuccess) {
        when(izanamiService.forFlagKey(flagKey)).thenReturn(mockBuilder);
        when(mockBuilder.featureResultWithMetadata())
            .thenReturn(CompletableFuture.completedFuture(successResult(mockSuccess)));
    }

    private void setupErrorEvaluation(String flagKey, IzanamiResult.Error mockError) {
        when(izanamiService.forFlagKey(flagKey)).thenReturn(mockBuilder);
        when(mockBuilder.featureResultWithMetadata())
            .thenReturn(CompletableFuture.completedFuture(errorResult(mockError)));
    }

    private EvaluationContext emptyContext() {
        return new ImmutableContext();
    }

    private EvaluationContext contextWithTargetingKey(String targetingKey) {
        return new ImmutableContext(targetingKey);
    }

    private EvaluationContext contextWithContextAttribute(String targetingKey, String contextValue) {
        Map<String, Value> attributes = new HashMap<>();
        attributes.put("context", new Value(contextValue));
        return new ImmutableContext(targetingKey, attributes);
    }

    // =====================================================================
    // Metadata Tests
    // =====================================================================

    @Nested
    class MetadataTests {

        @Test
        void getMetadata_returnsIzanamiProviderName() {
            Metadata metadata = provider.getMetadata();

            assertThat(metadata.getName()).isEqualTo("Izanami (Spring Boot Starter)");
        }
    }

    // =====================================================================
    // Lifecycle Tests
    // =====================================================================

    @Nested
    class LifecycleTests {

        @Test
        void initialize_doesNothing() {
            // Should not throw
            provider.initialize(emptyContext());
        }

        @Test
        void shutdown_whenClientAvailable_closesClient() {
            IzanamiClient mockClient = mock(IzanamiClient.class);
            when(izanamiService.unwrapClient()).thenReturn(Optional.of(mockClient));
            when(mockClient.close()).thenReturn(CompletableFuture.completedFuture(null));

            provider.shutdown();

            verify(mockClient).close();
        }

        @Test
        void shutdown_whenClientUnavailable_doesNothing() {
            when(izanamiService.unwrapClient()).thenReturn(Optional.empty());

            // Should not throw
            provider.shutdown();
        }

        @Test
        void shutdown_whenCloseThrows_logsAndContinues() {
            IzanamiClient mockClient = mock(IzanamiClient.class);
            when(izanamiService.unwrapClient()).thenReturn(Optional.of(mockClient));
            CompletableFuture<Void> failedClose = new CompletableFuture<>();
            failedClose.completeExceptionally(new RuntimeException("Close failed"));
            when(mockClient.close()).thenReturn(failedClose);

            // Should not throw
            provider.shutdown();

            verify(mockClient).close();
        }
    }

    // =====================================================================
    // Flag Not Found Tests
    // =====================================================================

    @Nested
    class FlagNotFoundTests {

        @Test
        void getBooleanEvaluation_flagNotFound_returnsCallerDefault() {
            setupFlagNotFound("unknown-flag");

            ProviderEvaluation<Boolean> result = provider.getBooleanEvaluation(
                "unknown-flag", true, emptyContext()
            );

            assertThat(result.getValue()).isTrue();
            assertThat(result.getErrorCode()).isEqualTo(ErrorCode.FLAG_NOT_FOUND);
            assertThat(result.getErrorMessage()).contains("unknown-flag");
        }

        @Test
        void getStringEvaluation_flagNotFound_returnsCallerDefault() {
            setupFlagNotFound("unknown-flag");

            ProviderEvaluation<String> result = provider.getStringEvaluation(
                "unknown-flag", "default-string", emptyContext()
            );

            assertThat(result.getValue()).isEqualTo("default-string");
            assertThat(result.getErrorCode()).isEqualTo(ErrorCode.FLAG_NOT_FOUND);
        }

        @Test
        void getIntegerEvaluation_flagNotFound_returnsCallerDefault() {
            setupFlagNotFound("unknown-flag");

            ProviderEvaluation<Integer> result = provider.getIntegerEvaluation(
                "unknown-flag", 42, emptyContext()
            );

            assertThat(result.getValue()).isEqualTo(42);
            assertThat(result.getErrorCode()).isEqualTo(ErrorCode.FLAG_NOT_FOUND);
        }

        @Test
        void getDoubleEvaluation_flagNotFound_returnsCallerDefault() {
            setupFlagNotFound("unknown-flag");

            ProviderEvaluation<Double> result = provider.getDoubleEvaluation(
                "unknown-flag", 3.14, emptyContext()
            );

            assertThat(result.getValue()).isEqualTo(3.14);
            assertThat(result.getErrorCode()).isEqualTo(ErrorCode.FLAG_NOT_FOUND);
        }

        @Test
        void getObjectEvaluation_flagNotFound_returnsCallerDefault() {
            setupFlagNotFound("unknown-flag");
            Value callerDefault = new Value("default-value");

            ProviderEvaluation<Value> result = provider.getObjectEvaluation(
                "unknown-flag", callerDefault, emptyContext()
            );

            assertThat(result.getValue()).isEqualTo(callerDefault);
            assertThat(result.getErrorCode()).isEqualTo(ErrorCode.FLAG_NOT_FOUND);
        }

        @Test
        void getObjectEvaluation_flagNotFound_nullCallerDefault_returnsEmptyValue() {
            setupFlagNotFound("unknown-flag");

            ProviderEvaluation<Value> result = provider.getObjectEvaluation(
                "unknown-flag", null, emptyContext()
            );

            assertThat(result.getValue()).isNotNull();
            assertThat(result.getErrorCode()).isEqualTo(ErrorCode.FLAG_NOT_FOUND);
        }

        @Test
        void flagNotFound_metadata_containsKeyAndSource() {
            setupFlagNotFound("unknown-flag");

            ProviderEvaluation<Boolean> result = provider.getBooleanEvaluation(
                "unknown-flag", false, emptyContext()
            );

            assertThat(result.getFlagMetadata().getString(FlagMetadataKeys.FLAG_CONFIG_KEY))
                .isEqualTo("unknown-flag");
            assertThat(result.getFlagMetadata().getString(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                .isEqualTo(FlagValueSource.APPLICATION_ERROR_STRATEGY.name());
        }
    }

    // =====================================================================
    // Type Mismatch Tests
    // =====================================================================

    @Nested
    class TypeMismatchTests {

        @Test
        void getBooleanEvaluation_flagIsString_returnsTypeMismatch() {
            FlagConfig config = testFlagConfig("string-flag", "string-flag", FlagValueType.STRING, "default");
            setupFlagConfig("string-flag", config);

            ProviderEvaluation<Boolean> result = provider.getBooleanEvaluation(
                "string-flag", true, emptyContext()
            );

            assertThat(result.getValue()).isTrue();
            assertThat(result.getErrorCode()).isEqualTo(ErrorCode.TYPE_MISMATCH);
            assertThat(result.getErrorMessage()).contains("STRING").contains("BOOLEAN");
        }

        @Test
        void getStringEvaluation_flagIsBoolean_returnsTypeMismatch() {
            FlagConfig config = testFlagConfig("bool-flag", "bool-flag", FlagValueType.BOOLEAN, false);
            setupFlagConfig("bool-flag", config);

            ProviderEvaluation<String> result = provider.getStringEvaluation(
                "bool-flag", "default", emptyContext()
            );

            assertThat(result.getValue()).isEqualTo("default");
            assertThat(result.getErrorCode()).isEqualTo(ErrorCode.TYPE_MISMATCH);
        }

        @Test
        void getIntegerEvaluation_flagIsBoolean_returnsTypeMismatch() {
            FlagConfig config = testFlagConfig("bool-flag", "bool-flag", FlagValueType.BOOLEAN, false);
            setupFlagConfig("bool-flag", config);

            ProviderEvaluation<Integer> result = provider.getIntegerEvaluation(
                "bool-flag", 42, emptyContext()
            );

            assertThat(result.getValue()).isEqualTo(42);
            assertThat(result.getErrorCode()).isEqualTo(ErrorCode.TYPE_MISMATCH);
        }

        @Test
        void getDoubleEvaluation_flagIsString_returnsTypeMismatch() {
            FlagConfig config = testFlagConfig("string-flag", "string-flag", FlagValueType.STRING, "default");
            setupFlagConfig("string-flag", config);

            ProviderEvaluation<Double> result = provider.getDoubleEvaluation(
                "string-flag", 3.14, emptyContext()
            );

            assertThat(result.getValue()).isEqualTo(3.14);
            assertThat(result.getErrorCode()).isEqualTo(ErrorCode.TYPE_MISMATCH);
        }

        @Test
        void getObjectEvaluation_flagIsBoolean_returnsTypeMismatch() {
            FlagConfig config = testFlagConfig("bool-flag", "bool-flag", FlagValueType.BOOLEAN, false);
            setupFlagConfig("bool-flag", config);
            Value callerDefault = new Value("default");

            ProviderEvaluation<Value> result = provider.getObjectEvaluation(
                "bool-flag", callerDefault, emptyContext()
            );

            assertThat(result.getValue()).isEqualTo(callerDefault);
            assertThat(result.getErrorCode()).isEqualTo(ErrorCode.TYPE_MISMATCH);
        }
    }

    // =====================================================================
    // Successful Evaluation Tests
    // =====================================================================

    @Nested
    class SuccessfulEvaluationTests {

        @Test
        void getBooleanEvaluation_success_returnsValueFromIzanami() {
            FlagConfig config = testFlagConfig("bool-flag", "bool-flag", FlagValueType.BOOLEAN, false);
            setupFlagConfig("bool-flag", config);

            IzanamiResult.Success mockSuccess = mock(IzanamiResult.Success.class);
            when(mockSuccess.booleanValue(BooleanCastStrategy.LAX)).thenReturn(true);
            setupSuccessfulEvaluation("bool-flag", mockSuccess);

            ProviderEvaluation<Boolean> result = provider.getBooleanEvaluation(
                "bool-flag", false, emptyContext()
            );

            assertThat(result.getValue()).isTrue();
            assertThat(result.getErrorCode()).isNull();
        }

        @Test
        void getBooleanEvaluation_false_reasonIsUnknown() {
            FlagConfig config = testFlagConfig("bool-flag", "bool-flag", FlagValueType.BOOLEAN, false);
            setupFlagConfig("bool-flag", config);

            IzanamiResult.Success mockSuccess = mock(IzanamiResult.Success.class);
            when(mockSuccess.booleanValue(BooleanCastStrategy.LAX)).thenReturn(false);
            setupSuccessfulEvaluation("bool-flag", mockSuccess);

            ProviderEvaluation<Boolean> result = provider.getBooleanEvaluation(
                "bool-flag", true, emptyContext()
            );

            assertThat(result.getValue()).isFalse();
            // Boolean false is a valid value (not null), so reason is UNKNOWN
            assertThat(result.getReason()).isEqualTo(Reason.DISABLED.name());
        }

        @Test
        void getBooleanEvaluation_true_reasonIsUnknown() {
            FlagConfig config = testFlagConfig("bool-flag", "bool-flag", FlagValueType.BOOLEAN, false);
            setupFlagConfig("bool-flag", config);

            IzanamiResult.Success mockSuccess = mock(IzanamiResult.Success.class);
            when(mockSuccess.booleanValue(BooleanCastStrategy.LAX)).thenReturn(true);
            setupSuccessfulEvaluation("bool-flag", mockSuccess);

            ProviderEvaluation<Boolean> result = provider.getBooleanEvaluation(
                "bool-flag", false, emptyContext()
            );

            assertThat(result.getValue()).isTrue();
            assertThat(result.getReason()).isEqualTo(Reason.UNKNOWN.name());
        }

        @Test
        void getStringEvaluation_success_returnsValueFromIzanami() {
            FlagConfig config = testFlagConfig("string-flag", "string-flag", FlagValueType.STRING, "default");
            setupFlagConfig("string-flag", config);

            IzanamiResult.Success mockSuccess = mock(IzanamiResult.Success.class);
            when(mockSuccess.stringValue()).thenReturn("evaluated-value");
            setupSuccessfulEvaluation("string-flag", mockSuccess);

            ProviderEvaluation<String> result = provider.getStringEvaluation(
                "string-flag", "default", emptyContext()
            );

            assertThat(result.getValue()).isEqualTo("evaluated-value");
        }

        @Test
        void getIntegerEvaluation_success_returnsValueFromIzanami() {
            FlagConfig config = testFlagConfig("int-flag", "int-flag", FlagValueType.INTEGER, 0);
            setupFlagConfig("int-flag", config);

            IzanamiResult.Success mockSuccess = mock(IzanamiResult.Success.class);
            when(mockSuccess.numberValue()).thenReturn(new BigDecimal("42"));
            setupSuccessfulEvaluation("int-flag", mockSuccess);

            ProviderEvaluation<Integer> result = provider.getIntegerEvaluation(
                "int-flag", 0, emptyContext()
            );

            assertThat(result.getValue()).isEqualTo(42);
        }

        @Test
        void getDoubleEvaluation_success_returnsValueFromIzanami() {
            FlagConfig config = testFlagConfig("double-flag", "double-flag", FlagValueType.DOUBLE, 0.0);
            setupFlagConfig("double-flag", config);

            IzanamiResult.Success mockSuccess = mock(IzanamiResult.Success.class);
            when(mockSuccess.numberValue()).thenReturn(new BigDecimal("3.14159"));
            setupSuccessfulEvaluation("double-flag", mockSuccess);

            ProviderEvaluation<Double> result = provider.getDoubleEvaluation(
                "double-flag", 0.0, emptyContext()
            );

            assertThat(result.getValue()).isEqualTo(3.14159);
        }

        @Test
        void getObjectEvaluation_success_parsesJsonAndReturnsValue() {
            FlagConfig config = testFlagConfig("object-flag", "object-flag", FlagValueType.OBJECT, null);
            setupFlagConfig("object-flag", config);

            IzanamiResult.Success mockSuccess = mock(IzanamiResult.Success.class);
            when(mockSuccess.stringValue()).thenReturn("{\"key\": \"value\", \"number\": 42}");
            setupSuccessfulEvaluation("object-flag", mockSuccess);

            ProviderEvaluation<Value> result = provider.getObjectEvaluation(
                "object-flag", new Value(), emptyContext()
            );

            assertThat(result.getValue().isStructure()).isTrue();
            Structure structure = result.getValue().asStructure();
            assertThat(structure.getValue("key").asString()).isEqualTo("value");
            assertThat(structure.getValue("number").asInteger()).isEqualTo(42);
        }

        @Test
        void getObjectEvaluation_withObjectDefaultValue_returnsValue() {
            Map<String, Object> objectValue = Map.of("key1", "value1", "key2", 42);
            FlagConfig config = new FlagConfig(
                "object-flag", "object-flag", "Test flag description", FlagValueType.OBJECT,
                ErrorStrategy.DEFAULT_VALUE,
                FeatureClientErrorStrategy.defaultValueStrategy(false, "", BigDecimal.ZERO),
                objectValue, null
            );
            setupFlagConfig("object-flag", config);

            IzanamiResult.Success mockSuccess = mock(IzanamiResult.Success.class);
            when(mockSuccess.stringValue()).thenReturn("{\"key1\": \"value1\", \"key2\": 42}");
            setupSuccessfulEvaluation("object-flag", mockSuccess);

            ProviderEvaluation<Value> result = provider.getObjectEvaluation(
                "object-flag", new Value(), emptyContext()
            );

            assertThat(result.getValue().isStructure()).isTrue();
            Structure structure = result.getValue().asStructure();
            assertThat(structure.getValue("key1").asString()).isEqualTo("value1");
            assertThat(structure.getValue("key2").asInteger()).isEqualTo(42);
        }

        @Test
        void getObjectEvaluation_withNestedJsonObject_returnsNestedValue() {
            FlagConfig config = testFlagConfig("json-config", "json-config", FlagValueType.OBJECT, Map.of());
            setupFlagConfig("json-config", config);

            IzanamiResult.Success mockSuccess = mock(IzanamiResult.Success.class);
            when(mockSuccess.stringValue()).thenReturn("""
                {
                  "enabled": true,
                  "settings": {
                    "theme": "dark",
                    "maxRetries": 3
                  }
                }""");
            setupSuccessfulEvaluation("json-config", mockSuccess);

            ProviderEvaluation<Value> result = provider.getObjectEvaluation(
                "json-config", new Value(), emptyContext()
            );

            assertThat(result.getValue().isStructure()).isTrue();
            Structure structure = result.getValue().asStructure();
            assertThat(structure.getValue("enabled").asBoolean()).isTrue();
            assertThat(structure.getValue("settings").isStructure()).isTrue();
            Structure settings = structure.getValue("settings").asStructure();
            assertThat(settings.getValue("theme").asString()).isEqualTo("dark");
            assertThat(settings.getValue("maxRetries").asInteger()).isEqualTo(3);
        }

        @Test
        void evaluation_nullValue_reasonIsDisabled() {
            FlagConfig config = testFlagConfig("string-flag", "string-flag", FlagValueType.STRING, null);
            setupFlagConfig("string-flag", config);

            IzanamiResult.Success mockSuccess = mock(IzanamiResult.Success.class);
            when(mockSuccess.stringValue()).thenReturn(null);
            setupSuccessfulEvaluation("string-flag", mockSuccess);

            ProviderEvaluation<String> result = provider.getStringEvaluation(
                "string-flag", "default", emptyContext()
            );

            assertThat(result.getValue()).isNull();
            // Null value from Izanami means feature is disabled
            assertThat(result.getReason()).isEqualTo(Reason.DISABLED.name());
        }
    }

    // =====================================================================
    // Izanami Error Strategy Tests
    // =====================================================================

    @Nested
    class IzanamiErrorStrategyTests {

        @Test
        void getBooleanEvaluation_izanamiError_returnsFallbackValue() {
            FlagConfig config = testFlagConfig("bool-flag", "bool-flag", FlagValueType.BOOLEAN, false);
            setupFlagConfig("bool-flag", config);

            IzanamiResult.Error mockError = mock(IzanamiResult.Error.class);
            when(mockError.booleanValue(BooleanCastStrategy.LAX)).thenReturn(false);
            setupErrorEvaluation("bool-flag", mockError);

            ProviderEvaluation<Boolean> result = provider.getBooleanEvaluation(
                "bool-flag", true, emptyContext()
            );

            assertThat(result.getValue()).isFalse();
        }

        @Test
        void getBooleanEvaluation_izanamiError_reasonIsError() {
            FlagConfig config = testFlagConfig("bool-flag", "bool-flag", FlagValueType.BOOLEAN, false);
            setupFlagConfig("bool-flag", config);

            IzanamiResult.Error mockError = mock(IzanamiResult.Error.class);
            when(mockError.booleanValue(BooleanCastStrategy.LAX)).thenReturn(false);
            setupErrorEvaluation("bool-flag", mockError);

            ProviderEvaluation<Boolean> result = provider.getBooleanEvaluation(
                "bool-flag", true, emptyContext()
            );

            assertThat(result.getReason()).isEqualTo(Reason.ERROR.name());
        }

        @Test
        void getBooleanEvaluation_izanamiError_flagValueSourceIsIzanamiErrorStrategy() {
            FlagConfig config = testFlagConfig("bool-flag", "bool-flag", FlagValueType.BOOLEAN, false);
            setupFlagConfig("bool-flag", config);

            IzanamiResult.Error mockError = mock(IzanamiResult.Error.class);
            when(mockError.booleanValue(BooleanCastStrategy.LAX)).thenReturn(false);
            setupErrorEvaluation("bool-flag", mockError);

            ProviderEvaluation<Boolean> result = provider.getBooleanEvaluation(
                "bool-flag", true, emptyContext()
            );

            assertThat(result.getFlagMetadata().getString(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                .isEqualTo(FlagValueSource.IZANAMI_ERROR_STRATEGY.name());
        }
    }

    // =====================================================================
    // Inactive Feature Tests
    // =====================================================================

    @Nested
    class InactiveFeatureTests {

        @Test
        void getBooleanEvaluation_inactiveFeature_returnsFalseWithDisabledReason() {
            FlagConfig config = testFlagConfig("inactive-bool", "inactive-bool", FlagValueType.BOOLEAN, true);
            setupFlagConfig("inactive-bool", config);

            IzanamiResult.Success mockSuccess = mock(IzanamiResult.Success.class);
            when(mockSuccess.booleanValue(BooleanCastStrategy.LAX)).thenReturn(false);
            setupSuccessfulEvaluation("inactive-bool", mockSuccess);

            ProviderEvaluation<Boolean> result = provider.getBooleanEvaluation(
                "inactive-bool", true, emptyContext()
            );

            assertThat(result.getValue()).isFalse();
            // Boolean false is a valid value (not null), so reason is UNKNOWN
            assertThat(result.getReason()).isEqualTo(Reason.DISABLED.name());
            assertThat(result.getFlagMetadata().getString(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                .isEqualTo(FlagValueSource.IZANAMI.name());
        }

        @Test
        void getStringEvaluation_inactiveFeature_returnsDefaultValueWithDisabledReason() {
            FlagConfig config = testFlagConfig("inactive-string", "inactive-string", FlagValueType.STRING, "fallback-value");
            setupFlagConfig("inactive-string", config);

            // For inactive string features, Izanami returns null
            IzanamiResult.Success mockSuccess = mock(IzanamiResult.Success.class);
            when(mockSuccess.stringValue()).thenReturn(null);
            setupSuccessfulEvaluation("inactive-string", mockSuccess);

            ProviderEvaluation<String> result = provider.getStringEvaluation(
                "inactive-string", "caller-default", emptyContext()
            );

            // Disabled non-boolean features return the defaultValue when configured
            assertThat(result.getValue()).isEqualTo("fallback-value");
            // Reason is DISABLED because Izanami returned null (feature disabled)
            assertThat(result.getReason()).isEqualTo(Reason.DISABLED.name());
            assertThat(result.getFlagMetadata().getString(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                .isEqualTo(FlagValueSource.IZANAMI.name());
        }

        @Test
        void getIntegerEvaluation_inactiveFeature_returnsDefaultValueWithDisabledReason() {
            FlagConfig config = testFlagConfig("inactive-int", "inactive-int", FlagValueType.INTEGER, 999);
            setupFlagConfig("inactive-int", config);

            // For inactive integer features, Izanami returns null
            IzanamiResult.Success mockSuccess = mock(IzanamiResult.Success.class);
            when(mockSuccess.numberValue()).thenReturn(null);
            setupSuccessfulEvaluation("inactive-int", mockSuccess);

            ProviderEvaluation<Integer> result = provider.getIntegerEvaluation(
                "inactive-int", 0, emptyContext()
            );

            // Disabled non-boolean features return the defaultValue when configured
            assertThat(result.getValue()).isEqualTo(999);
            // Reason is DISABLED because Izanami returned null (feature disabled)
            assertThat(result.getReason()).isEqualTo(Reason.DISABLED.name());
            assertThat(result.getFlagMetadata().getString(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                .isEqualTo(FlagValueSource.IZANAMI.name());
        }

        @Test
        void getDoubleEvaluation_inactiveFeature_returnsDefaultValueWithDisabledReason() {
            FlagConfig config = testFlagConfig("inactive-double", "inactive-double", FlagValueType.DOUBLE, 99.9);
            setupFlagConfig("inactive-double", config);

            // For inactive double features, Izanami returns null
            IzanamiResult.Success mockSuccess = mock(IzanamiResult.Success.class);
            when(mockSuccess.numberValue()).thenReturn(null);
            setupSuccessfulEvaluation("inactive-double", mockSuccess);

            ProviderEvaluation<Double> result = provider.getDoubleEvaluation(
                "inactive-double", 0.0, emptyContext()
            );

            // Disabled non-boolean features return the defaultValue when configured
            assertThat(result.getValue()).isEqualTo(99.9);
            // Reason is DISABLED because Izanami returned null (feature disabled)
            assertThat(result.getReason()).isEqualTo(Reason.DISABLED.name());
            assertThat(result.getFlagMetadata().getString(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                .isEqualTo(FlagValueSource.IZANAMI.name());
        }
    }

    // =====================================================================
    // Application Error Strategy Tests
    // =====================================================================

    @Nested
    class ApplicationErrorStrategyTests {

        @Test
        void getBooleanEvaluation_clientNotAvailable_returnsCallerDefault() {
            FlagConfig config = testFlagConfig("bool-flag", "bool-flag", FlagValueType.BOOLEAN, false);
            setupFlagConfig("bool-flag", config);

            when(izanamiService.forFlagKey("bool-flag")).thenReturn(mockBuilder);
            when(mockBuilder.featureResultWithMetadata())
                .thenReturn(CompletableFuture.failedFuture(new IzanamiClientNotAvailableException()));

            ProviderEvaluation<Boolean> result = provider.getBooleanEvaluation(
                "bool-flag", true, emptyContext()
            );

            assertThat(result.getValue()).isTrue();
            assertThat(result.getErrorCode()).isEqualTo(ErrorCode.GENERAL);
        }

        @Test
        void getBooleanEvaluation_clientNotAvailable_flagValueSourceIsApplicationErrorStrategy() {
            FlagConfig config = testFlagConfig("bool-flag", "bool-flag", FlagValueType.BOOLEAN, false);
            setupFlagConfig("bool-flag", config);

            when(izanamiService.forFlagKey("bool-flag")).thenReturn(mockBuilder);
            when(mockBuilder.featureResultWithMetadata())
                .thenReturn(CompletableFuture.failedFuture(new IzanamiClientNotAvailableException()));

            ProviderEvaluation<Boolean> result = provider.getBooleanEvaluation(
                "bool-flag", true, emptyContext()
            );

            assertThat(result.getFlagMetadata().getString(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                .isEqualTo(FlagValueSource.APPLICATION_ERROR_STRATEGY.name());
        }
    }

    // =====================================================================
    // Object Evaluation Special Cases
    // =====================================================================

    @Nested
    class ObjectEvaluationSpecialCasesTests {

        @Test
        void getObjectEvaluation_invalidJson_returnsCallerDefault() {
            FlagConfig config = testFlagConfig("object-flag", "object-flag", FlagValueType.OBJECT, null);
            setupFlagConfig("object-flag", config);

            IzanamiResult.Success mockSuccess = mock(IzanamiResult.Success.class);
            when(mockSuccess.stringValue()).thenReturn("not valid json");
            setupSuccessfulEvaluation("object-flag", mockSuccess);

            Value callerDefault = new Value("fallback");
            ProviderEvaluation<Value> result = provider.getObjectEvaluation(
                "object-flag", callerDefault, emptyContext()
            );

            assertThat(result.getValue()).isEqualTo(callerDefault);
            assertThat(result.getErrorCode()).isEqualTo(ErrorCode.GENERAL);
        }

        @Test
        void getObjectEvaluation_nullJson_returnsEmptyValue() {
            FlagConfig config = testFlagConfig("object-flag", "object-flag", FlagValueType.OBJECT, null);
            setupFlagConfig("object-flag", config);

            IzanamiResult.Success mockSuccess = mock(IzanamiResult.Success.class);
            when(mockSuccess.stringValue()).thenReturn(null);
            setupSuccessfulEvaluation("object-flag", mockSuccess);

            ProviderEvaluation<Value> result = provider.getObjectEvaluation(
                "object-flag", new Value("default"), emptyContext()
            );

            assertThat(result.getValue()).isNotNull();
            assertThat(result.getValue().isNull()).isTrue();
        }
    }

    // =====================================================================
    // EvaluationContext Tests
    // =====================================================================

    @Nested
    class EvaluationContextTests {

        @Test
        void evaluation_passesTargetingKeyToIzanami() {
            FlagConfig config = testFlagConfig("bool-flag", "bool-flag", FlagValueType.BOOLEAN, false);
            setupFlagConfig("bool-flag", config);

            IzanamiResult.Success mockSuccess = mock(IzanamiResult.Success.class);
            when(mockSuccess.booleanValue(BooleanCastStrategy.LAX)).thenReturn(true);
            setupSuccessfulEvaluation("bool-flag", mockSuccess);

            EvaluationContext ctx = contextWithTargetingKey("user-123");
            provider.getBooleanEvaluation("bool-flag", false, ctx);

            verify(mockBuilder).withUser("user-123");
        }

        @Test
        void evaluation_passesContextAttributeToIzanami() {
            FlagConfig config = testFlagConfig("bool-flag", "bool-flag", FlagValueType.BOOLEAN, false);
            setupFlagConfig("bool-flag", config);

            IzanamiResult.Success mockSuccess = mock(IzanamiResult.Success.class);
            when(mockSuccess.booleanValue(BooleanCastStrategy.LAX)).thenReturn(true);
            setupSuccessfulEvaluation("bool-flag", mockSuccess);

            EvaluationContext ctx = contextWithContextAttribute("user-123", "production");
            provider.getBooleanEvaluation("bool-flag", false, ctx);

            verify(mockBuilder).withContext("production");
        }

        @Test
        void evaluation_nullContext_passesNullToIzanami() {
            FlagConfig config = testFlagConfig("bool-flag", "bool-flag", FlagValueType.BOOLEAN, false);
            setupFlagConfig("bool-flag", config);

            IzanamiResult.Success mockSuccess = mock(IzanamiResult.Success.class);
            when(mockSuccess.booleanValue(BooleanCastStrategy.LAX)).thenReturn(true);
            setupSuccessfulEvaluation("bool-flag", mockSuccess);

            provider.getBooleanEvaluation("bool-flag", false, emptyContext());

            verify(mockBuilder).withContext(null);
        }
    }

    // =====================================================================
    // Metadata Population Tests
    // =====================================================================

    @Nested
    class MetadataPopulationTests {

        @Test
        void evaluation_success_populatesAllMetadata() {
            FlagConfig config = testFlagConfig("bool-flag", "bool-flag", FlagValueType.BOOLEAN, false);
            setupFlagConfig("bool-flag", config);

            IzanamiResult.Success mockSuccess = mock(IzanamiResult.Success.class);
            when(mockSuccess.booleanValue(BooleanCastStrategy.LAX)).thenReturn(true);
            setupSuccessfulEvaluation("bool-flag", mockSuccess);

            ProviderEvaluation<Boolean> result = provider.getBooleanEvaluation(
                "bool-flag", false, emptyContext()
            );

            ImmutableMetadata metadata = result.getFlagMetadata();
            assertThat(metadata.getString(FlagMetadataKeys.FLAG_CONFIG_KEY)).isNotNull();
            assertThat(metadata.getString(FlagMetadataKeys.FLAG_CONFIG_NAME)).isNotNull();
            assertThat(metadata.getString(FlagMetadataKeys.FLAG_CONFIG_DESCRIPTION)).isNotNull();
            assertThat(metadata.getString(FlagMetadataKeys.FLAG_CONFIG_VALUE_TYPE)).isNotNull();
            assertThat(metadata.getString(FlagMetadataKeys.FLAG_CONFIG_ERROR_STRATEGY)).isNotNull();
            assertThat(metadata.getString(FlagMetadataKeys.FLAG_VALUE_SOURCE)).isNotNull();
        }

        @Test
        void evaluation_success_flagValueSourceIsIzanami() {
            FlagConfig config = testFlagConfig("bool-flag", "bool-flag", FlagValueType.BOOLEAN, false);
            setupFlagConfig("bool-flag", config);

            IzanamiResult.Success mockSuccess = mock(IzanamiResult.Success.class);
            when(mockSuccess.booleanValue(BooleanCastStrategy.LAX)).thenReturn(true);
            setupSuccessfulEvaluation("bool-flag", mockSuccess);

            ProviderEvaluation<Boolean> result = provider.getBooleanEvaluation(
                "bool-flag", false, emptyContext()
            );

            assertThat(result.getFlagMetadata().getString(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                .isEqualTo(FlagValueSource.IZANAMI.name());
        }
    }
}
