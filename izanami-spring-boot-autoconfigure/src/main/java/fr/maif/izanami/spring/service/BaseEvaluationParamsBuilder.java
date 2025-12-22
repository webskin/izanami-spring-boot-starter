package fr.maif.izanami.spring.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.maif.IzanamiClient;
import fr.maif.features.values.BooleanCastStrategy;
import org.springframework.lang.Nullable;

import java.time.Duration;
import java.util.Objects;

/**
 * Base builder for evaluation params shared by feature and batch evaluators.
 */
class BaseEvaluationParamsBuilder<T extends BaseEvaluationParamsBuilder<T>> {
    @Nullable
    protected IzanamiClient client;
    protected ObjectMapper objectMapper;
    @Nullable
    protected String user;
    @Nullable
    protected String context;
    protected boolean ignoreCache;
    @Nullable
    protected Duration callTimeout;
    @Nullable
    protected String payload;
    protected BooleanCastStrategy booleanCastStrategy = BooleanCastStrategy.LAX;

    @SuppressWarnings("unchecked")
    protected T self() {
        return (T) this;
    }

    T client(@Nullable IzanamiClient client) {
        this.client = client;
        return self();
    }

    T objectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        return self();
    }

    T user(@Nullable String user) {
        this.user = user;
        return self();
    }

    T context(@Nullable String context) {
        this.context = context;
        return self();
    }

    T ignoreCache(boolean ignoreCache) {
        this.ignoreCache = ignoreCache;
        return self();
    }

    T callTimeout(@Nullable Duration callTimeout) {
        this.callTimeout = callTimeout;
        return self();
    }

    T payload(@Nullable String payload) {
        this.payload = payload;
        return self();
    }

    T booleanCastStrategy(BooleanCastStrategy booleanCastStrategy) {
        this.booleanCastStrategy = booleanCastStrategy;
        return self();
    }

    BaseEvaluationParams buildBase() {
        return new BaseEvaluationParams(
            client,
            Objects.requireNonNull(objectMapper, "objectMapper"),
            user,
            context,
            ignoreCache,
            callTimeout,
            payload,
            Objects.requireNonNull(booleanCastStrategy, "booleanCastStrategy")
        );
    }
}
