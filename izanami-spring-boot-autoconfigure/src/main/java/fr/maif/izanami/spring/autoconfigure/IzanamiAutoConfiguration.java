package fr.maif.izanami.spring.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfeature.sdk.FlagValueType;
import fr.maif.IzanamiClient;
import fr.maif.izanami.spring.openfeature.ErrorStrategy;
import fr.maif.izanami.spring.openfeature.FlagsProperties;
import fr.maif.izanami.spring.openfeature.api.ErrorStrategyFactory;
import fr.maif.izanami.spring.openfeature.api.FlagConfigService;
import fr.maif.izanami.spring.openfeature.internal.ErrorStrategyFactoryImpl;
import fr.maif.izanami.spring.openfeature.internal.FlagConfigServiceImpl;
import fr.maif.izanami.spring.service.IzanamiServiceImpl;
import fr.maif.izanami.spring.service.api.IzanamiService;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;

/**
 * Auto-configuration for Izanami core services and configuration properties.
 * <p>
 * This configuration is enabled by default when the Izanami client is on the classpath.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(IzanamiClient.class)
@ConditionalOnProperty(name = "izanami.enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureAfter(JacksonAutoConfiguration.class)
@EnableConfigurationProperties(IzanamiProperties.class)
public class IzanamiAutoConfiguration {

    /**
     * Create the default FlagsProperties bound to the {@code openfeature} prefix.
     * <p>
     * Applications can override this by defining their own {@code @Primary} bean
     * with a custom {@code @ConfigurationProperties} prefix.
     *
     * @return a FlagsProperties bound to {@code openfeature}
     */
    @Bean
    @ConditionalOnMissingBean
    @ConfigurationProperties(prefix = "openfeature")
    public FlagsProperties flagsProperties() {
        return new FlagsProperties();
    }

    /**
     * Create a default ObjectMapper if none is provided.
     * <p>
     * This ensures Izanami works even without Spring Boot's Jackson auto-configuration.
     *
     * @return a default ObjectMapper
     */
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    /**
     * Register the converter used to bind {@code openfeature.flags.<name>.valueType}.
     *
     * @return a String to {@link FlagValueType} converter
     */
    @Bean
    @ConfigurationPropertiesBinding
    public Converter<String, FlagValueType> stringToFlagValueTypeConverter() {
        return new StringToFlagValueTypeConverter();
    }

    /**
     * Register the converter used to bind {@code openfeature.flags.<name>.errorStrategy}.
     *
     * @return a String to {@link ErrorStrategy} converter
     */
    @Bean
    @ConfigurationPropertiesBinding
    public Converter<String, ErrorStrategy> stringToErrorStrategyConverter() {
        return new StringToErrorStrategyConverter();
    }

    /**
     * Bind scalar YAML values to {@code openfeature.flags.<name>.defaultValue}.
     * <p>
     * YAML scalar values are represented as a single-entry map under a reserved key, and later interpreted based on
     * {@code valueType}.
     *
     * @return a scalar-to-map converter
     */
    @Bean
    @ConfigurationPropertiesBinding
    public Converter<String, DefaultValueMap> stringToDefaultValueConverter() {
        return new StringToDefaultValueConverter();
    }

    /**
     * Bind scalar YAML booleans to {@code openfeature.flags.<name>.defaultValue}.
     *
     * @return a boolean-to-map converter
     */
    @Bean
    @ConfigurationPropertiesBinding
    public Converter<Boolean, DefaultValueMap> booleanToDefaultValueConverter() {
        return new BooleanToDefaultValueConverter();
    }

    /**
     * Bind scalar YAML numbers to {@code openfeature.flags.<name>.defaultValue}.
     *
     * @return a number-to-map converter
     */
    @Bean
    @ConfigurationPropertiesBinding
    public Converter<Number, DefaultValueMap> numberToDefaultValueConverter() {
        return new NumberToDefaultValueConverter();
    }

    /**
     * Create the error strategy factory used to translate flag configuration into Izanami client error strategies.
     *
     * @param objectMapperProvider Spring-managed {@link ObjectMapper} (optional)
     * @param beanFactory          Spring bean factory for callback bean lookup
     * @return an {@link ErrorStrategyFactory}
     */
    @Bean
    @ConditionalOnMissingBean
    public ErrorStrategyFactory errorStrategyFactory(
            ObjectProvider<ObjectMapper> objectMapperProvider,
            BeanFactory beanFactory
    ) {
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        return new ErrorStrategyFactoryImpl(objectMapper, beanFactory);
    }

    /**
     * Create the flag configuration service.
     *
     * @param flagsProperties      bound {@link FlagsProperties}
     * @param errorStrategyFactory factory for creating Izanami client error strategies
     * @return a {@link FlagConfigService}
     */
    @Bean
    @ConditionalOnMissingBean
    public FlagConfigService flagConfigService(FlagsProperties flagsProperties, ErrorStrategyFactory errorStrategyFactory) {
        return new FlagConfigServiceImpl(flagsProperties, errorStrategyFactory);
    }

    /**
     * Create the Izanami service.
     *
     * @param properties        Izanami properties
     * @param flagConfigService flag configuration service (used to preload ids)
     * @param objectMapper      Jackson ObjectMapper for JSON serialization
     * @return an {@link IzanamiService}
     */
    @Bean
    @ConditionalOnMissingBean
    public IzanamiService izanamiService(IzanamiProperties properties, FlagConfigService flagConfigService, ObjectMapper objectMapper) {
        return new IzanamiServiceImpl(properties, flagConfigService, objectMapper);
    }
}
