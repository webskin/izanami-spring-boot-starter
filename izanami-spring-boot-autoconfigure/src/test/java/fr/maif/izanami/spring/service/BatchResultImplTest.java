package fr.maif.izanami.spring.service;

import dev.openfeature.sdk.FlagValueType;
import fr.maif.FeatureClientErrorStrategy;
import fr.maif.features.results.IzanamiResult;
import fr.maif.features.values.BooleanCastStrategy;
import fr.maif.features.values.FeatureValue;
import fr.maif.izanami.spring.openfeature.ErrorStrategy;
import fr.maif.izanami.spring.openfeature.FlagConfig;
import fr.maif.izanami.spring.openfeature.FlagMetadataKeys;
import fr.maif.izanami.spring.service.api.ResultValueWithDetails;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BatchResultImpl}.
 */
class BatchResultImplTest {

    private static FlagConfig testBooleanConfig(String key, String name, boolean defaultValue) {
        return new FlagConfig(
            key, name, "Test flag", FlagValueType.BOOLEAN,
            ErrorStrategy.DEFAULT_VALUE,
            FeatureClientErrorStrategy.defaultValueStrategy(defaultValue, "", BigDecimal.ZERO),
            defaultValue, null
        );
    }

    private static FlagConfig testStringConfig(String key, String name, String defaultValue) {
        return new FlagConfig(
            key, name, "Test flag", FlagValueType.STRING,
            ErrorStrategy.DEFAULT_VALUE,
            FeatureClientErrorStrategy.defaultValueStrategy(false, defaultValue != null ? defaultValue : "", BigDecimal.ZERO),
            defaultValue, null
        );
    }

    private static FlagConfig testNumberConfig(String key, String name, BigDecimal defaultValue) {
        return new FlagConfig(
            key, name, "Test flag", FlagValueType.INTEGER,
            ErrorStrategy.DEFAULT_VALUE,
            FeatureClientErrorStrategy.defaultValueStrategy(false, "", defaultValue != null ? defaultValue : BigDecimal.ZERO),
            defaultValue, null
        );
    }

    private static IzanamiResult.Success createSuccessResult(boolean boolValue, String stringValue, BigDecimal numberValue) {
        FeatureValue featureValue = mock(FeatureValue.class);
        when(featureValue.booleanValue(any(BooleanCastStrategy.class))).thenReturn(boolValue);
        when(featureValue.stringValue()).thenReturn(stringValue);
        when(featureValue.numberValue()).thenReturn(numberValue);
        return new IzanamiResult.Success(featureValue);
    }

    @Nested
    class BooleanValueTests {

        @Test
        void booleanValue_whenEntryExists_returnsValue() {
            FlagConfig config = testBooleanConfig("uuid-1", "flag-1", false);
            IzanamiResult.Success success = createSuccessResult(true, null, null);
            Map<String, String> metadata = Map.of(FlagMetadataKeys.FLAG_CONFIG_KEY, "uuid-1");

            Map<String, BatchResultImpl.BatchResultEntry> entries = new LinkedHashMap<>();
            entries.put("flag-1", new BatchResultImpl.BatchResultEntry(success, config, metadata));
            BatchResultImpl result = new BatchResultImpl(entries);

            assertThat(result.booleanValue("flag-1")).isTrue();
        }

        @Test
        void booleanValue_whenEntryNotExists_returnsNull() {
            BatchResultImpl result = new BatchResultImpl(Map.of());

            assertThat(result.booleanValue("unknown")).isNull();
        }

        @Test
        void booleanValueDetails_whenResultNull_returnsFalse() {
            Map<String, String> metadata = Map.of(
                FlagMetadataKeys.FLAG_CONFIG_KEY, "missing",
                FlagMetadataKeys.FLAG_VALUE_SOURCE, "APPLICATION_ERROR_STRATEGY",
                FlagMetadataKeys.FLAG_EVALUATION_REASON, "FLAG_NOT_FOUND"
            );
            Map<String, BatchResultImpl.BatchResultEntry> entries = new LinkedHashMap<>();
            entries.put("missing", new BatchResultImpl.BatchResultEntry(null, null, metadata));
            BatchResultImpl result = new BatchResultImpl(entries);

            ResultValueWithDetails<Boolean> details = result.booleanValueDetails("missing");

            assertThat(details.value()).isFalse();
            assertThat(details.metadata().get(FlagMetadataKeys.FLAG_EVALUATION_REASON)).isEqualTo("FLAG_NOT_FOUND");
        }

