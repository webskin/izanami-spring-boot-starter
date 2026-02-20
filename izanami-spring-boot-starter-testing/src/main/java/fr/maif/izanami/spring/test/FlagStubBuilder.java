package fr.maif.izanami.spring.test;

import fr.maif.izanami.spring.service.api.FeatureRequestBuilder;
import fr.maif.izanami.spring.service.api.ResultValueWithDetails;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.when;

/**
 * Stub builder for configuring return values on a mocked {@link FeatureRequestBuilder}.
 * <p>
 * Obtained via {@link IzanamiMockHelper#givenFlagKey(fr.maif.izanami.spring.service.api.IzanamiService, String)}
 * or {@link IzanamiMockHelper#givenFlagName(fr.maif.izanami.spring.service.api.IzanamiService, String)}.
 * <p>
 * Example:
 * <pre>{@code
 * FlagStubBuilder stub = givenFlagKey(service, "uuid-1");
 * stub.willReturn(true);
 *
 * // Advanced: verify builder interactions
 * verify(stub.getBuilderMock()).withUser("alice");
 * }</pre>
 */
public final class FlagStubBuilder {

    private final FeatureRequestBuilder builderMock;

    FlagStubBuilder(FeatureRequestBuilder builderMock) {
        this.builderMock = builderMock;
    }

    /**
     * Stub the builder to return the given boolean value.
     * <p>
     * Stubs both {@code booleanValue()} and {@code booleanValueDetails()}.
     *
     * @param value the value to return
     */
    public void willReturn(boolean value) {
        when(builderMock.booleanValue()).thenReturn(CompletableFuture.completedFuture(value));
        when(builderMock.booleanValueDetails()).thenReturn(
                CompletableFuture.completedFuture(new ResultValueWithDetails<>(value, Map.of()))
        );
    }

    /**
     * Stub the builder to return the given string value.
     * <p>
     * Stubs both {@code stringValue()} and {@code stringValueDetails()}.
     *
     * @param value the value to return
     */
    public void willReturn(String value) {
        when(builderMock.stringValue()).thenReturn(CompletableFuture.completedFuture(value));
        when(builderMock.stringValueDetails()).thenReturn(
                CompletableFuture.completedFuture(new ResultValueWithDetails<>(value, Map.of()))
        );
    }

    /**
     * Stub the builder to return the given number value.
     * <p>
     * Stubs both {@code numberValue()} and {@code numberValueDetails()}.
     *
     * @param value the value to return
     */
    public void willReturn(BigDecimal value) {
        when(builderMock.numberValue()).thenReturn(CompletableFuture.completedFuture(value));
        when(builderMock.numberValueDetails()).thenReturn(
                CompletableFuture.completedFuture(new ResultValueWithDetails<>(value, Map.of()))
        );
    }

    /**
     * Stub the builder to fail with the given exception for all terminal methods.
     *
     * @param exception the exception to throw
     */
    public void willFailWith(Throwable exception) {
        CompletableFuture<Boolean> failedBool = CompletableFuture.failedFuture(exception);
        CompletableFuture<String> failedStr = CompletableFuture.failedFuture(exception);
        CompletableFuture<BigDecimal> failedNum = CompletableFuture.failedFuture(exception);
        CompletableFuture<ResultValueWithDetails<Boolean>> failedBoolD = CompletableFuture.failedFuture(exception);
        CompletableFuture<ResultValueWithDetails<String>> failedStrD = CompletableFuture.failedFuture(exception);
        CompletableFuture<ResultValueWithDetails<BigDecimal>> failedNumD = CompletableFuture.failedFuture(exception);

        when(builderMock.booleanValue()).thenReturn(failedBool);
        when(builderMock.stringValue()).thenReturn(failedStr);
        when(builderMock.numberValue()).thenReturn(failedNum);
        when(builderMock.booleanValueDetails()).thenReturn(failedBoolD);
        when(builderMock.stringValueDetails()).thenReturn(failedStrD);
        when(builderMock.numberValueDetails()).thenReturn(failedNumD);
    }

    /**
     * Get the underlying builder mock for advanced verification.
     * <p>
     * Example:
     * <pre>{@code
     * verify(stub.getBuilderMock()).withUser("alice");
     * }</pre>
     *
     * @return the Mockito mock of {@link FeatureRequestBuilder}
     */
    public FeatureRequestBuilder getBuilderMock() {
        return builderMock;
    }
}
