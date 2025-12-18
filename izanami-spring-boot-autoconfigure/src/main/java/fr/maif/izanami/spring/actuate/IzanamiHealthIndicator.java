package fr.maif.izanami.spring.actuate;

import fr.maif.izanami.spring.service.api.IzanamiService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * Health indicator for Izanami feature flag service.
 * <p>
 * Reports:
 * <ul>
 *   <li>{@code UP} - when the Izanami client is connected and flags are preloaded</li>
 *   <li>{@code DOWN} - when flag preloading is still in progress</li>
 *   <li>{@code OUT_OF_SERVICE} - when preloading completed but failed to connect</li>
 * </ul>
 * <p>
 * To include this indicator in the readiness probe, add to your application configuration:
 * <pre>{@code
 * management:
 *   endpoint:
 *     health:
 *       group:
 *         readiness:
 *           include: readinessState,izanami
 * }</pre>
 *
 * @see IzanamiService#whenLoaded()
 * @see IzanamiService#isConnected()
 */
public class IzanamiHealthIndicator implements HealthIndicator {

    private final IzanamiService izanamiService;

    public IzanamiHealthIndicator(IzanamiService izanamiService) {
        this.izanamiService = izanamiService;
    }

    @Override
    public Health health() {
        boolean loadingComplete = izanamiService.whenLoaded().isDone();

        if (!loadingComplete) {
            return Health.down()
                .withDetail("reason", "Flag preloading in progress")
                .build();
        }

        if (izanamiService.isConnected()) {
            return Health.up()
                .withDetail("status", "Connected and flags preloaded")
                .build();
        }

        return Health.outOfService()
            .withDetail("reason", "Failed to connect or preload flags")
            .build();
    }
}
