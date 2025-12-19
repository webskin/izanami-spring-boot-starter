package fr.maif.izanami.spring.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.maif.FeatureClientErrorStrategy;
import fr.maif.IzanamiClient;
import fr.maif.errors.IzanamiError;
import fr.maif.features.results.IzanamiResult;
import fr.maif.features.values.BooleanCastStrategy;
import fr.maif.izanami.spring.openfeature.FlagConfig;
import fr.maif.requests.FeatureRequest;
import fr.maif.requests.SpecificFeatureRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Performs batch feature flag evaluation using SpecificFeatureRequest
 * for per-feature error strategies.
 * <p>
 * This class is package-private and not part of the public API.
 */
final class IzanamiBatchFeatureEvaluator {
    private static final Logger log = LoggerFactory.getLogger(IzanamiBatchFeatureEvaluator.class);

    @Nullable
    private final IzanamiClient client;
    private final ObjectMapper objectMapper;
    private final Map<String, FlagConfig> flagConfigs;  // keyed by Izanami key
    private final Map<String, String> identifierToKey;  // user identifier -> Izanami key
    private final Set<String> notFoundIdentifiers;      // identifiers for flags not in configuration
    private final String user;
    private final String context;
    private final boolean ignoreCache;
    @Nullable
    private final Duration callTimeout;
    @Nullable
    private final String payload;
    private final BooleanCastStrategy booleanCastStrategy;
    @Nullable
    private final FeatureClientErrorStrategy<?> errorStrategyOverride;

