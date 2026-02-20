package fr.maif.izanami.spring.test;

import fr.maif.izanami.spring.service.api.BatchFeatureRequestBuilder;
import fr.maif.izanami.spring.service.api.FeatureRequestBuilder;
import fr.maif.izanami.spring.service.api.IzanamiService;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Mockito helper for creating and configuring {@link IzanamiService} mocks.
 * <p>
 * Eliminates the boilerplate of manually creating RETURNS_SELF builder mocks
 * and wiring them to the service mock.
 * <p>
 * Example usage:
 * <pre>{@code
 * import static fr.maif.izanami.spring.test.IzanamiMockHelper.*;
 *
 * IzanamiService service = mockIzanamiService();
 *
 * // One-liner per flag
 * givenFlagKey(service, "uuid-1").willReturn(true);
 * givenFlagName(service, "tier").willReturn("premium");
 * givenFlagKey(service, "score").willReturn(new BigDecimal("42"));
 *
 * // Batch
 * givenFlagKeys(service, "f1", "f2").willReturn(
 *     BatchResultBuilder.create()
 *         .withBooleanFlag("f1", true)
 *         .withStringFlag("f2", "value")
 *         .build()
 * );
 * }</pre>
 */
public final class IzanamiMockHelper {

    private IzanamiMockHelper() {
    }

    /**
     * Create a pre-configured {@link IzanamiService} mock.
     * <p>
     * The mock is configured with safe defaults:
     * <ul>
     *   <li>{@code isConnected()} returns {@code true}</li>
     *   <li>{@code unwrapClient()} returns {@code Optional.empty()}</li>
     *   <li>{@code whenLoaded()} returns a completed future</li>
     * </ul>
     *
     * @return a Mockito mock of IzanamiService
     */
    public static IzanamiService mockIzanamiService() {
        IzanamiService service = mock(IzanamiService.class);
        when(service.isConnected()).thenReturn(true);
        when(service.unwrapClient()).thenReturn(Optional.empty());
        when(service.whenLoaded()).thenReturn(CompletableFuture.completedFuture(null));
        return service;
    }

    /**
     * Set up a stub for a single flag by key.
     *
     * @param service the mocked IzanamiService
     * @param flagKey the flag key (UUID)
     * @return a stub builder to configure the return value
     */
    public static FlagStubBuilder givenFlagKey(IzanamiService service, String flagKey) {
        FeatureRequestBuilder builder = mock(FeatureRequestBuilder.class, RETURNS_SELF);
        when(service.forFlagKey(flagKey)).thenReturn(builder);
        return new FlagStubBuilder(builder);
    }

    /**
     * Set up a stub for a single flag by name.
     *
     * @param service  the mocked IzanamiService
     * @param flagName the flag name
     * @return a stub builder to configure the return value
     */
    public static FlagStubBuilder givenFlagName(IzanamiService service, String flagName) {
        FeatureRequestBuilder builder = mock(FeatureRequestBuilder.class, RETURNS_SELF);
        when(service.forFlagName(flagName)).thenReturn(builder);
        return new FlagStubBuilder(builder);
    }

    /**
     * Set up a stub for a batch of flags by keys.
     *
     * @param service  the mocked IzanamiService
     * @param flagKeys the flag keys (UUIDs)
     * @return a batch stub builder to configure the return value
     */
    public static BatchFlagStubBuilder givenFlagKeys(IzanamiService service, String... flagKeys) {
        BatchFeatureRequestBuilder builder = mock(BatchFeatureRequestBuilder.class, RETURNS_SELF);
        when(service.forFlagKeys(flagKeys)).thenReturn(builder);
        return new BatchFlagStubBuilder(builder);
    }

    /**
     * Set up a stub for a batch of flags by names.
     *
     * @param service   the mocked IzanamiService
     * @param flagNames the flag names
     * @return a batch stub builder to configure the return value
     */
    public static BatchFlagStubBuilder givenFlagNames(IzanamiService service, String... flagNames) {
        BatchFeatureRequestBuilder builder = mock(BatchFeatureRequestBuilder.class, RETURNS_SELF);
        when(service.forFlagNames(flagNames)).thenReturn(builder);
        return new BatchFlagStubBuilder(builder);
    }
}
