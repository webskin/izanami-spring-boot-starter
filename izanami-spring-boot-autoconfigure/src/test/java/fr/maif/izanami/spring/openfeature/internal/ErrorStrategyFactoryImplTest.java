package fr.maif.izanami.spring.openfeature.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfeature.sdk.FlagValueType;
import fr.maif.FeatureClientErrorStrategy;
import fr.maif.errors.IzanamiError;
import fr.maif.izanami.spring.openfeature.ErrorStrategy;
import fr.maif.izanami.spring.openfeature.api.IzanamiErrorCallback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ErrorStrategyFactoryImpl}.
 */
class ErrorStrategyFactoryImplTest {

    private ObjectMapper objectMapper;
    private BeanFactory beanFactory;
    private ErrorStrategyFactoryImpl factory;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        beanFactory = mock(BeanFactory.class);
        factory = new ErrorStrategyFactoryImpl(objectMapper, beanFactory);
    }

    @Nested
    class DefaultValueStrategy {

        @Test
        void createsBooleanDefaultValueStrategy() {
            FeatureClientErrorStrategy<?> strategy = factory.createErrorStrategy(
                ErrorStrategy.DEFAULT_VALUE,
                FlagValueType.BOOLEAN,
                true,
                null,
                "test-flag"
            );

            assertThat(strategy).isInstanceOf(FeatureClientErrorStrategy.DefaultValueStrategy.class);
            // Verify the strategy returns the default value on error
            Boolean result = strategy.handleError(new IzanamiError("test error")).join();
            assertThat(result).isTrue();
        }

        @Test
        void createsStringDefaultValueStrategy() {
            FeatureClientErrorStrategy<?> strategy = factory.createErrorStrategy(
                ErrorStrategy.DEFAULT_VALUE,
                FlagValueType.STRING,
                "fallback",
                null,
                "test-flag"
            );

            assertThat(strategy).isInstanceOf(FeatureClientErrorStrategy.DefaultValueStrategy.class);
            String result = strategy.handleErrorForString(new IzanamiError("test error")).join();
            assertThat(result).isEqualTo("fallback");
        }

        @Test
        void createsNumberDefaultValueStrategy() {
            FeatureClientErrorStrategy<?> strategy = factory.createErrorStrategy(
                ErrorStrategy.DEFAULT_VALUE,
                FlagValueType.DOUBLE,
                42.5,
                null,
                "test-flag"
            );

            assertThat(strategy).isInstanceOf(FeatureClientErrorStrategy.DefaultValueStrategy.class);
            BigDecimal result = strategy.handleErrorForNumber(new IzanamiError("test error")).join();
            assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(42.5));
        }

        @Test
        void createsIntegerDefaultValueStrategy() {
            FeatureClientErrorStrategy<?> strategy = factory.createErrorStrategy(
                ErrorStrategy.DEFAULT_VALUE,
                FlagValueType.INTEGER,
                100,
                null,
                "test-flag"
            );

            assertThat(strategy).isInstanceOf(FeatureClientErrorStrategy.DefaultValueStrategy.class);
            BigDecimal result = strategy.handleErrorForNumber(new IzanamiError("test error")).join();
            assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(100));
        }

        @Test
        void createsObjectDefaultValueStrategy_serializesToJson() {
            Map<String, Object> objectDefault = Map.of("key", "value", "count", 42);
            FeatureClientErrorStrategy<?> strategy = factory.createErrorStrategy(
                ErrorStrategy.DEFAULT_VALUE,
                FlagValueType.OBJECT,
                objectDefault,
                null,
                "test-flag"
            );

            String result = strategy.handleErrorForString(new IzanamiError("test error")).join();
            assertThat(result).contains("\"key\"").contains("\"value\"").contains("42");
        }

        @Test
        void handlesNullDefaultValue() {
            FeatureClientErrorStrategy<?> strategy = factory.createErrorStrategy(
                ErrorStrategy.DEFAULT_VALUE,
                FlagValueType.BOOLEAN,
                null,
                null,
                "test-flag"
            );

            Boolean result = strategy.handleError(new IzanamiError("test error")).join();
            assertThat(result).isFalse();
        }

        @Test
        void handlesBigDecimalDefaultValue() {
            FeatureClientErrorStrategy<?> strategy = factory.createErrorStrategy(
                ErrorStrategy.DEFAULT_VALUE,
                FlagValueType.DOUBLE,
                new BigDecimal("123.456"),
                null,
                "test-flag"
            );

            BigDecimal result = strategy.handleErrorForNumber(new IzanamiError("test error")).join();
            assertThat(result).isEqualByComparingTo(new BigDecimal("123.456"));
        }

        @Test
        void handlesStringNumberDefaultValue() {
            FeatureClientErrorStrategy<?> strategy = factory.createErrorStrategy(
                ErrorStrategy.DEFAULT_VALUE,
                FlagValueType.DOUBLE,
                "99.99",
                null,
                "test-flag"
            );

            BigDecimal result = strategy.handleErrorForNumber(new IzanamiError("test error")).join();
            assertThat(result).isEqualByComparingTo(new BigDecimal("99.99"));
        }
    }

    @Nested
    class NullValueStrategy {

        @Test
        void createsNullValueStrategy() {
            FeatureClientErrorStrategy<?> strategy = factory.createErrorStrategy(
                ErrorStrategy.NULL_VALUE,
                FlagValueType.STRING,
                "ignored",
                null,
                "test-flag"
            );

            assertThat(strategy).isInstanceOf(FeatureClientErrorStrategy.NullValueStrategy.class);
        }
    }

    @Nested
    class FailStrategy {

        @Test
        void createsFailStrategy() {
            FeatureClientErrorStrategy<?> strategy = factory.createErrorStrategy(
                ErrorStrategy.FAIL,
                FlagValueType.BOOLEAN,
                null,
                null,
                "test-flag"
            );

            assertThat(strategy).isInstanceOf(FeatureClientErrorStrategy.FailStrategy.class);
        }
    }

    @Nested
    class CallbackStrategy {

        @Test
        void createsCallbackStrategyWithValidBean() {
            IzanamiErrorCallback callback = mock(IzanamiErrorCallback.class);
            when(beanFactory.getBean("myCallback")).thenReturn(callback);
            when(callback.onError(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(true));

            FeatureClientErrorStrategy<?> strategy = factory.createErrorStrategy(
                ErrorStrategy.CALLBACK,
                FlagValueType.BOOLEAN,
                null,
                "myCallback",
                "test-flag"
            );

            assertThat(strategy).isInstanceOf(FeatureClientErrorStrategy.CallbackStrategy.class);
            Boolean result = strategy.handleError(new IzanamiError("test")).join();
            assertThat(result).isTrue();
            verify(callback).onError(any(), eq("test-flag"), eq(FlagValueType.BOOLEAN), eq(FlagValueType.BOOLEAN));
        }

        @Test
        void createsCallbackStrategyWithMissingBean_usesDefaults() {
            when(beanFactory.getBean("missingBean"))
                .thenThrow(new NoSuchBeanDefinitionException("missingBean"));

            FeatureClientErrorStrategy<?> strategy = factory.createErrorStrategy(
                ErrorStrategy.CALLBACK,
                FlagValueType.BOOLEAN,
                true,
                "missingBean",
                "test-flag"
            );

            // Should fall back to default value
            Boolean result = strategy.handleError(new IzanamiError("test")).join();
            assertThat(result).isTrue();
        }

        @Test
        void createsCallbackStrategyWithInvalidBeanType_usesDefaults() {
            when(beanFactory.getBean("invalidBean")).thenReturn("not a callback");

            FeatureClientErrorStrategy<?> strategy = factory.createErrorStrategy(
                ErrorStrategy.CALLBACK,
                FlagValueType.STRING,
                "fallback",
                "invalidBean",
                "test-flag"
            );

            // Should fall back to default value
            String result = strategy.handleErrorForString(new IzanamiError("test")).join();
            assertThat(result).isEqualTo("fallback");
        }

        @Test
        void createsCallbackStrategyWithNullBean_usesDefaults() {
            FeatureClientErrorStrategy<?> strategy = factory.createErrorStrategy(
                ErrorStrategy.CALLBACK,
                FlagValueType.BOOLEAN,
                false,
                null,
                "test-flag"
            );

            Boolean result = strategy.handleError(new IzanamiError("test")).join();
            assertThat(result).isFalse();
        }

        @Test
        void createsCallbackStrategyWithBlankBean_usesDefaults() {
            FeatureClientErrorStrategy<?> strategy = factory.createErrorStrategy(
                ErrorStrategy.CALLBACK,
                FlagValueType.BOOLEAN,
                true,
                "   ",
                "test-flag"
            );

            Boolean result = strategy.handleError(new IzanamiError("test")).join();
            assertThat(result).isTrue();
        }

        @Test
        void callbackReturnsString_coercesToBoolean() {
            IzanamiErrorCallback callback = mock(IzanamiErrorCallback.class);
            when(beanFactory.getBean("myCallback")).thenReturn(callback);
            when(callback.onError(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture("true"));

            FeatureClientErrorStrategy<?> strategy = factory.createErrorStrategy(
                ErrorStrategy.CALLBACK,
                FlagValueType.BOOLEAN,
                null,
                "myCallback",
                "test-flag"
            );

            Boolean result = strategy.handleError(new IzanamiError("test")).join();
            assertThat(result).isTrue();
        }

        @Test
        void callbackReturnsNumber_coercesToBoolean() {
            IzanamiErrorCallback callback = mock(IzanamiErrorCallback.class);
            when(beanFactory.getBean("myCallback")).thenReturn(callback);
            when(callback.onError(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(1));

            FeatureClientErrorStrategy<?> strategy = factory.createErrorStrategy(
                ErrorStrategy.CALLBACK,
                FlagValueType.BOOLEAN,
                null,
                "myCallback",
                "test-flag"
            );

            Boolean result = strategy.handleError(new IzanamiError("test")).join();
            assertThat(result).isTrue();
        }

        @Test
        void callbackReturnsZero_coercesToFalse() {
            IzanamiErrorCallback callback = mock(IzanamiErrorCallback.class);
            when(beanFactory.getBean("myCallback")).thenReturn(callback);
            when(callback.onError(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(0));

            FeatureClientErrorStrategy<?> strategy = factory.createErrorStrategy(
                ErrorStrategy.CALLBACK,
                FlagValueType.BOOLEAN,
                null,
                "myCallback",
                "test-flag"
            );

            Boolean result = strategy.handleError(new IzanamiError("test")).join();
            assertThat(result).isFalse();
        }

        @Test
        void callbackReturnsObject_coercesToJsonString() {
            IzanamiErrorCallback callback = mock(IzanamiErrorCallback.class);
            when(beanFactory.getBean("myCallback")).thenReturn(callback);
            when(callback.onError(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(Map.of("key", "value")));

            FeatureClientErrorStrategy<?> strategy = factory.createErrorStrategy(
                ErrorStrategy.CALLBACK,
                FlagValueType.STRING,
                null,
                "myCallback",
                "test-flag"
            );

            String result = strategy.handleErrorForString(new IzanamiError("test")).join();
            assertThat(result).contains("\"key\"").contains("\"value\"");
        }

        @Test
        void callbackReturnsInvalidNumber_coercesToZero() {
            IzanamiErrorCallback callback = mock(IzanamiErrorCallback.class);
            when(beanFactory.getBean("myCallback")).thenReturn(callback);
            when(callback.onError(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture("not-a-number"));

            FeatureClientErrorStrategy<?> strategy = factory.createErrorStrategy(
                ErrorStrategy.CALLBACK,
                FlagValueType.DOUBLE,
                null,
                "myCallback",
                "test-flag"
            );

            BigDecimal result = strategy.handleErrorForNumber(new IzanamiError("test")).join();
            assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void callbackReturnsNull_coercesToEmptyString() {
            IzanamiErrorCallback callback = mock(IzanamiErrorCallback.class);
            when(beanFactory.getBean("myCallback")).thenReturn(callback);
            when(callback.onError(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

            FeatureClientErrorStrategy<?> strategy = factory.createErrorStrategy(
                ErrorStrategy.CALLBACK,
                FlagValueType.STRING,
                null,
                "myCallback",
                "test-flag"
            );

            String result = strategy.handleErrorForString(new IzanamiError("test")).join();
            assertThat(result).isEmpty();
        }
    }
}