        @Test
        void booleanValueDetails_whenEntryNotExists_returnsNullWithEmptyMetadata() {
            BatchResultImpl result = new BatchResultImpl(Map.of());

            ResultValueWithDetails<Boolean> details = result.booleanValueDetails("unknown");

            assertThat(details.value()).isNull();
            assertThat(details.metadata()).isEmpty();
        }
    }

    @Nested
    class StringValueTests {

        @Test
        void stringValue_whenEntryExists_returnsValue() {
            FlagConfig config = testStringConfig("uuid-1", "flag-1", "default");
            IzanamiResult.Success success = createSuccessResult(true, "actual-value", null);
            Map<String, String> metadata = Map.of(FlagMetadataKeys.FLAG_CONFIG_KEY, "uuid-1");

            Map<String, BatchResultImpl.BatchResultEntry> entries = new LinkedHashMap<>();
            entries.put("flag-1", new BatchResultImpl.BatchResultEntry(success, config, metadata));
            BatchResultImpl result = new BatchResultImpl(entries);

            assertThat(result.stringValue("flag-1")).isEqualTo("actual-value");
        }

        @Test
        void stringValue_whenEntryNotExists_returnsNull() {
            BatchResultImpl result = new BatchResultImpl(Map.of());

            assertThat(result.stringValue("unknown")).isNull();
        }

        @Test
        void stringValueDetails_whenResultNull_returnsEmptyString() {
            Map<String, String> metadata = Map.of(
                FlagMetadataKeys.FLAG_CONFIG_KEY, "missing",
                FlagMetadataKeys.FLAG_VALUE_SOURCE, "APPLICATION_ERROR_STRATEGY",
                FlagMetadataKeys.FLAG_EVALUATION_REASON, "FLAG_NOT_FOUND"
            );
            Map<String, BatchResultImpl.BatchResultEntry> entries = new LinkedHashMap<>();
            entries.put("missing", new BatchResultImpl.BatchResultEntry(null, null, metadata));
            BatchResultImpl result = new BatchResultImpl(entries);

            ResultValueWithDetails<String> details = result.stringValueDetails("missing");

            assertThat(details.value()).isEmpty();
            assertThat(details.metadata().get(FlagMetadataKeys.FLAG_EVALUATION_REASON)).isEqualTo("FLAG_NOT_FOUND");
        }

        @Test
        void stringValueDetails_disabled_usesDefaultValue() {
            FlagConfig config = testStringConfig("uuid-1", "flag-1", "fallback");
            IzanamiResult.Success success = createSuccessResult(false, null, null);
            Map<String, String> metadata = Map.of(FlagMetadataKeys.FLAG_CONFIG_KEY, "uuid-1");

            Map<String, BatchResultImpl.BatchResultEntry> entries = new LinkedHashMap<>();
            entries.put("flag-1", new BatchResultImpl.BatchResultEntry(success, config, metadata));
            BatchResultImpl result = new BatchResultImpl(entries);

            ResultValueWithDetails<String> details = result.stringValueDetails("flag-1");

            assertThat(details.value()).isEqualTo("fallback");
        }
    }

    @Nested
    class NumberValueTests {

        @Test
        void numberValue_whenEntryExists_returnsValue() {
            FlagConfig config = testNumberConfig("uuid-1", "flag-1", BigDecimal.ZERO);
            IzanamiResult.Success success = createSuccessResult(true, null, new BigDecimal("42.5"));
            Map<String, String> metadata = Map.of(FlagMetadataKeys.FLAG_CONFIG_KEY, "uuid-1");

            Map<String, BatchResultImpl.BatchResultEntry> entries = new LinkedHashMap<>();
            entries.put("flag-1", new BatchResultImpl.BatchResultEntry(success, config, metadata));
            BatchResultImpl result = new BatchResultImpl(entries);

            assertThat(result.numberValue("flag-1")).isEqualByComparingTo(new BigDecimal("42.5"));
        }

