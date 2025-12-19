package fr.maif.izanami.spring.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfeature.sdk.FlagValueType;
import fr.maif.FeatureCacheConfiguration;
import fr.maif.FeatureClientErrorStrategy;
import fr.maif.IzanamiClient;
import fr.maif.izanami.spring.autoconfigure.IzanamiProperties;
import fr.maif.izanami.spring.openfeature.FlagConfig;
import fr.maif.izanami.spring.openfeature.FlagMetadataKeys;
import fr.maif.izanami.spring.openfeature.FlagValueSource;
import fr.maif.izanami.spring.openfeature.api.FlagConfigService;
import fr.maif.izanami.spring.service.api.BatchFeatureRequestBuilder;
import fr.maif.izanami.spring.service.api.BatchResult;
import fr.maif.izanami.spring.service.api.ResultValueWithDetails;
import fr.maif.requests.IzanamiConnectionInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.util.*;
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

        // Default error strategy for FLAG_NOT_FOUND (when flag key/name is not in configuration)
        // Returns: false for boolean, "" for string, BigDecimal.ZERO for number
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
     * If the flag key is not found in configuration, the returned builder will
     * produce default values ({@code false}, {@code ""}, {@code BigDecimal.ZERO})
     * with {@code FLAG_NOT_FOUND} evaluation reason.
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
     */
    @Override
    public FeatureRequestBuilder forFlagKey(String flagKey) {
        log.debug("Building feature request for flag key: {}", flagKey);
        Optional<FlagConfig> flagConfig = flagConfigService.getFlagConfigByKey(flagKey);
        if (flagConfig.isPresent()) {
            return new FeatureRequestBuilder(this, flagConfig.get());
        }
        log.debug("Flag key '{}' not found in configuration", flagKey);
        return new FeatureRequestBuilder(this, flagKey);
    }

    /**
     * Start building a feature request for the given flag name.
     * <p>
     * If the flag name is not found in configuration, the returned builder will
     * produce default values ({@code false}, {@code ""}, {@code BigDecimal.ZERO})
     * with {@code FLAG_NOT_FOUND} evaluation reason.
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
     */
    @Override
    public FeatureRequestBuilder forFlagName(String flagName) {
        log.debug("Building feature request for flag name: {}", flagName);
        Optional<FlagConfig> flagConfig = flagConfigService.getFlagConfigByName(flagName);
        if (flagConfig.isPresent()) {
            return new FeatureRequestBuilder(this, flagConfig.get());
        }
        log.debug("Flag name '{}' not found in configuration", flagName);
        return new FeatureRequestBuilder(this, flagName);
    }

    // =====================================================================
    // Batch Fluent API entry points
    // =====================================================================

    /**
     * Start building a batch feature request for multiple flag keys (UUIDs).
     * <p>
     * If any flag key is not found in configuration, it will be included in the result
     * with default values ({@code false}, {@code ""}, {@code BigDecimal.ZERO})
     * and {@code FLAG_NOT_FOUND} evaluation reason.
     *
     * @param flagKeys the flag keys (UUIDs) as configured in Izanami
     * @return a builder for configuring and executing the batch feature request
     */
    @Override
    public BatchFeatureRequestBuilder forFlagKeys(String... flagKeys) {
        log.debug("Building batch feature request for {} keys", flagKeys.length);

        Map<String, FlagConfig> configs = new LinkedHashMap<>();
        Map<String, String> identifierToKey = new LinkedHashMap<>();
        Set<String> notFoundIdentifiers = new LinkedHashSet<>();

        for (String key : flagKeys) {
            Optional<FlagConfig> config = flagConfigService.getFlagConfigByKey(key);
            if (config.isPresent()) {
                configs.put(key, config.get());
                identifierToKey.put(key, key);  // Keys map to themselves
            } else {
                log.debug("Flag key '{}' not found in configuration", key);
                notFoundIdentifiers.add(key);
            }
        }

        return new BatchFeatureRequestBuilderImpl(this, configs, identifierToKey, notFoundIdentifiers);
    }

    /**
     * Start building a batch feature request for multiple flag names.
     * <p>
     * If any flag name is not found in configuration, it will be included in the result
     * with default values ({@code false}, {@code ""}, {@code BigDecimal.ZERO})
     * and {@code FLAG_NOT_FOUND} evaluation reason.
     *
     * @param flagNames the flag names as configured in openfeature.flags
     * @return a builder for configuring and executing the batch feature request
     */
    @Override
    public BatchFeatureRequestBuilder forFlagNames(String... flagNames) {
        log.debug("Building batch feature request for {} names", flagNames.length);

        Map<String, FlagConfig> configs = new LinkedHashMap<>();
        Map<String, String> identifierToKey = new LinkedHashMap<>();
        Set<String> notFoundIdentifiers = new LinkedHashSet<>();

        for (String name : flagNames) {
            Optional<FlagConfig> config = flagConfigService.getFlagConfigByName(name);
            if (config.isPresent()) {
                configs.put(config.get().key(), config.get());
                identifierToKey.put(name, config.get().key());  // Names map to keys
            } else {
                log.debug("Flag name '{}' not found in configuration", name);
                notFoundIdentifiers.add(name);
            }
        }

        return new BatchFeatureRequestBuilderImpl(this, configs, identifierToKey, notFoundIdentifiers);
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
        private final Optional<FlagConfig> flagConfig;
        private final String flagIdentifier;  // Original identifier for metadata when flag not found
        private String user;
        private String context;

        private FeatureRequestBuilder(IzanamiServiceImpl service, FlagConfig flagConfig) {
            this.service = service;
            this.flagConfig = Optional.of(flagConfig);
            this.flagIdentifier = flagConfig.key();
        }

        /**
         * Creates a builder for a flag that was not found in configuration.
         * Evaluations will return default values with FLAG_NOT_FOUND reason.
         */
        private FeatureRequestBuilder(IzanamiServiceImpl service, String flagIdentifier) {
            this.service = service;
            this.flagConfig = Optional.empty();
            this.flagIdentifier = flagIdentifier;
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
            return booleanValueDetails().thenApply(ResultValueWithDetails::value);
        }

        @Override
        public CompletableFuture<String> stringValue() {
            return stringValueDetails().thenApply(ResultValueWithDetails::value);
        }

        @Override
        public CompletableFuture<BigDecimal> numberValue() {
            return numberValueDetails().thenApply(ResultValueWithDetails::value);
        }

        @Override
        public CompletableFuture<ResultValueWithDetails<Boolean>> booleanValueDetails() {
            return flagConfig
                .map(fc -> createEvaluator(fc).booleanValueDetails())
                .orElseGet(() -> CompletableFuture.completedFuture(flagNotFoundResult(false)));
        }

        @Override
        public CompletableFuture<ResultValueWithDetails<String>> stringValueDetails() {
            return flagConfig
                .map(fc -> createEvaluator(fc).stringValueDetails())
                .orElseGet(() -> CompletableFuture.completedFuture(flagNotFoundResult("")));
        }

        @Override
        public CompletableFuture<ResultValueWithDetails<BigDecimal>> numberValueDetails() {
            return flagConfig
                .map(fc -> createEvaluator(fc).numberValueDetails())
                .orElseGet(() -> CompletableFuture.completedFuture(flagNotFoundResult(BigDecimal.ZERO)));
        }

        private <T> ResultValueWithDetails<T> flagNotFoundResult(T defaultValue) {
            log.warn("Flag '{}' not found in configuration, returning default value: {}", flagIdentifier, defaultValue);
            Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put(FlagMetadataKeys.FLAG_CONFIG_KEY, flagIdentifier);
            metadata.put(FlagMetadataKeys.FLAG_VALUE_SOURCE, FlagValueSource.APPLICATION_ERROR_STRATEGY.name());
            metadata.put(FlagMetadataKeys.FLAG_EVALUATION_REASON, "FLAG_NOT_FOUND");
            return new ResultValueWithDetails<>(defaultValue, metadata);
        }

        private IzanamiFeatureEvaluator createEvaluator(FlagConfig fc) {
            return new IzanamiFeatureEvaluator(
                service.clientRef.get(),  // May be null - evaluator handles gracefully
                service.objectMapper,
                fc,
                user,
                context
            );
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
        private final Set<String> notFoundIdentifiers;
        private String user;
        private String context;
        private boolean ignoreCache;

        private BatchFeatureRequestBuilderImpl(
                IzanamiServiceImpl service,
                Map<String, FlagConfig> flagConfigs,
                Map<String, String> identifierToKey,
                Set<String> notFoundIdentifiers) {
            this.service = service;
            this.flagConfigs = flagConfigs;
            this.identifierToKey = identifierToKey;
            this.notFoundIdentifiers = notFoundIdentifiers;
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
                notFoundIdentifiers,
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
