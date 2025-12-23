package fr.maif.izanami.spring.service;

import fr.maif.features.results.IzanamiResult;
import fr.maif.features.values.BooleanCastStrategy;
import fr.maif.izanami.spring.openfeature.FlagConfig;
import fr.maif.izanami.spring.service.api.BatchResult;
import fr.maif.izanami.spring.service.api.ResultValueWithDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

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

    /**
     * Internal record holding result + flagConfig + computed metadata for a single flag.
     * <p>
     * For FAIL strategy, exception is thrown when value is extracted via {@code result.booleanValue()} etc.
     *
     * @param result        the Izanami result (may be null for FLAG_NOT_FOUND)
     * @param flagConfig    the flag configuration (may be null for FLAG_NOT_FOUND)
     * @param baseMetadata  pre-computed metadata for this flag
     */
    record BatchResultEntry(
        IzanamiResult.Result result,
        FlagConfig flagConfig,
        Map<String, String> baseMetadata
    ) {}

    BatchResultImpl(Map<String, BatchResultEntry> entries) {
        this.entries = Map.copyOf(entries);
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
        // Unknown flag id (not requested or not configured) -> treat as FLAG_NOT_FOUND.
        if (entry == null) {
            return unknownFlagResult(flagId, false);
        }
        // Handle FLAG_NOT_FOUND entries (no result or flagConfig)
        // baseMetadata already includes FLAG_VALUE_SOURCE and FLAG_EVALUATION_REASON from IzanamiBatchFeatureEvaluator
        if (entry.result() == null) {
            return new ResultValueWithDetails<>(false, unmodifiableMap(entry.baseMetadata()));
        }
        // FAIL strategy throws IzanamiException in result.booleanValue() via valueExtractor
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
        // Unknown flag id (not requested or not configured) -> treat as FLAG_NOT_FOUND.
        if (entry == null) {
            return unknownFlagResult(flagId, "");
        }
        // Handle FLAG_NOT_FOUND entries (no result or flagConfig)
        if (entry.result() == null) {
            return new ResultValueWithDetails<>("", unmodifiableMap(entry.baseMetadata()));
        }
        // FAIL strategy throws IzanamiException in result.stringValue() via valueExtractor
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
        // Unknown flag id (not requested or not configured) -> treat as FLAG_NOT_FOUND.
        if (entry == null) {
            return unknownFlagResult(flagId, BigDecimal.ZERO);
        }
        // Handle FLAG_NOT_FOUND entries (no result or flagConfig)
        if (entry.result() == null) {
            return new ResultValueWithDetails<>(BigDecimal.ZERO, unmodifiableMap(entry.baseMetadata()));
        }
        // FAIL strategy throws IzanamiException in result.numberValue() via valueExtractor
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
        return IzanamiEvaluationHelper.buildResultWithDetails(
            entry.result(),
            valueExtractor,
            entry.baseMetadata(),
            disabledValueResolver,
            isDisabledCheck,
            entry.flagConfig().key()
        );
    }

    private <T> ResultValueWithDetails<T> unknownFlagResult(String flagId, T defaultValue) {
        log.warn("Flag '{}' not found in batch result, returning default values", flagId);
        return new ResultValueWithDetails<>(
            defaultValue,
            unmodifiableMap(IzanamiEvaluationHelper.buildFlagNotFoundMetadata(flagId))
        );
    }
}
