package fr.maif.izanami.spring.service;

import fr.maif.FeatureClientErrorStrategy;
import fr.maif.IzanamiClient;
import fr.maif.features.values.BooleanCastStrategy;
import fr.maif.izanami.spring.service.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Reactive implementation of {@link ReactiveIzanamiService}.
 * <p>
 * This is a thin adapter over the existing {@link IzanamiService}. It reactively resolves
 * user and context at subscription time, then delegates to the sync service with already-resolved
 * explicit values. This avoids duplicating builder/evaluator logic.
 */
public final class ReactiveIzanamiServiceImpl implements ReactiveIzanamiService {
    private static final Logger log = LoggerFactory.getLogger(ReactiveIzanamiServiceImpl.class);

    private final IzanamiService delegate;
    private final ReactiveContextResolver contextResolver;
    private final ReactiveUserResolver userResolver;

    public ReactiveIzanamiServiceImpl(
            IzanamiService delegate,
            ReactiveContextResolver contextResolver,
            ReactiveUserResolver userResolver) {
        this.delegate = delegate;
        this.contextResolver = contextResolver;
        this.userResolver = userResolver;
    }

    @Override
    public Optional<IzanamiClient> unwrapClient() {
        return delegate.unwrapClient();
    }

    @Override
    public Mono<Void> whenLoaded() {
        return Mono.fromFuture(delegate.whenLoaded());
    }

    @Override
    public boolean isConnected() {
        return delegate.isConnected();
    }

    @Override
    public ReactiveFeatureRequestBuilder forFlagKey(String flagKey) {
        log.debug("Building reactive feature request for flag key: {}", flagKey);
        return new ReactiveFeatureRequestBuilderImpl(flagKey, true);
    }

    @Override
    public ReactiveFeatureRequestBuilder forFlagName(String flagName) {
        log.debug("Building reactive feature request for flag name: {}", flagName);
        return new ReactiveFeatureRequestBuilderImpl(flagName, false);
    }

    @Override
    public ReactiveBatchFeatureRequestBuilder forFlagKeys(String... flagKeys) {
        log.debug("Building reactive batch feature request for {} keys", flagKeys.length);
        return new ReactiveBatchFeatureRequestBuilderImpl(flagKeys.clone(), true);
    }

    @Override
    public ReactiveBatchFeatureRequestBuilder forFlagNames(String... flagNames) {
        log.debug("Building reactive batch feature request for {} names", flagNames.length);
        return new ReactiveBatchFeatureRequestBuilderImpl(flagNames.clone(), false);
    }

    // =====================================================================
    // Helper: resolve user/context as Optional-wrapped Mono
    // =====================================================================

