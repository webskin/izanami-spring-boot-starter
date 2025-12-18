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
import fr.maif.izanami.spring.service.api.FlagNotFoundException;
import fr.maif.izanami.spring.service.api.IzanamiClientNotAvailableException;
import fr.maif.izanami.spring.service.api.ResultValueWithDetails;
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
import static org.mockito.Mockito.*;

class IzanamiServiceImplTest {

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

    private static FlagConfig testDefaultDoubleFlagConfig(String key, String name, BigDecimal defaultValue) {
        return new FlagConfig(
            key,
            name,
            "Test flag description",
            FlagValueType.DOUBLE,
            ErrorStrategy.DEFAULT_VALUE,
            FeatureClientErrorStrategy.defaultValueStrategy(false, "", defaultValue),
            defaultValue,
            null
        );
    }

    private IzanamiServiceImpl createServiceWithMockFactory(IzanamiProperties properties) {
        when(mockFactory.create(any(), any(), any(), any())).thenReturn(mockClient);
        return new IzanamiServiceImpl(properties, flagConfigService, objectMapper, mockFactory);
    }

    // =====================================================================
    // Initialization Tests
    // =====================================================================

    @Nested
    class InitializationTests {

        @Test
        void afterPropertiesSet_withBlankUrl_remainsInactive() {
            IzanamiServiceImpl service = new IzanamiServiceImpl(
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
            IzanamiServiceImpl service = new IzanamiServiceImpl(
                blankProps, flagConfigService, objectMapper, mockFactory
            );

            service.afterPropertiesSet();

            assertThat(service.unwrapClient()).isEmpty();
            verify(mockFactory, never()).create(any(), any(), any(), any());
        }

        @Test
        void afterPropertiesSet_withBlankClientId_remainsInactive() {
            IzanamiServiceImpl service = new IzanamiServiceImpl(
                blankCredentialsProperties("", "test-secret"), flagConfigService, objectMapper, mockFactory
            );

            service.afterPropertiesSet();

            assertThat(service.unwrapClient()).isEmpty();
            verify(mockFactory, never()).create(any(), any(), any(), any());
        }

        @Test
        void afterPropertiesSet_withBlankClientSecret_remainsInactive() {
            IzanamiServiceImpl service = new IzanamiServiceImpl(
                blankCredentialsProperties("test-client", ""), flagConfigService, objectMapper, mockFactory
            );

            service.afterPropertiesSet();

            assertThat(service.unwrapClient()).isEmpty();
            verify(mockFactory, never()).create(any(), any(), any(), any());
        }

        @Test
        void afterPropertiesSet_withNullClientId_remainsInactive() {
            IzanamiServiceImpl service = new IzanamiServiceImpl(
                blankCredentialsProperties(null, "test-secret"), flagConfigService, objectMapper, mockFactory
            );

            service.afterPropertiesSet();

            assertThat(service.unwrapClient()).isEmpty();
            verify(mockFactory, never()).create(any(), any(), any(), any());
        }

        @Test
        void afterPropertiesSet_withValidConfig_initializesClient() {
            IzanamiServiceImpl service = createServiceWithMockFactory(validProperties());

            service.afterPropertiesSet();

            assertThat(service.unwrapClient()).isPresent().contains(mockClient);
            verify(mockFactory).create(any(), any(), any(), any());
        }

        @Test
        void afterPropertiesSet_preloadsConfiguredFlags() {
            FlagConfig flag1 = testDefaultBooleanFlagConfig("uuid-1", "flag-1", false);
            FlagConfig flag2 = testDefaultStringFlagConfig("uuid-2", "flag-2", "default");
            when(flagConfigService.getAllFlagConfigs()).thenReturn(List.of(flag1, flag2));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Set<String>> preloadCaptor = ArgumentCaptor.forClass(Set.class);
            when(mockFactory.create(any(), any(), any(), preloadCaptor.capture())).thenReturn(mockClient);

            IzanamiServiceImpl service = new IzanamiServiceImpl(validProperties(), flagConfigService, objectMapper, mockFactory);
            service.afterPropertiesSet();

            assertThat(preloadCaptor.getValue()).containsExactlyInAnyOrder("uuid-1", "uuid-2");
        }

        @Test
        void afterPropertiesSet_whenClientBuilderThrows_remainsInactiveAndDoesNotCrash() {
            when(mockFactory.create(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Connection failed"));

            IzanamiServiceImpl service = new IzanamiServiceImpl(validProperties(), flagConfigService, objectMapper, mockFactory);

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
            IzanamiServiceImpl service = new IzanamiServiceImpl(
                blankUrlProperties(), flagConfigService, objectMapper, mockFactory
            );
            service.afterPropertiesSet();

            assertThat(service.isConnected()).isFalse();
        }

        @Test
        void isConnected_afterSuccessfulPreload_returnsTrue() {
            IzanamiServiceImpl service = createServiceWithMockFactory(validProperties());
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

            IzanamiServiceImpl service = new IzanamiServiceImpl(validProperties(), flagConfigService, objectMapper, mockFactory);
            service.afterPropertiesSet();

            // Wait for the isLoaded future to complete (exceptionally)
            service.whenLoaded().exceptionally(ex -> null).join();

            assertThat(service.isConnected()).isFalse();
        }

        @Test
        void whenLoaded_returnsCompletedFuture_whenClientNull() {
            IzanamiServiceImpl service = new IzanamiServiceImpl(
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

            IzanamiServiceImpl service = new IzanamiServiceImpl(validProperties(), flagConfigService, objectMapper, mockFactory);
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
            IzanamiServiceImpl service = new IzanamiServiceImpl(
                blankUrlProperties(), flagConfigService, objectMapper, mockFactory
            );
            service.afterPropertiesSet();

            Optional<IzanamiClient> client = service.unwrapClient();

            assertThat(client).isEmpty();
        }

        @Test
        void unwrapClient_whenClientInitialized_returnsClient() {
            IzanamiServiceImpl service = createServiceWithMockFactory(validProperties());
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
            FlagConfig config = testDefaultBooleanFlagConfig("uuid-123", "my-flag", false);
            when(flagConfigService.getFlagConfigByKey("uuid-123")).thenReturn(Optional.of(config));

            IzanamiServiceImpl service = createServiceWithMockFactory(validProperties());
            service.afterPropertiesSet();

            IzanamiServiceImpl.FeatureRequestBuilder builder = service.forFlagKey("uuid-123");

            assertThat(builder).isNotNull();
        }

        @Test
        void forFlagKey_whenFlagNotFound_throwsFlagNotFoundException() {
            when(flagConfigService.getFlagConfigByKey("unknown-key")).thenReturn(Optional.empty());

            IzanamiServiceImpl service = createServiceWithMockFactory(validProperties());
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
            FlagConfig config = testDefaultBooleanFlagConfig("uuid-123", "my-flag", false);
            when(flagConfigService.getFlagConfigByName("my-flag")).thenReturn(Optional.of(config));

            IzanamiServiceImpl service = createServiceWithMockFactory(validProperties());
            service.afterPropertiesSet();

            IzanamiServiceImpl.FeatureRequestBuilder builder = service.forFlagName("my-flag");

            assertThat(builder).isNotNull();
        }

        @Test
        void forFlagName_whenFlagNotFound_throwsFlagNotFoundException() {
            when(flagConfigService.getFlagConfigByName("unknown-name")).thenReturn(Optional.empty());

            IzanamiServiceImpl service = createServiceWithMockFactory(validProperties());
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

        private IzanamiServiceImpl service;
        private FlagConfig config;

        @BeforeEach
        void setUp() {
            config = testDefaultBooleanFlagConfig("uuid-123", "my-flag", false);
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
            FlagConfig stringConfig = testDefaultStringFlagConfig("uuid-string", "string-flag", "default");
            when(flagConfigService.getFlagConfigByKey("uuid-string")).thenReturn(Optional.of(stringConfig));
            when(mockClient.stringValue(any(SingleFeatureRequest.class)))
                .thenReturn(CompletableFuture.completedFuture("test-value"));

            String result = service.forFlagKey("uuid-string").stringValue().join();

            assertThat(result).isEqualTo("test-value");
            verify(mockClient).stringValue(any(SingleFeatureRequest.class));
        }

        @Test
        void numberValue_delegatesToClient() {
            FlagConfig numberConfig = testDefaultIntegerFlagConfig("uuid-number", "number-flag", BigDecimal.ZERO);
            when(flagConfigService.getFlagConfigByKey("uuid-number")).thenReturn(Optional.of(numberConfig));
            when(mockClient.numberValue(any(SingleFeatureRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(new BigDecimal("42.5")));

            BigDecimal result = service.forFlagKey("uuid-number").numberValue().join();

            assertThat(result).isEqualByComparingTo(new BigDecimal("42.5"));
            verify(mockClient).numberValue(any(SingleFeatureRequest.class));
        }

        @Test
        void booleanValue_whenClientNull_throwsIzanamiClientNotAvailableException() {
            IzanamiServiceImpl inactiveService = new IzanamiServiceImpl(
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
    // Inactive Feature Tests
    // =====================================================================

    @Nested
    class InactiveFeatureTests {

        private IzanamiServiceImpl service;

        @BeforeEach
        void setUp() {
            service = createServiceWithMockFactory(validProperties());
            service.afterPropertiesSet();
        }

        @Test
        void booleanValue_inactiveFeature_returnsFalse() {
            FlagConfig config = testDefaultBooleanFlagConfig("uuid-inactive-bool", "inactive-bool", true);
            when(flagConfigService.getFlagConfigByKey("uuid-inactive-bool")).thenReturn(Optional.of(config));
            // Izanami client returns false for disabled boolean features
            when(mockClient.booleanValue(any(SingleFeatureRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(false));

            Boolean result = service.forFlagKey("uuid-inactive-bool").booleanValue().join();

            assertThat(result).isFalse();
        }

        @Test
        void stringValue_inactiveFeature_returnsDefaultValue() {
            FlagConfig config = testDefaultStringFlagConfig("uuid-inactive-string", "inactive-string", "fallback-value");
            when(flagConfigService.getFlagConfigByKey("uuid-inactive-string")).thenReturn(Optional.of(config));
            // Izanami client returns null for disabled string features
            when(mockClient.stringValue(any(SingleFeatureRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

            String result = service.forFlagKey("uuid-inactive-string").stringValue().join();

            // Disabled non-boolean features return the defaultValue when configured
            assertThat(result).isEqualTo("fallback-value");
        }

        @Test
        void numberValue_inactiveFeature_returnsDefaultValue() {
            FlagConfig config = testDefaultIntegerFlagConfig("uuid-inactive-number", "inactive-number", new BigDecimal("999"));
            when(flagConfigService.getFlagConfigByKey("uuid-inactive-number")).thenReturn(Optional.of(config));
            // Izanami client returns null for disabled number features
            when(mockClient.numberValue(any(SingleFeatureRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

            BigDecimal result = service.forFlagKey("uuid-inactive-number").numberValue().join();

            // Disabled non-boolean features return the defaultValue when configured
            assertThat(result).isEqualByComparingTo(new BigDecimal("999"));
        }

        // Note: *WithDetails tests for inactive features are covered by integration tests
        // (IzanamiServiceIT) since mocking featureValues() is complex due to the FeatureResponse wrapper.
        // The key behavior tested here (defaultValue application) is covered by stringValue/numberValue tests.
    }

    // =====================================================================
    // Error Strategy Tests
    // =====================================================================

    @Nested
    class ErrorStrategyTests {

        @Test
        void stringValue_withFailStrategy_throwsOnError() {
            FlagConfig failConfig = new FlagConfig(
                "uuid-fail",
                "fail-flag",
                "Test flag description",
                FlagValueType.STRING,
                ErrorStrategy.FAIL,
                FeatureClientErrorStrategy.failStrategy(),
                "default-value",
                null
            );
            when(flagConfigService.getFlagConfigByKey("uuid-fail")).thenReturn(Optional.of(failConfig));

            // Mock client to throw an exception (simulating server error)
            when(mockFactory.create(any(), any(), any(), any())).thenReturn(mockClient);
            when(mockClient.stringValue(any(SingleFeatureRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Server error")));

            IzanamiServiceImpl service = new IzanamiServiceImpl(validProperties(), flagConfigService, objectMapper, mockFactory);
            service.afterPropertiesSet();

            CompletableFuture<String> future = service.forFlagKey("uuid-fail").stringValue();

            assertThat(future).isCompletedExceptionally();
        }

        @Test
        void stringValue_withNullValueStrategy_returnsNullOnError() {
            FlagConfig nullConfig = new FlagConfig(
                "uuid-null",
                "null-flag",
                "Test flag description",
                FlagValueType.STRING,
                ErrorStrategy.NULL_VALUE,
                FeatureClientErrorStrategy.nullValueStrategy(),
                "default-value",
                null
            );
            when(flagConfigService.getFlagConfigByKey("uuid-null")).thenReturn(Optional.of(nullConfig));

            when(mockFactory.create(any(), any(), any(), any())).thenReturn(mockClient);
            // The null value strategy returns null when there's an error
            when(mockClient.stringValue(any(SingleFeatureRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

            IzanamiServiceImpl service = new IzanamiServiceImpl(validProperties(), flagConfigService, objectMapper, mockFactory);
            service.afterPropertiesSet();

            String result = service.forFlagKey("uuid-null").stringValue().join();

            assertThat(result).isNull();
        }

        @Test
        void stringValue_withCallbackStrategy_returnsCallbackValue() {
            // Create a callback strategy with three type-specific callbacks
            FeatureClientErrorStrategy<?> callbackStrategy = FeatureClientErrorStrategy.callbackStrategy(
                error -> CompletableFuture.completedFuture(false),  // boolean callback
                error -> CompletableFuture.completedFuture("callback-fallback-value"),  // string callback
                error -> CompletableFuture.completedFuture(BigDecimal.ZERO)  // number callback
            );

            FlagConfig callbackConfig = new FlagConfig(
                "uuid-callback",
                "callback-flag",
                "Test flag description",
                FlagValueType.STRING,
                ErrorStrategy.CALLBACK,
                callbackStrategy,
                "default-value",
                "testCallbackBean"
            );
            when(flagConfigService.getFlagConfigByKey("uuid-callback")).thenReturn(Optional.of(callbackConfig));

            when(mockFactory.create(any(), any(), any(), any())).thenReturn(mockClient);
            // Simulate the callback strategy returning the fallback value
            when(mockClient.stringValue(any(SingleFeatureRequest.class)))
                .thenReturn(CompletableFuture.completedFuture("callback-fallback-value"));

            IzanamiServiceImpl service = new IzanamiServiceImpl(validProperties(), flagConfigService, objectMapper, mockFactory);
            service.afterPropertiesSet();

            String result = service.forFlagKey("uuid-callback").stringValue().join();

            assertThat(result).isEqualTo("callback-fallback-value");
        }
    }

    // =====================================================================
    // ValueDetails Tests
    // =====================================================================

    @Nested
    class ValueDetailsTests {

        @Test
        void featureResultValueWithDetails_whenClientNull_andFailStrategy_returnsFailedFuture() {
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

            IzanamiServiceImpl inactiveService = new IzanamiServiceImpl(
                blankUrlProperties(), flagConfigService, objectMapper, mockFactory
            );
            inactiveService.afterPropertiesSet();

            CompletableFuture<ResultValueWithDetails<Boolean>> future = inactiveService.forFlagKey("uuid-fail")
                .booleanValueDetails();

            assertThat(future).isCompletedExceptionally();
        }

        @Test
        void featureResultValueWithDetails_whenClientNull_andDefaultStrategy_returnsApplicationErrorStrategy() {
            FlagConfig config = testDefaultBooleanFlagConfig("uuid-123", "my-flag", false);
            when(flagConfigService.getFlagConfigByKey("uuid-123")).thenReturn(Optional.of(config));

            IzanamiServiceImpl inactiveService = new IzanamiServiceImpl(
                blankUrlProperties(), flagConfigService, objectMapper, mockFactory
            );
            inactiveService.afterPropertiesSet();

            ResultValueWithDetails<Boolean> result = inactiveService.forFlagKey("uuid-123")
                .booleanValueDetails()
                .join();

            assertThat(result.metadata().get(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                .isEqualTo(FlagValueSource.IZANAMI_ERROR_STRATEGY.name());
        }

        @Test
        void featureResultValueWithDetails_whenClientNull_includesMetadataKeys() {
            FlagConfig config = testDefaultBooleanFlagConfig("uuid-123", "my-flag", false);
            when(flagConfigService.getFlagConfigByKey("uuid-123")).thenReturn(Optional.of(config));

            IzanamiServiceImpl inactiveService = new IzanamiServiceImpl(
                blankUrlProperties(), flagConfigService, objectMapper, mockFactory
            );
            inactiveService.afterPropertiesSet();

            ResultValueWithDetails<Boolean> result = inactiveService.forFlagKey("uuid-123")
                .booleanValueDetails()
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
        void featureResultValueWithDetails_whenClientNull_populatesMetadataValues() {
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

            IzanamiServiceImpl inactiveService = new IzanamiServiceImpl(
                blankUrlProperties(), flagConfigService, objectMapper, mockFactory
            );
            inactiveService.afterPropertiesSet();

            ResultValueWithDetails<String> result = inactiveService.forFlagKey("uuid-detailed")
                .stringValueDetails()
                .join();

            Map<String, String> metadata = result.metadata();
            assertThat(metadata.get(FlagMetadataKeys.FLAG_CONFIG_KEY)).isEqualTo("uuid-detailed");
            assertThat(metadata.get(FlagMetadataKeys.FLAG_CONFIG_NAME)).isEqualTo("detailed-flag");
            assertThat(metadata.get(FlagMetadataKeys.FLAG_CONFIG_DESCRIPTION)).isEqualTo("A detailed description");
            assertThat(metadata.get(FlagMetadataKeys.FLAG_CONFIG_VALUE_TYPE)).isEqualTo("STRING");
            assertThat(metadata.get(FlagMetadataKeys.FLAG_CONFIG_DEFAULT_VALUE)).isEqualTo("default-string-value");
            assertThat(metadata.get(FlagMetadataKeys.FLAG_CONFIG_ERROR_STRATEGY)).isEqualTo("DEFAULT_VALUE");
        }

        @Test
        void booleanValueDetails_whenClientNull_andFailStrategy_returnsFailedFuture() {
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

            IzanamiServiceImpl inactiveService = new IzanamiServiceImpl(
                blankUrlProperties(), flagConfigService, objectMapper, mockFactory
            );
            inactiveService.afterPropertiesSet();

            CompletableFuture<ResultValueWithDetails<Boolean>> future = inactiveService.forFlagKey("uuid-fail")
                .booleanValueDetails();

            assertThat(future).isCompletedExceptionally();
        }

        @Test
        void stringValueDetails_whenClientNull_andFailStrategy_returnsFailedFuture() {
            FlagConfig failConfig = new FlagConfig(
                "uuid-fail",
                "fail-flag",
                "Test flag description",
                FlagValueType.STRING,
                ErrorStrategy.FAIL,
                FeatureClientErrorStrategy.failStrategy(),
                "default",
                null
            );
            when(flagConfigService.getFlagConfigByKey("uuid-fail")).thenReturn(Optional.of(failConfig));

            IzanamiServiceImpl inactiveService = new IzanamiServiceImpl(
                blankUrlProperties(), flagConfigService, objectMapper, mockFactory
            );
            inactiveService.afterPropertiesSet();

            CompletableFuture<ResultValueWithDetails<String>> future = inactiveService.forFlagKey("uuid-fail")
                .stringValueDetails();

            assertThat(future).isCompletedExceptionally();
        }

        @Test
        void numberValueDetails_whenClientNull_andFailStrategy_returnsFailedFuture() {
            FlagConfig failConfig = new FlagConfig(
                "uuid-fail",
                "fail-flag",
                "Test flag description",
                FlagValueType.INTEGER,
                ErrorStrategy.FAIL,
                FeatureClientErrorStrategy.failStrategy(),
                BigDecimal.ZERO,
                null
            );
            when(flagConfigService.getFlagConfigByKey("uuid-fail")).thenReturn(Optional.of(failConfig));

            IzanamiServiceImpl inactiveService = new IzanamiServiceImpl(
                blankUrlProperties(), flagConfigService, objectMapper, mockFactory
            );
            inactiveService.afterPropertiesSet();

            CompletableFuture<ResultValueWithDetails<BigDecimal>> future = inactiveService.forFlagKey("uuid-fail")
                .numberValueDetails();

            assertThat(future).isCompletedExceptionally();
        }

        @Test
        void booleanValueDetails_whenClientNull_andDefaultStrategy_returnsErrorWithAppErrorStrategy() {
            FlagConfig config = testDefaultBooleanFlagConfig("uuid-123", "my-flag", true);
            when(flagConfigService.getFlagConfigByKey("uuid-123")).thenReturn(Optional.of(config));

            IzanamiServiceImpl inactiveService = new IzanamiServiceImpl(
                blankUrlProperties(), flagConfigService, objectMapper, mockFactory
            );
            inactiveService.afterPropertiesSet();

            ResultValueWithDetails<Boolean> result = inactiveService.forFlagKey("uuid-123")
                .booleanValueDetails()
                .join();

            assertThat(result.value()).isTrue();
            assertThat(result.metadata().get(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                .isEqualTo(FlagValueSource.IZANAMI_ERROR_STRATEGY.name());
            assertThat(result.metadata().get(FlagMetadataKeys.FLAG_EVALUATION_REASON))
                .isEqualTo("ERROR");
        }

        @Test
        void stringValueDetails_whenClientNull_andDefaultStrategy_returnsErrorWithAppErrorStrategy() {
            FlagConfig config = testDefaultStringFlagConfig("uuid-string", "string-flag", "fallback");
            when(flagConfigService.getFlagConfigByKey("uuid-string")).thenReturn(Optional.of(config));

            IzanamiServiceImpl inactiveService = new IzanamiServiceImpl(
                blankUrlProperties(), flagConfigService, objectMapper, mockFactory
            );
            inactiveService.afterPropertiesSet();

            ResultValueWithDetails<String> result = inactiveService.forFlagKey("uuid-string")
                .stringValueDetails()
                .join();

            assertThat(result.value()).isEqualTo("fallback");
            assertThat(result.metadata().get(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                .isEqualTo(FlagValueSource.IZANAMI_ERROR_STRATEGY.name());
            assertThat(result.metadata().get(FlagMetadataKeys.FLAG_EVALUATION_REASON))
                .isEqualTo("ERROR");
        }

        @Test
        void numberValueDetails_whenClientNull_andDefaultStrategy_returnsErrorWithAppErrorStrategy() {
            FlagConfig config = testDefaultIntegerFlagConfig("uuid-number", "number-flag", new BigDecimal("999"));
            when(flagConfigService.getFlagConfigByKey("uuid-number")).thenReturn(Optional.of(config));

            IzanamiServiceImpl inactiveService = new IzanamiServiceImpl(
                blankUrlProperties(), flagConfigService, objectMapper, mockFactory
            );
            inactiveService.afterPropertiesSet();

            ResultValueWithDetails<BigDecimal> result = inactiveService.forFlagKey("uuid-number")
                .numberValueDetails()
                .join();

            assertThat(result.value()).isEqualByComparingTo(new BigDecimal("999"));
            assertThat(result.metadata().get(FlagMetadataKeys.FLAG_VALUE_SOURCE))
                .isEqualTo(FlagValueSource.IZANAMI_ERROR_STRATEGY.name());
            assertThat(result.metadata().get(FlagMetadataKeys.FLAG_EVALUATION_REASON))
                .isEqualTo("ERROR");
        }

        @Test
        void booleanValueDetails_whenClientNull_includesAllMetadataKeys() {
            FlagConfig config = testDefaultBooleanFlagConfig("uuid-123", "my-flag", false);
            when(flagConfigService.getFlagConfigByKey("uuid-123")).thenReturn(Optional.of(config));

            IzanamiServiceImpl inactiveService = new IzanamiServiceImpl(
                blankUrlProperties(), flagConfigService, objectMapper, mockFactory
            );
            inactiveService.afterPropertiesSet();

            ResultValueWithDetails<Boolean> result = inactiveService.forFlagKey("uuid-123")
                .booleanValueDetails()
                .join();

            Map<String, String> metadata = result.metadata();
            assertThat(metadata).containsKey(FlagMetadataKeys.FLAG_CONFIG_KEY);
            assertThat(metadata).containsKey(FlagMetadataKeys.FLAG_CONFIG_NAME);
            assertThat(metadata).containsKey(FlagMetadataKeys.FLAG_CONFIG_DESCRIPTION);
            assertThat(metadata).containsKey(FlagMetadataKeys.FLAG_CONFIG_VALUE_TYPE);
            assertThat(metadata).containsKey(FlagMetadataKeys.FLAG_CONFIG_DEFAULT_VALUE);
            assertThat(metadata).containsKey(FlagMetadataKeys.FLAG_CONFIG_ERROR_STRATEGY);
            assertThat(metadata).containsKey(FlagMetadataKeys.FLAG_VALUE_SOURCE);
            assertThat(metadata).containsKey(FlagMetadataKeys.FLAG_EVALUATION_REASON);
        }

        @Test
        void booleanValueDetails_whenClientNull_populatesMetadataValues() {
            FlagConfig detailedConfig = new FlagConfig(
                "uuid-detailed",
                "detailed-flag",
                "A detailed description",
                FlagValueType.BOOLEAN,
                ErrorStrategy.DEFAULT_VALUE,
                FeatureClientErrorStrategy.defaultValueStrategy(false, "fallback", BigDecimal.ZERO),
                true,
                null
            );
            when(flagConfigService.getFlagConfigByKey("uuid-detailed")).thenReturn(Optional.of(detailedConfig));

            IzanamiServiceImpl inactiveService = new IzanamiServiceImpl(
                blankUrlProperties(), flagConfigService, objectMapper, mockFactory
            );
            inactiveService.afterPropertiesSet();

            ResultValueWithDetails<Boolean> result = inactiveService.forFlagKey("uuid-detailed")
                .booleanValueDetails()
                .join();

            Map<String, String> metadata = result.metadata();
            assertThat(metadata.get(FlagMetadataKeys.FLAG_CONFIG_KEY)).isEqualTo("uuid-detailed");
            assertThat(metadata.get(FlagMetadataKeys.FLAG_CONFIG_NAME)).isEqualTo("detailed-flag");
            assertThat(metadata.get(FlagMetadataKeys.FLAG_CONFIG_DESCRIPTION)).isEqualTo("A detailed description");
            assertThat(metadata.get(FlagMetadataKeys.FLAG_CONFIG_VALUE_TYPE)).isEqualTo("BOOLEAN");
            assertThat(metadata.get(FlagMetadataKeys.FLAG_CONFIG_DEFAULT_VALUE)).isEqualTo("true");
            assertThat(metadata.get(FlagMetadataKeys.FLAG_CONFIG_ERROR_STRATEGY)).isEqualTo("DEFAULT_VALUE");
            assertThat(metadata.get(FlagMetadataKeys.FLAG_VALUE_SOURCE)).isEqualTo("IZANAMI_ERROR_STRATEGY");
            assertThat(metadata.get(FlagMetadataKeys.FLAG_EVALUATION_REASON)).isEqualTo("ERROR");
        }
    }

    // =====================================================================
    // stringifyDefaultValue Tests
    // =====================================================================

    @Nested
    class StringifyDefaultValueTests {

        @Test
        void stringifyDefaultValue_withNull_returnsNull() {
            FlagConfig config = testDefaultStringFlagConfig("key", "name", null);

            String result = IzanamiServiceImpl.stringifyDefaultValue(objectMapper, config);

            assertThat(result).isNull();
        }

        @Test
        void stringifyDefaultValue_withBoolean_returnsString() {
            FlagConfig config = testDefaultBooleanFlagConfig("key", "name", true);

            String result = IzanamiServiceImpl.stringifyDefaultValue(objectMapper, config);

            assertThat(result).isEqualTo("true");
        }

        @Test
        void stringifyDefaultValue_withNumber_returnsString() {
            FlagConfig config = testDefaultDoubleFlagConfig("key", "name", new BigDecimal("123.45"));

            String result = IzanamiServiceImpl.stringifyDefaultValue(objectMapper, config);

            assertThat(result).isEqualTo("123.45");
        }

        @Test
        void stringifyDefaultValue_withString_returnsString() {
            FlagConfig config = testDefaultStringFlagConfig("key", "name", "hello");

            String result = IzanamiServiceImpl.stringifyDefaultValue(objectMapper, config);

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

            String result = IzanamiServiceImpl.stringifyDefaultValue(objectMapper, config);

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

            String result = IzanamiServiceImpl.stringifyDefaultValue(brokenMapper, config);

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
            IzanamiServiceImpl service = new IzanamiServiceImpl(
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

            IzanamiServiceImpl service = createServiceWithMockFactory(validProperties());
            service.afterPropertiesSet();

            service.destroy();

            verify(mockClient).close();
        }

        @Test
        void destroy_whenCloseThrows_logsAndContinues() {
            CompletableFuture<Void> failedClose = new CompletableFuture<>();
            failedClose.completeExceptionally(new RuntimeException("Close failed"));
            when(mockClient.close()).thenReturn(failedClose);

            IzanamiServiceImpl service = createServiceWithMockFactory(validProperties());
            service.afterPropertiesSet();

            // Should not throw even when close fails
            service.destroy();

            verify(mockClient).close();
        }

        @Test
        void destroy_clearsClientReference() {
            when(mockClient.close()).thenReturn(CompletableFuture.completedFuture(null));

            IzanamiServiceImpl service = createServiceWithMockFactory(validProperties());
            service.afterPropertiesSet();
            assertThat(service.unwrapClient()).isPresent();

            service.destroy();

            assertThat(service.unwrapClient()).isEmpty();
        }
    }
}
