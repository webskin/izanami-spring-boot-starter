package fr.maif.izanami.spring.openfeature.internal;

import dev.openfeature.sdk.*;
import fr.maif.FeatureClientErrorStrategy;
import fr.maif.izanami.spring.openfeature.ErrorStrategy;
import fr.maif.izanami.spring.openfeature.FlagConfig;
import fr.maif.izanami.spring.openfeature.ValueConverter;
import fr.maif.izanami.spring.openfeature.api.ExtendedOpenFeatureClientException;
import fr.maif.izanami.spring.openfeature.api.FlagConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ExtendedOpenFeatureClientImpl}.
 */
class ExtendedOpenFeatureClientImplTest {

    private Client delegate;
    private FlagConfigService flagConfigService;
    private ValueConverter valueConverter;
    private ExtendedOpenFeatureClientImpl client;

    @BeforeEach
    void setUp() {
        delegate = mock(Client.class);
        flagConfigService = mock(FlagConfigService.class);
        valueConverter = mock(ValueConverter.class);
        client = new ExtendedOpenFeatureClientImpl(delegate, flagConfigService, valueConverter);
    }

    private FlagConfig createFlagConfig(String key, String name, Object defaultValue) {
        return new FlagConfig(
            key,
            name,
            "Test flag",
            FlagValueType.BOOLEAN,
            ErrorStrategy.DEFAULT_VALUE,
            FeatureClientErrorStrategy.defaultValueStrategy(false, "", BigDecimal.ZERO),
            defaultValue,
            null
        );
    }

    private FlagConfig createFlagConfigWithType(String key, String name, FlagValueType type, Object defaultValue) {
        return new FlagConfig(
            key,
            name,
            "Test flag",
            type,
            ErrorStrategy.DEFAULT_VALUE,
            FeatureClientErrorStrategy.defaultValueStrategy(false, "", BigDecimal.ZERO),
            defaultValue,
            null
        );
    }

    private FlagConfig createFlagConfigWithFailStrategy(String key, String name) {
        return new FlagConfig(
            key,
            name,
            "Test flag",
            FlagValueType.BOOLEAN,
            ErrorStrategy.FAIL,
            FeatureClientErrorStrategy.failStrategy(),
            null,
            null
        );
    }

    @Nested
    class AutoComputedDefaultValue {

        @Test
        void getBooleanValue_autoDefault_returnsDelegateResult() {
            FlagConfig config = createFlagConfig("uuid-1", "feature-1", true);
            when(flagConfigService.getFlagConfigByKey("uuid-1")).thenReturn(Optional.of(config));
            when(delegate.getBooleanValue("uuid-1", true)).thenReturn(false);

            Boolean result = client.getBooleanValue("uuid-1");

            assertThat(result).isFalse();
            verify(delegate).getBooleanValue("uuid-1", true);
        }

        @Test
        void getBooleanValue_withContext_returnsDelegateResult() {
            FlagConfig config = createFlagConfig("uuid-1", "feature-1", true);
            when(flagConfigService.getFlagConfigByKey("uuid-1")).thenReturn(Optional.of(config));
            EvaluationContext ctx = new ImmutableContext();
            when(delegate.getBooleanValue("uuid-1", true, ctx)).thenReturn(true);

            Boolean result = client.getBooleanValue("uuid-1", ctx);

            assertThat(result).isTrue();
            verify(delegate).getBooleanValue("uuid-1", true, ctx);
        }

        @Test
        void getStringValue_autoDefault_returnsDelegateResult() {
            FlagConfig config = createFlagConfigWithType("uuid-1", "feature-1", FlagValueType.STRING, "default");
            when(flagConfigService.getFlagConfigByKey("uuid-1")).thenReturn(Optional.of(config));
            when(delegate.getStringValue("uuid-1", "default")).thenReturn("actual");

            String result = client.getStringValue("uuid-1");

            assertThat(result).isEqualTo("actual");
            verify(delegate).getStringValue("uuid-1", "default");
        }

