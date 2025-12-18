package fr.maif.izanami.spring.service.api;

import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

public interface FeatureRequestBuilder {
    FeatureRequestBuilder withUser(@Nullable String user);

    FeatureRequestBuilder withContext(@Nullable String context);

    CompletableFuture<Boolean> booleanValue();

    CompletableFuture<String> stringValue();

    CompletableFuture<BigDecimal> numberValue();

    CompletableFuture<ResultValueWithDetails<Boolean>> booleanValueDetails();

    CompletableFuture<ResultValueWithDetails<String>> stringValueDetails();

    CompletableFuture<ResultValueWithDetails<BigDecimal>> numberValueDetails();
}
