package fr.maif.izanami.spring.service;

import fr.maif.izanami.spring.autoconfigure.IzanamiProperties;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PropertyBasedRootContextProviderTest {

    @Test
    void returnsConfiguredRootContext() {
        IzanamiProperties props = new IzanamiProperties();
        props.setRootContext("BUILD");

        PropertyBasedRootContextProvider provider = new PropertyBasedRootContextProvider(props);

        assertThat(provider.root()).isPresent().contains("BUILD");
    }

    @Test
    void returnsEmptyWhenRootContextNull() {
        IzanamiProperties props = new IzanamiProperties();
        props.setRootContext(null);

        PropertyBasedRootContextProvider provider = new PropertyBasedRootContextProvider(props);

        assertThat(provider.root()).isEmpty();
    }

    @Test
    void returnsEmptyWhenRootContextBlank() {
        IzanamiProperties props = new IzanamiProperties();
        props.setRootContext("   ");

        PropertyBasedRootContextProvider provider = new PropertyBasedRootContextProvider(props);

        assertThat(provider.root()).isEmpty();
    }

    @Test
    void returnsEmptyWhenRootContextEmpty() {
        IzanamiProperties props = new IzanamiProperties();
        props.setRootContext("");

        PropertyBasedRootContextProvider provider = new PropertyBasedRootContextProvider(props);

        assertThat(provider.root()).isEmpty();
    }

    @Test
    void preservesRootContextWithSlashes() {
        IzanamiProperties props = new IzanamiProperties();
        props.setRootContext("BUILD/sub");

        PropertyBasedRootContextProvider provider = new PropertyBasedRootContextProvider(props);

        assertThat(provider.root()).isPresent().contains("BUILD/sub");
    }

    @Test
    void defaultPropertiesHaveNoRootContext() {
        IzanamiProperties props = new IzanamiProperties();

        PropertyBasedRootContextProvider provider = new PropertyBasedRootContextProvider(props);

        assertThat(provider.root()).isEmpty();
    }
}
