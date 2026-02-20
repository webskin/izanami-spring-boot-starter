package fr.maif.izanami.spring.test;

import fr.maif.izanami.spring.service.api.BatchResult;
import fr.maif.izanami.spring.service.api.ReactiveIzanamiService;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static fr.maif.izanami.spring.test.ReactiveIzanamiMockHelper.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

class ReactiveIzanamiMockHelperTest {

    @Test
    void mockServiceShouldBeConnected() {
        ReactiveIzanamiService service = mockReactiveIzanamiService();
        assertThat(service.isConnected()).isTrue();
    }

    @Test
    void mockServiceShouldReturnEmptyClient() {
        ReactiveIzanamiService service = mockReactiveIzanamiService();
        assertThat(service.unwrapClient()).isEmpty();
    }

    @Test
    void mockServiceShouldReturnCompletedWhenLoaded() {
        ReactiveIzanamiService service = mockReactiveIzanamiService();
        StepVerifier.create(service.whenLoaded()).verifyComplete();
    }

    // --- Boolean flag ---

    @Test
    void givenFlagKeyShouldStubBooleanValue() {
        ReactiveIzanamiService service = mockReactiveIzanamiService();
        givenFlagKey(service, "uuid-1").willReturn(true);

        StepVerifier.create(service.forFlagKey("uuid-1").booleanValue())
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void givenFlagKeyShouldStubBooleanValueDetails() {
        ReactiveIzanamiService service = mockReactiveIzanamiService();
        givenFlagKey(service, "uuid-1").willReturn(true);

        StepVerifier.create(service.forFlagKey("uuid-1").booleanValueDetails())
                .assertNext(details -> {
                    assertThat(details.value()).isTrue();
                    assertThat(details.metadata()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    void givenFlagNameShouldStubBooleanValue() {
        ReactiveIzanamiService service = mockReactiveIzanamiService();
        givenFlagName(service, "my-feature").willReturn(false);

        StepVerifier.create(service.forFlagName("my-feature").booleanValue())
                .expectNext(false)
                .verifyComplete();
    }

    // --- String flag ---

    @Test
    void givenFlagKeyShouldStubStringValue() {
        ReactiveIzanamiService service = mockReactiveIzanamiService();
        givenFlagKey(service, "uuid-2").willReturn("premium");

        StepVerifier.create(service.forFlagKey("uuid-2").stringValue())
                .expectNext("premium")
                .verifyComplete();
    }

    @Test
    void givenFlagKeyShouldStubStringValueDetails() {
        ReactiveIzanamiService service = mockReactiveIzanamiService();
        givenFlagKey(service, "uuid-2").willReturn("premium");

        StepVerifier.create(service.forFlagKey("uuid-2").stringValueDetails())
                .assertNext(details -> assertThat(details.value()).isEqualTo("premium"))
                .verifyComplete();
    }

    // --- Number flag ---

    @Test
    void givenFlagKeyShouldStubNumberValue() {
        ReactiveIzanamiService service = mockReactiveIzanamiService();
        givenFlagKey(service, "uuid-3").willReturn(new BigDecimal("42"));

        StepVerifier.create(service.forFlagKey("uuid-3").numberValue())
                .assertNext(v -> assertThat(v).isEqualByComparingTo("42"))
                .verifyComplete();
    }

    // --- Failure ---

    @Test
    void willFailWithShouldMakeAllTerminalMethodsFail() {
        ReactiveIzanamiService service = mockReactiveIzanamiService();
        RuntimeException error = new RuntimeException("boom");
        givenFlagKey(service, "uuid-1").willFailWith(error);

        var builder = service.forFlagKey("uuid-1");
        StepVerifier.create(builder.booleanValue()).verifyErrorMessage("boom");
        StepVerifier.create(builder.stringValue()).verifyErrorMessage("boom");
        StepVerifier.create(builder.numberValue()).verifyErrorMessage("boom");
    }

    // --- Builder chaining ---

    @Test
    void builderShouldSupportChaining() {
        ReactiveIzanamiService service = mockReactiveIzanamiService();
        ReactiveFlagStubBuilder stub = givenFlagKey(service, "uuid-1");
        stub.willReturn(true);

        StepVerifier.create(
                service.forFlagKey("uuid-1")
                        .withUser("alice")
                        .withContext("prod")
                        .booleanValue()
        ).expectNext(true).verifyComplete();

        verify(stub.getBuilderMock()).withUser("alice");
        verify(stub.getBuilderMock()).withContext("prod");
    }

    // --- Batch ---

    @Test
    void givenFlagKeysShouldStubBatchResult() {
        ReactiveIzanamiService service = mockReactiveIzanamiService();
        BatchResult batchResult = BatchResultBuilder.create()
                .withBooleanFlag("f1", true)
                .withStringFlag("f2", "value")
                .build();

        givenFlagKeys(service, "f1", "f2").willReturn(batchResult);

        StepVerifier.create(service.forFlagKeys("f1", "f2").values())
                .assertNext(result -> {
                    assertThat(result.booleanValue("f1")).isTrue();
                    assertThat(result.stringValue("f2")).isEqualTo("value");
                })
                .verifyComplete();
    }

    @Test
    void givenFlagNamesShouldStubBatchResult() {
        ReactiveIzanamiService service = mockReactiveIzanamiService();
        BatchResult batchResult = BatchResultBuilder.create()
                .withBooleanFlag("feature-a", true)
                .build();

        givenFlagNames(service, "feature-a").willReturn(batchResult);

        StepVerifier.create(service.forFlagNames("feature-a").values())
                .assertNext(result -> assertThat(result.booleanValue("feature-a")).isTrue())
                .verifyComplete();
    }

    @Test
    void batchWillFailWithShouldMakeValuesFail() {
        ReactiveIzanamiService service = mockReactiveIzanamiService();
        RuntimeException error = new RuntimeException("batch boom");
        givenFlagKeys(service, "f1").willFailWith(error);

        StepVerifier.create(service.forFlagKeys("f1").values())
                .verifyErrorMessage("batch boom");
    }

    @Test
    void batchBuilderShouldSupportChaining() {
        ReactiveIzanamiService service = mockReactiveIzanamiService();
        BatchResult batchResult = BatchResultBuilder.create()
                .withBooleanFlag("f1", true)
                .build();
        ReactiveBatchFlagStubBuilder stub = givenFlagKeys(service, "f1");
        stub.willReturn(batchResult);

        StepVerifier.create(service.forFlagKeys("f1").withUser("alice").values())
                .assertNext(result -> assertThat(result.booleanValue("f1")).isTrue())
                .verifyComplete();

        verify(stub.getBuilderMock()).withUser("alice");
    }
}
