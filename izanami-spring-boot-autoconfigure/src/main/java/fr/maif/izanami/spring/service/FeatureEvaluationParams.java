package fr.maif.izanami.spring.service;

import fr.maif.FeatureClientErrorStrategy;
import fr.maif.izanami.spring.openfeature.FlagConfig;

/**
 * Evaluation params for single-flag evaluation.
 */
final class FeatureEvaluationParams extends BaseEvaluationParams {
    private final FlagConfig flagConfig;
    private final FeatureClientErrorStrategy<?> effectiveErrorStrategy;

    FeatureEvaluationParams(
        BaseEvaluationParams baseParams,
        FlagConfig flagConfig,
        FeatureClientErrorStrategy<?> effectiveErrorStrategy
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
        this.flagConfig = flagConfig;
        this.effectiveErrorStrategy = effectiveErrorStrategy;
    }

    FlagConfig flagConfig() {
        return flagConfig;
    }

    FeatureClientErrorStrategy<?> effectiveErrorStrategy() {
        return effectiveErrorStrategy;
    }

    static FeatureEvaluationParamsBuilder builder() {
        return new FeatureEvaluationParamsBuilder();
    }
}
