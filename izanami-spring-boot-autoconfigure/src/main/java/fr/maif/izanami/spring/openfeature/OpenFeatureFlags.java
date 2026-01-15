package fr.maif.izanami.spring.openfeature;

import org.springframework.beans.factory.annotation.Qualifier;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation for programmatically configured {@link FlagsProperties} beans.
 * <p>
 * Use this annotation on {@code @Bean} methods that return {@link FlagsProperties}
 * built using the fluent builder API. These beans will be collected and merged
 * with YAML-configured flags, with programmatic flags taking precedence.
 *
 * <pre>{@code
 * @Configuration
 * public class MyFlagsConfig {
 *
 *     @Bean
 *     @OpenFeatureFlags
 *     public FlagsProperties myFlags() {
 *         return FlagsProperties.builder()
 *             .flag("my-feature", flag -> flag
 *                 .key("abc-123-uuid")
 *                 .valueType(FlagValueType.BOOLEAN)
 *                 .defaultValue(false))
 *             .build();
 *     }
 * }
 * }</pre>
 *
 * <p>Multiple beans annotated with {@code @OpenFeatureFlags} are allowed and will be merged
 * in bean registration order (later beans override earlier ones for flags with the same name).
 *
 * @see FlagsProperties#builder()
 * @see FlagsPropertiesBuilder
 */
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Qualifier
public @interface OpenFeatureFlags {
}
