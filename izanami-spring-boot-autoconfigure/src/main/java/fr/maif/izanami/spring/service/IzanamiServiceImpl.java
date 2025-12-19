package fr.maif.izanami.spring.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfeature.sdk.FlagValueType;
import fr.maif.FeatureCacheConfiguration;
import fr.maif.FeatureClientErrorStrategy;
import fr.maif.IzanamiClient;
import fr.maif.features.values.BooleanCastStrategy;
import fr.maif.izanami.spring.autoconfigure.IzanamiProperties;
import fr.maif.izanami.spring.openfeature.FlagConfig;
import fr.maif.izanami.spring.openfeature.api.FlagConfigService;
import fr.maif.izanami.spring.service.api.BatchFeatureRequestBuilder;
import fr.maif.izanami.spring.service.api.BatchResult;
import fr.maif.izanami.spring.service.api.FlagNotFoundException;
import fr.maif.izanami.spring.service.api.ResultValueWithDetails;
import fr.maif.requests.IzanamiConnectionInformation;
import fr.maif.requests.SingleFeatureRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
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
public final class IzanamiServiceImpl implements InitializingBean, DisposableBean, fr.maif.izanami.spring.service.api.IzanamiService {
    private static final Logger log = LoggerFactory.getLogger(IzanamiServiceImpl.class);

    private final IzanamiProperties properties;
    private final FlagConfigService flagConfigService;
    private final ObjectMapper objectMapper;
    private final IzanamiClientFactory clientFactory;

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
    public IzanamiServiceImpl(IzanamiProperties properties, FlagConfigService flagConfigService, ObjectMapper objectMapper) {
        this(properties, flagConfigService, objectMapper, IzanamiClientFactory.DEFAULT);
    }

    /**
     * Package-private constructor for testing with a custom client factory.
     */
    IzanamiServiceImpl(IzanamiProperties properties, FlagConfigService flagConfigService,
                       ObjectMapper objectMapper, IzanamiClientFactory clientFactory) {
        this.properties = properties;
        this.flagConfigService = flagConfigService;
        this.objectMapper = objectMapper;
        this.clientFactory = clientFactory;
    }

    /**
     * Factory interface for creating IzanamiClient instances.
     * Package-private for testing purposes.
     */
    @FunctionalInterface
    interface IzanamiClientFactory {
        IzanamiClient create(
            IzanamiConnectionInformation connectionInfo,
            FeatureCacheConfiguration cacheConfiguration,
            FeatureClientErrorStrategy<?> errorStrategy,
            Set<String> preloadedFeatures
        );

        IzanamiClientFactory DEFAULT = (conn, cache, err, features) ->
            IzanamiClient.newBuilder(conn)
                .withCacheConfiguration(cache)
                .withErrorStrategy(err)
                .withPreloadedFeatures(features)
                .build();
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
        if (isBlank(properties.getClientId()) || isBlank(properties.getClientSecret())) {
            log.info("Izanami credentials are not configured (izanami.client-id / izanami.client-secret); Izanami client will remain inactive");
            return;
        }

        log.info("Initializing Izanami client for {}", url);

        IzanamiConnectionInformation connectionInformation = IzanamiConnectionInformation.connectionInformation()
            .withUrl(url)
            .withClientId(properties.getClientId())
            .withClientSecret(properties.getClientSecret());

        IzanamiProperties.Cache cacheProperties = properties.getCache();
        FeatureCacheConfiguration cacheConfiguration = FeatureCacheConfiguration.newBuilder()
            .enabled(Boolean.TRUE.equals(cacheProperties.getEnabled()))
            .withRefreshInterval(cacheProperties.getRefreshInterval())
            .shouldUseServerSentEvent(Boolean.TRUE.equals(cacheProperties.getSse().getEnabled()))
            .withServerSentEventKeepAliveInterval(cacheProperties.getSse().getKeepAliveInterval())
            .build();

