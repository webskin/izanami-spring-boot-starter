package fr.maif.izanami.spring.test;

import fr.maif.izanami.spring.openfeature.FlagMetadataKeys;
import fr.maif.izanami.spring.openfeature.FlagValueSource;
import fr.maif.izanami.spring.service.api.BatchResult;
import fr.maif.izanami.spring.service.api.ResultValueWithDetails;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Builder for creating test {@link BatchResult} instances without connecting to Izanami.
 * <p>
 * Example usage:
 * <pre>{@code
 * BatchResult result = BatchResultBuilder.create()
 *     .withBooleanFlag("feature-a", true)
 *     .withStringFlag("feature-b", "premium")
 *     .withNumberFlag("feature-c", new BigDecimal("42"))
 *     .build();
 * }</pre>
 */
public final class BatchResultBuilder {

    private final Map<String, ResultValueWithDetails<Boolean>> booleans = new HashMap<>();
    private final Map<String, ResultValueWithDetails<String>> strings = new HashMap<>();
    private final Map<String, ResultValueWithDetails<BigDecimal>> numbers = new HashMap<>();

    private BatchResultBuilder() {
    }

    /**
     * Create a new builder.
     *
     * @return a new empty builder
     */
    public static BatchResultBuilder create() {
        return new BatchResultBuilder();
    }

    /**
     * Add a boolean flag with an empty metadata map.
     *
     * @param flagId the flag identifier
     * @param value  the boolean value
     * @return this builder
     */
    public BatchResultBuilder withBooleanFlag(String flagId, boolean value) {
        return withBooleanFlag(flagId, value, Map.of());
    }

    /**
     * Add a boolean flag with metadata.
     *
     * @param flagId   the flag identifier
     * @param value    the boolean value
     * @param metadata evaluation metadata
     * @return this builder
     */
    public BatchResultBuilder withBooleanFlag(String flagId, boolean value, Map<String, String> metadata) {
        booleans.put(flagId, new ResultValueWithDetails<>(value, metadata));
        return this;
    }

    /**
     * Add a string flag with an empty metadata map.
     *
     * @param flagId the flag identifier
     * @param value  the string value
     * @return this builder
     */
    public BatchResultBuilder withStringFlag(String flagId, String value) {
        return withStringFlag(flagId, value, Map.of());
    }

    /**
     * Add a string flag with metadata.
     *
     * @param flagId   the flag identifier
     * @param value    the string value
     * @param metadata evaluation metadata
     * @return this builder
     */
    public BatchResultBuilder withStringFlag(String flagId, String value, Map<String, String> metadata) {
        strings.put(flagId, new ResultValueWithDetails<>(value, metadata));
        return this;
    }

    /**
     * Add a number flag with an empty metadata map.
     *
     * @param flagId the flag identifier
     * @param value  the number value
     * @return this builder
     */
    public BatchResultBuilder withNumberFlag(String flagId, BigDecimal value) {
        return withNumberFlag(flagId, value, Map.of());
    }

    /**
     * Add a number flag with metadata.
     *
     * @param flagId   the flag identifier
     * @param value    the number value
     * @param metadata evaluation metadata
     * @return this builder
     */
    public BatchResultBuilder withNumberFlag(String flagId, BigDecimal value, Map<String, String> metadata) {
        numbers.put(flagId, new ResultValueWithDetails<>(value, metadata));
        return this;
    }

    /**
     * Build the {@link BatchResult}.
     *
     * @return a new immutable batch result
     */
    public BatchResult build() {
        return new TestBatchResult(
                Map.copyOf(booleans),
                Map.copyOf(strings),
                Map.copyOf(numbers)
        );
    }

    private static final class TestBatchResult implements BatchResult {

        private final Map<String, ResultValueWithDetails<Boolean>> booleans;
        private final Map<String, ResultValueWithDetails<String>> strings;
        private final Map<String, ResultValueWithDetails<BigDecimal>> numbers;

        TestBatchResult(
                Map<String, ResultValueWithDetails<Boolean>> booleans,
                Map<String, ResultValueWithDetails<String>> strings,
                Map<String, ResultValueWithDetails<BigDecimal>> numbers
        ) {
            this.booleans = booleans;
            this.strings = strings;
            this.numbers = numbers;
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
            return booleans.getOrDefault(flagId, flagNotFoundResult(flagId, false));
        }

        @Override
        public ResultValueWithDetails<String> stringValueDetails(String flagId) {
            return strings.getOrDefault(flagId, flagNotFoundResult(flagId, ""));
        }

        @Override
        public ResultValueWithDetails<BigDecimal> numberValueDetails(String flagId) {
            return numbers.getOrDefault(flagId, flagNotFoundResult(flagId, BigDecimal.ZERO));
        }

        private static <T> ResultValueWithDetails<T> flagNotFoundResult(String flagId, T defaultValue) {
            Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put(FlagMetadataKeys.FLAG_CONFIG_KEY, flagId);
            metadata.put(FlagMetadataKeys.FLAG_VALUE_SOURCE, FlagValueSource.APPLICATION_ERROR_STRATEGY.name());
            metadata.put(FlagMetadataKeys.FLAG_EVALUATION_REASON, "FLAG_NOT_FOUND");
            return new ResultValueWithDetails<>(defaultValue, Map.copyOf(metadata));
        }

        @Override
        public Set<String> flagIdentifiers() {
            return Stream.of(booleans.keySet(), strings.keySet(), numbers.keySet())
                    .flatMap(Set::stream)
                    .collect(Collectors.toUnmodifiableSet());
        }

        @Override
        public boolean hasFlag(String flagId) {
            return booleans.containsKey(flagId) || strings.containsKey(flagId) || numbers.containsKey(flagId);
        }
    }
}
