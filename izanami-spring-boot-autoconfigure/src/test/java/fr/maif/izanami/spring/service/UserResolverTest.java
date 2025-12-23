package fr.maif.izanami.spring.service;

import fr.maif.izanami.spring.service.api.UserProvider;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class UserResolverTest {

    @Nested
    class ResolutionOrderTests {

        @Test
        void explicitUser_takesPrecedence_overProvider() {
            // Given
            UserProvider userProvider = () -> Optional.of("provider-user");
            UserResolver resolver = createResolver(userProvider);

            // When
            Optional<String> result = resolver.resolve("explicit-user");

            // Then
            assertThat(result).isPresent().contains("explicit-user");
        }

        @Test
        void userProvider_usedWhenNoExplicitUser() {
            // Given
            UserProvider userProvider = () -> Optional.of("provider-user");
            UserResolver resolver = createResolver(userProvider);

            // When
            Optional<String> result = resolver.resolve(null);

            // Then
            assertThat(result).isPresent().contains("provider-user");
        }

        @Test
        void noProvider_returnsEmpty() {
            // Given
            UserResolver resolver = createResolver(null);

            // When
            Optional<String> result = resolver.resolve(null);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        void emptyExplicitUser_fallsBackToProvider() {
            // Given
            UserProvider userProvider = () -> Optional.of("provider-user");
            UserResolver resolver = createResolver(userProvider);

            // When - empty string should be treated as no explicit user
            Optional<String> result = resolver.resolve("");

            // Then - should use provider
            assertThat(result).isPresent().contains("provider-user");
        }

        @Test
        void blankExplicitUser_fallsBackToProvider() {
            // Given
            UserProvider userProvider = () -> Optional.of("provider-user");
            UserResolver resolver = createResolver(userProvider);

            // When - blank string should be treated as no explicit user
            Optional<String> result = resolver.resolve("   ");

            // Then - should use provider
            assertThat(result).isPresent().contains("provider-user");
        }
    }

    @Nested
    class ResilienceTests {

        @Test
        void requestScopeException_handledGracefully() {
            // Given - provider throws (simulating out-of-request-scope access)
            @SuppressWarnings("unchecked")
            ObjectProvider<UserProvider> userProvider = mock(ObjectProvider.class);
            when(userProvider.getIfAvailable()).thenThrow(new IllegalStateException("No request scope"));

            UserResolver resolver = new UserResolver(userProvider);

            // When
            Optional<String> result = resolver.resolve(null);

            // Then - returns empty
            assertThat(result).isEmpty();
        }

        @Test
        void providerReturnsEmpty_returnsEmpty() {
            // Given
            UserProvider userProvider = Optional::empty;
            UserResolver resolver = createResolver(userProvider);

            // When
            Optional<String> result = resolver.resolve(null);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        void providerReturnsEmptyString_treatedAsNotPresent() {
            // Given
            UserProvider userProvider = () -> Optional.of("");
            UserResolver resolver = createResolver(userProvider);

            // When
            Optional<String> result = resolver.resolve(null);

            // Then - empty string is filtered out
            assertThat(result).isEmpty();
        }

        @Test
        void providerReturnsBlankString_treatedAsNotPresent() {
            // Given
            UserProvider userProvider = () -> Optional.of("   ");
            UserResolver resolver = createResolver(userProvider);

            // When
            Optional<String> result = resolver.resolve(null);

            // Then - blank string is filtered out
            assertThat(result).isEmpty();
        }
    }

    @Nested
    class NormalizationTests {

        @Test
        void explicitUser_trimmed() {
            // Given
            UserResolver resolver = createResolver(null);

            // When
            Optional<String> result = resolver.resolve("  user-123  ");

            // Then
            assertThat(result).isPresent().contains("user-123");
        }

        @Test
        void providerUser_trimmed() {
            // Given
            UserProvider userProvider = () -> Optional.of("  provider-user  ");
            UserResolver resolver = createResolver(userProvider);

            // When
            Optional<String> result = resolver.resolve(null);

            // Then
            assertThat(result).isPresent().contains("provider-user");
        }
    }

    // Helper method to create resolver with mocked ObjectProvider
    @SuppressWarnings("unchecked")
    private UserResolver createResolver(UserProvider provider) {
        ObjectProvider<UserProvider> objectProvider = mock(ObjectProvider.class);
        when(objectProvider.getIfAvailable()).thenReturn(provider);
        return new UserResolver(objectProvider);
    }
}
