package fr.maif.izanami.spring.test;

import fr.maif.izanami.spring.openfeature.FlagMetadataKeys;
import fr.maif.izanami.spring.openfeature.FlagValueSource;
import fr.maif.izanami.spring.service.api.BatchResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BatchResultBuilderTest {

    @Test
    void shouldBuildBooleanFlag() {
        BatchResult result = BatchResultBuilder.create()
                .withBooleanFlag("f1", true)
                .build();

        assertThat(result.booleanValue("f1")).isTrue();
        assertThat(result.hasFlag("f1")).isTrue();
        assertThat(result.flagIdentifiers()).containsExactly("f1");
    }

    @Test
    void shouldBuildStringFlag() {
        BatchResult result = BatchResultBuilder.create()
                .withStringFlag("f1", "premium")
                .build();

        assertThat(result.stringValue("f1")).isEqualTo("premium");
        assertThat(result.hasFlag("f1")).isTrue();
    }

    @Test
    void shouldBuildNumberFlag() {
        BatchResult result = BatchResultBuilder.create()
                .withNumberFlag("f1", new BigDecimal("42"))
                .build();

        assertThat(result.numberValue("f1")).isEqualByComparingTo("42");
        assertThat(result.hasFlag("f1")).isTrue();
    }

    @Test
    void shouldReturnDefaultsForUnknownFlags() {
        BatchResult result = BatchResultBuilder.create().build();

        assertThat(result.booleanValue("unknown")).isFalse();
        assertThat(result.stringValue("unknown")).isEmpty();
        assertThat(result.numberValue("unknown")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.hasFlag("unknown")).isFalse();
    }

    @Test
    void shouldReturnDetailsWithMetadata() {
        Map<String, String> metadata = Map.of("source", "test");
        BatchResult result = BatchResultBuilder.create()
                .withBooleanFlag("f1", true, metadata)
                .build();

        assertThat(result.booleanValueDetails("f1").value()).isTrue();
        assertThat(result.booleanValueDetails("f1").metadata()).containsEntry("source", "test");
    }

    @Test
    void shouldReturnFlagNotFoundMetadataForUnknownFlagDetails() {
        BatchResult result = BatchResultBuilder.create().build();

        var boolDetails = result.booleanValueDetails("unknown");
        assertThat(boolDetails.value()).isFalse();
        assertThat(boolDetails.metadata())
                .containsEntry(FlagMetadataKeys.FLAG_CONFIG_KEY, "unknown")
                .containsEntry(FlagMetadataKeys.FLAG_VALUE_SOURCE, FlagValueSource.APPLICATION_ERROR_STRATEGY.name())
                .containsEntry(FlagMetadataKeys.FLAG_EVALUATION_REASON, "FLAG_NOT_FOUND");

        var strDetails = result.stringValueDetails("unknown");
        assertThat(strDetails.value()).isEmpty();
        assertThat(strDetails.metadata())
                .containsEntry(FlagMetadataKeys.FLAG_EVALUATION_REASON, "FLAG_NOT_FOUND");

        var numDetails = result.numberValueDetails("unknown");
        assertThat(numDetails.value()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(numDetails.metadata())
                .containsEntry(FlagMetadataKeys.FLAG_EVALUATION_REASON, "FLAG_NOT_FOUND");
    }

    @Test
    void shouldBuildMultiTypeBatchResult() {
        BatchResult result = BatchResultBuilder.create()
                .withBooleanFlag("b1", true)
                .withStringFlag("s1", "hello")
                .withNumberFlag("n1", BigDecimal.TEN)
                .build();

        assertThat(result.flagIdentifiers()).containsExactlyInAnyOrder("b1", "s1", "n1");
        assertThat(result.booleanValue("b1")).isTrue();
        assertThat(result.stringValue("s1")).isEqualTo("hello");
        assertThat(result.numberValue("n1")).isEqualByComparingTo("10");
    }
}
