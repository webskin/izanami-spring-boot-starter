package fr.maif.izanami.spring.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.maif.features.results.IzanamiResult;
import fr.maif.izanami.spring.openfeature.FlagConfig;
import fr.maif.izanami.spring.openfeature.FlagMetadataKeys;
import fr.maif.izanami.spring.openfeature.FlagValueSource;
import fr.maif.izanami.spring.service.api.ResultValueWithDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static java.util.Collections.unmodifiableMap;

/**
 * Shared evaluation utilities for feature flag processing.
 * <p>
 * This class is package-private and not part of the public API.
 */
final class IzanamiEvaluationHelper {
    private static final Logger log = LoggerFactory.getLogger(IzanamiEvaluationHelper.class);

    private IzanamiEvaluationHelper() {
        // Utility class
    }

    /**
     * Holds the computed value, source, and reason for a feature evaluation.
     */
    record EvaluationOutcome<T>(T value, FlagValueSource source, String reason) {}

    /**
     * Computes the evaluation outcome (value, source, reason) based on the Izanami result.
     * <p>
     * For disabled non-boolean features, Izanami returns null (Success with NullValue).
     * In this case, the configured defaultValue is applied if available.
     *
     * @param result               the Izanami result
     * @param rawValue             the raw value extracted from the result
     * @param disabledValueResolver resolves the value when feature is disabled (null from Izanami)
     * @param isDisabledCheck      checks if the value indicates a disabled feature
     * @see <a href="https://github.com/MAIF/izanami-java-client/blob/v2.3.7/src/main/java/fr/maif/features/values/NullValue.java">NullValue</a>
     */
    static <T> EvaluationOutcome<T> computeOutcome(
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

    /**
     * Builds the final {@link ResultValueWithDetails} from an Izanami result.
     * <p>
     * This is the common evaluation flow used by both single and batch evaluations.
     * It extracts the value, computes the outcome, updates metadata, logs appropriately,
     * and returns the final result.
     *
     * @param result               the Izanami result
     * @param valueExtractor       extracts the raw value from IzanamiResult.Result
     * @param baseMetadata         base metadata map (will be copied, not mutated)
     * @param disabledValueResolver resolves the value when feature is disabled
     * @param isDisabledCheck      checks if the value indicates a disabled feature
     * @param flagKey              the flag key for logging purposes
     * @return the result value with evaluation metadata
     */
    static <T> ResultValueWithDetails<T> buildResultWithDetails(
            IzanamiResult.Result result,
            Function<IzanamiResult.Result, T> valueExtractor,
            Map<String, String> baseMetadata,
            Supplier<T> disabledValueResolver,
            Predicate<T> isDisabledCheck,
            String flagKey
    ) {
        Map<String, String> metadata = new LinkedHashMap<>(baseMetadata);
        T rawValue = valueExtractor.apply(result);

        EvaluationOutcome<T> outcome = computeOutcome(result, rawValue, disabledValueResolver, isDisabledCheck);

        metadata.put(FlagMetadataKeys.FLAG_VALUE_SOURCE, outcome.source().name());
        metadata.put(FlagMetadataKeys.FLAG_EVALUATION_REASON, outcome.reason());

        if (outcome.source() == FlagValueSource.APPLICATION_ERROR_STRATEGY) {
            log.warn("Flag {} evaluated using application default value (feature disabled), value={}", flagKey, outcome.value());
        } else if (outcome.source() == FlagValueSource.IZANAMI_ERROR_STRATEGY) {
            log.warn("Flag {} evaluated using Izanami error strategy (evaluation error), value={}", flagKey, outcome.value());
        } else {
            log.debug("Evaluated flag {} = {} with details, reason={}", flagKey, outcome.value(), outcome.reason());
        }

        return new ResultValueWithDetails<>(outcome.value(), unmodifiableMap(metadata));
    }

    /**
     * Build base metadata map for a flag configuration.
     *
     * @param config       the flag configuration
     * @param objectMapper Jackson ObjectMapper for serializing default value
     * @return mutable metadata map with flag configuration details
     */
    static Map<String, String> buildBaseMetadata(FlagConfig config, ObjectMapper objectMapper) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put(FlagMetadataKeys.FLAG_CONFIG_KEY, config.key());
        metadata.put(FlagMetadataKeys.FLAG_CONFIG_NAME, config.name());
        metadata.put(FlagMetadataKeys.FLAG_CONFIG_DESCRIPTION, config.description());
        metadata.put(FlagMetadataKeys.FLAG_CONFIG_VALUE_TYPE, config.valueType().name());
        metadata.put(FlagMetadataKeys.FLAG_CONFIG_DEFAULT_VALUE, IzanamiServiceImpl.stringifyDefaultValue(objectMapper, config));
        metadata.put(FlagMetadataKeys.FLAG_CONFIG_ERROR_STRATEGY, config.errorStrategy().name());
        return metadata;
    }

    /**
     * Converts a value to BigDecimal. Returns null if the input is null or not a number.
     */
    static BigDecimal toBigDecimal(Object value) {
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