        @Test
        void getIntegerValue_autoDefault_returnsDelegateResult() {
            FlagConfig config = createFlagConfigWithType("uuid-1", "feature-1", FlagValueType.INTEGER, 42);
            when(flagConfigService.getFlagConfigByKey("uuid-1")).thenReturn(Optional.of(config));
            when(delegate.getIntegerValue("uuid-1", 42)).thenReturn(100);

            Integer result = client.getIntegerValue("uuid-1");

            assertThat(result).isEqualTo(100);
            verify(delegate).getIntegerValue("uuid-1", 42);
        }

        @Test
        void getDoubleValue_autoDefault_returnsDelegateResult() {
            FlagConfig config = createFlagConfigWithType("uuid-1", "feature-1", FlagValueType.DOUBLE, 3.14);
            when(flagConfigService.getFlagConfigByKey("uuid-1")).thenReturn(Optional.of(config));
            when(delegate.getDoubleValue("uuid-1", 3.14)).thenReturn(2.71);

            Double result = client.getDoubleValue("uuid-1");

            assertThat(result).isEqualTo(2.71);
            verify(delegate).getDoubleValue("uuid-1", 3.14);
        }

        @Test
        void getObjectValue_autoDefault_returnsDelegateResult() {
            FlagConfig config = createFlagConfigWithType("uuid-1", "feature-1", FlagValueType.OBJECT, "{}");
            when(flagConfigService.getFlagConfigByKey("uuid-1")).thenReturn(Optional.of(config));
            Value defaultValue = new Value("{}");
            Value actualValue = new Value("result");
            when(valueConverter.objectToValue("{}")).thenReturn(defaultValue);
            when(delegate.getObjectValue("uuid-1", defaultValue)).thenReturn(actualValue);

            Value result = client.getObjectValue("uuid-1");

            assertThat(result).isEqualTo(actualValue);
            verify(delegate).getObjectValue("uuid-1", defaultValue);
        }

        @Test
        void getBooleanDetails_autoDefault_returnsDelegateResult() {
            FlagConfig config = createFlagConfig("uuid-1", "feature-1", true);
            when(flagConfigService.getFlagConfigByKey("uuid-1")).thenReturn(Optional.of(config));
            FlagEvaluationDetails<Boolean> details = FlagEvaluationDetails.<Boolean>builder()
                .flagKey("uuid-1")
                .value(false)
                .build();
            when(delegate.getBooleanDetails("uuid-1", true)).thenReturn(details);

            FlagEvaluationDetails<Boolean> result = client.getBooleanDetails("uuid-1");

            assertThat(result.getValue()).isFalse();
            verify(delegate).getBooleanDetails("uuid-1", true);
        }
    }

    @Nested
    class ValidationErrors {

        @Test
        void getBooleanValue_flagNotConfigured_throwsException() {
            when(flagConfigService.getFlagConfigByKey("unknown")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> client.getBooleanValue("unknown"))
                .isInstanceOf(ExtendedOpenFeatureClientException.class)
                .hasMessageContaining("not configured")
                .hasMessageContaining("unknown");
        }

        @Test
        void getBooleanValue_nonDefaultValueStrategy_throwsException() {
            FlagConfig config = createFlagConfigWithFailStrategy("uuid-1", "feature-1");
            when(flagConfigService.getFlagConfigByKey("uuid-1")).thenReturn(Optional.of(config));

            assertThatThrownBy(() -> client.getBooleanValue("uuid-1"))
                .isInstanceOf(ExtendedOpenFeatureClientException.class)
                .hasMessageContaining("errorStrategy")
                .hasMessageContaining("auto-computed defaultValue");
        }

        @Test
        void getBooleanValue_nullDefaultValue_throwsException() {
            FlagConfig config = createFlagConfig("uuid-1", "feature-1", null);
            when(flagConfigService.getFlagConfigByKey("uuid-1")).thenReturn(Optional.of(config));

            assertThatThrownBy(() -> client.getBooleanValue("uuid-1"))
                .isInstanceOf(ExtendedOpenFeatureClientException.class)
                .hasMessageContaining("no defaultValue configured");
        }

