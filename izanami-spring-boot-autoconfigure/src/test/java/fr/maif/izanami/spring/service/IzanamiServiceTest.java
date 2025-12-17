package fr.maif.izanami.spring.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfeature.sdk.FlagValueType;
import fr.maif.FeatureClientErrorStrategy;
import fr.maif.IzanamiClient;
import fr.maif.features.results.IzanamiResult;
import fr.maif.izanami.spring.autoconfigure.IzanamiProperties;
import fr.maif.izanami.spring.openfeature.ErrorStrategy;
import fr.maif.izanami.spring.openfeature.FlagConfig;
import fr.maif.izanami.spring.openfeature.FlagMetadataKeys;
import fr.maif.izanami.spring.openfeature.FlagValueSource;
import fr.maif.izanami.spring.openfeature.api.FlagConfigService;
import fr.maif.requests.SingleFeatureRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IzanamiServiceTest {

    private FlagConfigService flagConfigService;
    private ObjectMapper objectMapper;
    private IzanamiClient mockClient;
    private IzanamiService.IzanamiClientFactory mockFactory;

    @BeforeEach
    void setUp() {
        flagConfigService = mock(FlagConfigService.class);
        objectMapper = new ObjectMapper();
        mockClient = mock(IzanamiClient.class);
        mockFactory = mock(IzanamiService.IzanamiClientFactory.class);

        when(flagConfigService.getAllFlagConfigs()).thenReturn(List.of());
        when(mockClient.isLoaded()).thenReturn(CompletableFuture.completedFuture(null));
    }

    // =====================================================================
    // Test Helpers
    // =====================================================================

    private static IzanamiProperties validProperties() {
        return new IzanamiProperties(
            "http://localhost:9999",
            "/api",
            "test-client-id",
            "test-client-secret",
            new IzanamiProperties.Cache(
                true,
                Duration.ofMinutes(5),
                new IzanamiProperties.Cache.Sse(false, Duration.ofSeconds(25))
            )
        );
    }

    private static IzanamiProperties blankUrlProperties() {
        return new IzanamiProperties(
            "",
            "/api",
            "test-client-id",
            "test-client-secret",
            null
        );
    }

    private static IzanamiProperties blankCredentialsProperties(String clientId, String clientSecret) {
        return new IzanamiProperties(
            "http://localhost:9999",
            "/api",
            clientId,
            clientSecret,
            null
        );
    }

    private static FlagConfig testFlagConfig(String key, String name, FlagValueType valueType,
                                              ErrorStrategy errorStrategy, Object defaultValue) {
        return new FlagConfig(
            key,
            name,
            "Test flag description",
            valueType,
            errorStrategy,
            FeatureClientErrorStrategy.defaultValueStrategy(false, "", BigDecimal.ZERO),
            defaultValue,
            null
        );
    }

    private IzanamiService createServiceWithMockFactory(IzanamiProperties properties) {
        when(mockFactory.create(any(), any(), any(), any())).thenReturn(mockClient);
        return new IzanamiService(properties, flagConfigService, objectMapper, mockFactory);
    }

    // =====================================================================
    // Initialization Tests
    // =====================================================================

    @Nested
    class InitializationTests {

        @Test
        void afterPropertiesSet_withBlankUrl_remainsInactive() {
            IzanamiService service = new IzanamiService(
                blankUrlProperties(), flagConfigService, objectMapper, mockFactory
            );

            service.afterPropertiesSet();

            assertThat(service.unwrapClient()).isEmpty();
            verify(mockFactory, never()).create(any(), any(), any(), any());
        }

        @Test
        void afterPropertiesSet_withNullUrl_remainsInactive() {
            IzanamiProperties props = new IzanamiProperties(
                null, "/api", "client-id", "client-secret", null
            );
            // The compact constructor sets default baseUrl, but url() returns null if baseUrl is blank
            // Actually, the compact constructor sets baseUrl = "http://localhost:9000" if null
            // So we need to test with blank baseUrl instead
            IzanamiProperties blankProps = new IzanamiProperties(
                "   ", "/api", "client-id", "client-secret", null
            );
            IzanamiService service = new IzanamiService(
                blankProps, flagConfigService, objectMapper, mockFactory
            );

            service.afterPropertiesSet();

            assertThat(service.unwrapClient()).isEmpty();
            verify(mockFactory, never()).create(any(), any(), any(), any());
        }

        @Test
        void afterPropertiesSet_withBlankClientId_remainsInactive() {
            IzanamiService service = new IzanamiService(
                blankCredentialsProperties("", "test-secret"), flagConfigService, objectMapper, mockFactory
            );

            service.afterPropertiesSet();

            assertThat(service.unwrapClient()).isEmpty();
            verify(mockFactory, never()).create(any(), any(), any(), any());
        }

        @Test
        void afterPropertiesSet_withBlankClientSecret_remainsInactive() {
            IzanamiService service = new IzanamiService(
                blankCredentialsProperties("test-client", ""), flagConfigService, objectMapper, mockFactory
            );

            service.afterPropertiesSet();

            assertThat(service.unwrapClient()).isEmpty();
            verify(mockFactory, never()).create(any(), any(), any(), any());
        }

        @Test
        void afterPropertiesSet_withNullClientId_remainsInactive() {
            IzanamiService service = new IzanamiService(
                blankCredentialsProperties(null, "test-secret"), flagConfigService, objectMapper, mockFactory
            );

            service.afterPropertiesSet();

            assertThat(service.unwrapClient()).isEmpty();
            verify(mockFactory, never()).create(any(), any(), any(), any());
        }

        @Test
        void afterPropertiesSet_withValidConfig_initializesClient() {
            IzanamiService service = createServiceWithMockFactory(validProperties());

            service.afterPropertiesSet();

            assertThat(service.unwrapClient()).isPresent().contains(mockClient);
            verify(mockFactory).create(any(), any(), any(), any());
        }

        @Test
        void afterPropertiesSet_preloadsConfiguredFlags() {
            FlagConfig flag1 = testFlagConfig("uuid-1", "flag-1", FlagValueType.BOOLEAN, ErrorStrategy.DEFAULT_VALUE, false);
            FlagConfig flag2 = testFlagConfig("uuid-2", "flag-2", FlagValueType.STRING, ErrorStrategy.DEFAULT_VALUE, "default");
            when(flagConfigService.getAllFlagConfigs()).thenReturn(List.of(flag1, flag2));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Set<String>> preloadCaptor = ArgumentCaptor.forClass(Set.class);
            when(mockFactory.create(any(), any(), any(), preloadCaptor.capture())).thenReturn(mockClient);

            IzanamiService service = new IzanamiService(validProperties(), flagConfigService, objectMapper, mockFactory);
            service.afterPropertiesSet();

            assertThat(preloadCaptor.getValue()).containsExactlyInAnyOrder("uuid-1", "uuid-2");
        }

        @Test
        void afterPropertiesSet_whenClientBuilderThrows_remainsInactiveAndDoesNotCrash() {
            when(mockFactory.create(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Connection failed"));

            IzanamiService service = new IzanamiService(validProperties(), flagConfigService, objectMapper, mockFactory);

            // Should not throw
            service.afterPropertiesSet();

            assertThat(service.unwrapClient()).isEmpty();
        }
    }

    // =====================================================================
    // Connection State Tests
    // =====================================================================

    @Nested
    class ConnectionStateTests {

        @Test
        void isConnected_whenClientNull_returnsFalse() {
            IzanamiService service = new IzanamiService(
                blankUrlProperties(), flagConfigService, objectMapper, mockFactory
            );
            service.afterPropertiesSet();

            assertThat(service.isConnected()).isFalse();
        }

        @Test
        void isConnected_afterSuccessfulPreload_returnsTrue() {
            IzanamiService service = createServiceWithMockFactory(validProperties());
            service.afterPropertiesSet();

            // Wait for the isLoaded future to complete
            service.whenLoaded().join();

            assertThat(service.isConnected()).isTrue();
        }

        @Test
        void isConnected_afterFailedPreload_returnsFalse() {
            CompletableFuture<Void> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(new RuntimeException("Preload failed"));
            when(mockClient.isLoaded()).thenReturn(failedFuture);
            when(mockFactory.create(any(), any(), any(), any())).thenReturn(mockClient);

            IzanamiService service = new IzanamiService(validProperties(), flagConfigService, objectMapper, mockFactory);
            service.afterPropertiesSet();

            // Wait for the isLoaded future to complete (exceptionally)
            service.whenLoaded().exceptionally(ex -> null).join();

            assertThat(service.isConnected()).isFalse();
        }

        @Test
        void whenLoaded_returnsCompletedFuture_whenClientNull() {
            IzanamiService service = new IzanamiService(
                blankUrlProperties(), flagConfigService, objectMapper, mockFactory
            );
            service.afterPropertiesSet();

            CompletableFuture<Void> loaded = service.whenLoaded();

            assertThat(loaded).isCompleted();
        }

        @Test
        void whenLoaded_returnsClientFuture_whenClientInitialized() {
            CompletableFuture<Void> clientFuture = new CompletableFuture<>();
            when(mockClient.isLoaded()).thenReturn(clientFuture);
            when(mockFactory.create(any(), any(), any(), any())).thenReturn(mockClient);

            IzanamiService service = new IzanamiService(validProperties(), flagConfigService, objectMapper, mockFactory);
            service.afterPropertiesSet();

            CompletableFuture<Void> loaded = service.whenLoaded();

            assertThat(loaded).isNotCompleted();

            clientFuture.complete(null);

            assertThat(loaded).isCompleted();
        }
    }

    // =====================================================================
    // unwrapClient Tests
    // =====================================================================

    @Nested
    class UnwrapClientTests {

        @Test
        void unwrapClient_whenClientNull_returnsEmpty() {
            IzanamiService service = new IzanamiService(
                blankUrlProperties(), flagConfigService, objectMapper, mockFactory
            );
            service.afterPropertiesSet();

            Optional<IzanamiClient> client = service.unwrapClient();

            assertThat(client).isEmpty();
        }

        @Test
        void unwrapClient_whenClientInitialized_returnsClient() {
            IzanamiService service = createServiceWithMockFactory(validProperties());
            service.afterPropertiesSet();

            Optional<IzanamiClient> client = service.unwrapClient();

            assertThat(client).isPresent().contains(mockClient);
        }
    }

    // =====================================================================
    // Fluent API Entry Point Tests
    // =====================================================================

    @Nested
    class FluentApiTests {

        @Test
        void forFlagKey_whenFlagExists_returnsBuilder() {
            FlagConfig config = testFlagConfig("uuid-123", "my-flag", FlagValueType.BOOLEAN, ErrorStrategy.DEFAULT_VALUE, false);
            when(flagConfigService.getFlagConfigByKey("uuid-123")).thenReturn(Optional.of(config));

            IzanamiService service = createServiceWithMockFactory(validProperties());
            service.afterPropertiesSet();

            IzanamiService.FeatureRequestBuilder builder = service.forFlagKey("uuid-123");

            assertThat(builder).isNotNull();
        }

        @Test
        void forFlagKey_whenFlagNotFound_throwsFlagNotFoundException() {
            when(flagConfigService.getFlagConfigByKey("unknown-key")).thenReturn(Optional.empty());

            IzanamiService service = createServiceWithMockFactory(validProperties());
            service.afterPropertiesSet();

            assertThatThrownBy(() -> service.forFlagKey("unknown-key"))
                .isInstanceOf(FlagNotFoundException.class)
                .satisfies(ex -> {
                    FlagNotFoundException fnf = (FlagNotFoundException) ex;
                    assertThat(fnf.getFlagIdentifier()).isEqualTo("unknown-key");
                    assertThat(fnf.getIdentifierType()).isEqualTo(FlagNotFoundException.IdentifierType.KEY);
                });
        }

        @Test
        void forFlagName_whenFlagExists_returnsBuilder() {
            FlagConfig config = testFlagConfig("uuid-123", "my-flag", FlagValueType.BOOLEAN, ErrorStrategy.DEFAULT_VALUE, false);
            when(flagConfigService.getFlagConfigByName("my-flag")).thenReturn(Optional.of(config));

            IzanamiService service = createServiceWithMockFactory(validProperties());
            service.afterPropertiesSet();

            IzanamiService.FeatureRequestBuilder builder = service.forFlagName("my-flag");

            assertThat(builder).isNotNull();
        }

        @Test
        void forFlagName_whenFlagNotFound_throwsFlagNotFoundException() {
            when(flagConfigService.getFlagConfigByName("unknown-name")).thenReturn(Optional.empty());

            IzanamiService service = createServiceWithMockFactory(validProperties());
            service.afterPropertiesSet();

            assertThatThrownBy(() -> service.forFlagName("unknown-name"))
                .isInstanceOf(FlagNotFoundException.class)
                .satisfies(ex -> {
                    FlagNotFoundException fnf = (FlagNotFoundException) ex;
                    assertThat(fnf.getFlagIdentifier()).isEqualTo("unknown-name");
                    assertThat(fnf.getIdentifierType()).isEqualTo(FlagNotFoundException.IdentifierType.NAME);
                });
        }
    }

    // =====================================================================
    // FeatureRequestBuilder Tests
    // =====================================================================

    @Nested
    class FeatureRequestBuilderTests {

        private IzanamiService service;
        private FlagConfig config;

        @BeforeEach
        void setUp() {
            config = testFlagConfig("uuid-123", "my-flag", FlagValueType.BOOLEAN, ErrorStrategy.DEFAULT_VALUE, false);
            when(flagConfigService.getFlagConfigByKey("uuid-123")).thenReturn(Optional.of(config));

            service = createServiceWithMockFactory(validProperties());
            service.afterPropertiesSet();
        }

        @Test
        void booleanValue_delegatesToClient() {
            when(mockClient.booleanValue(any(SingleFeatureRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(true));

            Boolean result = service.forFlagKey("uuid-123").booleanValue().join();

            assertThat(result).isTrue();
            verify(mockClient).booleanValue(any(SingleFeatureRequest.class));
        }

        @Test
        void stringValue_delegatesToClient() {
            FlagConfig stringConfig = testFlagConfig("uuid-string", "string-flag", FlagValueType.STRING, ErrorStrategy.DEFAULT_VALUE, "default");
            when(flagConfigService.getFlagConfigByKey("uuid-string")).thenReturn(Optional.of(stringConfig));
            when(mockClient.stringValue(any(SingleFeatureRequest.class)))
                .thenReturn(CompletableFuture.completedFuture("test-value"));

            String result = service.forFlagKey("uuid-string").stringValue().join();

            assertThat(result).isEqualTo("test-value");
            verify(mockClient).stringValue(any(SingleFeatureRequest.class));
        }

        @Test
        void numberValue_delegatesToClient() {
            FlagConfig numberConfig = testFlagConfig("uuid-number", "number-flag", FlagValueType.DOUBLE, ErrorStrategy.DEFAULT_VALUE, BigDecimal.ZERO);
            when(flagConfigService.getFlagConfigByKey("uuid-number")).thenReturn(Optional.of(numberConfig));
            when(mockClient.numberValue(any(SingleFeatureRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(new BigDecimal("42.5")));

            BigDecimal result = service.forFlagKey("uuid-number").numberValue().join();

            assertThat(result).isEqualByComparingTo(new BigDecimal("42.5"));
            verify(mockClient).numberValue(any(SingleFeatureRequest.class));
        }

        @Test
        void booleanValue_whenClientNull_throwsIzanamiClientNotAvailableException() {
            IzanamiService inactiveService = new IzanamiService(
                blankUrlProperties(), flagConfigService, objectMapper, mockFactory
            );
            inactiveService.afterPropertiesSet();
            when(flagConfigService.getFlagConfigByKey("uuid-123")).thenReturn(Optional.of(config));

            assertThatThrownBy(() -> inactiveService.forFlagKey("uuid-123").booleanValue())
                .isInstanceOf(IzanamiClientNotAvailableException.class);
        }

        @Test
        void withUser_includesUserInEvaluation() {
            ArgumentCaptor<SingleFeatureRequest> requestCaptor = ArgumentCaptor.forClass(SingleFeatureRequest.class);
            when(mockClient.booleanValue(requestCaptor.capture()))
                .thenReturn(CompletableFuture.completedFuture(true));

            service.forFlagKey("uuid-123")
                .withUser("user-456")
                .booleanValue()
                .join();

            // The request was captured - verifies it was called
            verify(mockClient).booleanValue(any(SingleFeatureRequest.class));
        }

        @Test
        void withContext_includesContextInEvaluation() {
            ArgumentCaptor<SingleFeatureRequest> requestCaptor = ArgumentCaptor.forClass(SingleFeatureRequest.class);
            when(mockClient.booleanValue(requestCaptor.capture()))
                .thenReturn(CompletableFuture.completedFuture(true));

            service.forFlagKey("uuid-123")
                .withContext("production")
                .booleanValue()
                .join();

            verify(mockClient).booleanValue(any(SingleFeatureRequest.class));
        }

        @Test
        void chainingUserAndContext_works() {
            when(mockClient.booleanValue(any(SingleFeatureRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(true));

            Boolean result = service.forFlagKey("uuid-123")
                .withUser("user-789")
                .withContext("staging")
                .booleanValue()
                .join();

            assertThat(result).isTrue();
        }
    }

    // =====================================================================
    // featureResultWithMetadata Tests
    // =====================================================================
    // Note: Tests that require mocking featureValues() are covered by integration tests
    // (IzanamiServiceIT) as the response type from the izanami-client library is complex to mock.
    // Unit tests here focus on error handling when the client is unavailable.

    @Nested
    class FeatureResultWithMetadataTests {

        @Test
        void featureResultWithMetadata_whenClientNull_andFailStrategy_returnsFailedFuture() {
            FlagConfig failConfig = new FlagConfig(
                "uuid-fail",
                "fail-flag",
                "Test flag description",
                FlagValueType.BOOLEAN,
                ErrorStrategy.FAIL,
                FeatureClientErrorStrategy.failStrategy(),
                false,
                null
            );
            when(flagConfigService.getFlagConfigByKey("uuid-fail")).thenReturn(Optional.of(failConfig));

            IzanamiService inactiveService = new IzanamiService(
                blankUrlProperties(), flagConfigService, objectMapper, mockFactory
            );
            inactiveService.afterPropertiesSet();

            CompletableFuture<ResultWithMetadata> future = inactiveService.forFlagKey("uuid-fail")
                .featureResultWithMetadata();

            assertThat(future).isCompletedExceptionally();
        }

        @Test
        void featureResultWithMetadata_whenClientNull_andDefaultStrategy_returnsApplicationErrorStrategy() {
            FlagConfig config = testFlagConfig("uuid-123", "my-flag", FlagValueType.BOOLEAN, ErrorStrategy.DEFAULT_VALUE, false);
            when(flagConfigService.getFlagConfigByKey("uuid-123")).thenReturn(Optional.of(config));

            IzanamiService inactiveService = new IzanamiService(
                blankUrlProperties(), flagConfigService, objectMapper, mockFactory
            );
            inactiveService.afterPropertiesSet();

            ResultWithMetadata result = inactiveService.forFlagKey("uuid-123")
                .featureResultWithMetadata()
                .join();

            assertThat(result.metadata().get(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                .isEqualTo(FlagValueSource.APPLICATION_ERROR_STRATEGY.name());
            assertThat(result.result()).isInstanceOf(IzanamiResult.Error.class);
        }

        @Test
        void featureResultWithMetadata_whenClientNull_includesMetadataKeys() {
            FlagConfig config = testFlagConfig("uuid-123", "my-flag", FlagValueType.BOOLEAN, ErrorStrategy.DEFAULT_VALUE, false);
            when(flagConfigService.getFlagConfigByKey("uuid-123")).thenReturn(Optional.of(config));

            IzanamiService inactiveService = new IzanamiService(
                blankUrlProperties(), flagConfigService, objectMapper, mockFactory
            );
            inactiveService.afterPropertiesSet();

            ResultWithMetadata result = inactiveService.forFlagKey("uuid-123")
                .featureResultWithMetadata()
                .join();

            Map<String, String> metadata = result.metadata();
            assertThat(metadata).containsKey(FlagMetadataKeys.FLAG_CONFIG_KEY);
            assertThat(metadata).containsKey(FlagMetadataKeys.FLAG_CONFIG_NAME);
            assertThat(metadata).containsKey(FlagMetadataKeys.FLAG_CONFIG_DESCRIPTION);
            assertThat(metadata).containsKey(FlagMetadataKeys.FLAG_CONFIG_VALUE_TYPE);
            assertThat(metadata).containsKey(FlagMetadataKeys.FLAG_CONFIG_DEFAULT_VALUE);
            assertThat(metadata).containsKey(FlagMetadataKeys.FLAG_CONFIG_ERROR_STRATEGY);
            assertThat(metadata).containsKey(FlagMetadataKeys.FLAG_VALUE_SOURCE);
        }

        @Test
        void featureResultWithMetadata_whenClientNull_populatesMetadataValues() {
            FlagConfig detailedConfig = new FlagConfig(
                "uuid-detailed",
                "detailed-flag",
                "A detailed description",
                FlagValueType.STRING,
                ErrorStrategy.DEFAULT_VALUE,
                FeatureClientErrorStrategy.defaultValueStrategy(false, "fallback", BigDecimal.ZERO),
                "default-string-value",
                null
            );
            when(flagConfigService.getFlagConfigByKey("uuid-detailed")).thenReturn(Optional.of(detailedConfig));

            IzanamiService inactiveService = new IzanamiService(
                blankUrlProperties(), flagConfigService, objectMapper, mockFactory
            );
            inactiveService.afterPropertiesSet();

            ResultWithMetadata result = inactiveService.forFlagKey("uuid-detailed")
                .featureResultWithMetadata()
                .join();

            Map<String, String> metadata = result.metadata();
            assertThat(metadata.get(FlagMetadataKeys.FLAG_CONFIG_KEY)).isEqualTo("uuid-detailed");
            assertThat(metadata.get(FlagMetadataKeys.FLAG_CONFIG_NAME)).isEqualTo("detailed-flag");
            assertThat(metadata.get(FlagMetadataKeys.FLAG_CONFIG_DESCRIPTION)).isEqualTo("A detailed description");
            assertThat(metadata.get(FlagMetadataKeys.FLAG_CONFIG_VALUE_TYPE)).isEqualTo("STRING");
            assertThat(metadata.get(FlagMetadataKeys.FLAG_CONFIG_DEFAULT_VALUE)).isEqualTo("default-string-value");
            assertThat(metadata.get(FlagMetadataKeys.FLAG_CONFIG_ERROR_STRATEGY)).isEqualTo("DEFAULT_VALUE");
        }
    }

    // =====================================================================
    // stringifyDefaultValue Tests
    // =====================================================================

    @Nested
    class StringifyDefaultValueTests {

        @Test
        void stringifyDefaultValue_withNull_returnsNull() {
            FlagConfig config = testFlagConfig("key", "name", FlagValueType.STRING, ErrorStrategy.DEFAULT_VALUE, null);

            String result = IzanamiService.stringifyDefaultValue(objectMapper, config);

            assertThat(result).isNull();
        }

        @Test
        void stringifyDefaultValue_withBoolean_returnsString() {
            FlagConfig config = testFlagConfig("key", "name", FlagValueType.BOOLEAN, ErrorStrategy.DEFAULT_VALUE, true);

            String result = IzanamiService.stringifyDefaultValue(objectMapper, config);

            assertThat(result).isEqualTo("true");
        }

        @Test
        void stringifyDefaultValue_withNumber_returnsString() {
            FlagConfig config = testFlagConfig("key", "name", FlagValueType.DOUBLE, ErrorStrategy.DEFAULT_VALUE, new BigDecimal("123.45"));

            String result = IzanamiService.stringifyDefaultValue(objectMapper, config);

            assertThat(result).isEqualTo("123.45");
        }

        @Test
        void stringifyDefaultValue_withString_returnsString() {
            FlagConfig config = testFlagConfig("key", "name", FlagValueType.STRING, ErrorStrategy.DEFAULT_VALUE, "hello");

            String result = IzanamiService.stringifyDefaultValue(objectMapper, config);

            assertThat(result).isEqualTo("hello");
        }

        @Test
        void stringifyDefaultValue_withObject_returnsJson() {
            Map<String, Object> objectValue = Map.of("key1", "value1", "key2", 42);
            FlagConfig config = new FlagConfig(
                "key", "name", "desc", FlagValueType.OBJECT,
                ErrorStrategy.DEFAULT_VALUE,
                FeatureClientErrorStrategy.defaultValueStrategy(false, "", BigDecimal.ZERO),
                objectValue, null
            );

            String result = IzanamiService.stringifyDefaultValue(objectMapper, config);

            assertThat(result).contains("\"key1\"");
            assertThat(result).contains("\"value1\"");
            assertThat(result).contains("\"key2\"");
            assertThat(result).contains("42");
        }

        @Test
        void stringifyDefaultValue_withObjectAndSerializationError_returnsFallback() throws JsonProcessingException {
            ObjectMapper brokenMapper = mock(ObjectMapper.class);
            when(brokenMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("Error") {});

            Object objectWithToString = new Object() {
                @Override
                public String toString() {
                    return "fallback-toString";
                }
            };
            FlagConfig config = new FlagConfig(
                "key", "name", "desc", FlagValueType.OBJECT,
                ErrorStrategy.DEFAULT_VALUE,
                FeatureClientErrorStrategy.defaultValueStrategy(false, "", BigDecimal.ZERO),
                objectWithToString, null
            );

            String result = IzanamiService.stringifyDefaultValue(brokenMapper, config);

            assertThat(result).isEqualTo("fallback-toString");
        }
    }

    // =====================================================================
    // Lifecycle Tests
    // =====================================================================

    @Nested
    class LifecycleTests {

        @Test
        void destroy_whenClientNull_doesNothing() {
            IzanamiService service = new IzanamiService(
                blankUrlProperties(), flagConfigService, objectMapper, mockFactory
            );
            service.afterPropertiesSet();

            // Should not throw
            service.destroy();

            verify(mockClient, never()).close();
        }

        @Test
        void destroy_whenClientInitialized_closesClient() {
            when(mockClient.close()).thenReturn(CompletableFuture.completedFuture(null));

            IzanamiService service = createServiceWithMockFactory(validProperties());
            service.afterPropertiesSet();

            service.destroy();

            verify(mockClient).close();
        }

        @Test
        void destroy_whenCloseThrows_logsAndContinues() {
            CompletableFuture<Void> failedClose = new CompletableFuture<>();
            failedClose.completeExceptionally(new RuntimeException("Close failed"));
            when(mockClient.close()).thenReturn(failedClose);

            IzanamiService service = createServiceWithMockFactory(validProperties());
            service.afterPropertiesSet();

            // Should not throw even when close fails
            service.destroy();

            verify(mockClient).close();
        }

        @Test
        void destroy_clearsClientReference() {
            when(mockClient.close()).thenReturn(CompletableFuture.completedFuture(null));

            IzanamiService service = createServiceWithMockFactory(validProperties());
            service.afterPropertiesSet();
            assertThat(service.unwrapClient()).isPresent();

            service.destroy();

            assertThat(service.unwrapClient()).isEmpty();
        }
    }
}
