package fr.maif.izanami.spring.test;

import fr.maif.izanami.spring.service.api.BatchResult;
import fr.maif.izanami.spring.service.api.IzanamiService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.ExecutionException;

import static fr.maif.izanami.spring.test.IzanamiMockHelper.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

class IzanamiMockHelperTest {

    @Test
    void mockServiceShouldBeConnected() {
        IzanamiService service = mockIzanamiService();
        assertThat(service.isConnected()).isTrue();
    }

    @Test
    void mockServiceShouldReturnEmptyClient() {
        IzanamiService service = mockIzanamiService();
        assertThat(service.unwrapClient()).isEmpty();
    }

    @Test
    void mockServiceShouldReturnCompletedWhenLoaded() throws Exception {
        IzanamiService service = mockIzanamiService();
        assertThat(service.whenLoaded()).isCompleted();
    }

    // --- Boolean flag ---

    @Test
    void givenFlagKeyShouldStubBooleanValue() throws Exception {
        IzanamiService service = mockIzanamiService();
        givenFlagKey(service, "uuid-1").willReturn(true);

        assertThat(service.forFlagKey("uuid-1").booleanValue().get()).isTrue();
    }

    @Test
    void givenFlagKeyShouldStubBooleanValueDetails() throws Exception {
        IzanamiService service = mockIzanamiService();
        givenFlagKey(service, "uuid-1").willReturn(true);

        var details = service.forFlagKey("uuid-1").booleanValueDetails().get();
        assertThat(details.value()).isTrue();
        assertThat(details.metadata()).isEmpty();
    }

    @Test
    void givenFlagNameShouldStubBooleanValue() throws Exception {
        IzanamiService service = mockIzanamiService();
        givenFlagName(service, "my-feature").willReturn(false);

        assertThat(service.forFlagName("my-feature").booleanValue().get()).isFalse();
    }

    // --- String flag ---

    @Test
    void givenFlagKeyShouldStubStringValue() throws Exception {
        IzanamiService service = mockIzanamiService();
        givenFlagKey(service, "uuid-2").willReturn("premium");

        assertThat(service.forFlagKey("uuid-2").stringValue().get()).isEqualTo("premium");
    }

    @Test
    void givenFlagKeyShouldStubStringValueDetails() throws Exception {
        IzanamiService service = mockIzanamiService();
        givenFlagKey(service, "uuid-2").willReturn("premium");

        var details = service.forFlagKey("uuid-2").stringValueDetails().get();
        assertThat(details.value()).isEqualTo("premium");
    }

    // --- Number flag ---

    @Test
    void givenFlagKeyShouldStubNumberValue() throws Exception {
        IzanamiService service = mockIzanamiService();
        givenFlagKey(service, "uuid-3").willReturn(new BigDecimal("42"));

        assertThat(service.forFlagKey("uuid-3").numberValue().get()).isEqualByComparingTo("42");
    }

    @Test
    void givenFlagKeyShouldStubNumberValueDetails() throws Exception {
        IzanamiService service = mockIzanamiService();
        givenFlagKey(service, "uuid-3").willReturn(new BigDecimal("42"));

        var details = service.forFlagKey("uuid-3").numberValueDetails().get();
        assertThat(details.value()).isEqualByComparingTo("42");
    }

    // --- Failure ---

    @Test
    void willFailWithShouldMakeAllTerminalMethodsFail() {
        IzanamiService service = mockIzanamiService();
        RuntimeException error = new RuntimeException("boom");
        givenFlagKey(service, "uuid-1").willFailWith(error);

        var builder = service.forFlagKey("uuid-1");
        assertThatThrownBy(() -> builder.booleanValue().get())
                .isInstanceOf(ExecutionException.class)
                .hasCause(error);
        assertThatThrownBy(() -> builder.stringValue().get())
                .isInstanceOf(ExecutionException.class)
                .hasCause(error);
        assertThatThrownBy(() -> builder.numberValue().get())
                .isInstanceOf(ExecutionException.class)
                .hasCause(error);
    }

    // --- Builder chaining ---

    @Test
    void builderShouldSupportChaining() throws Exception {
        IzanamiService service = mockIzanamiService();
        FlagStubBuilder stub = givenFlagKey(service, "uuid-1");
        stub.willReturn(true);

        Boolean result = service.forFlagKey("uuid-1")
                .withUser("alice")
                .withContext("prod")
                .booleanValue()
                .get();

        assertThat(result).isTrue();
        verify(stub.getBuilderMock()).withUser("alice");
        verify(stub.getBuilderMock()).withContext("prod");
    }

    // --- Batch ---

    @Test
    void givenFlagKeysShouldStubBatchResult() throws Exception {
        IzanamiService service = mockIzanamiService();
        BatchResult batchResult = BatchResultBuilder.create()
                .withBooleanFlag("f1", true)
                .withStringFlag("f2", "value")
                .build();

        givenFlagKeys(service, "f1", "f2").willReturn(batchResult);

        BatchResult result = service.forFlagKeys("f1", "f2").values().get();
        assertThat(result.booleanValue("f1")).isTrue();
        assertThat(result.stringValue("f2")).isEqualTo("value");
    }

    @Test
    void givenFlagNamesShouldStubBatchResult() throws Exception {
        IzanamiService service = mockIzanamiService();
        BatchResult batchResult = BatchResultBuilder.create()
                .withBooleanFlag("feature-a", true)
                .build();

        givenFlagNames(service, "feature-a").willReturn(batchResult);

        BatchResult result = service.forFlagNames("feature-a").values().get();
        assertThat(result.booleanValue("feature-a")).isTrue();
    }

    @Test
    void batchWillFailWithShouldMakeValuesFail() {
        IzanamiService service = mockIzanamiService();
        RuntimeException error = new RuntimeException("batch boom");
        givenFlagKeys(service, "f1").willFailWith(error);

        assertThatThrownBy(() -> service.forFlagKeys("f1").values().get())
                .isInstanceOf(ExecutionException.class)
                .hasCause(error);
    }

    @Test
    void batchBuilderShouldSupportChaining() throws Exception {
        IzanamiService service = mockIzanamiService();
        BatchResult batchResult = BatchResultBuilder.create()
                .withBooleanFlag("f1", true)
                .build();
        BatchFlagStubBuilder stub = givenFlagKeys(service, "f1");
        stub.willReturn(batchResult);

        service.forFlagKeys("f1").withUser("alice").values().get();

        verify(stub.getBuilderMock()).withUser("alice");
    }
}