    IzanamiBatchFeatureEvaluator(
            @Nullable IzanamiClient client,
            ObjectMapper objectMapper,
            Map<String, FlagConfig> flagConfigs,
            Map<String, String> identifierToKey,
            Set<String> notFoundIdentifiers,
            @Nullable String user,
            @Nullable String context,
            boolean ignoreCache,
            @Nullable Duration callTimeout,
            @Nullable String payload,
            BooleanCastStrategy booleanCastStrategy,
            @Nullable FeatureClientErrorStrategy<?> errorStrategyOverride
    ) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.flagConfigs = flagConfigs;
        this.identifierToKey = identifierToKey;
        this.notFoundIdentifiers = notFoundIdentifiers;
        this.user = user;
        this.context = context;
        this.ignoreCache = ignoreCache;
        this.callTimeout = callTimeout;
        this.payload = payload;
        this.booleanCastStrategy = booleanCastStrategy;
        this.errorStrategyOverride = errorStrategyOverride;
    }

    /**
     * Execute the batch evaluation.
     *
     * @return a future containing the batch result
     */
    CompletableFuture<BatchResultImpl> evaluate() {
        // If no configured flags, just return not-found results
        if (flagConfigs.isEmpty()) {
            log.debug("No configured flags, returning FLAG_NOT_FOUND for {} identifiers", notFoundIdentifiers.size());
            return CompletableFuture.completedFuture(buildNotFoundOnlyResult());
        }

        if (client == null) {
            log.debug("Izanami client not available, returning defaults for all {} flags", flagConfigs.size());
            return CompletableFuture.completedFuture(buildAllDefaultResults());
        }

        // Build SpecificFeatureRequests with per-feature error strategies
        Set<SpecificFeatureRequest> specificRequests = buildSpecificFeatureRequests();

        // Build request with all features and their error strategies
        FeatureRequest request = FeatureRequest.newFeatureRequest()
            .withSpecificFeatures(specificRequests)
            .withBooleanCastStrategy(booleanCastStrategy);

        request = IzanamiEvaluationHelper.applyCommonConfiguration(
            request, user, context, ignoreCache, callTimeout, payload
        );

        log.debug("Making bulk Izanami query for {} features", specificRequests.size());

        return client.featureValues(request)
            .handle((izanamiResult, error) -> {
                if (error != null) {
                    log.warn("Bulk Izanami query failed: {}", error.getMessage());
                    return buildAllErrorResults(error);
                }
                return buildResultFromIzanamiResult(izanamiResult);
            });
    }

    /**
     * Build SpecificFeatureRequests with per-feature error strategies.
     * Uses per-request override if provided, otherwise uses FlagConfig default.
     */
    private Set<SpecificFeatureRequest> buildSpecificFeatureRequests() {
        Set<SpecificFeatureRequest> requests = new HashSet<>();
        for (FlagConfig config : flagConfigs.values()) {
            FeatureClientErrorStrategy<?> effectiveStrategy =
                IzanamiEvaluationHelper.computeEffectiveErrorStrategy(errorStrategyOverride, config.clientErrorStrategy());
            SpecificFeatureRequest specific = SpecificFeatureRequest.feature(config.key())
                .withErrorStrategy(effectiveStrategy);
            requests.add(specific);
        }
        return requests;
    }

    /**
     * Build batch result from Izanami response.
     */
    private BatchResultImpl buildResultFromIzanamiResult(IzanamiResult izanamiResult) {
        Map<String, BatchResultImpl.BatchResultEntry> entries = new LinkedHashMap<>();

        for (Map.Entry<String, String> mapping : identifierToKey.entrySet()) {
            String userIdentifier = mapping.getKey();
            String izanamiKey = mapping.getValue();
            FlagConfig config = flagConfigs.get(izanamiKey);

            FeatureClientErrorStrategy<?> effectiveStrategy =
                IzanamiEvaluationHelper.computeEffectiveErrorStrategy(errorStrategyOverride, config.clientErrorStrategy());

            IzanamiResult.Result result = izanamiResult.results.get(izanamiKey);
            if (result == null) {
                // Feature not in response - create error result with effective strategy
                // FAIL strategy will throw IzanamiException when value is extracted
                log.warn("Feature {} not found in Izanami response", izanamiKey);
                result = new IzanamiResult.Error(
                    effectiveStrategy,
                    new IzanamiError("Feature not found in response: " + izanamiKey)
                );
            }

            Map<String, String> metadata = IzanamiEvaluationHelper.buildBaseMetadata(config, objectMapper);
            entries.put(userIdentifier, new BatchResultImpl.BatchResultEntry(result, config, metadata));
        }

        // Add not-found entries
        addNotFoundEntries(entries);

        log.debug("Successfully built batch result for {} features", entries.size());
        return new BatchResultImpl(entries);
    }

    /**
     * Build result when client is not available - all flags get error result.
     */
    private BatchResultImpl buildAllDefaultResults() {
        Map<String, BatchResultImpl.BatchResultEntry> entries = new LinkedHashMap<>();

        for (Map.Entry<String, String> mapping : identifierToKey.entrySet()) {
            String userIdentifier = mapping.getKey();
            String izanamiKey = mapping.getValue();
            FlagConfig config = flagConfigs.get(izanamiKey);

            FeatureClientErrorStrategy<?> effectiveStrategy =
                IzanamiEvaluationHelper.computeEffectiveErrorStrategy(errorStrategyOverride, config.clientErrorStrategy());

            // FAIL strategy will throw IzanamiException when value is extracted
            IzanamiResult.Result result = new IzanamiResult.Error(
                effectiveStrategy,
                new IzanamiError("Izanami client not available")
            );

            Map<String, String> metadata = IzanamiEvaluationHelper.buildBaseMetadata(config, objectMapper);
            entries.put(userIdentifier, new BatchResultImpl.BatchResultEntry(result, config, metadata));
        }

        // Add not-found entries
        addNotFoundEntries(entries);

        return new BatchResultImpl(entries);
    }

    /**
     * Build result when bulk query fails - all flags get error result.
     */
    private BatchResultImpl buildAllErrorResults(Throwable error) {
        Map<String, BatchResultImpl.BatchResultEntry> entries = new LinkedHashMap<>();

        for (Map.Entry<String, String> mapping : identifierToKey.entrySet()) {
            String userIdentifier = mapping.getKey();
            String izanamiKey = mapping.getValue();
            FlagConfig config = flagConfigs.get(izanamiKey);

            FeatureClientErrorStrategy<?> effectiveStrategy =
                IzanamiEvaluationHelper.computeEffectiveErrorStrategy(errorStrategyOverride, config.clientErrorStrategy());

            // FAIL strategy will throw IzanamiException when value is extracted
            IzanamiResult.Result result = new IzanamiResult.Error(
                effectiveStrategy,
                new IzanamiError(error.getMessage())
            );

            Map<String, String> metadata = IzanamiEvaluationHelper.buildBaseMetadata(config, objectMapper);
            entries.put(userIdentifier, new BatchResultImpl.BatchResultEntry(result, config, metadata));
        }

        // Add not-found entries
        addNotFoundEntries(entries);

        return new BatchResultImpl(entries);
    }

    /**
     * Build result containing only not-found entries (when no configured flags).
     */
    private BatchResultImpl buildNotFoundOnlyResult() {
        Map<String, BatchResultImpl.BatchResultEntry> entries = new LinkedHashMap<>();
        addNotFoundEntries(entries);
        return new BatchResultImpl(entries);
    }

    /**
     * Add entries for flags not found in configuration with FLAG_NOT_FOUND reason.
     * Not-found flags have null result, so value extraction returns type-safe defaults (no exception).
     */
    private void addNotFoundEntries(Map<String, BatchResultImpl.BatchResultEntry> entries) {
        for (String identifier : notFoundIdentifiers) {
            log.warn("Flag '{}' not found in configuration, returning default values", identifier);
            Map<String, String> metadata = IzanamiEvaluationHelper.buildFlagNotFoundMetadata(identifier);
            entries.put(identifier, new BatchResultImpl.BatchResultEntry(null, null, metadata));
        }
    }

}
