package fr.maif.izanami.spring.service;

import fr.maif.izanami.spring.service.api.ReactiveUserProvider;
import fr.maif.izanami.spring.service.api.UserProvider;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReactiveUserResolverTest {

    @Nested
    class ResolutionOrderTests {

        @Test
        void explicitUser_takesPrecedence_overProviders() {
            // Given
            ReactiveUserProvider reactiveProvider = () -> Mono.just("reactive-user");
            UserProvider syncProvider = () -> Optional.of("sync-user");
            ReactiveUserResolver resolver = createResolver(reactiveProvider, syncProvider);

            // When / Then
            StepVerifier.create(resolver.resolve("explicit-user"))
                    .expectNext("explicit-user")
                    .verifyComplete();
        }

        @Test
        void reactiveProvider_takesPrecedence_overSyncProvider() {
            // Given
            ReactiveUserProvider reactiveProvider = () -> Mono.just("reactive-user");
            UserProvider syncProvider = () -> Optional.of("sync-user");
            ReactiveUserResolver resolver = createResolver(reactiveProvider, syncProvider);

            // When / Then
            StepVerifier.create(resolver.resolve(null))
                    .expectNext("reactive-user")
                    .verifyComplete();
        }

        @Test
        void syncProvider_usedWhenNoReactiveProvider() {
            // Given
            UserProvider syncProvider = () -> Optional.of("sync-user");
            ReactiveUserResolver resolver = createResolver(null, syncProvider);

            // When / Then
            StepVerifier.create(resolver.resolve(null))
                    .expectNext("sync-user")
                    .verifyComplete();
        }

        @Test
        void syncProvider_usedWhenReactiveProviderReturnsEmpty() {
            // Given
            ReactiveUserProvider reactiveProvider = Mono::empty;
            UserProvider syncProvider = () -> Optional.of("sync-user");
            ReactiveUserResolver resolver = createResolver(reactiveProvider, syncProvider);

            // When / Then
            StepVerifier.create(resolver.resolve(null))
                    .expectNext("sync-user")
                    .verifyComplete();
        }

        @Test
        void noProviders_returnsEmpty() {
            // Given
            ReactiveUserResolver resolver = createResolver(null, null);

            // When / Then
            StepVerifier.create(resolver.resolve(null))
                    .verifyComplete();
        }

        @Test
        void emptyExplicitUser_fallsBackToProvider() {
            // Given
            ReactiveUserProvider reactiveProvider = () -> Mono.just("reactive-user");
            ReactiveUserResolver resolver = createResolver(reactiveProvider, null);

            // When / Then
            StepVerifier.create(resolver.resolve(""))
                    .expectNext("reactive-user")
                    .verifyComplete();
        }

        @Test
        void blankExplicitUser_fallsBackToProvider() {
            // Given
            ReactiveUserProvider reactiveProvider = () -> Mono.just("reactive-user");
            ReactiveUserResolver resolver = createResolver(reactiveProvider, null);

            // When / Then
            StepVerifier.create(resolver.resolve("   "))
                    .expectNext("reactive-user")
                    .verifyComplete();
        }
    }

    @Nested
    class ResilienceTests {

        @Test
        void reactiveProviderError_fallsBackToSyncProvider() {
            // Given
            ReactiveUserProvider reactiveProvider = () -> Mono.error(new RuntimeException("Reactive failure"));
            UserProvider syncProvider = () -> Optional.of("sync-user");
            ReactiveUserResolver resolver = createResolver(reactiveProvider, syncProvider);

            // When / Then
            StepVerifier.create(resolver.resolve(null))
                    .expectNext("sync-user")
                    .verifyComplete();
        }

        @Test
        void syncProviderException_handledGracefully() {
            // Given - sync provider throws (simulating out-of-request-scope access)
            @SuppressWarnings("unchecked")
            ObjectProvider<UserProvider> syncObjectProvider = mock(ObjectProvider.class);
            when(syncObjectProvider.getIfAvailable()).thenThrow(new IllegalStateException("No request scope"));

            @SuppressWarnings("unchecked")
            ObjectProvider<ReactiveUserProvider> reactiveObjectProvider = mock(ObjectProvider.class);
            when(reactiveObjectProvider.getIfAvailable()).thenReturn(null);

            ReactiveUserResolver resolver = new ReactiveUserResolver(reactiveObjectProvider, syncObjectProvider);

            // When / Then
            StepVerifier.create(resolver.resolve(null))
                    .verifyComplete();
        }

        @Test
        void reactiveProviderBeanAccessException_handledGracefully() {
            // Given - ObjectProvider itself throws
            @SuppressWarnings("unchecked")
            ObjectProvider<ReactiveUserProvider> reactiveObjectProvider = mock(ObjectProvider.class);
            when(reactiveObjectProvider.getIfAvailable()).thenThrow(new RuntimeException("Bean access error"));

            UserProvider syncProvider = () -> Optional.of("sync-user");
            @SuppressWarnings("unchecked")
            ObjectProvider<UserProvider> syncObjectProvider = mock(ObjectProvider.class);
            when(syncObjectProvider.getIfAvailable()).thenReturn(syncProvider);

            ReactiveUserResolver resolver = new ReactiveUserResolver(reactiveObjectProvider, syncObjectProvider);

            // When / Then - falls back to sync provider
            StepVerifier.create(resolver.resolve(null))
                    .expectNext("sync-user")
                    .verifyComplete();
        }

        @Test
        void syncProviderReturnsEmpty_returnsEmpty() {
            // Given
            UserProvider syncProvider = Optional::empty;
            ReactiveUserResolver resolver = createResolver(null, syncProvider);

            // When / Then
            StepVerifier.create(resolver.resolve(null))
                    .verifyComplete();
        }

        @Test
        void syncProviderReturnsEmptyString_treatedAsNotPresent() {
            // Given
            UserProvider syncProvider = () -> Optional.of("");
            ReactiveUserResolver resolver = createResolver(null, syncProvider);

            // When / Then
            StepVerifier.create(resolver.resolve(null))
                    .verifyComplete();
        }

        @Test
        void syncProviderReturnsBlankString_treatedAsNotPresent() {
            // Given
            UserProvider syncProvider = () -> Optional.of("   ");
            ReactiveUserResolver resolver = createResolver(null, syncProvider);

            // When / Then
            StepVerifier.create(resolver.resolve(null))
                    .verifyComplete();
        }
    }

    @Nested
    class NormalizationTests {

        @Test
        void explicitUser_trimmed() {
            // Given
            ReactiveUserResolver resolver = createResolver(null, null);

            // When / Then
            StepVerifier.create(resolver.resolve("  user-123  "))
                    .expectNext("user-123")
                    .verifyComplete();
        }

        @Test
        void reactiveProviderUser_trimmed() {
            // Given
            ReactiveUserProvider reactiveProvider = () -> Mono.just("  reactive-user  ");
            ReactiveUserResolver resolver = createResolver(reactiveProvider, null);

            // When / Then
            StepVerifier.create(resolver.resolve(null))
                    .expectNext("reactive-user")
                    .verifyComplete();
        }

        @Test
        void syncProviderUser_trimmed() {
            // Given
            UserProvider syncProvider = () -> Optional.of("  sync-user  ");
            ReactiveUserResolver resolver = createResolver(null, syncProvider);

            // When / Then
            StepVerifier.create(resolver.resolve(null))
                    .expectNext("sync-user")
                    .verifyComplete();
        }
    }

    // Helper method to create resolver with mocked ObjectProviders
    @SuppressWarnings("unchecked")
    private ReactiveUserResolver createResolver(ReactiveUserProvider reactiveProvider, UserProvider syncProvider) {
        ObjectProvider<ReactiveUserProvider> reactiveObjectProvider = mock(ObjectProvider.class);
        ObjectProvider<UserProvider> syncObjectProvider = mock(ObjectProvider.class);

        when(reactiveObjectProvider.getIfAvailable()).thenReturn(reactiveProvider);
        when(syncObjectProvider.getIfAvailable()).thenReturn(syncProvider);

        return new ReactiveUserResolver(reactiveObjectProvider, syncObjectProvider);
    }
}
