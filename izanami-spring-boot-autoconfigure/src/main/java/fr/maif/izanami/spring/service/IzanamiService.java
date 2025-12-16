package fr.maif.izanami.spring.service;

import fr.maif.FeatureCacheConfiguration;
import fr.maif.FeatureClientErrorStrategy;
import fr.maif.IzanamiClient;
import fr.maif.features.results.IzanamiResult;
import fr.maif.features.values.BooleanCastStrategy;
import fr.maif.izanami.spring.autoconfigure.IzanamiProperties;
import fr.maif.izanami.spring.openfeature.FlagConfig;
import fr.maif.izanami.spring.openfeature.api.FlagConfigService;
import fr.maif.requests.FeatureRequest;
import fr.maif.requests.IzanamiConnectionInformation;
import fr.maif.requests.SingleFeatureRequest;
import org.springframework.lang.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

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
    private final FlagConfigService flagConfigService;

    private final AtomicReference<IzanamiClient> clientRef = new AtomicReference<>();
    private final AtomicReference<CompletableFuture<Void>> loadedRef = new AtomicReference<>(CompletableFuture.completedFuture(null));
    private volatile boolean connected;

    /**
     * Create a new service.
     *
     * @param properties        Izanami properties
     * @param flagConfigService flag configuration service providing IDs to preload
     */
    public IzanamiService(IzanamiProperties properties, FlagConfigService flagConfigService) {
        this.properties = properties;
        this.flagConfigService = flagConfigService;
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

        Set<String> featureFlagIdsToPreload = flagConfigService.getAllFlagConfigs().stream()
            .map(config -> config.key())
            .collect(Collectors.toSet());

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
     *
     * @param featureRequest request containing exactly one feature key
     * @return optional containing the first result if available
     * @throws IzanamiClientNotAvailableException if the Izanami client is not available
     */
    public Optional<IzanamiResult.Result> getFeatureResult(FeatureRequest featureRequest) {
        IzanamiClient client = clientRef.get();
        if (client == null) {
            throw new IzanamiClientNotAvailableException();
        }
        try {
            IzanamiResult result = client.featureValues(featureRequest).join();
            if (result == null || result.results == null || result.results.isEmpty()) {
                return Optional.empty();
            }
            return Optional.ofNullable(result.results.values().iterator().next());
        } catch (Exception e) {
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

    // =====================================================================
    // Fluent API entry points
    // =====================================================================

    /**
     * Start building a feature request for the given flag key (UUID).
     * <p>
     * Example usage:
     * <pre>{@code
     * izanamiService.forFlagKey("my-flag-uuid")
     *     .withUser("user-123")
     *     .booleanValue()
     *     .thenAccept(enabled -> ...);
     * }</pre>
     *
     * @param flagKey the flag key (UUID) as configured in Izanami
     * @return a builder for configuring and executing the feature request
     * @throws FlagNotFoundException if the flag key is not found in configuration
     */
    public FeatureRequestBuilder forFlagKey(String flagKey) {
        FlagConfig flagConfig = flagConfigService
            .getFlagConfigByKey(flagKey)
            .orElseThrow(() -> new FlagNotFoundException(flagKey, FlagNotFoundException.IdentifierType.KEY));
        return new FeatureRequestBuilder(flagConfig);
    }

    /**
     * Start building a feature request for the given flag name.
     * <p>
     * Example usage:
     * <pre>{@code
     * izanamiService.forFlagName("my-feature")
     *     .withUser("user-123")
     *     .stringValue()
     *     .thenAccept(value -> ...);
     * }</pre>
     *
     * @param flagName the flag name as configured in openfeature.flags
     * @return a builder for configuring and executing the feature request
     * @throws FlagNotFoundException if the flag name is not found in configuration
     */
    public FeatureRequestBuilder forFlagName(String flagName) {
        FlagConfig flagConfig = flagConfigService
            .getFlagConfigByName(flagName)
            .orElseThrow(() -> new FlagNotFoundException(flagName, FlagNotFoundException.IdentifierType.NAME));
        return new FeatureRequestBuilder(flagConfig);
    }

    // =====================================================================
    // Feature Request Builder
    // =====================================================================

    /**
     * Fluent builder for configuring and executing feature flag evaluations.
     * <p>
     * Use {@link #forFlagKey(String)} or {@link #forFlagName(String)} to obtain an instance.
     */
    public final class FeatureRequestBuilder {
        private final FlagConfig flagConfig;
        private String user;
        private String context;

        private FeatureRequestBuilder(FlagConfig flagConfig) {
            this.flagConfig = flagConfig;
        }

        /**
         * Set the user identifier for this evaluation.
         *
         * @param user the user identifier
         * @return this builder for chaining
         */
        public FeatureRequestBuilder withUser(@Nullable String user) {
            this.user = user;
            return this;
        }

        /**
         * Set the context for this evaluation.
         *
         * @param context the evaluation context
         * @return this builder for chaining
         */
        public FeatureRequestBuilder withContext(@Nullable String context) {
            this.context = context;
            return this;
        }

        /**
         * Evaluate the feature flag as a boolean.
         *
         * @return a future containing the boolean value
         * @throws IzanamiClientNotAvailableException if the Izanami client is not available
         */
        public CompletableFuture<Boolean> booleanValue() {
            return evaluateBoolean(buildRequest());
        }

        /**
         * Evaluate the feature flag as a string.
         *
         * @return a future containing the string value
         * @throws IzanamiClientNotAvailableException if the Izanami client is not available
         */
        public CompletableFuture<String> stringValue() {
            return evaluateString(buildRequest());
        }

        /**
         * Evaluate the feature flag as a number.
         *
         * @return a future containing the number value as BigDecimal
         * @throws IzanamiClientNotAvailableException if the Izanami client is not available
         */
        public CompletableFuture<BigDecimal> numberValue() {
            return evaluateNumber(buildRequest());
        }

        /**
         * Retrieve the raw feature result.
         * <p>
         * This method never throws; in case of any error, it returns {@link Optional#empty()}.
         *
         * @return optional containing the result if available
         */
        public Optional<IzanamiResult.Result> featureResult() {
            return evaluateFeatureResult(flagConfig, user, context);
        }

        private SingleFeatureRequest buildRequest() {
            SingleFeatureRequest request = SingleFeatureRequest.newSingleFeatureRequest(flagConfig.key())
                .withErrorStrategy(flagConfig.errorStrategy())
                .withBooleanCastStrategy(BooleanCastStrategy.LAX);
            if (user != null) {
                request = request.withUser(user);
            }
            if (context != null) {
                request = request.withContext(context);
            }
            return request;
        }
    }

    // =====================================================================
    // Internal evaluation methods
    // =====================================================================

    private Optional<IzanamiResult.Result> evaluateFeatureResult(
            FlagConfig flagConfig,
            @Nullable String user,
            @Nullable String context
    ) {
        IzanamiClient client = clientRef.get();
        if (client == null) {
            throw new IzanamiClientNotAvailableException();
        }
        try {
            FeatureRequest featureRequest = FeatureRequest.newFeatureRequest()
                .withFeature(flagConfig.key())
                .withErrorStrategy(flagConfig.errorStrategy())
                .withBooleanCastStrategy(BooleanCastStrategy.LAX);
            if (user != null) {
                featureRequest = featureRequest.withUser(user);
            }
            if (context != null) {
                featureRequest = featureRequest.withContext(context);
            }
            IzanamiResult result = client.featureValues(featureRequest).join();
            if (result == null || result.results == null || result.results.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(result.results.values().iterator().next());
        } catch (Exception e) {
            log.debug("Izanami evaluation failed; falling back to configured defaults: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private CompletableFuture<Boolean> evaluateBoolean(SingleFeatureRequest request) {
        IzanamiClient client = clientRef.get();
        if (client == null) {
            throw new IzanamiClientNotAvailableException();
        }
        return client.booleanValue(request);
    }

    private CompletableFuture<String> evaluateString(SingleFeatureRequest request) {
        IzanamiClient client = clientRef.get();
        if (client == null) {
            throw new IzanamiClientNotAvailableException();
        }
        return client.stringValue(request);
    }

    private CompletableFuture<BigDecimal> evaluateNumber(SingleFeatureRequest request) {
        IzanamiClient client = clientRef.get();
        if (client == null) {
            throw new IzanamiClientNotAvailableException();
        }
        return client.numberValue(request);
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
