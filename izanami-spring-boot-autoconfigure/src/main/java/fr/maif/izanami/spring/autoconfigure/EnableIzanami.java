package fr.maif.izanami.spring.autoconfigure;

import org.springframework.boot.autoconfigure.ImportAutoConfiguration;

import java.lang.annotation.*;

/**
 * Optional explicit opt-in annotation for Izanami + OpenFeature auto-configuration.
 * <p>
 * Auto-configuration is enabled by default when the starter is on the classpath.
 * Use this annotation when you prefer to explicitly import the auto-configuration from a configuration class.
 * <p>
 * Note: {@code izanami.enabled=false} still disables all beans, even when this annotation is present.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ImportAutoConfiguration({
    IzanamiAutoConfiguration.class,
    OpenFeatureAutoConfiguration.class
})
public @interface EnableIzanami {
}

