package fr.maif.izanami.spring.service;

import fr.maif.FeatureClientErrorStrategy;
import fr.maif.izanami.spring.openfeature.FlagConfig;
import org.springframework.lang.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * Evaluation params for batch feature evaluation.
 */
final class BatchEvaluationParams extends BaseEvaluationParams {
    private final Map<String, FlagConfig> flagConfigs;  // keyed by Izanami key
    private final Map<String, String> identifierToKey;  // user identifier -> Izanami key
    private final Set<String> notFoundIdentifiers;      // identifiers for flags not in configuration
    @Nullable
    private final FeatureClientErrorStrategy<?> errorStrategyOverride;

    BatchEvaluationParams(
        BaseEvaluationParams baseParams,
        Map<String, FlagConfig> flagConfigs,
        Map<String, String> identifierToKey,
        Set<String> notFoundIdentifiers,
        @Nullable FeatureClientErrorStrategy<?> errorStrategyOverride
    ) {
        super(
            baseParams.client(),
            baseParams.objectMapper(),
            baseParams.user(),
            baseParams.context(),
            baseParams.ignoreCache(),
            baseParams.callTimeout(),
            baseParams.payload(),
            baseParams.booleanCastStrategy()
        );
        this.flagConfigs = flagConfigs;
        this.identifierToKey = identifierToKey;
        this.notFoundIdentifiers = notFoundIdentifiers;
        this.errorStrategyOverride = errorStrategyOverride;
    }

    static BatchEvaluationParamsBuilder builder() {
        return new BatchEvaluationParamsBuilder();
    }

    Map<String, FlagConfig> flagConfigs() {
        return flagConfigs;
    }

    Map<String, String> identifierToKey() {
        return identifierToKey;
    }

    Set<String> notFoundIdentifiers() {
        return notFoundIdentifiers;
    }

    @Nullable
    FeatureClientErrorStrategy<?> errorStrategyOverride() {
        return errorStrategyOverride;
    }

}
