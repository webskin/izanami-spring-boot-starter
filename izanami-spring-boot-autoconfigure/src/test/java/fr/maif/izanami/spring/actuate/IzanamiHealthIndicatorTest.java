package fr.maif.izanami.spring.actuate;

import fr.maif.izanami.spring.service.IzanamiService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IzanamiHealthIndicatorTest {

    @Test
    void reportsUpWhenConnectedAndLoaded() {
        IzanamiService service = mock(IzanamiService.class);
        when(service.whenLoaded()).thenReturn(CompletableFuture.completedFuture(null));
        when(service.isConnected()).thenReturn(true);

        IzanamiHealthIndicator indicator = new IzanamiHealthIndicator(service);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("status", "Connected and flags preloaded");
    }

    @Test
    void reportsDownWhenLoadingInProgress() {
        IzanamiService service = mock(IzanamiService.class);
        CompletableFuture<Void> incompleteFuture = new CompletableFuture<>();
        when(service.whenLoaded()).thenReturn(incompleteFuture);

        IzanamiHealthIndicator indicator = new IzanamiHealthIndicator(service);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("reason", "Flag preloading in progress");
    }

    @Test
    void reportsOutOfServiceWhenLoadedButNotConnected() {
        IzanamiService service = mock(IzanamiService.class);
        when(service.whenLoaded()).thenReturn(CompletableFuture.completedFuture(null));
        when(service.isConnected()).thenReturn(false);

        IzanamiHealthIndicator indicator = new IzanamiHealthIndicator(service);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
        assertThat(health.getDetails()).containsEntry("reason", "Failed to connect or preload flags");
    }
}
