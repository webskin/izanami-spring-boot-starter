package fr.maif.izanami.spring.test;

import fr.maif.izanami.spring.service.api.BatchResult;
import fr.maif.izanami.spring.service.api.ReactiveBatchFeatureRequestBuilder;
import reactor.core.publisher.Mono;

import static org.mockito.Mockito.when;

/**
 * Stub builder for configuring return values on a mocked {@link ReactiveBatchFeatureRequestBuilder}.
 * <p>
 * Reactive counterpart of {@link BatchFlagStubBuilder}.
 * <p>
 * Obtained via {@link ReactiveIzanamiMockHelper#givenFlagKeys(fr.maif.izanami.spring.service.api.ReactiveIzanamiService, String...)}
 * or {@link ReactiveIzanamiMockHelper#givenFlagNames(fr.maif.izanami.spring.service.api.ReactiveIzanamiService, String...)}.
 */
public final class ReactiveBatchFlagStubBuilder {

    private final ReactiveBatchFeatureRequestBuilder builderMock;

    ReactiveBatchFlagStubBuilder(ReactiveBatchFeatureRequestBuilder builderMock) {
        this.builderMock = builderMock;
    }

    /**
     * Stub the builder to return the given batch result.
     *
     * @param result the batch result to return
     */
    public void willReturn(BatchResult result) {
        when(builderMock.values()).thenReturn(Mono.just(result));
    }

    /**
     * Stub the builder to fail with the given exception.
     *
     * @param exception the exception to throw
     */
    public void willFailWith(Throwable exception) {
        when(builderMock.values()).thenReturn(Mono.error(exception));
    }

    /**
     * Get the underlying builder mock for advanced verification.
     *
     * @return the Mockito mock of {@link ReactiveBatchFeatureRequestBuilder}
     */
    public ReactiveBatchFeatureRequestBuilder getBuilderMock() {
        return builderMock;
    }
}
