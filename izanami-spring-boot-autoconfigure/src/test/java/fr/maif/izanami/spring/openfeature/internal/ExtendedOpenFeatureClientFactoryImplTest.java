package fr.maif.izanami.spring.openfeature.internal;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.OpenFeatureAPI;
import fr.maif.izanami.spring.openfeature.ValueConverter;
import fr.maif.izanami.spring.openfeature.api.ExtendedOpenFeatureClient;
import fr.maif.izanami.spring.openfeature.api.FlagConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ExtendedOpenFeatureClientFactoryImpl}.
 */
class ExtendedOpenFeatureClientFactoryImplTest {

    private OpenFeatureAPI openFeatureAPI;
    private FlagConfigService flagConfigService;
    private ValueConverter valueConverter;
    private ExtendedOpenFeatureClientFactoryImpl factory;

    @BeforeEach
    void setUp() {
        openFeatureAPI = mock(OpenFeatureAPI.class);
        flagConfigService = mock(FlagConfigService.class);
        valueConverter = mock(ValueConverter.class);
        factory = new ExtendedOpenFeatureClientFactoryImpl(openFeatureAPI, flagConfigService, valueConverter);
    }

    @Test
    void getClient_returnsExtendedClient() {
        Client delegate = mock(Client.class);
        when(openFeatureAPI.getClient()).thenReturn(delegate);

        ExtendedOpenFeatureClient client = factory.getClient();

        assertThat(client).isInstanceOf(ExtendedOpenFeatureClientImpl.class);
        verify(openFeatureAPI).getClient();
    }

    @Test
    void getClient_withDomain_returnsExtendedClient() {
        Client delegate = mock(Client.class);
        when(openFeatureAPI.getClient("my-domain")).thenReturn(delegate);

        ExtendedOpenFeatureClient client = factory.getClient("my-domain");

        assertThat(client).isInstanceOf(ExtendedOpenFeatureClientImpl.class);
        verify(openFeatureAPI).getClient("my-domain");
    }

    @Test
    void getClient_withDomainAndVersion_returnsExtendedClient() {
        Client delegate = mock(Client.class);
        when(openFeatureAPI.getClient("my-domain", "1.0.0")).thenReturn(delegate);

        ExtendedOpenFeatureClient client = factory.getClient("my-domain", "1.0.0");

        assertThat(client).isInstanceOf(ExtendedOpenFeatureClientImpl.class);
        verify(openFeatureAPI).getClient("my-domain", "1.0.0");
    }

    @Test
    void getClient_createsNewInstanceEachTime() {
        Client delegate = mock(Client.class);
        when(openFeatureAPI.getClient()).thenReturn(delegate);

        ExtendedOpenFeatureClient client1 = factory.getClient();
        ExtendedOpenFeatureClient client2 = factory.getClient();

        assertThat(client1).isNotSameAs(client2);
    }
}
