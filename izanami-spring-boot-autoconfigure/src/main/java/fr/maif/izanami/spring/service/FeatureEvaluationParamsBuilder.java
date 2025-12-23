package fr.maif.izanami.spring.service;

import fr.maif.FeatureClientErrorStrategy;
import fr.maif.izanami.spring.openfeature.FlagConfig;

import java.util.Objects;

/**
 * Builder for {@link FeatureEvaluationParams}.
 */
final class FeatureEvaluationParamsBuilder extends BaseEvaluationParamsBuilder<FeatureEvaluationParamsBuilder> {
    private FlagConfig flagConfig;
    private FeatureClientErrorStrategy<?> effectiveErrorStrategy;

    FeatureEvaluationParamsBuilder flagConfig(FlagConfig flagConfig) {
        this.flagConfig = flagConfig;
        return this;
    }

    FeatureEvaluationParamsBuilder effectiveErrorStrategy(FeatureClientErrorStrategy<?> effectiveErrorStrategy) {
        this.effectiveErrorStrategy = effectiveErrorStrategy;
        return this;
    }

    FeatureEvaluationParams build() {
        return new FeatureEvaluationParams(
            buildBase(),
            Objects.requireNonNull(flagConfig, "flagConfig"),
            Objects.requireNonNull(effectiveErrorStrategy, "effectiveErrorStrategy")
        );
    }
}