        FeatureClientErrorStrategy.DefaultValueStrategy defaultErrorStrategy =
            FeatureClientErrorStrategy.defaultValueStrategy(false, "", BigDecimal.ZERO);

        Set<String> featureFlagIdsToPreload = flagConfigService.getAllFlagConfigs().stream()
            .map(FlagConfig::key)
            .collect(Collectors.toSet());

        IzanamiClient client = clientFactory.create(
            connectionInformation,
            cacheConfiguration,
            defaultErrorStrategy,
            featureFlagIdsToPreload
        );

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
    @Override
    public Optional<IzanamiClient> unwrapClient() {
        return Optional.ofNullable(clientRef.get());
    }

    /**
     * @return a future which completes when the initial preload has finished (successfully or not).
     */
    @Override
    public CompletableFuture<Void> whenLoaded() {
        return loadedRef.get();
    }

    /**
     * Check if the Izanami client is connected and has successfully preloaded flags.
     *
     * @return {@code true} if connected and preloaded, {@code false} otherwise
     */
    @Override
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
    @Override
    public FeatureRequestBuilder forFlagKey(String flagKey) {
        log.debug("Building feature request for flag key: {}", flagKey);
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
    @Override
    public FeatureRequestBuilder forFlagName(String flagName) {
        log.debug("Building feature request for flag name: {}", flagName);
        FlagConfig flagConfig = flagConfigService
            .getFlagConfigByName(flagName)
            .orElseThrow(() -> new FlagNotFoundException(flagName, FlagNotFoundException.IdentifierType.NAME));
        return new FeatureRequestBuilder(this, flagConfig);
    }

    // =====================================================================
    // Batch Fluent API entry points
    // =====================================================================

    /**
     * Start building a batch feature request for multiple flag keys (UUIDs).
     *
     * @param flagKeys the flag keys (UUIDs) as configured in Izanami
     * @return a builder for configuring and executing the batch feature request
     * @throws FlagNotFoundException if any flag key is not found in configuration
     */
    @Override
    public BatchFeatureRequestBuilder forFlagKeys(String... flagKeys) {
        log.debug("Building batch feature request for {} keys", flagKeys.length);

        Map<String, FlagConfig> configs = new LinkedHashMap<>();
        Map<String, String> identifierToKey = new LinkedHashMap<>();

        for (String key : flagKeys) {
            FlagConfig config = flagConfigService.getFlagConfigByKey(key)
                .orElseThrow(() -> new FlagNotFoundException(key, FlagNotFoundException.IdentifierType.KEY));
            configs.put(key, config);
            identifierToKey.put(key, key);  // Keys map to themselves
        }

        return new BatchFeatureRequestBuilderImpl(this, configs, identifierToKey);
    }

    /**
     * Start building a batch feature request for multiple flag names.
     *
     * @param flagNames the flag names as configured in openfeature.flags
     * @return a builder for configuring and executing the batch feature request
     * @throws FlagNotFoundException if any flag name is not found in configuration
     */
    @Override
    public BatchFeatureRequestBuilder forFlagNames(String... flagNames) {
        log.debug("Building batch feature request for {} names", flagNames.length);

        Map<String, FlagConfig> configs = new LinkedHashMap<>();
        Map<String, String> identifierToKey = new LinkedHashMap<>();

        for (String name : flagNames) {
            FlagConfig config = flagConfigService.getFlagConfigByName(name)
                .orElseThrow(() -> new FlagNotFoundException(name, FlagNotFoundException.IdentifierType.NAME));
            configs.put(config.key(), config);
            identifierToKey.put(name, config.key());  // Names map to keys
        }

        return new BatchFeatureRequestBuilderImpl(this, configs, identifierToKey);
    }

    // =====================================================================
    // Feature Request Builder
    // =====================================================================

    /**
     * Fluent builder for configuring and executing feature flag evaluations.
     * <p>
     * Use {@link #forFlagKey(String)} or {@link #forFlagName(String)} to obtain an instance.
     */
    public static final class FeatureRequestBuilder implements fr.maif.izanami.spring.service.api.FeatureRequestBuilder {
        private final IzanamiServiceImpl service;
        private final FlagConfig flagConfig;
        private String user;
        private String context;

        private FeatureRequestBuilder(IzanamiServiceImpl service, FlagConfig flagConfig) {
            this.service = service;
            this.flagConfig = flagConfig;
        }

        /**
         * Set the user identifier for this evaluation.
         *
         * @param user the user identifier
         * @return this builder for chaining
         */
        @Override
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
        @Override
        public FeatureRequestBuilder withContext(@Nullable String context) {
            this.context = context;
            return this;
        }

        @Override
        public CompletableFuture<Boolean> booleanValue() {
            return createEvaluator().booleanValue();
        }

        @Override
        public CompletableFuture<String> stringValue() {
            return createEvaluator().stringValue();
        }

        @Override
        public CompletableFuture<BigDecimal> numberValue() {
            return createEvaluator().numberValue();
        }

        @Override
        public CompletableFuture<ResultValueWithDetails<Boolean>> booleanValueDetails() {
            return createEvaluator().booleanValueDetails();
        }

        @Override
        public CompletableFuture<ResultValueWithDetails<String>> stringValueDetails() {
            return createEvaluator().stringValueDetails();
        }

        @Override
        public CompletableFuture<ResultValueWithDetails<BigDecimal>> numberValueDetails() {
            return createEvaluator().numberValueDetails();
        }

        private IzanamiFeatureEvaluator createEvaluator() {
            return new IzanamiFeatureEvaluator(
                service.clientRef.get(),  // May be null - evaluator handles gracefully
                service.objectMapper,
                flagConfig,
                buildRequest(),
                user,
                context
            );
        }

        private SingleFeatureRequest buildRequest() {
            SingleFeatureRequest request = SingleFeatureRequest.newSingleFeatureRequest(flagConfig.key())
                .withErrorStrategy(flagConfig.clientErrorStrategy())
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
    // Batch Feature Request Builder
    // =====================================================================

    /**
     * Fluent builder for configuring and executing batch feature flag evaluations.
     * <p>
     * Use {@link #forFlagKeys(String...)} or {@link #forFlagNames(String...)} to obtain an instance.
     */
    public static final class BatchFeatureRequestBuilderImpl implements BatchFeatureRequestBuilder {
        private final IzanamiServiceImpl service;
        private final Map<String, FlagConfig> flagConfigs;
        private final Map<String, String> identifierToKey;
        private String user;
        private String context;
        private boolean ignoreCache;

        private BatchFeatureRequestBuilderImpl(
                IzanamiServiceImpl service,
                Map<String, FlagConfig> flagConfigs,
                Map<String, String> identifierToKey) {
            this.service = service;
            this.flagConfigs = flagConfigs;
            this.identifierToKey = identifierToKey;
        }

        @Override
        public BatchFeatureRequestBuilder withUser(@Nullable String user) {
            this.user = user;
            return this;
        }

        @Override
        public BatchFeatureRequestBuilder withContext(@Nullable String context) {
            this.context = context;
            return this;
        }

        @Override
        public BatchFeatureRequestBuilder ignoreCache(boolean ignoreCache) {
            this.ignoreCache = ignoreCache;
            return this;
        }

        @Override
        public CompletableFuture<BatchResult> values() {
            IzanamiBatchFeatureEvaluator evaluator = new IzanamiBatchFeatureEvaluator(
                service.clientRef.get(),  // May be null - evaluator handles gracefully
                service.objectMapper,
                flagConfigs,
                identifierToKey,
                user,
                context,
                ignoreCache
            );
            return evaluator.evaluate().thenApply(r -> r);
        }
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
