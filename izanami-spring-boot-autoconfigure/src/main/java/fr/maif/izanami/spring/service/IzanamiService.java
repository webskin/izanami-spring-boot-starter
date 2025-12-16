package fr.maif.izanami.spring.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfeature.sdk.FlagValueType;
import fr.maif.FeatureCacheConfiguration;
import fr.maif.FeatureClientErrorStrategy;
import fr.maif.IzanamiClient;
import fr.maif.errors.IzanamiError;
import fr.maif.features.results.IzanamiResult;
import fr.maif.features.values.BooleanCastStrategy;
import fr.maif.izanami.spring.autoconfigure.IzanamiProperties;
import fr.maif.izanami.spring.openfeature.ErrorStrategy;
import fr.maif.izanami.spring.openfeature.FlagConfig;
import fr.maif.izanami.spring.openfeature.FlagMetadataKeys;
import fr.maif.izanami.spring.openfeature.FlagValueSource;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static java.util.Collections.unmodifiableMap;

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
    private final ObjectMapper objectMapper;

    private final AtomicReference<IzanamiClient> clientRef = new AtomicReference<>();
    private final AtomicReference<CompletableFuture<Void>> loadedRef = new AtomicReference<>(CompletableFuture.completedFuture(null));
    private volatile boolean connected;

    /**
     * Create a new service.
     *
     * @param properties        Izanami properties
     * @param flagConfigService flag configuration service providing IDs to preload
     * @param objectMapper      Jackson ObjectMapper for JSON serialization
     */
    public IzanamiService(IzanamiProperties properties, FlagConfigService flagConfigService, ObjectMapper objectMapper) {
        this.properties = properties;
        this.flagConfigService = flagConfigService;
        this.objectMapper = objectMapper;
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
            .map(FlagConfig::key)
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
     * Explicit escape hatch to access the underlying Izanami client.
     *
     * @return the configured {@link IzanamiClient} when available
     */
    public Optional<IzanamiClient> unwrapClient() {
        return Optional.ofNullable(clientRef.get());
    }

    /**
     * @return a future which completes when the initial preload has finished (successfully or not).
     */
    public CompletableFuture<Void> whenLoaded() {
        return loadedRef.get();
    }

    /**
     * Check if the Izanami client is connected and has successfully preloaded flags.
     *
     * @return {@code true} if connected and preloaded, {@code false} otherwise
     */
    public boolean isConnected() {
        return connected;
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
        return new FeatureRequestBuilder(this, flagConfig);
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
        return new FeatureRequestBuilder(this, flagConfig);
    }

    // =====================================================================
    // Feature Request Builder
    // =====================================================================

    /**
     * Fluent builder for configuring and executing feature flag evaluations.
     * <p>
     * Use {@link #forFlagKey(String)} or {@link #forFlagName(String)} to obtain an instance.
     */
    public static final class FeatureRequestBuilder {
        private final IzanamiService service;
        private final FlagConfig flagConfig;
        private String user;
        private String context;

        private FeatureRequestBuilder(IzanamiService service, FlagConfig flagConfig) {
            this.service = service;
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
            return service.evaluateBoolean(buildRequest());
        }

        /**
         * Evaluate the feature flag as a string.
         *
         * @return a future containing the string value
         * @throws IzanamiClientNotAvailableException if the Izanami client is not available
         */
        public CompletableFuture<String> stringValue() {
            return service.evaluateString(buildRequest());
        }

        /**
         * Evaluate the feature flag as a number.
         *
         * @return a future containing the number value as BigDecimal
         * @throws IzanamiClientNotAvailableException if the Izanami client is not available
         */
        public CompletableFuture<BigDecimal> numberValue() {
            return service.evaluateNumber(buildRequest());
        }

        /**
         * Retrieve the raw feature result asynchronously.
         *
         * @return a future containing an optional with the result if available
         * @throws IzanamiClientNotAvailableException if the Izanami client is not available
         */
        public CompletableFuture<Optional<IzanamiResult.Result>> featureResult() {
            return service.evaluateFeatureResult(flagConfig, user, context);
        }

        /**
         * Retrieve the raw feature result with metadata asynchronously.
         *
         * @return a future containing an optional with the result and metadata if available
         * @throws IzanamiClientNotAvailableException if the Izanami client is not available
         */
        public CompletableFuture<Optional<ResultWithMetadata>> featureResultWithMetadata() {
            Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put(FlagMetadataKeys.FLAG_CONFIG_KEY, flagConfig.key());
            metadata.put(FlagMetadataKeys.FLAG_CONFIG_NAME, flagConfig.name());
            metadata.put(FlagMetadataKeys.FLAG_CONFIG_DESCRIPTION, flagConfig.description());
            metadata.put(FlagMetadataKeys.FLAG_CONFIG_VALUE_TYPE, flagConfig.valueType().name());
            metadata.put(FlagMetadataKeys.FLAG_CONFIG_DEFAULT_VALUE, stringifyDefaultValue(service.objectMapper, flagConfig));
            metadata.put(FlagMetadataKeys.FLAG_CONFIG_ERROR_STRATEGY, flagConfig.rawErrorStrategy().name());
            try {
                return service.evaluateFeatureResult(flagConfig, user, context).thenApply(mayBeResult -> mayBeResult.map(r -> {
                    FlagValueSource valueSource = (r instanceof IzanamiResult.Success)
                        ? FlagValueSource.IZANAMI
                        : FlagValueSource.IZANAMI_ERROR_STRATEGY;
                    metadata.put(FlagMetadataKeys.FLAG_VALUE_SOURCE, valueSource.name());
                    return new ResultWithMetadata(r, unmodifiableMap(metadata));
                }));
            } catch (Exception e) {
                if (flagConfig.rawErrorStrategy() == ErrorStrategy.FAIL) {
                    return CompletableFuture.failedFuture(e);
                }
                metadata.put(FlagMetadataKeys.FLAG_VALUE_SOURCE, FlagValueSource.APPLICATION_ERROR_STRATEGY.name());
                return CompletableFuture.completedFuture(Optional.of(new ResultWithMetadata(
                    new IzanamiResult.Error(flagConfig.errorStrategy(), new IzanamiError(e.getMessage())),
                    unmodifiableMap(metadata))
                ));
            }
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

    private CompletableFuture<Optional<IzanamiResult.Result>> evaluateFeatureResult(
            FlagConfig flagConfig,
            @Nullable String user,
            @Nullable String context
    ) {
        IzanamiClient client = requireClient();
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
            return client
                .featureValues(featureRequest)
                .thenApply(r -> {
                    if (r.results != null && !r.results.isEmpty()) {
                        return Optional.of(r.results.values().iterator().next());
                    } else {
                        return Optional.empty();
                    }
                });
        } catch (Exception e) {
            log.debug("Izanami evaluation failed; falling back to configured defaults: {}", e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }

    private IzanamiClient requireClient() {
        IzanamiClient client = clientRef.get();
        if (client == null) {
            throw new IzanamiClientNotAvailableException();
        }
        return client;
    }

    private CompletableFuture<Boolean> evaluateBoolean(SingleFeatureRequest request) {
        return requireClient().booleanValue(request);
    }

    private CompletableFuture<String> evaluateString(SingleFeatureRequest request) {
        return requireClient().stringValue(request);
    }

    private CompletableFuture<BigDecimal> evaluateNumber(SingleFeatureRequest request) {
        return requireClient().numberValue(request);
    }

    public static String stringifyDefaultValue(ObjectMapper objectMapper, FlagConfig config) {
        Object defaultValue = config.defaultValue();
        if (defaultValue == null) {
            return null;
        }
        if (config.valueType() == FlagValueType.OBJECT) {
            try {
                return objectMapper.writeValueAsString(defaultValue);
            } catch (JsonProcessingException e) {
                return defaultValue.toString();
            }
        }
        return defaultValue.toString();
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