    private Mono<Optional<String>> resolveUser(@Nullable String explicitUser) {
        return userResolver.resolve(explicitUser)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());
    }

    private Mono<Optional<String>> resolveContext(@Nullable String explicitContext) {
        return contextResolver.resolve(explicitContext)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());
    }

    // =====================================================================
    // Reactive Feature Request Builder
    // =====================================================================

    private final class ReactiveFeatureRequestBuilderImpl implements ReactiveFeatureRequestBuilder {
        private final String flagIdentifier;
        private final boolean isKey;
        private String user;
        private String context;
        private boolean ignoreCache = false;
        private Duration callTimeout = null;
        private String payload = null;
        private BooleanCastStrategy booleanCastStrategy = BooleanCastStrategy.LAX;
        private FeatureClientErrorStrategy<?> errorStrategy = null;

        private ReactiveFeatureRequestBuilderImpl(String flagIdentifier, boolean isKey) {
            this.flagIdentifier = flagIdentifier;
            this.isKey = isKey;
        }

        @Override
        public ReactiveFeatureRequestBuilder withUser(@Nullable String user) {
            this.user = user;
            return this;
        }

        @Override
        public ReactiveFeatureRequestBuilder withContext(@Nullable String context) {
            this.context = context;
            return this;
        }

        @Override
        public ReactiveFeatureRequestBuilder ignoreCache(boolean ignoreCache) {
            this.ignoreCache = ignoreCache;
            return this;
        }

        @Override
        public ReactiveFeatureRequestBuilder withCallTimeout(@Nullable Duration timeout) {
            this.callTimeout = timeout;
            return this;
        }

        @Override
        public ReactiveFeatureRequestBuilder withPayload(@Nullable String payload) {
            this.payload = payload;
            return this;
        }

        @Override
        public ReactiveFeatureRequestBuilder withBooleanCastStrategy(BooleanCastStrategy strategy) {
            this.booleanCastStrategy = strategy;
            return this;
        }

        @Override
        public ReactiveFeatureRequestBuilder withErrorStrategy(@Nullable FeatureClientErrorStrategy<?> errorStrategy) {
            this.errorStrategy = errorStrategy;
            return this;
        }

        @Override
        public Mono<Boolean> booleanValue() {
            return booleanValueDetails().map(ResultValueWithDetails::value);
        }

        @Override
        public Mono<String> stringValue() {
            return stringValueDetails().map(ResultValueWithDetails::value);
        }

        @Override
        public Mono<BigDecimal> numberValue() {
            return numberValueDetails().map(ResultValueWithDetails::value);
        }

        @Override
        public Mono<ResultValueWithDetails<Boolean>> booleanValueDetails() {
            return resolveAndDelegate(syncBuilder -> syncBuilder.booleanValueDetails());
        }

        @Override
        public Mono<ResultValueWithDetails<String>> stringValueDetails() {
            return resolveAndDelegate(syncBuilder -> syncBuilder.stringValueDetails());
        }

        @Override
        public Mono<ResultValueWithDetails<BigDecimal>> numberValueDetails() {
            return resolveAndDelegate(syncBuilder -> syncBuilder.numberValueDetails());
        }

        private <T> Mono<T> resolveAndDelegate(
                java.util.function.Function<FeatureRequestBuilder, CompletableFuture<T>> terminal) {
            // Mono.defer ensures the reactive user/context resolution and sync builder
            // creation happen lazily at subscription time, not at assembly time. Without it,
            // the Mono.zip would execute eagerly when the method is called, defeating the
            // purpose of reactive context propagation (e.g. reading from Reactor Context
            // or SecurityContext which are only available during subscription).
            return Mono.defer(() -> {
                Mono<Optional<String>> userMono = resolveUser(this.user);
                Mono<Optional<String>> ctxMono = resolveContext(this.context);

                return Mono.zip(userMono, ctxMono)
                        .flatMap(tuple -> {
                            String resolvedUser = tuple.getT1().orElse(null);
                            String resolvedCtx = tuple.getT2().orElse(null);

                            FeatureRequestBuilder syncBuilder = isKey
                                    ? delegate.forFlagKey(flagIdentifier)
                                    : delegate.forFlagName(flagIdentifier);

                            configureBuilder(syncBuilder, resolvedUser, resolvedCtx);
                            return Mono.fromFuture(terminal.apply(syncBuilder));
                        });
            });
        }

        private void configureBuilder(FeatureRequestBuilder builder,
                                       @Nullable String resolvedUser,
                                       @Nullable String resolvedCtx) {
            builder.withUser(resolvedUser)
                    .withContext(resolvedCtx)
                    .ignoreCache(ignoreCache)
                    .withCallTimeout(callTimeout)
                    .withPayload(payload)
                    .withBooleanCastStrategy(booleanCastStrategy)
                    .withErrorStrategy(errorStrategy);
        }
    }

    // =====================================================================
    // Reactive Batch Feature Request Builder
    // =====================================================================

    private final class ReactiveBatchFeatureRequestBuilderImpl implements ReactiveBatchFeatureRequestBuilder {
        private final String[] flagIdentifiers;
        private final boolean isKeys;
        private String user;
        private String context;
        private boolean ignoreCache = false;
        private Duration callTimeout = null;
        private String payload = null;
        private BooleanCastStrategy booleanCastStrategy = BooleanCastStrategy.LAX;
        private FeatureClientErrorStrategy<?> errorStrategy = null;

        private ReactiveBatchFeatureRequestBuilderImpl(String[] flagIdentifiers, boolean isKeys) {
            this.flagIdentifiers = flagIdentifiers;
            this.isKeys = isKeys;
        }

        @Override
        public ReactiveBatchFeatureRequestBuilder withUser(@Nullable String user) {
            this.user = user;
            return this;
        }

        @Override
        public ReactiveBatchFeatureRequestBuilder withContext(@Nullable String context) {
            this.context = context;
            return this;
        }

        @Override
        public ReactiveBatchFeatureRequestBuilder ignoreCache(boolean ignoreCache) {
            this.ignoreCache = ignoreCache;
            return this;
        }

        @Override
        public ReactiveBatchFeatureRequestBuilder withCallTimeout(@Nullable Duration timeout) {
            this.callTimeout = timeout;
            return this;
        }

        @Override
        public ReactiveBatchFeatureRequestBuilder withPayload(@Nullable String payload) {
            this.payload = payload;
            return this;
        }

        @Override
        public ReactiveBatchFeatureRequestBuilder withBooleanCastStrategy(BooleanCastStrategy strategy) {
            this.booleanCastStrategy = strategy;
            return this;
        }

        @Override
        public ReactiveBatchFeatureRequestBuilder withErrorStrategy(@Nullable FeatureClientErrorStrategy<?> errorStrategy) {
            this.errorStrategy = errorStrategy;
            return this;
        }

        @Override
        public Mono<BatchResult> values() {
            return Mono.defer(() -> {
                Mono<Optional<String>> userMono = resolveUser(this.user);
                Mono<Optional<String>> ctxMono = resolveContext(this.context);

                return Mono.zip(userMono, ctxMono)
                        .flatMap(tuple -> {
                            String resolvedUser = tuple.getT1().orElse(null);
                            String resolvedCtx = tuple.getT2().orElse(null);

                            BatchFeatureRequestBuilder syncBuilder = isKeys
                                    ? delegate.forFlagKeys(flagIdentifiers)
                                    : delegate.forFlagNames(flagIdentifiers);

                            syncBuilder.withUser(resolvedUser)
                                    .withContext(resolvedCtx)
                                    .ignoreCache(ignoreCache)
                                    .withCallTimeout(callTimeout)
                                    .withPayload(payload)
                                    .withBooleanCastStrategy(booleanCastStrategy)
                                    .withErrorStrategy(errorStrategy);

                            return Mono.fromFuture(syncBuilder.values());
                        });
            });
        }
    }
}
