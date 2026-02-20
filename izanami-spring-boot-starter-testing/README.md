# Izanami Spring Boot Starter Testing

Mockito testing utilities for [Izanami Spring Boot Starter](../README.md). Eliminates the boilerplate of mocking the fluent builder API (`forFlagKey().withUser().booleanValue()`).

## Dependency

```xml
<dependency>
    <groupId>fr.maif</groupId>
    <artifactId>izanami-spring-boot-starter-testing</artifactId>
    <version>${izanami-starter.version}</version>
    <scope>test</scope>
</dependency>
```

## Usage

### Single flag stubs

```java
import static fr.maif.izanami.spring.test.IzanamiMockHelper.*;

IzanamiService service = mockIzanamiService();

// One-liner per flag
givenFlagKey(service, "uuid-1").willReturn(true);
givenFlagName(service, "tier").willReturn("premium");
givenFlagKey(service, "score").willReturn(new BigDecimal("42"));

// Use in tests
Boolean enabled = service.forFlagKey("uuid-1")
    .withUser("alice")
    .booleanValue()
    .join();  // true
```

### Batch stubs

```java
givenFlagKeys(service, "f1", "f2").willReturn(
    BatchResultBuilder.create()
        .withBooleanFlag("f1", true)
        .withStringFlag("f2", "value")
        .build()
);

BatchResult result = service.forFlagKeys("f1", "f2").values().join();
```

### Failure stubs

```java
givenFlagKey(service, "uuid-1").willFailWith(new RuntimeException("boom"));
```

### Verify builder interactions

```java
FlagStubBuilder stub = givenFlagKey(service, "uuid-1");
stub.willReturn(true);

// ... code under test ...

verify(stub.getBuilderMock()).withUser("alice");
verify(stub.getBuilderMock()).withContext("production");
```

### Reactive (WebFlux)

```java
import static fr.maif.izanami.spring.test.ReactiveIzanamiMockHelper.*;

ReactiveIzanamiService service = mockReactiveIzanamiService();

givenFlagKey(service, "uuid-1").willReturn(true);
givenFlagName(service, "tier").willReturn("premium");

// Verify with StepVerifier
StepVerifier.create(service.forFlagKey("uuid-1").booleanValue())
    .expectNext(true)
    .verifyComplete();
```

### Mocking FlagConfigService

`FlagConfigService` has a simple `Optional`-based API that doesn't need helper utilities — plain Mockito works fine:

```java
FlagConfigService configService = mock(FlagConfigService.class);
when(configService.getFlagConfigByKey("uuid-1")).thenReturn(Optional.of(myFlagConfig));
when(configService.getFlagConfigByName("my-feature")).thenReturn(Optional.of(myFlagConfig));
when(configService.getAllFlagConfigs()).thenReturn(List.of(myFlagConfig));
```

## API Reference

| Class | Description |
|-------|-------------|
| `IzanamiMockHelper` | Static entry point: `mockIzanamiService()`, `givenFlagKey()`, `givenFlagName()`, `givenFlagKeys()`, `givenFlagNames()` |
| `ReactiveIzanamiMockHelper` | Reactive counterpart of `IzanamiMockHelper` |
| `FlagStubBuilder` | Configures single flag return values: `willReturn(bool/String/BigDecimal)`, `willFailWith()` |
| `ReactiveFlagStubBuilder` | Reactive counterpart of `FlagStubBuilder` |
| `BatchFlagStubBuilder` | Configures batch return values: `willReturn(BatchResult)`, `willFailWith()` |
| `ReactiveBatchFlagStubBuilder` | Reactive counterpart of `BatchFlagStubBuilder` |
| `BatchResultBuilder` | Builds test `BatchResult` instances: `withBooleanFlag()`, `withStringFlag()`, `withNumberFlag()` |
