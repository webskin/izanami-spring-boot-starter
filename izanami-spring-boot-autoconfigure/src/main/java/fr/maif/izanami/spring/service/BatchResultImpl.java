package fr.maif.izanami.spring.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.maif.features.results.IzanamiResult;
import fr.maif.features.values.BooleanCastStrategy;
import fr.maif.izanami.spring.openfeature.FlagConfig;
import fr.maif.izanami.spring.openfeature.FlagMetadataKeys;
import fr.maif.izanami.spring.openfeature.FlagValueSource;
import fr.maif.izanami.spring.service.api.BatchResult;
import fr.maif.izanami.spring.service.api.ResultValueWithDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static fr.maif.izanami.spring.service.IzanamiEvaluationHelper.computeOutcome;
import static java.util.Collections.unmodifiableMap;

/**
 * Implementation of {@link BatchResult} that wraps Izanami results and applies
 * error strategies per flag.
 * <p>
 * This class is package-private and not part of the public API.
 */
final class BatchResultImpl implements BatchResult {
    private static final Logger log = LoggerFactory.getLogger(BatchResultImpl.class);

    private final Map<String, BatchResultEntry> entries;
    private final ObjectMapper objectMapper;

    /**
     * Internal record holding result + flagConfig + computed metadata for a single flag.
     */
    record BatchResultEntry(
        IzanamiResult.Result result,
        FlagConfig flagConfig,
        Map<String, String> baseMetadata
    ) {}

    BatchResultImpl(Map<String, BatchResultEntry> entries, ObjectMapper objectMapper) {
        this.entries = Map.copyOf(entries);
        this.objectMapper = objectMapper;
    }

    @Override
    public Boolean booleanValue(String flagId) {
        return booleanValueDetails(flagId).value();
    }

    @Override
    public String stringValue(String flagId) {
        return stringValueDetails(flagId).value();
    }

    @Override
    public BigDecimal numberValue(String flagId) {
        return numberValueDetails(flagId).value();
    }

    @Override
    public ResultValueWithDetails<Boolean> booleanValueDetails(String flagId) {
        BatchResultEntry entry = entries.get(flagId);
        if (entry == null) {
            return new ResultValueWithDetails<>(null, Map.of());
        }
        return evaluateWithDetails(
            entry,
            result -> result.booleanValue(BooleanCastStrategy.LAX),
            () -> null,  // Boolean doesn't use default for disabled - false is the disabled value
            Boolean.FALSE::equals  // false means disabled for boolean features
        );
    }

    @Override
    public ResultValueWithDetails<String> stringValueDetails(String flagId) {
        BatchResultEntry entry = entries.get(flagId);
        if (entry == null) {
            return new ResultValueWithDetails<>(null, Map.of());
        }
        return evaluateWithDetails(
            entry,
            IzanamiResult.Result::stringValue,
            () -> entry.flagConfig().defaultValue() != null ? entry.flagConfig().defaultValue().toString() : null,
            Objects::isNull  // null means disabled for string features
        );
    }

    @Override
    public ResultValueWithDetails<BigDecimal> numberValueDetails(String flagId) {
        BatchResultEntry entry = entries.get(flagId);
        if (entry == null) {
            return new ResultValueWithDetails<>(null, Map.of());
        }
        return evaluateWithDetails(
            entry,
            IzanamiResult.Result::numberValue,
            () -> IzanamiEvaluationHelper.toBigDecimal(entry.flagConfig().defaultValue()),
            Objects::isNull  // null means disabled for number features
        );
    }

    @Override
    public Set<String> flagIdentifiers() {
        return entries.keySet();
    }

    @Override
    public boolean hasFlag(String flagId) {
        return entries.containsKey(flagId);
    }

    // =========================================================================
    // Internal evaluation helpers
    // =========================================================================

    /**
     * Generic evaluation method that handles the common flow for all *ValueDetails methods.
     *
     * @param entry                the batch result entry for this flag
     * @param valueExtractor       extracts the raw value from IzanamiResult.Result
     * @param disabledValueResolver resolves the value when feature is disabled (null from Izanami)
     * @param isDisabledCheck      checks if the value indicates a disabled feature
     */
    private <T> ResultValueWithDetails<T> evaluateWithDetails(
            BatchResultEntry entry,
            Function<IzanamiResult.Result, T> valueExtractor,
            Supplier<T> disabledValueResolver,
            Predicate<T> isDisabledCheck
    ) {
        Map<String, String> metadata = new LinkedHashMap<>(entry.baseMetadata());
        IzanamiResult.Result result = entry.result();
        FlagConfig flagConfig = entry.flagConfig();
        T rawValue = valueExtractor.apply(result);

        IzanamiEvaluationHelper.EvaluationOutcome<T> outcome = computeOutcome(result, rawValue, disabledValueResolver, isDisabledCheck);

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
    }
}
