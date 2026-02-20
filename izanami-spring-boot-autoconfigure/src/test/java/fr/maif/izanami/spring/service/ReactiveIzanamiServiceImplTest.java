package fr.maif.izanami.spring.service;

import fr.maif.IzanamiClient;
import fr.maif.izanami.spring.service.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ReactiveIzanamiServiceImplTest {

    private IzanamiService mockDelegate;
    private ReactiveUserResolver userResolver;
    private ReactiveContextResolver contextResolver;
    private ReactiveIzanamiServiceImpl service;

    @BeforeEach
    void setUp() {
        mockDelegate = mock(IzanamiService.class);
        userResolver = createUserResolver(null, null);
        contextResolver = createContextResolver(null, null, null);
        service = new ReactiveIzanamiServiceImpl(mockDelegate, contextResolver, userResolver);
    }

    @Nested
    class DelegationTests {

        @Test
        void unwrapClient_delegatesToSyncService() {
            // Given
            IzanamiClient mockClient = mock(IzanamiClient.class);
            when(mockDelegate.unwrapClient()).thenReturn(Optional.of(mockClient));

            // When
            Optional<IzanamiClient> result = service.unwrapClient();

            // Then
            assertThat(result).isPresent().contains(mockClient);
        }

        @Test
        void unwrapClient_emptyWhenDelegateReturnsEmpty() {
            // Given
            when(mockDelegate.unwrapClient()).thenReturn(Optional.empty());

            // When
            Optional<IzanamiClient> result = service.unwrapClient();

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        void isConnected_delegatesToSyncService() {
            // Given
            when(mockDelegate.isConnected()).thenReturn(true);

            // Then
            assertThat(service.isConnected()).isTrue();
        }

        @Test
        void whenLoaded_bridgesFromCompletableFuture() {
            // Given
            when(mockDelegate.whenLoaded()).thenReturn(CompletableFuture.completedFuture(null));

            // When / Then
            StepVerifier.create(service.whenLoaded())
                    .verifyComplete();
        }
    }

    @Nested
    class SingleFlagEvaluationTests {

        @Test
        void forFlagKey_booleanValue_delegatesWithResolvedUserAndContext() {
            // Given
            ReactiveUserProvider reactiveUserProvider = () -> Mono.just("reactive-user");
            userResolver = createUserResolver(reactiveUserProvider, null);

            ReactiveSubContextResolver reactiveSubResolver = () -> Mono.just("mobile");
            contextResolver = createContextResolver(reactiveSubResolver, null,
                    () -> Optional.of("BUILD"));

            service = new ReactiveIzanamiServiceImpl(mockDelegate, contextResolver, userResolver);

            FeatureRequestBuilder syncBuilder = mock(FeatureRequestBuilder.class, RETURNS_SELF);
            when(mockDelegate.forFlagKey("test-uuid")).thenReturn(syncBuilder);

            ResultValueWithDetails<Boolean> expected = new ResultValueWithDetails<>(true, Map.of());
            when(syncBuilder.booleanValueDetails()).thenReturn(CompletableFuture.completedFuture(expected));

            // When / Then
            StepVerifier.create(service.forFlagKey("test-uuid").booleanValue())
                    .expectNext(true)
                    .verifyComplete();

            verify(syncBuilder).withUser("reactive-user");
            verify(syncBuilder).withContext("BUILD/mobile");
        }

        @Test
        void forFlagName_stringValue_delegatesCorrectly() {
            // Given
            FeatureRequestBuilder syncBuilder = mock(FeatureRequestBuilder.class, RETURNS_SELF);
            when(mockDelegate.forFlagName("my-feature")).thenReturn(syncBuilder);

            ResultValueWithDetails<String> expected = new ResultValueWithDetails<>("value", Map.of());
            when(syncBuilder.stringValueDetails()).thenReturn(CompletableFuture.completedFuture(expected));

            // When / Then
            StepVerifier.create(service.forFlagName("my-feature").stringValue())
                    .expectNext("value")
                    .verifyComplete();
        }

        @Test
        void forFlagKey_numberValue_delegatesCorrectly() {
            // Given
            FeatureRequestBuilder syncBuilder = mock(FeatureRequestBuilder.class, RETURNS_SELF);
            when(mockDelegate.forFlagKey("uuid-1")).thenReturn(syncBuilder);

            ResultValueWithDetails<BigDecimal> expected = new ResultValueWithDetails<>(new BigDecimal("42"), Map.of());
            when(syncBuilder.numberValueDetails()).thenReturn(CompletableFuture.completedFuture(expected));

            // When / Then
            StepVerifier.create(service.forFlagKey("uuid-1").numberValue())
                    .expectNext(new BigDecimal("42"))
                    .verifyComplete();
        }

        @Test
        void forFlagName_booleanValueDetails_returnsFullDetails() {
            // Given
            FeatureRequestBuilder syncBuilder = mock(FeatureRequestBuilder.class, RETURNS_SELF);
            when(mockDelegate.forFlagName("my-feature")).thenReturn(syncBuilder);

            ResultValueWithDetails<Boolean> expected = new ResultValueWithDetails<>(
                    false, Map.of("FLAG_VALUE_SOURCE", "APPLICATION_ERROR_STRATEGY"));
            when(syncBuilder.booleanValueDetails()).thenReturn(CompletableFuture.completedFuture(expected));

            // When / Then
            StepVerifier.create(service.forFlagName("my-feature").booleanValueDetails())
                    .assertNext(result -> {
                        assertThat(result.value()).isFalse();
                        assertThat(result.metadata()).containsEntry("FLAG_VALUE_SOURCE", "APPLICATION_ERROR_STRATEGY");
                    })
                    .verifyComplete();
        }

        @Test
        void explicitUserAndContext_takesPrecedence() {
            // Given
            ReactiveUserProvider reactiveUserProvider = () -> Mono.just("reactive-user");
            userResolver = createUserResolver(reactiveUserProvider, null);

            ReactiveSubContextResolver reactiveSubResolver = () -> Mono.just("reactive-sub");
            contextResolver = createContextResolver(reactiveSubResolver, null,
                    () -> Optional.of("ROOT"));

            service = new ReactiveIzanamiServiceImpl(mockDelegate, contextResolver, userResolver);

            FeatureRequestBuilder syncBuilder = mock(FeatureRequestBuilder.class, RETURNS_SELF);
            when(mockDelegate.forFlagName("my-feature")).thenReturn(syncBuilder);

            ResultValueWithDetails<Boolean> expected = new ResultValueWithDetails<>(true, Map.of());
            when(syncBuilder.booleanValueDetails()).thenReturn(CompletableFuture.completedFuture(expected));

            // When / Then
            StepVerifier.create(service.forFlagName("my-feature")
                            .withUser("explicit-user")
                            .withContext("EXPLICIT")
                            .booleanValue())
                    .expectNext(true)
                    .verifyComplete();

            verify(syncBuilder).withUser("explicit-user");
            verify(syncBuilder).withContext("EXPLICIT");
        }
    }

    @Nested
    class BatchEvaluationTests {

        @Test
        void forFlagKeys_delegatesWithResolvedUserAndContext() {
            // Given
            ReactiveUserProvider reactiveUserProvider = () -> Mono.just("reactive-user");
            userResolver = createUserResolver(reactiveUserProvider, null);
            contextResolver = createContextResolver(null, null, () -> Optional.of("BUILD"));

            service = new ReactiveIzanamiServiceImpl(mockDelegate, contextResolver, userResolver);

            BatchFeatureRequestBuilder syncBatchBuilder = mock(BatchFeatureRequestBuilder.class, RETURNS_SELF);
            when(mockDelegate.forFlagKeys("uuid-1", "uuid-2")).thenReturn(syncBatchBuilder);

            BatchResult mockResult = mock(BatchResult.class);
            when(mockResult.booleanValue("uuid-1")).thenReturn(true);
            when(syncBatchBuilder.values()).thenReturn(CompletableFuture.completedFuture(mockResult));

            // When / Then
            StepVerifier.create(service.forFlagKeys("uuid-1", "uuid-2").values())
                    .assertNext(result -> assertThat(result.booleanValue("uuid-1")).isTrue())
                    .verifyComplete();

            verify(syncBatchBuilder).withUser("reactive-user");
            verify(syncBatchBuilder).withContext("BUILD");
        }

        @Test
        void forFlagNames_delegatesCorrectly() {
            // Given
            BatchFeatureRequestBuilder syncBatchBuilder = mock(BatchFeatureRequestBuilder.class, RETURNS_SELF);
            when(mockDelegate.forFlagNames("feature-a", "feature-b")).thenReturn(syncBatchBuilder);

            BatchResult mockResult = mock(BatchResult.class);
            when(mockResult.stringValue("feature-a")).thenReturn("hello");
            when(syncBatchBuilder.values()).thenReturn(CompletableFuture.completedFuture(mockResult));

            // When / Then
            StepVerifier.create(service.forFlagNames("feature-a", "feature-b").values())
                    .assertNext(result -> assertThat(result.stringValue("feature-a")).isEqualTo("hello"))
                    .verifyComplete();
        }
    }

    @Nested
    class NoProviderTests {

        @Test
        void noUserOrContextProviders_delegatesWithNull() {
            // Given
            FeatureRequestBuilder syncBuilder = mock(FeatureRequestBuilder.class, RETURNS_SELF);
            when(mockDelegate.forFlagName("my-feature")).thenReturn(syncBuilder);

            ResultValueWithDetails<Boolean> expected = new ResultValueWithDetails<>(false, Map.of());
            when(syncBuilder.booleanValueDetails()).thenReturn(CompletableFuture.completedFuture(expected));

            // When / Then
            StepVerifier.create(service.forFlagName("my-feature").booleanValue())
                    .expectNext(false)
                    .verifyComplete();

            verify(syncBuilder).withUser(null);
            verify(syncBuilder).withContext(null);
        }
    }

    // =====================================================================
    // Test Helpers
    // =====================================================================

    @SuppressWarnings("unchecked")
    private ReactiveUserResolver createUserResolver(
            ReactiveUserProvider reactiveProvider, UserProvider syncProvider) {
        ObjectProvider<ReactiveUserProvider> reactiveObjectProvider = mock(ObjectProvider.class);
        ObjectProvider<UserProvider> syncObjectProvider = mock(ObjectProvider.class);

        when(reactiveObjectProvider.getIfAvailable()).thenReturn(reactiveProvider);
        when(syncObjectProvider.getIfAvailable()).thenReturn(syncProvider);

        return new ReactiveUserResolver(reactiveObjectProvider, syncObjectProvider);
    }

    @SuppressWarnings("unchecked")
    private ReactiveContextResolver createContextResolver(
            ReactiveSubContextResolver reactiveSubResolver,
            SubContextResolver syncSubResolver,
            RootContextProvider rootProvider) {
        ObjectProvider<ReactiveSubContextResolver> reactiveSubObjectProvider = mock(ObjectProvider.class);
        ObjectProvider<SubContextResolver> syncSubObjectProvider = mock(ObjectProvider.class);
        ObjectProvider<RootContextProvider> rootObjectProvider = mock(ObjectProvider.class);

        when(reactiveSubObjectProvider.getIfAvailable()).thenReturn(reactiveSubResolver);
        when(syncSubObjectProvider.getIfAvailable()).thenReturn(syncSubResolver);
        when(rootObjectProvider.getIfAvailable()).thenReturn(rootProvider);

        return new ReactiveContextResolver(reactiveSubObjectProvider, syncSubObjectProvider, rootObjectProvider);
    }
}
