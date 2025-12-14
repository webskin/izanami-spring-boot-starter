package fr.maif.izanami.spring.service;

import fr.maif.FeatureCacheConfiguration;
import fr.maif.FeatureClientErrorStrategy;
import fr.maif.IzanamiClient;
import fr.maif.features.results.IzanamiResult;
import fr.maif.izanami.spring.autoconfigure.IzanamiProperties;
import fr.maif.requests.FeatureRequest;
import fr.maif.requests.IzanamiConnectionInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thin lifecycle wrapper around {@link IzanamiClient}.
 * <p>
 * This service is deliberately resilient:
 * <ul>
 *   <li>It never throws from Spring lifecycle callbacks.</li>
 *   <li>If the client cannot be created (missing credentials, invalid URL, etc.), it stays inactive.</li>
 *   <li>If Izanami is unreachable, evaluations fall back via configured error strategies.</li>
 * </ul>
 * <p>
 * The underlying {@link IzanamiClient} is intentionally not exposed as a Spring bean; for advanced use-cases,
 * {@link #unwrapClient()} provides an explicit escape hatch.
 */
public final class IzanamiService implements InitializingBean, DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(IzanamiService.class);

    private final IzanamiProperties properties;
    private final Set<String> featureFlagIdsToPreload;

    private final AtomicReference<IzanamiClient> clientRef = new AtomicReference<>();
    private final AtomicReference<CompletableFuture<Void>> loadedRef = new AtomicReference<>(CompletableFuture.completedFuture(null));
    private volatile boolean connected;

    /**
     * Create a new service.
     *
     * @param properties               Izanami properties
     * @param featureFlagIdsToPreload  ids to preload in the Izanami client cache
     */
    public IzanamiService(IzanamiProperties properties, Set<String> featureFlagIdsToPreload) {
        this.properties = properties;
        this.featureFlagIdsToPreload = Set.copyOf(featureFlagIdsToPreload);
    }

    @Override
    public void afterPropertiesSet() {
        try {
            initializeClient();
        } catch (Exception e) {
            log.warn("Failed to initialize Izanami client; evaluations will fall back to configured defaults: {}", e.getMessage(), e);
        }
    }

    private void initializeClient() {
        String url = properties.url();
        if (url == null || url.isBlank()) {
            log.info("Izanami URL is not configured (izanami.base-url / izanami.api-path); Izanami client will remain inactive");
            return;
        }
        if (isBlank(properties.clientId()) || isBlank(properties.clientSecret())) {
            log.info("Izanami credentials are not configured (izanami.client-id / izanami.client-secret); Izanami client will remain inactive");
            return;
        }

        log.info("Initializing Izanami client for {}", url);

        IzanamiConnectionInformation connectionInformation = IzanamiConnectionInformation.connectionInformation()
            .withUrl(url)
            .withClientId(properties.clientId())
            .withClientSecret(properties.clientSecret());

        IzanamiProperties.Cache cacheProperties = properties.cache();
        FeatureCacheConfiguration cacheConfiguration = FeatureCacheConfiguration.newBuilder()
            .enabled(Boolean.TRUE.equals(cacheProperties.enabled()))
            .withRefreshInterval(cacheProperties.refreshInterval())
            .shouldUseServerSentEvent(Boolean.TRUE.equals(cacheProperties.sse().enabled()))
            .withServerSentEventKeepAliveInterval(cacheProperties.sse().keepAliveInterval())
            .build();

        FeatureClientErrorStrategy.DefaultValueStrategy defaultErrorStrategy =
            FeatureClientErrorStrategy.defaultValueStrategy(false, "", BigDecimal.ZERO);

        IzanamiClient client = IzanamiClient.newBuilder(connectionInformation)
            .withCacheConfiguration(cacheConfiguration)
            .withErrorStrategy(defaultErrorStrategy)
            .withPreloadedFeatures(featureFlagIdsToPreload)
            .build();

        clientRef.set(client);

        CompletableFuture<Void> loadedFuture = client.isLoaded()
            .whenComplete((ignored, error) -> {
                if (error != null) {
                    connected = false;
                    log.warn("Izanami client failed to preload flags; evaluations will rely on fallback strategies: {}", error.getMessage());
                } else {
                    connected = true;
                    log.info("Izanami client preloaded {} flag(s)", featureFlagIdsToPreload.size());
                }
            });
        loadedRef.set(loadedFuture);
    }

    /**
     * @return {@code true} if the client is configured and preloading completed successfully.
     */
    public boolean isConnected() {
        return connected && clientRef.get() != null;
    }

    /**
     * Explicit escape hatch to access the underlying Izanami client.
     *
     * @return the configured {@link IzanamiClient} when available
     */
    public Optional<IzanamiClient> unwrapClient() {
        return Optional.ofNullable(clientRef.get());
    }

    /**
     * Retrieve a single feature result (success or error) for a pre-built request.
     * <p>
     * This method never throws; in case of any error, it returns {@link Optional#empty()}.
     *
     * @param featureRequest request containing exactly one feature id
     * @return optional containing the first result if available
     */
    public Optional<IzanamiResult.Result> getFeatureResult(FeatureRequest featureRequest) {
        return getFeatureResult(featureRequest, false);
    }

    /**
     * Retrieve a single feature result (success or error) for a pre-built request.
     * <p>
     * When {@code propagateErrors} is {@code false}, this method never throws; in case of any error,
     * it returns {@link Optional#empty()}. When {@code propagateErrors} is {@code true}, exceptions
     * from the Izanami client (such as those thrown by the fail error strategy) are propagated to the caller.
     *
     * @param featureRequest  request containing exactly one feature id
     * @param propagateErrors if {@code true}, exceptions from Izanami are propagated instead of being caught
     * @return optional containing the first result if available
     */
    public Optional<IzanamiResult.Result> getFeatureResult(FeatureRequest featureRequest, boolean propagateErrors) {
        IzanamiClient client = clientRef.get();
        if (client == null) {
            return Optional.empty();
        }
        try {
            IzanamiResult result = client.featureValues(featureRequest).join();
            if (result == null || result.results == null || result.results.isEmpty()) {
                return Optional.empty();
            }
            return Optional.ofNullable(result.results.values().iterator().next());
        } catch (Exception e) {
            if (propagateErrors) {
                if (e instanceof RuntimeException re) {
                    throw re;
                }
                throw new RuntimeException(e);
            }
            log.debug("Izanami evaluation failed; falling back to configured defaults: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * @return a future which completes when the initial preload has finished (successfully or not).
     */
    public CompletableFuture<Void> whenLoaded() {
        return loadedRef.get();
    }

    @Override
    public void destroy() {
        IzanamiClient client = clientRef.getAndSet(null);
        if (client == null) {
            return;
        }
        try {
            client.close().join();
        } catch (Exception e) {
            log.debug("Error while closing Izanami client: {}", e.getMessage());
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