        @Test
        void getBooleanValue_wrongDefaultType_throwsException() {
            FlagConfig config = createFlagConfig("uuid-1", "feature-1", "not-a-boolean");
            when(flagConfigService.getFlagConfigByKey("uuid-1")).thenReturn(Optional.of(config));

            assertThatThrownBy(() -> client.getBooleanValue("uuid-1"))
                .isInstanceOf(ExtendedOpenFeatureClientException.class)
                .hasMessageContaining("type")
                .hasMessageContaining("Boolean");
        }

        @Test
        void getStringValue_wrongDefaultType_throwsException() {
            FlagConfig config = createFlagConfigWithType("uuid-1", "feature-1", FlagValueType.STRING, 123);
            when(flagConfigService.getFlagConfigByKey("uuid-1")).thenReturn(Optional.of(config));

            assertThatThrownBy(() -> client.getStringValue("uuid-1"))
                .isInstanceOf(ExtendedOpenFeatureClientException.class)
                .hasMessageContaining("type")
                .hasMessageContaining("String");
        }
    }

    @Nested
    class ByNameMethods {

        @Test
        void getBooleanValueByName_autoDefault_usesKeyForDelegate() {
            FlagConfig config = createFlagConfig("uuid-1", "my-feature", true);
            when(flagConfigService.getFlagConfigByName("my-feature")).thenReturn(Optional.of(config));
            when(delegate.getBooleanValue("uuid-1", true)).thenReturn(false);

            Boolean result = client.getBooleanValueByName("my-feature");

            assertThat(result).isFalse();
            verify(delegate).getBooleanValue("uuid-1", true);
        }

        @Test
        void getStringValueByName_autoDefault_usesKeyForDelegate() {
            FlagConfig config = createFlagConfigWithType("uuid-1", "my-feature", FlagValueType.STRING, "default");
            when(flagConfigService.getFlagConfigByName("my-feature")).thenReturn(Optional.of(config));
            when(delegate.getStringValue("uuid-1", "default")).thenReturn("result");

            String result = client.getStringValueByName("my-feature");

            assertThat(result).isEqualTo("result");
            verify(delegate).getStringValue("uuid-1", "default");
        }

        @Test
        void getBooleanValueByName_flagNotFound_throwsException() {
            when(flagConfigService.getFlagConfigByName("unknown")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> client.getBooleanValueByName("unknown"))
                .isInstanceOf(ExtendedOpenFeatureClientException.class)
                .hasMessageContaining("not configured")
                .hasMessageContaining("unknown");
        }

        @Test
        void getBooleanDetailsByName_returnsDetails() {
            FlagConfig config = createFlagConfig("uuid-1", "my-feature", true);
            when(flagConfigService.getFlagConfigByName("my-feature")).thenReturn(Optional.of(config));
            FlagEvaluationDetails<Boolean> details = FlagEvaluationDetails.<Boolean>builder()
                .flagKey("uuid-1")
                .value(true)
                .build();
            when(delegate.getBooleanDetails("uuid-1", true)).thenReturn(details);

            FlagEvaluationDetails<Boolean> result = client.getBooleanDetailsByName("my-feature");

            assertThat(result.getValue()).isTrue();
        }
    }

    @Nested
    class DelegatedMethods {

        @Test
        void getBooleanValue_withExplicitDefault_delegatesDirectly() {
            when(delegate.getBooleanValue("uuid-1", false)).thenReturn(true);

            Boolean result = client.getBooleanValue("uuid-1", false);

            assertThat(result).isTrue();
            verify(delegate).getBooleanValue("uuid-1", false);
            verifyNoInteractions(flagConfigService);
        }

        @Test
        void getStringValue_withExplicitDefault_delegatesDirectly() {
            when(delegate.getStringValue("uuid-1", "default")).thenReturn("result");

            String result = client.getStringValue("uuid-1", "default");

            assertThat(result).isEqualTo("result");
            verifyNoInteractions(flagConfigService);
        }

