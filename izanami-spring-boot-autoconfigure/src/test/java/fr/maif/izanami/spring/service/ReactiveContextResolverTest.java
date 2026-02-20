package fr.maif.izanami.spring.service;

import fr.maif.izanami.spring.service.api.ReactiveSubContextResolver;
import fr.maif.izanami.spring.service.api.RootContextProvider;
import fr.maif.izanami.spring.service.api.SubContextResolver;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReactiveContextResolverTest {

    @Nested
    class ResolutionOrderTests {

        @Test
        void explicitContext_takesPrecedence_overAllProviders() {
            // Given
            ReactiveSubContextResolver reactiveSubResolver = () -> Mono.just("reactive-sub");
            SubContextResolver syncSubResolver = () -> Optional.of("sync-sub");
            RootContextProvider rootProvider = () -> Optional.of("ROOT");
            ReactiveContextResolver resolver = createResolver(reactiveSubResolver, syncSubResolver, rootProvider);

            // When / Then
            StepVerifier.create(resolver.resolve("EXPLICIT/path"))
                    .expectNext("EXPLICIT/path")
                    .verifyComplete();
        }

        @Test
        void reactiveSubContext_combinedWithRoot() {
            // Given
            ReactiveSubContextResolver reactiveSubResolver = () -> Mono.just("mobile");
            RootContextProvider rootProvider = () -> Optional.of("BUILD");
            ReactiveContextResolver resolver = createResolver(reactiveSubResolver, null, rootProvider);

            // When / Then
            StepVerifier.create(resolver.resolve(null))
                    .expectNext("BUILD/mobile")
                    .verifyComplete();
        }

        @Test
        void reactiveSubContext_takesPrecedence_overSyncSubContext() {
            // Given
            ReactiveSubContextResolver reactiveSubResolver = () -> Mono.just("reactive-sub");
            SubContextResolver syncSubResolver = () -> Optional.of("sync-sub");
            RootContextProvider rootProvider = () -> Optional.of("BUILD");
            ReactiveContextResolver resolver = createResolver(reactiveSubResolver, syncSubResolver, rootProvider);

            // When / Then
            StepVerifier.create(resolver.resolve(null))
                    .expectNext("BUILD/reactive-sub")
                    .verifyComplete();
        }

        @Test
        void syncSubContext_usedWhenNoReactiveSubContext() {
            // Given
            SubContextResolver syncSubResolver = () -> Optional.of("sync-sub");
            RootContextProvider rootProvider = () -> Optional.of("BUILD");
            ReactiveContextResolver resolver = createResolver(null, syncSubResolver, rootProvider);

            // When / Then
            StepVerifier.create(resolver.resolve(null))
                    .expectNext("BUILD/sync-sub")
                    .verifyComplete();
        }

        @Test
        void syncSubContext_usedWhenReactiveReturnsEmpty() {
            // Given
            ReactiveSubContextResolver reactiveSubResolver = Mono::empty;
            SubContextResolver syncSubResolver = () -> Optional.of("sync-sub");
            RootContextProvider rootProvider = () -> Optional.of("BUILD");
            ReactiveContextResolver resolver = createResolver(reactiveSubResolver, syncSubResolver, rootProvider);

            // When / Then
            StepVerifier.create(resolver.resolve(null))
                    .expectNext("BUILD/sync-sub")
                    .verifyComplete();
        }

        @Test
        void rootContextOnly_usedWhenNoSubContext() {
            // Given
            RootContextProvider rootProvider = () -> Optional.of("BUILD");
            ReactiveContextResolver resolver = createResolver(null, null, rootProvider);

            // When / Then
            StepVerifier.create(resolver.resolve(null))
                    .expectNext("BUILD")
                    .verifyComplete();
        }

        @Test
        void noProviders_returnsEmpty() {
            // Given
            ReactiveContextResolver resolver = createResolver(null, null, null);

            // When / Then
            StepVerifier.create(resolver.resolve(null))
                    .verifyComplete();
        }

        @Test
        void emptyExplicitContext_fallsBackToProviders() {
            // Given
            RootContextProvider rootProvider = () -> Optional.of("BUILD");
            ReactiveContextResolver resolver = createResolver(null, null, rootProvider);

            // When / Then
            StepVerifier.create(resolver.resolve(""))
                    .expectNext("BUILD")
                    .verifyComplete();
        }

        @Test
        void blankExplicitContext_fallsBackToProviders() {
            // Given
            RootContextProvider rootProvider = () -> Optional.of("BUILD");
            ReactiveContextResolver resolver = createResolver(null, null, rootProvider);

            // When / Then
            StepVerifier.create(resolver.resolve("   "))
                    .expectNext("BUILD")
                    .verifyComplete();
        }
    }

    @Nested
    class ResilienceTests {

        @Test
        void subContextOnly_usedAsFullContext() {
            // Given - no root provider
            ReactiveSubContextResolver reactiveSubResolver = () -> Mono.just("mobile");
            ReactiveContextResolver resolver = createResolver(reactiveSubResolver, null, null);

            // When / Then - sub-context used as full context (with warning logged)
            StepVerifier.create(resolver.resolve(null))
                    .expectNext("mobile")
                    .verifyComplete();
        }

        @Test
        void reactiveSubProviderError_fallsBackToSyncSub() {
            // Given
            ReactiveSubContextResolver reactiveSubResolver = () -> Mono.error(new RuntimeException("Reactive failure"));
            SubContextResolver syncSubResolver = () -> Optional.of("sync-sub");
            RootContextProvider rootProvider = () -> Optional.of("BUILD");
            ReactiveContextResolver resolver = createResolver(reactiveSubResolver, syncSubResolver, rootProvider);

            // When / Then
            StepVerifier.create(resolver.resolve(null))
                    .expectNext("BUILD/sync-sub")
                    .verifyComplete();
        }

        @Test
        void syncSubProviderException_handledGracefully() {
            // Given - sync sub resolver throws (simulating out-of-request-scope access)
            @SuppressWarnings("unchecked")
            ObjectProvider<SubContextResolver> syncSubObjectProvider = mock(ObjectProvider.class);
            when(syncSubObjectProvider.getIfAvailable()).thenThrow(new IllegalStateException("No request scope"));

            @SuppressWarnings("unchecked")
            ObjectProvider<ReactiveSubContextResolver> reactiveSubObjectProvider = mock(ObjectProvider.class);
            when(reactiveSubObjectProvider.getIfAvailable()).thenReturn(null);

            @SuppressWarnings("unchecked")
            ObjectProvider<RootContextProvider> rootObjectProvider = mock(ObjectProvider.class);
            when(rootObjectProvider.getIfAvailable()).thenReturn(() -> Optional.of("BUILD"));

            ReactiveContextResolver resolver = new ReactiveContextResolver(
                    reactiveSubObjectProvider, syncSubObjectProvider, rootObjectProvider);

            // When / Then - falls back to root only
            StepVerifier.create(resolver.resolve(null))
                    .expectNext("BUILD")
                    .verifyComplete();
        }

        @Test
        void rootProviderException_handledGracefully() {
            // Given
            @SuppressWarnings("unchecked")
            ObjectProvider<RootContextProvider> rootObjectProvider = mock(ObjectProvider.class);
            when(rootObjectProvider.getIfAvailable()).thenThrow(new RuntimeException("Provider error"));

            @SuppressWarnings("unchecked")
            ObjectProvider<ReactiveSubContextResolver> reactiveSubObjectProvider = mock(ObjectProvider.class);
            when(reactiveSubObjectProvider.getIfAvailable()).thenReturn(null);

            @SuppressWarnings("unchecked")
            ObjectProvider<SubContextResolver> syncSubObjectProvider = mock(ObjectProvider.class);
            when(syncSubObjectProvider.getIfAvailable()).thenReturn(null);

            ReactiveContextResolver resolver = new ReactiveContextResolver(
                    reactiveSubObjectProvider, syncSubObjectProvider, rootObjectProvider);

            // When / Then
            StepVerifier.create(resolver.resolve(null))
                    .verifyComplete();
        }

        @Test
        void emptyRootContext_treatedAsNotPresent() {
            // Given
            ReactiveSubContextResolver reactiveSubResolver = () -> Mono.just("mobile");
            RootContextProvider rootProvider = () -> Optional.of("");
            ReactiveContextResolver resolver = createResolver(reactiveSubResolver, null, rootProvider);

            // When / Then - empty root is filtered out, sub-context used as full
            StepVerifier.create(resolver.resolve(null))
                    .expectNext("mobile")
                    .verifyComplete();
        }

        @Test
        void blankSubContext_treatedAsNotPresent() {
            // Given
            ReactiveSubContextResolver reactiveSubResolver = () -> Mono.just("   ");
            RootContextProvider rootProvider = () -> Optional.of("BUILD");
            ReactiveContextResolver resolver = createResolver(reactiveSubResolver, null, rootProvider);

            // When / Then - blank sub-context filtered out, only root used
            StepVerifier.create(resolver.resolve(null))
                    .expectNext("BUILD")
                    .verifyComplete();
        }
    }

    @Nested
    class NormalizationTests {

        @Test
        void explicitContextWithSlashes_normalized() {
            ReactiveContextResolver resolver = createResolver(null, null, null);

            StepVerifier.create(resolver.resolve("//EXPLICIT//path//"))
                    .expectNext("EXPLICIT/path")
                    .verifyComplete();
        }

        @Test
        void rootAndSubWithSlashes_normalizedCorrectly() {
            // Given
            ReactiveSubContextResolver reactiveSubResolver = () -> Mono.just("/mobile/");
            RootContextProvider rootProvider = () -> Optional.of("/BUILD/");
            ReactiveContextResolver resolver = createResolver(reactiveSubResolver, null, rootProvider);

            // When / Then
            StepVerifier.create(resolver.resolve(null))
                    .expectNext("BUILD/mobile")
                    .verifyComplete();
        }
    }

    @Nested
    class CombinationTests {

        @Test
        void nestedSubContext_combinesWithRoot() {
            // Given
            ReactiveSubContextResolver reactiveSubResolver = () -> Mono.just("mobile/android");
            RootContextProvider rootProvider = () -> Optional.of("BUILD");
            ReactiveContextResolver resolver = createResolver(reactiveSubResolver, null, rootProvider);

            // When / Then
            StepVerifier.create(resolver.resolve(null))
                    .expectNext("BUILD/mobile/android")
                    .verifyComplete();
        }

        @Test
        void deeplyNestedSubContext_combinesWithRoot() {
            // Given
            ReactiveSubContextResolver reactiveSubResolver = () -> Mono.just("region/eu/west");
            RootContextProvider rootProvider = () -> Optional.of("PROD");
            ReactiveContextResolver resolver = createResolver(reactiveSubResolver, null, rootProvider);

            // When / Then
            StepVerifier.create(resolver.resolve(null))
                    .expectNext("PROD/region/eu/west")
                    .verifyComplete();
        }

        @Test
        void nestedSubContextWithSlashes_normalizedCorrectly() {
            // Given
            ReactiveSubContextResolver reactiveSubResolver = () -> Mono.just("/mobile//android/");
            RootContextProvider rootProvider = () -> Optional.of("BUILD");
            ReactiveContextResolver resolver = createResolver(reactiveSubResolver, null, rootProvider);

            // When / Then
            StepVerifier.create(resolver.resolve(null))
                    .expectNext("BUILD/mobile/android")
                    .verifyComplete();
        }
    }

    // Helper method to create resolver with mocked ObjectProviders
    @SuppressWarnings("unchecked")
    private ReactiveContextResolver createResolver(
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