        @Test
        void numberValue_whenEntryNotExists_returnsNull() {
            BatchResultImpl result = new BatchResultImpl(Map.of());

            assertThat(result.numberValue("unknown")).isNull();
        }

        @Test
        void numberValueDetails_whenResultNull_returnsZero() {
            Map<String, String> metadata = Map.of(
                FlagMetadataKeys.FLAG_CONFIG_KEY, "missing",
                FlagMetadataKeys.FLAG_VALUE_SOURCE, "APPLICATION_ERROR_STRATEGY",
                FlagMetadataKeys.FLAG_EVALUATION_REASON, "FLAG_NOT_FOUND"
            );
            Map<String, BatchResultImpl.BatchResultEntry> entries = new LinkedHashMap<>();
            entries.put("missing", new BatchResultImpl.BatchResultEntry(null, null, metadata));
            BatchResultImpl result = new BatchResultImpl(entries);

            ResultValueWithDetails<BigDecimal> details = result.numberValueDetails("missing");

            assertThat(details.value()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void numberValueDetails_disabled_usesDefaultValue() {
            FlagConfig config = testNumberConfig("uuid-1", "flag-1", new BigDecimal("999"));
            IzanamiResult.Success success = createSuccessResult(false, null, null);
            Map<String, String> metadata = Map.of(FlagMetadataKeys.FLAG_CONFIG_KEY, "uuid-1");

            Map<String, BatchResultImpl.BatchResultEntry> entries = new LinkedHashMap<>();
            entries.put("flag-1", new BatchResultImpl.BatchResultEntry(success, config, metadata));
            BatchResultImpl result = new BatchResultImpl(entries);

            ResultValueWithDetails<BigDecimal> details = result.numberValueDetails("flag-1");

            assertThat(details.value()).isEqualByComparingTo(new BigDecimal("999"));
        }
    }

    @Nested
    class FlagIdentifiersTests {

        @Test
        void flagIdentifiers_returnsAllKeys() {
            Map<String, BatchResultImpl.BatchResultEntry> entries = new LinkedHashMap<>();
            entries.put("flag-1", new BatchResultImpl.BatchResultEntry(null, null, Map.of()));
            entries.put("flag-2", new BatchResultImpl.BatchResultEntry(null, null, Map.of()));
            entries.put("flag-3", new BatchResultImpl.BatchResultEntry(null, null, Map.of()));
            BatchResultImpl result = new BatchResultImpl(entries);

            Set<String> identifiers = result.flagIdentifiers();

            assertThat(identifiers).containsExactlyInAnyOrder("flag-1", "flag-2", "flag-3");
        }

        @Test
        void flagIdentifiers_emptyResult_returnsEmptySet() {
            BatchResultImpl result = new BatchResultImpl(Map.of());

            assertThat(result.flagIdentifiers()).isEmpty();
        }
    }

    @Nested
    class HasFlagTests {

        @Test
        void hasFlag_existingFlag_returnsTrue() {
            Map<String, BatchResultImpl.BatchResultEntry> entries = new LinkedHashMap<>();
            entries.put("flag-1", new BatchResultImpl.BatchResultEntry(null, null, Map.of()));
            BatchResultImpl result = new BatchResultImpl(entries);

            assertThat(result.hasFlag("flag-1")).isTrue();
        }

        @Test
        void hasFlag_nonExistingFlag_returnsFalse() {
            BatchResultImpl result = new BatchResultImpl(Map.of());

            assertThat(result.hasFlag("unknown")).isFalse();
        }
    }

    @Nested
    class ImmutabilityTests {

        @Test
        void entriesAreImmutable() {
            Map<String, BatchResultImpl.BatchResultEntry> entries = new LinkedHashMap<>();
            entries.put("flag-1", new BatchResultImpl.BatchResultEntry(null, null, Map.of()));
            BatchResultImpl result = new BatchResultImpl(entries);

            // Modify original map - should not affect BatchResultImpl
            entries.put("flag-2", new BatchResultImpl.BatchResultEntry(null, null, Map.of()));

            assertThat(result.flagIdentifiers()).containsExactly("flag-1");
        }
    }
}
