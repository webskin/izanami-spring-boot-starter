package fr.maif.izanami.spring.service;

import fr.maif.FeatureClientErrorStrategy;
import fr.maif.izanami.spring.openfeature.FlagConfig;
import org.springframework.lang.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Builder for {@link BatchEvaluationParams}.
 */
final class BatchEvaluationParamsBuilder extends BaseEvaluationParamsBuilder<BatchEvaluationParamsBuilder> {
    private Map<String, FlagConfig> flagConfigs;
    private Map<String, String> identifierToKey;
    private Set<String> notFoundIdentifiers;
    @Nullable
    private FeatureClientErrorStrategy<?> errorStrategyOverride;

    BatchEvaluationParamsBuilder flagConfigs(Map<String, FlagConfig> flagConfigs) {
        this.flagConfigs = flagConfigs;
        return this;
    }

    BatchEvaluationParamsBuilder identifierToKey(Map<String, String> identifierToKey) {
        this.identifierToKey = identifierToKey;
        return this;
    }

    BatchEvaluationParamsBuilder notFoundIdentifiers(Set<String> notFoundIdentifiers) {
        this.notFoundIdentifiers = notFoundIdentifiers;
        return this;
    }

    BatchEvaluationParamsBuilder errorStrategyOverride(
        @Nullable FeatureClientErrorStrategy<?> errorStrategyOverride
    ) {
        this.errorStrategyOverride = errorStrategyOverride;
        return this;
    }

    BatchEvaluationParams build() {
        return new BatchEvaluationParams(
            buildBase(),
            Map.copyOf(Objects.requireNonNull(flagConfigs, "flagConfigs")),
            Map.copyOf(Objects.requireNonNull(identifierToKey, "identifierToKey")),
            Set.copyOf(Objects.requireNonNull(notFoundIdentifiers, "notFoundIdentifiers")),
            errorStrategyOverride
        );
    }
}
