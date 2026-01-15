package fr.maif.izanami.spring.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfeature.sdk.FlagValueType;
import fr.maif.IzanamiClient;
import fr.maif.izanami.spring.openfeature.ErrorStrategy;
import fr.maif.izanami.spring.openfeature.FlagsProperties;
import fr.maif.izanami.spring.openfeature.OpenFeatureFlags;
import fr.maif.izanami.spring.openfeature.api.ErrorStrategyFactory;
import fr.maif.izanami.spring.openfeature.api.FlagConfigService;
import fr.maif.izanami.spring.openfeature.internal.ErrorStrategyFactoryImpl;
import fr.maif.izanami.spring.openfeature.internal.FlagConfigServiceImpl;
import fr.maif.izanami.spring.openfeature.internal.RawFlagConfig;
import fr.maif.izanami.spring.service.CompositeContextResolver;
import fr.maif.izanami.spring.service.IzanamiServiceImpl;
import fr.maif.izanami.spring.service.UserResolver;
import fr.maif.izanami.spring.service.api.IzanamiService;
import fr.maif.izanami.spring.service.api.RootContextProvider;
import fr.maif.izanami.spring.service.api.SubContextResolver;
import fr.maif.izanami.spring.service.api.UserProvider;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
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
import org.springframework.context.annotation.Primary;
import org.springframework.core.convert.converter.Converter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Auto-configuration for Izanami core services and configuration properties.
 * <p>
 * This configuration is enabled by default when the Izanami client is on the classpath.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(IzanamiClient.class)
@ConditionalOnProperty(name = "izanami.enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureAfter(JacksonAutoConfiguration.class)
@EnableConfigurationProperties
public class IzanamiAutoConfiguration {

    /**
     * Bean name for IzanamiProperties.
     * <p>
     * Use this constant when defining a custom bean to override the default
     * {@code izanami} prefix.
     */
    public static final String IZANAMI_PROPERTIES_BEAN = "izanamiProperties";

    /**
     * Bean name for the YAML-bound FlagsProperties.
     * <p>
     * Use this constant when defining a custom bean to override the default
     * {@code openfeature} prefix.
     */
    public static final String FLAGS_PROPERTIES_BEAN = "flagsProperties";

    /**
     * Create the IzanamiProperties from {@code izanami} prefix.
     * <p>
     * To use a custom prefix, define a bean named {@code izanamiProperties}:
     * <pre>{@code
     * @Bean
     * @ConfigurationProperties(prefix = "custom.izanami")
     * public IzanamiProperties izanamiProperties() {
     *     return new IzanamiProperties();
     * }
     * }</pre>
     *
     * @return IzanamiProperties bound to {@code izanami}
     */
    @Bean(name = IZANAMI_PROPERTIES_BEAN)
    @ConditionalOnMissingBean(name = IZANAMI_PROPERTIES_BEAN)
    @ConfigurationProperties(prefix = "izanami")
    IzanamiProperties izanamiProperties() {
        return new IzanamiProperties();
    }

    /**
     * Create the YAML-bound FlagsProperties from {@code openfeature} prefix.
     * <p>
     * This bean is merged with programmatic flags in {@link #mergedFlagsProperties}.
     * <p>
     * To use a custom YAML prefix, define a bean with this exact name
     * ({@value FLAGS_PROPERTIES_BEAN}) and your custom prefix:
     * <pre>{@code
     * @Bean(name = IzanamiAutoConfiguration.FLAGS_PROPERTIES_BEAN)
     * @ConfigurationProperties(prefix = "custom.prefix")
     * public FlagsProperties flagsProperties() {
     *     return new FlagsProperties();
     * }
     * }</pre>
     *
     * @return a FlagsProperties bound to {@code openfeature}
     */
    @Bean(name = FLAGS_PROPERTIES_BEAN)
    @ConditionalOnMissingBean(name = FLAGS_PROPERTIES_BEAN)
    @ConfigurationProperties(prefix = "openfeature")
    FlagsProperties flagsProperties() {
        return new FlagsProperties();
    }

    /**
     * Create the merged FlagsProperties combining YAML and programmatic configurations.
     * <p>
     * Programmatic flags (beans annotated with {@link OpenFeatureFlags}) take precedence
     * over YAML-configured flags with the same name.
     *
     * @param yamlFlags          YAML-bound flags from {@code openfeature} prefix (or custom prefix)
     * @param programmaticFlags  programmatic flags from {@link OpenFeatureFlags} beans (optional)
     * @return merged FlagsProperties
     * @see OpenFeatureFlags
     * @see FlagsProperties#builder()
     */
    @Bean
    @Primary
    public FlagsProperties mergedFlagsProperties(
            @Qualifier(FLAGS_PROPERTIES_BEAN) FlagsProperties yamlFlags,
            @OpenFeatureFlags ObjectProvider<List<FlagsProperties>> programmaticFlags
    ) {
        Map<String, RawFlagConfig> merged = new LinkedHashMap<>(yamlFlags.getFlags());

        List<FlagsProperties> programmatic = programmaticFlags.getIfAvailable();
        if (programmatic != null) {
            for (FlagsProperties props : programmatic) {
                merged.putAll(props.getFlags());
            }
        }

        FlagsProperties result = new FlagsProperties();
        result.setFlags(merged);
        return result;
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
     * Create the default property-based RootContextProvider when no custom bean is present.
     *
     * @param properties Izanami properties containing root-context
     * @return a {@link RootContextProvider} reading from properties
     */
    @Bean
    @ConditionalOnMissingBean(RootContextProvider.class)
    RootContextProvider rootContextProvider(IzanamiProperties properties) {
        return new fr.maif.izanami.spring.service.PropertyBasedRootContextProvider(properties);
    }

    /**
     * Create the composite context resolver.
     * <p>
     * Uses ObjectProvider to safely handle optional and request-scoped beans.
     *
     * @param rootContextProvider provider for root context (optional, will use property-based default)
     * @param subContextResolver  resolver for sub-context (optional, typically request-scoped)
     * @return a {@link CompositeContextResolver}
     */
    @Bean
    CompositeContextResolver compositeContextResolver(
            ObjectProvider<RootContextProvider> rootContextProvider,
            ObjectProvider<SubContextResolver> subContextResolver
    ) {
        return new CompositeContextResolver(rootContextProvider, subContextResolver);
    }

    /**
     * Create the user resolver.
     * <p>
     * Uses ObjectProvider to safely handle optional and request-scoped beans.
     *
     * @param userProvider provider for user identifier (optional, typically request-scoped)
     * @return a {@link UserResolver}
     */
    @Bean
    UserResolver userResolver(ObjectProvider<UserProvider> userProvider) {
        return new UserResolver(userProvider);
    }

    /**
     * Create the Izanami service.
     *
     * @param properties        Izanami properties
     * @param flagConfigService flag configuration service (used to preload ids)
     * @param objectMapper      Jackson ObjectMapper for JSON serialization
     * @param contextResolver   composite context resolver for default context resolution
     * @param userResolver      user resolver for default user resolution
     * @return an {@link IzanamiService}
     */
    @Bean
    @ConditionalOnMissingBean
    public IzanamiService izanamiService(
            IzanamiProperties properties,
            FlagConfigService flagConfigService,
            ObjectMapper objectMapper,
            CompositeContextResolver contextResolver,
            UserResolver userResolver
    ) {
        return new IzanamiServiceImpl(properties, flagConfigService, objectMapper, contextResolver, userResolver);
    }
}