        @Test
        void getMetadata_delegatesToUnderlyingClient() {
            ClientMetadata metadata = mock(ClientMetadata.class);
            when(delegate.getMetadata()).thenReturn(metadata);

            ClientMetadata result = client.getMetadata();

            assertThat(result).isSameAs(metadata);
        }

        @Test
        void getEvaluationContext_delegatesToUnderlyingClient() {
            EvaluationContext ctx = new ImmutableContext();
            when(delegate.getEvaluationContext()).thenReturn(ctx);

            EvaluationContext result = client.getEvaluationContext();

            assertThat(result).isSameAs(ctx);
        }

        @Test
        void setEvaluationContext_delegatesAndReturnsThis() {
            EvaluationContext ctx = new ImmutableContext();

            Client result = client.setEvaluationContext(ctx);

            assertThat(result).isSameAs(client);
            verify(delegate).setEvaluationContext(ctx);
        }

        @Test
        void addHooks_delegatesAndReturnsThis() {
            Hook hook = mock(Hook.class);

            Client result = client.addHooks(hook);

            assertThat(result).isSameAs(client);
            verify(delegate).addHooks(hook);
        }

        @Test
        void getProviderState_delegatesToUnderlyingClient() {
            when(delegate.getProviderState()).thenReturn(ProviderState.READY);

            ProviderState result = client.getProviderState();

            assertThat(result).isEqualTo(ProviderState.READY);
        }

        @Test
        void track_delegatesToUnderlyingClient() {
            client.track("event-name");

            verify(delegate).track("event-name");
        }

        @Test
        void track_withContext_delegatesToUnderlyingClient() {
            EvaluationContext ctx = new ImmutableContext();

            client.track("event-name", ctx);

            verify(delegate).track("event-name", ctx);
        }
    }

    @Nested
    class ContextAndOptionsVariants {

        @Test
        void getBooleanValue_withContextAndOptions_passesAll() {
            FlagConfig config = createFlagConfig("uuid-1", "feature-1", true);
            when(flagConfigService.getFlagConfigByKey("uuid-1")).thenReturn(Optional.of(config));
            EvaluationContext ctx = new ImmutableContext();
            FlagEvaluationOptions options = FlagEvaluationOptions.builder().build();
            when(delegate.getBooleanValue("uuid-1", true, ctx, options)).thenReturn(false);

            Boolean result = client.getBooleanValue("uuid-1", ctx, options);

            assertThat(result).isFalse();
            verify(delegate).getBooleanValue("uuid-1", true, ctx, options);
        }

        @Test
        void getStringDetails_withContext_passesContext() {
            FlagConfig config = createFlagConfigWithType("uuid-1", "feature-1", FlagValueType.STRING, "default");
            when(flagConfigService.getFlagConfigByKey("uuid-1")).thenReturn(Optional.of(config));
            EvaluationContext ctx = new ImmutableContext();
            FlagEvaluationDetails<String> details = FlagEvaluationDetails.<String>builder()
                .flagKey("uuid-1")
                .value("result")
                .build();
            when(delegate.getStringDetails("uuid-1", "default", ctx)).thenReturn(details);

            FlagEvaluationDetails<String> result = client.getStringDetails("uuid-1", ctx);

            assertThat(result.getValue()).isEqualTo("result");
        }

        @Test
        void getIntegerValueByName_withContextAndOptions_passesAll() {
            FlagConfig config = createFlagConfigWithType("uuid-1", "feature-1", FlagValueType.INTEGER, 42);
            when(flagConfigService.getFlagConfigByName("feature-1")).thenReturn(Optional.of(config));
            EvaluationContext ctx = new ImmutableContext();
            FlagEvaluationOptions options = FlagEvaluationOptions.builder().build();
            when(delegate.getIntegerValue("uuid-1", 42, ctx, options)).thenReturn(100);

            Integer result = client.getIntegerValueByName("feature-1", ctx, options);

            assertThat(result).isEqualTo(100);
        }
    }
}
