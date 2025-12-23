package fr.maif.izanami.spring.service;

import fr.maif.izanami.spring.service.api.RootContextProvider;
import fr.maif.izanami.spring.service.api.SubContextResolver;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompositeContextResolverTest {

    @Nested
    class ResolutionOrderTests {

        @Test
        void explicitContext_takesPrecedence_overAllProviders() {
            // Given
            RootContextProvider rootProvider = () -> Optional.of("ROOT");
            SubContextResolver subResolver = () -> Optional.of("sub");
            CompositeContextResolver resolver = createResolver(rootProvider, subResolver);

            // When
            Optional<String> result = resolver.resolve("EXPLICIT/path");

            // Then
            assertThat(result).isPresent().contains("EXPLICIT/path");
        }

        @Test
        void subContextWithRoot_combinesCorrectly() {
            // Given
            RootContextProvider rootProvider = () -> Optional.of("BUILD");
            SubContextResolver subResolver = () -> Optional.of("mobile");
            CompositeContextResolver resolver = createResolver(rootProvider, subResolver);

            // When
            Optional<String> result = resolver.resolve(null);

            // Then
            assertThat(result).isPresent().contains("BUILD/mobile");
        }

        @Test
        void rootContextOnly_usedWhenNoSubContext() {
            // Given
            RootContextProvider rootProvider = () -> Optional.of("BUILD");
            SubContextResolver subResolver = Optional::empty;
            CompositeContextResolver resolver = createResolver(rootProvider, subResolver);

            // When
            Optional<String> result = resolver.resolve(null);

            // Then
            assertThat(result).isPresent().contains("BUILD");
        }

        @Test
        void noProviders_returnsEmpty() {
            // Given
            CompositeContextResolver resolver = createResolver(null, null);

            // When
            Optional<String> result = resolver.resolve(null);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        void emptyExplicitContext_fallsBackToProviders() {
            // Given
            RootContextProvider rootProvider = () -> Optional.of("BUILD");
            CompositeContextResolver resolver = createResolver(rootProvider, null);

            // When - empty string should be treated as no explicit context
            Optional<String> result = resolver.resolve("");

            // Then - should use root provider
            assertThat(result).isPresent().contains("BUILD");
        }

        @Test
        void blankExplicitContext_fallsBackToProviders() {
            // Given
            RootContextProvider rootProvider = () -> Optional.of("BUILD");
            CompositeContextResolver resolver = createResolver(rootProvider, null);

            // When - blank string should be treated as no explicit context
            Optional<String> result = resolver.resolve("   ");

            // Then - should use root provider
            assertThat(result).isPresent().contains("BUILD");
        }
    }

    @Nested
    class ResilienceTests {

        @Test
        void subContextOnly_usedAsFullContext() {
            // Given - no root provider
            SubContextResolver subResolver = () -> Optional.of("mobile");
            CompositeContextResolver resolver = createResolver(null, subResolver);

            // When
            Optional<String> result = resolver.resolve(null);

            // Then - sub-context used as full context (with warning logged)
            assertThat(result).isPresent().contains("mobile");
        }

        @Test
        void requestScopeException_handledGracefully() {
            // Given - sub resolver throws (simulating out-of-request-scope access)
            @SuppressWarnings("unchecked")
            ObjectProvider<SubContextResolver> subProvider = mock(ObjectProvider.class);
            when(subProvider.getIfAvailable()).thenThrow(new IllegalStateException("No request scope"));

            @SuppressWarnings("unchecked")
            ObjectProvider<RootContextProvider> rootProvider = mock(ObjectProvider.class);
            when(rootProvider.getIfAvailable()).thenReturn(() -> Optional.of("BUILD"));

            CompositeContextResolver resolver = new CompositeContextResolver(rootProvider, subProvider);

            // When
            Optional<String> result = resolver.resolve(null);

            // Then - falls back to root only
            assertThat(result).isPresent().contains("BUILD");
        }

        @Test
        void rootProviderException_handledGracefully() {
            // Given - root provider throws
            @SuppressWarnings("unchecked")
            ObjectProvider<RootContextProvider> rootProvider = mock(ObjectProvider.class);
            when(rootProvider.getIfAvailable()).thenThrow(new RuntimeException("Provider error"));

            @SuppressWarnings("unchecked")
            ObjectProvider<SubContextResolver> subProvider = mock(ObjectProvider.class);
            when(subProvider.getIfAvailable()).thenReturn(null);

            CompositeContextResolver resolver = new CompositeContextResolver(rootProvider, subProvider);

            // When
            Optional<String> result = resolver.resolve(null);

            // Then - returns empty
            assertThat(result).isEmpty();
        }

        @Test
        void emptyRootContext_treatedAsNotPresent() {
            // Given
            RootContextProvider rootProvider = () -> Optional.of("");
            SubContextResolver subResolver = () -> Optional.of("mobile");
            CompositeContextResolver resolver = createResolver(rootProvider, subResolver);

            // When
            Optional<String> result = resolver.resolve(null);

            // Then - empty root is filtered out, sub-context used as full
            assertThat(result).isPresent().contains("mobile");
        }

        @Test
        void blankSubContext_treatedAsNotPresent() {
            // Given
            RootContextProvider rootProvider = () -> Optional.of("BUILD");
            SubContextResolver subResolver = () -> Optional.of("   ");
            CompositeContextResolver resolver = createResolver(rootProvider, subResolver);

            // When
            Optional<String> result = resolver.resolve(null);

            // Then - blank sub-context filtered out, only root used
            assertThat(result).isPresent().contains("BUILD");
        }
    }

    @Nested
    class NormalizationTests {

        @Test
        void trimWhitespace() {
            CompositeContextResolver resolver = createResolver(null, null);
            assertThat(resolver.normalize("  BUILD  ")).isEqualTo("BUILD");
        }

        @Test
        void removeLeadingSlash() {
            CompositeContextResolver resolver = createResolver(null, null);
            assertThat(resolver.normalize("/BUILD")).isEqualTo("BUILD");
        }

        @Test
        void removeTrailingSlash() {
            CompositeContextResolver resolver = createResolver(null, null);
            assertThat(resolver.normalize("BUILD/")).isEqualTo("BUILD");
        }

        @Test
        void collapseMultipleSlashes() {
            CompositeContextResolver resolver = createResolver(null, null);
            assertThat(resolver.normalize("BUILD//mobile///web")).isEqualTo("BUILD/mobile/web");
        }

        @Test
        void complexNormalization() {
            CompositeContextResolver resolver = createResolver(null, null);
            assertThat(resolver.normalize("  /BUILD//mobile/  ")).isEqualTo("BUILD/mobile");
        }

        @Test
        void nullInput_returnsEmptyString() {
            CompositeContextResolver resolver = createResolver(null, null);
            assertThat(resolver.normalize(null)).isEqualTo("");
        }

        @Test
        void emptyInput_returnsEmptyString() {
            CompositeContextResolver resolver = createResolver(null, null);
            assertThat(resolver.normalize("")).isEqualTo("");
        }

        @Test
        void onlySlashes_returnsEmptyString() {
            CompositeContextResolver resolver = createResolver(null, null);
            assertThat(resolver.normalize("///")).isEqualTo("");
        }
    }

    @Nested
    class CombinationTests {

        @Test
        void rootAndSubWithSlashes_normalizedCorrectly() {
            // Given - providers return values with slashes
            RootContextProvider rootProvider = () -> Optional.of("/BUILD/");
            SubContextResolver subResolver = () -> Optional.of("/mobile/");
            CompositeContextResolver resolver = createResolver(rootProvider, subResolver);

            // When
            Optional<String> result = resolver.resolve(null);

            // Then - normalized and combined correctly
            assertThat(result).isPresent().contains("BUILD/mobile");
        }

        @Test
        void explicitContextWithSlashes_normalizedCorrectly() {
            CompositeContextResolver resolver = createResolver(null, null);

            Optional<String> result = resolver.resolve("//EXPLICIT//path//");

            assertThat(result).isPresent().contains("EXPLICIT/path");
        }

        @Test
        void nestedSubContext_combinesWithRoot() {
            // Given - sub-context is a nested path
            RootContextProvider rootProvider = () -> Optional.of("BUILD");
            SubContextResolver subResolver = () -> Optional.of("mobile/android");
            CompositeContextResolver resolver = createResolver(rootProvider, subResolver);

            // When
            Optional<String> result = resolver.resolve(null);

            // Then - root + nested sub-context combined correctly
            assertThat(result).isPresent().contains("BUILD/mobile/android");
        }

        @Test
        void deeplyNestedSubContext_combinesWithRoot() {
            // Given - sub-context is a deeply nested path
            RootContextProvider rootProvider = () -> Optional.of("PROD");
            SubContextResolver subResolver = () -> Optional.of("region/eu/west");
            CompositeContextResolver resolver = createResolver(rootProvider, subResolver);

            // When
            Optional<String> result = resolver.resolve(null);

            // Then
            assertThat(result).isPresent().contains("PROD/region/eu/west");
        }

        @Test
        void nestedSubContextWithSlashes_normalizedCorrectly() {
            // Given - sub-context has extra slashes
            RootContextProvider rootProvider = () -> Optional.of("BUILD");
            SubContextResolver subResolver = () -> Optional.of("/mobile//android/");
            CompositeContextResolver resolver = createResolver(rootProvider, subResolver);

            // When
            Optional<String> result = resolver.resolve(null);

            // Then - slashes normalized
            assertThat(result).isPresent().contains("BUILD/mobile/android");
        }
    }

    // Helper method to create resolver with mocked ObjectProviders
    @SuppressWarnings("unchecked")
    private CompositeContextResolver createResolver(
            RootContextProvider rootProvider,
            SubContextResolver subResolver
    ) {
        ObjectProvider<RootContextProvider> rootObjectProvider = mock(ObjectProvider.class);
        ObjectProvider<SubContextResolver> subObjectProvider = mock(ObjectProvider.class);

        when(rootObjectProvider.getIfAvailable()).thenReturn(rootProvider);
        when(subObjectProvider.getIfAvailable()).thenReturn(subResolver);

        return new CompositeContextResolver(rootObjectProvider, subObjectProvider);
    }
}
