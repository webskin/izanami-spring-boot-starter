package fr.maif.izanami.spring.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.maif.IzanamiClient;
import fr.maif.features.values.BooleanCastStrategy;
import org.springframework.lang.Nullable;

import java.time.Duration;

/**
 * Common evaluation parameters shared by single-flag and batch evaluators.
 * <p>
 * Package-private and internal to the service layer.
 */
class BaseEvaluationParams {
    @Nullable
    private final IzanamiClient client;
    private final ObjectMapper objectMapper;
    @Nullable
    private final String user;
    @Nullable
    private final String context;
    private final boolean ignoreCache;
    @Nullable
    private final Duration callTimeout;
    @Nullable
    private final String payload;
    private final BooleanCastStrategy booleanCastStrategy;

    BaseEvaluationParams(
        @Nullable IzanamiClient client,
        ObjectMapper objectMapper,
        @Nullable String user,
        @Nullable String context,
        boolean ignoreCache,
        @Nullable Duration callTimeout,
        @Nullable String payload,
        BooleanCastStrategy booleanCastStrategy
    ) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.user = user;
        this.context = context;
        this.ignoreCache = ignoreCache;
        this.callTimeout = callTimeout;
        this.payload = payload;
        this.booleanCastStrategy = booleanCastStrategy;
    }

    @Nullable
    IzanamiClient client() {
        return client;
    }

    ObjectMapper objectMapper() {
        return objectMapper;
    }

    @Nullable
    String user() {
        return user;
    }

    @Nullable
    String context() {
        return context;
    }

    boolean ignoreCache() {
        return ignoreCache;
    }

    @Nullable
    Duration callTimeout() {
        return callTimeout;
    }

    @Nullable
    String payload() {
        return payload;
    }

    BooleanCastStrategy booleanCastStrategy() {
        return booleanCastStrategy;
    }
}
