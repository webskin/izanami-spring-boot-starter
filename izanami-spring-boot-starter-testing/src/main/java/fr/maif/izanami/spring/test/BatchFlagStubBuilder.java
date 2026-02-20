package fr.maif.izanami.spring.test;

import fr.maif.izanami.spring.service.api.BatchFeatureRequestBuilder;
import fr.maif.izanami.spring.service.api.BatchResult;

import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.when;

/**
 * Stub builder for configuring return values on a mocked {@link BatchFeatureRequestBuilder}.
 * <p>
 * Obtained via {@link IzanamiMockHelper#givenFlagKeys(fr.maif.izanami.spring.service.api.IzanamiService, String...)}
 * or {@link IzanamiMockHelper#givenFlagNames(fr.maif.izanami.spring.service.api.IzanamiService, String...)}.
 * <p>
 * Example:
 * <pre>{@code
 * givenFlagKeys(service, "f1", "f2").willReturn(
 *     BatchResultBuilder.create()
 *         .withBooleanFlag("f1", true)
 *         .withStringFlag("f2", "value")
 *         .build()
 * );
 * }</pre>
 */
public final class BatchFlagStubBuilder {

    private final BatchFeatureRequestBuilder builderMock;

    BatchFlagStubBuilder(BatchFeatureRequestBuilder builderMock) {
        this.builderMock = builderMock;
    }

    /**
     * Stub the builder to return the given batch result.
     *
     * @param result the batch result to return
     */
    public void willReturn(BatchResult result) {
        when(builderMock.values()).thenReturn(CompletableFuture.completedFuture(result));
    }

    /**
     * Stub the builder to fail with the given exception.
     *
     * @param exception the exception to throw
     */
    public void willFailWith(Throwable exception) {
        when(builderMock.values()).thenReturn(CompletableFuture.failedFuture(exception));
    }

    /**
     * Get the underlying builder mock for advanced verification.
     *
     * @return the Mockito mock of {@link BatchFeatureRequestBuilder}
     */
    public BatchFeatureRequestBuilder getBuilderMock() {
        return builderMock;
    }
}
