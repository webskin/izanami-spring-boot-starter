package fr.maif.izanami.spring.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.maif.IzanamiClient;
import fr.maif.izanami.spring.openfeature.FlagConfig;
import fr.maif.izanami.spring.openfeature.ErrorStrategy;
import fr.maif.izanami.spring.openfeature.EvaluationValueType;
import fr.maif.izanami.spring.openfeature.FlagsProperties;
import fr.maif.izanami.spring.openfeature.api.ErrorStrategyFactory;
import fr.maif.izanami.spring.openfeature.api.FlagConfigService;
import fr.maif.izanami.spring.openfeature.internal.ErrorStrategyFactoryImpl;
import fr.maif.izanami.spring.openfeature.internal.FlagConfigServiceImpl;
import fr.maif.izanami.spring.service.IzanamiService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Auto-configuration for Izanami core services and configuration properties.
 * <p>
 * This configuration is enabled by default when the Izanami client is on the classpath.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(IzanamiClient.class)
@ConditionalOnProperty(name = "izanami.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({IzanamiProperties.class, FlagsProperties.class})
public class IzanamiAutoConfiguration {

    /**
     * Register the converter used to bind {@code openfeature.flags[*].valueType}.
     *
     * @return a String to {@link EvaluationValueType} converter
     */
    @Bean
    @ConfigurationPropertiesBinding
    public Converter<String, EvaluationValueType> stringToEvaluationValueTypeConverter() {
        return new StringToEvaluationValueTypeConverter();
    }

    /**
     * Register the converter used to bind {@code openfeature.flags[*].errorStrategy}.
     *
     * @return a String to {@link ErrorStrategy} converter
     */
    @Bean
    @ConfigurationPropertiesBinding
    public Converter<String, ErrorStrategy> stringToErrorStrategyConverter() {
        return new StringToErrorStrategyConverter();
    }

    /**
     * Bind scalar YAML values to {@code openfeature.flags[*].defaultValue}.
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
     * Bind scalar YAML booleans to {@code openfeature.flags[*].defaultValue}.
     *
     * @return a boolean-to-map converter
     */
    @Bean
    @ConfigurationPropertiesBinding
    public Converter<Boolean, DefaultValueMap> booleanToDefaultValueConverter() {
        return new BooleanToDefaultValueConverter();
    }

    /**
     * Bind scalar YAML numbers to {@code openfeature.flags[*].defaultValue}.
     *
     * @return a number-to-map converter
     */
    @Bean
    @ConfigurationPropertiesBinding
    public Converter<Number, DefaultValueMap> numberToDefaultValueConverter() {
        return new NumberToDefaultValueConverter();
    }

    /**
     * Create the flag configuration service.
     *
     * @param flagsProperties bound {@link FlagsProperties}
     * @return a {@link FlagConfigService}
     */
    @Bean
    @ConditionalOnMissingBean
    public FlagConfigService flagConfigService(FlagsProperties flagsProperties) {
        return new FlagConfigServiceImpl(flagsProperties);
    }

    /**
     * Create the error strategy factory used to translate flag configuration into Izanami client error strategies.
     *
     * @param objectMapperProvider Spring-managed {@link ObjectMapper} (optional)
     * @return an {@link ErrorStrategyFactory}
     */
    @Bean
    @ConditionalOnMissingBean
    public ErrorStrategyFactory errorStrategyFactory(ObjectProvider<ObjectMapper> objectMapperProvider) {
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        return new ErrorStrategyFactoryImpl(objectMapper);
    }

    /**
     * Create the Izanami service.
     *
     * @param properties        Izanami properties
     * @param flagConfigService flag configuration service (used to preload ids)
     * @return an {@link IzanamiService}
     */
    @Bean
    @ConditionalOnMissingBean
    public IzanamiService izanamiService(IzanamiProperties properties, FlagConfigService flagConfigService) {
        Set<String> idsToPreload = flagConfigService.getAllFlagConfigs().stream()
            .map(FlagConfig::id)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        return new IzanamiService(properties, idsToPreload);
    }
}
