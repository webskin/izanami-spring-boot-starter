package fr.maif.izanami.spring.test;

import fr.maif.izanami.spring.service.api.ReactiveFeatureRequestBuilder;
import fr.maif.izanami.spring.service.api.ResultValueWithDetails;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Map;

import static org.mockito.Mockito.when;

/**
 * Stub builder for configuring return values on a mocked {@link ReactiveFeatureRequestBuilder}.
 * <p>
 * Reactive counterpart of {@link FlagStubBuilder}. Terminal methods are stubbed
 * with {@link Mono} instead of {@link java.util.concurrent.CompletableFuture}.
 * <p>
 * Obtained via {@link ReactiveIzanamiMockHelper#givenFlagKey(fr.maif.izanami.spring.service.api.ReactiveIzanamiService, String)}
 * or {@link ReactiveIzanamiMockHelper#givenFlagName(fr.maif.izanami.spring.service.api.ReactiveIzanamiService, String)}.
 */
public final class ReactiveFlagStubBuilder {

    private final ReactiveFeatureRequestBuilder builderMock;

    ReactiveFlagStubBuilder(ReactiveFeatureRequestBuilder builderMock) {
        this.builderMock = builderMock;
    }

    /**
     * Stub the builder to return the given boolean value.
     *
     * @param value the value to return
     */
    public void willReturn(boolean value) {
        when(builderMock.booleanValue()).thenReturn(Mono.justOrEmpty(value));
        when(builderMock.booleanValueDetails()).thenReturn(
                Mono.justOrEmpty(new ResultValueWithDetails<>(value, Map.of()))
        );
    }

    /**
     * Stub the builder to return the given string value.
     *
     * @param value the value to return (may be null)
     */
    public void willReturn(String value) {
        when(builderMock.stringValue()).thenReturn(Mono.justOrEmpty(value));
        when(builderMock.stringValueDetails()).thenReturn(
                Mono.justOrEmpty(new ResultValueWithDetails<>(value, Map.of()))
        );
    }

    /**
     * Stub the builder to return the given number value.
     *
     * @param value the value to return (may be null)
     */
    public void willReturn(BigDecimal value) {
        when(builderMock.numberValue()).thenReturn(Mono.justOrEmpty(value));
        when(builderMock.numberValueDetails()).thenReturn(
                Mono.justOrEmpty(new ResultValueWithDetails<>(value, Map.of()))
        );
    }

    /**
     * Stub the builder to fail with the given exception for all terminal methods.
     *
     * @param exception the exception to throw
     */
    public void willFailWith(Throwable exception) {
        when(builderMock.booleanValue()).thenReturn(Mono.error(exception));
        when(builderMock.stringValue()).thenReturn(Mono.error(exception));
        when(builderMock.numberValue()).thenReturn(Mono.error(exception));
        when(builderMock.booleanValueDetails()).thenReturn(Mono.error(exception));
        when(builderMock.stringValueDetails()).thenReturn(Mono.error(exception));
        when(builderMock.numberValueDetails()).thenReturn(Mono.error(exception));
    }

    /**
     * Get the underlying builder mock for advanced verification.
     *
     * @return the Mockito mock of {@link ReactiveFeatureRequestBuilder}
     */
    public ReactiveFeatureRequestBuilder getBuilderMock() {
        return builderMock;
    }
}
