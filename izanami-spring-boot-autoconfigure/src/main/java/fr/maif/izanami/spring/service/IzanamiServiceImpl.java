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
import fr.maif.izanami.spring.service.api.FlagNotFoundException;
import fr.maif.izanami.spring.service.api.IzanamiClientNotAvailableException;
import fr.maif.izanami.spring.service.api.ResultValueWithDetails;
import fr.maif.requests.FeatureRequest;
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
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
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

        private record ResultWithMetadata(IzanamiResult.Result result, Map<String, String> metadata) {}

        /**
         * Holds the computed value, source, and reason for a feature evaluation.
         */
        private record EvaluationOutcome<T>(T value, FlagValueSource source, String reason) {}

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

        /**
         * Evaluate the feature flag as a boolean.
         *
         * @return a future containing the boolean value
         * @throws IzanamiClientNotAvailableException if the Izanami client is not available
         */
        @Override
        public CompletableFuture<Boolean> booleanValue() {
            log.debug("Evaluating flag {} as boolean", flagConfig.key());
            return service.evaluateBoolean(buildRequest())
                .whenComplete((result, error) -> {
                    if (error == null) {
                        log.debug("Evaluated flag {} as boolean = {}", flagConfig.key(), result);
                    }
                });
        }

        /**
         * Evaluate the feature flag as a string.
         *
         * @return a future containing the string value
         * @throws IzanamiClientNotAvailableException if the Izanami client is not available
         */
        @Override
        public CompletableFuture<String> stringValue() {
            log.debug("Evaluating flag {} as string", flagConfig.key());
            return service.evaluateString(buildRequest(), flagConfig)
                .whenComplete((result, error) -> {
                    if (error == null) {
                        log.debug("Evaluated flag {} as string = {}", flagConfig.key(), result);
                    }
                });
        }

        /**
         * Evaluate the feature flag as a number.
         *
         * @return a future containing the number value as BigDecimal
         * @throws IzanamiClientNotAvailableException if the Izanami client is not available
         */
        @Override
        public CompletableFuture<BigDecimal> numberValue() {
            log.debug("Evaluating flag {} as number", flagConfig.key());
            return service.evaluateNumber(buildRequest(), flagConfig)
                .whenComplete((result, error) -> {
                    if (error == null) {
                        log.debug("Evaluated flag {} as number = {}", flagConfig.key(), result);
                    }
                });
        }

        /**
         * Retrieve the raw feature result with metadata asynchronously.
         *
         * @return a future containing an optional with the result and metadata if available
         * @throws IzanamiClientNotAvailableException if the Izanami client is not available
         */
        public CompletableFuture<ResultWithMetadata> featureResultWithMetadata() {
            Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put(FlagMetadataKeys.FLAG_CONFIG_KEY, flagConfig.key());
            metadata.put(FlagMetadataKeys.FLAG_CONFIG_NAME, flagConfig.name());
            metadata.put(FlagMetadataKeys.FLAG_CONFIG_DESCRIPTION, flagConfig.description());
            metadata.put(FlagMetadataKeys.FLAG_CONFIG_VALUE_TYPE, flagConfig.valueType().name());
            metadata.put(FlagMetadataKeys.FLAG_CONFIG_DEFAULT_VALUE, stringifyDefaultValue(service.objectMapper, flagConfig));
            metadata.put(FlagMetadataKeys.FLAG_CONFIG_ERROR_STRATEGY, flagConfig.errorStrategy().name());
            try {
                return service.evaluateFeatureResult(flagConfig, user, context)
                    .thenApply(r -> new ResultWithMetadata(r, unmodifiableMap(metadata)));
            } catch (Exception e) {
                if (flagConfig.errorStrategy() == ErrorStrategy.FAIL) {
                    return CompletableFuture.failedFuture(e);
                }
                return CompletableFuture.completedFuture(new ResultWithMetadata(
                    new IzanamiResult.Error(flagConfig.clientErrorStrategy(), new IzanamiError(e.getMessage())),
                    unmodifiableMap(metadata)
                ));
            }
        }

        /**
         * Evaluate the feature flag as a boolean and return detailed result with metadata.
         *
         * @return a future containing the boolean value with evaluation metadata
         * @throws IzanamiClientNotAvailableException if the Izanami client is not available
         */
        @Override
        public CompletableFuture<ResultValueWithDetails<Boolean>> booleanValueDetails() {
            log.debug("Evaluating flag {} as boolean with details", flagConfig.key());
            return evaluateWithDetails(
                result -> result.booleanValue(BooleanCastStrategy.LAX),
                () -> null,  // Boolean doesn't use default for disabled - false is the disabled value
                Boolean.FALSE::equals  // false means disabled for boolean features
            );
        }

        /**
         * Evaluate the feature flag as a string and return detailed result with metadata.
         *
         * @return a future containing the string value with evaluation metadata
         * @throws IzanamiClientNotAvailableException if the Izanami client is not available
         */
        @Override
        public CompletableFuture<ResultValueWithDetails<String>> stringValueDetails() {
            log.debug("Evaluating flag {} as string with details", flagConfig.key());
            return evaluateWithDetails(
                IzanamiResult.Result::stringValue,
                () -> flagConfig.defaultValue() != null ? flagConfig.defaultValue().toString() : null,
                Objects::isNull  // null means disabled for string features
            );
        }

        /**
         * Evaluate the feature flag as a number and return detailed result with metadata.
         *
         * @return a future containing the number value as BigDecimal with evaluation metadata
         * @throws IzanamiClientNotAvailableException if the Izanami client is not available
         */
        @Override
        public CompletableFuture<ResultValueWithDetails<BigDecimal>> numberValueDetails() {
            log.debug("Evaluating flag {} as number with details", flagConfig.key());
            return evaluateWithDetails(
                IzanamiResult.Result::numberValue,
                () -> toBigDecimal(flagConfig.defaultValue()),
                Objects::isNull  // null means disabled for number features
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

        /**
         * Generic evaluation method that handles the common flow for all *ValueDetails methods.
         *
         * @param valueExtractor       extracts the raw value from IzanamiResult.Result
         * @param disabledValueResolver resolves the value when feature is disabled (null from Izanami)
         * @param isDisabledCheck      checks if the value indicates a disabled feature
         */
        private <T> CompletableFuture<ResultValueWithDetails<T>> evaluateWithDetails(
                Function<IzanamiResult.Result, T> valueExtractor,
                Supplier<T> disabledValueResolver,
                Predicate<T> isDisabledCheck
        ) {
            return featureResultWithMetadata().thenApply(resultWithMetadata -> {
                Map<String, String> metadata = new LinkedHashMap<>(resultWithMetadata.metadata());
                IzanamiResult.Result result = resultWithMetadata.result();
                T rawValue = valueExtractor.apply(result);

                EvaluationOutcome<T> outcome = computeOutcome(result, rawValue, disabledValueResolver, isDisabledCheck);

                metadata.put(FlagMetadataKeys.FLAG_VALUE_SOURCE, outcome.source().name());
                metadata.put(FlagMetadataKeys.FLAG_EVALUATION_REASON, outcome.reason());

                if (outcome.source() == FlagValueSource.APPLICATION_ERROR_STRATEGY) {
                    log.warn("Flag {} evaluated using application default value (feature disabled), value={}", flagConfig.key(), outcome.value());
                } else if (outcome.source() == FlagValueSource.IZANAMI_ERROR_STRATEGY) {
                    log.warn("Flag {} evaluated using Izanami error strategy (evaluation error), value={}", flagConfig.key(), outcome.value());
                } else {
                    log.debug("Evaluated flag {} = {} with details, reason={}", flagConfig.key(), outcome.value(), outcome.reason());
                }
                return new ResultValueWithDetails<>(outcome.value(), unmodifiableMap(metadata));
            });
        }

        /**
         * Computes the evaluation outcome (value, source, reason) based on the Izanami result.
         * <p>
         * For disabled non-boolean features, Izanami returns null (Success with NullValue).
         * In this case, the configured defaultValue is applied if available.
         *
         * @see <a href="https://github.com/MAIF/izanami-java-client/blob/v2.3.7/src/main/java/fr/maif/features/values/NullValue.java">NullValue</a>
         */
        private <T> EvaluationOutcome<T> computeOutcome(
                IzanamiResult.Result result,
                T rawValue,
                Supplier<T> disabledValueResolver,
                Predicate<T> isDisabledCheck
        ) {
            if (result instanceof IzanamiResult.Success) {
                if (isDisabledCheck.test(rawValue)) {
                    // Feature is disabled - apply default value if configured
                    T resolvedValue = disabledValueResolver.get();
                    return new EvaluationOutcome<>(
                        resolvedValue != null ? resolvedValue : rawValue,
                        resolvedValue != null ? FlagValueSource.APPLICATION_ERROR_STRATEGY : FlagValueSource.IZANAMI,
                        "DISABLED"
                    );
                }
                return new EvaluationOutcome<>(rawValue, FlagValueSource.IZANAMI, "ORIGIN_OR_CACHE");
            }
            // Error case - value comes from Izanami's error strategy
            return new EvaluationOutcome<>(rawValue, FlagValueSource.IZANAMI_ERROR_STRATEGY, "ERROR");
        }
    }

    // =====================================================================
    // Internal evaluation methods
    // =====================================================================

    private CompletableFuture<IzanamiResult.Result> evaluateFeatureResult(
            FlagConfig flagConfig,
            @Nullable String user,
            @Nullable String context
    ) {
        if (log.isTraceEnabled()) {
            log.trace("Querying Izanami: key={}, user={}, context={}", flagConfig.key(), user, context);
        }
        IzanamiClient client = requireClient();
        try {
            FeatureRequest featureRequest = FeatureRequest.newFeatureRequest()
                .withFeature(flagConfig.key())
                .withErrorStrategy(flagConfig.clientErrorStrategy())
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
                    // Has always a value
                    return r.results.values().iterator().next();
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

    private CompletableFuture<String> evaluateString(SingleFeatureRequest request, FlagConfig flagConfig) {
        return requireClient().stringValue(request)
            .thenApply(value -> {
                // Disabled features return null - apply default if configured
                if (value == null && flagConfig.defaultValue() != null
                        && flagConfig.errorStrategy() == ErrorStrategy.DEFAULT_VALUE) {
                    String defaultValue = flagConfig.defaultValue().toString();
                    log.warn("Flag {} evaluated using application default value (feature disabled), value={}", flagConfig.key(), defaultValue);
                    return defaultValue;
                }
                return value;
            });
    }

    private CompletableFuture<BigDecimal> evaluateNumber(SingleFeatureRequest request, FlagConfig flagConfig) {
        return requireClient().numberValue(request)
            .thenApply(value -> {
                // Disabled features return null - apply default if configured
                if (value == null && flagConfig.defaultValue() != null
                        && flagConfig.errorStrategy() == ErrorStrategy.DEFAULT_VALUE) {
                    BigDecimal defaultValue = toBigDecimal(flagConfig.defaultValue());
                    log.warn("Flag {} evaluated using application default value (feature disabled), value={}", flagConfig.key(), defaultValue);
                    return defaultValue;
                }
                return value;
            });
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

    /**
     * Converts a value to BigDecimal. Returns null if the input is null or not a number.
     */
    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString());
        }
        return null;
    }


}
