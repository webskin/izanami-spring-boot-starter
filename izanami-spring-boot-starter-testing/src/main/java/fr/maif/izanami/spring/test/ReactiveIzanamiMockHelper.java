package fr.maif.izanami.spring.test;

import fr.maif.izanami.spring.service.api.ReactiveBatchFeatureRequestBuilder;
import fr.maif.izanami.spring.service.api.ReactiveFeatureRequestBuilder;
import fr.maif.izanami.spring.service.api.ReactiveIzanamiService;
import reactor.core.publisher.Mono;

import java.util.Optional;

import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Mockito helper for creating and configuring {@link ReactiveIzanamiService} mocks.
 * <p>
 * Reactive counterpart of {@link IzanamiMockHelper}. Uses the same DSL pattern
 * but stubs reactive return types ({@link reactor.core.publisher.Mono}).
 * <p>
 * Example usage:
 * <pre>{@code
 * import static fr.maif.izanami.spring.test.ReactiveIzanamiMockHelper.*;
 *
 * ReactiveIzanamiService service = mockReactiveIzanamiService();
 *
 * givenFlagKey(service, "uuid-1").willReturn(true);
 * givenFlagName(service, "tier").willReturn("premium");
 * }</pre>
 */
public final class ReactiveIzanamiMockHelper {

    private ReactiveIzanamiMockHelper() {
    }

    /**
     * Create a pre-configured {@link ReactiveIzanamiService} mock.
     * <p>
     * The mock is configured with safe defaults:
     * <ul>
     *   <li>{@code isConnected()} returns {@code true}</li>
     *   <li>{@code unwrapClient()} returns {@code Optional.empty()}</li>
     *   <li>{@code whenLoaded()} returns {@code Mono.empty()}</li>
     * </ul>
     *
     * @return a Mockito mock of ReactiveIzanamiService
     */
    public static ReactiveIzanamiService mockReactiveIzanamiService() {
        ReactiveIzanamiService service = mock(ReactiveIzanamiService.class);
        when(service.isConnected()).thenReturn(true);
        when(service.unwrapClient()).thenReturn(Optional.empty());
        when(service.whenLoaded()).thenReturn(Mono.empty());
        return service;
    }

    /**
     * Set up a stub for a single flag by key.
     *
     * @param service the mocked ReactiveIzanamiService
     * @param flagKey the flag key (UUID)
     * @return a reactive stub builder to configure the return value
     */
    public static ReactiveFlagStubBuilder givenFlagKey(ReactiveIzanamiService service, String flagKey) {
        ReactiveFeatureRequestBuilder builder = mock(ReactiveFeatureRequestBuilder.class, RETURNS_SELF);
        when(service.forFlagKey(flagKey)).thenReturn(builder);
        return new ReactiveFlagStubBuilder(builder);
    }

    /**
     * Set up a stub for a single flag by name.
     *
     * @param service  the mocked ReactiveIzanamiService
     * @param flagName the flag name
     * @return a reactive stub builder to configure the return value
     */
    public static ReactiveFlagStubBuilder givenFlagName(ReactiveIzanamiService service, String flagName) {
        ReactiveFeatureRequestBuilder builder = mock(ReactiveFeatureRequestBuilder.class, RETURNS_SELF);
        when(service.forFlagName(flagName)).thenReturn(builder);
        return new ReactiveFlagStubBuilder(builder);
    }

    /**
     * Set up a stub for a batch of flags by keys.
     *
     * @param service  the mocked ReactiveIzanamiService
     * @param flagKeys the flag keys (UUIDs)
     * @return a reactive batch stub builder to configure the return value
     */
    public static ReactiveBatchFlagStubBuilder givenFlagKeys(ReactiveIzanamiService service, String... flagKeys) {
        ReactiveBatchFeatureRequestBuilder builder = mock(ReactiveBatchFeatureRequestBuilder.class, RETURNS_SELF);
        when(service.forFlagKeys(flagKeys)).thenReturn(builder);
        return new ReactiveBatchFlagStubBuilder(builder);
    }

    /**
     * Set up a stub for a batch of flags by names.
     *
     * @param service   the mocked ReactiveIzanamiService
     * @param flagNames the flag names
     * @return a reactive batch stub builder to configure the return value
     */
    public static ReactiveBatchFlagStubBuilder givenFlagNames(ReactiveIzanamiService service, String... flagNames) {
        ReactiveBatchFeatureRequestBuilder builder = mock(ReactiveBatchFeatureRequestBuilder.class, RETURNS_SELF);
        when(service.forFlagNames(flagNames)).thenReturn(builder);
        return new ReactiveBatchFlagStubBuilder(builder);
    }
}
