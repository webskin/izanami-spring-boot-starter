package fr.maif.izanami.spring.autoconfigure;

import fr.maif.izanami.spring.openfeature.ErrorStrategy;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;

/**
 * Spring Boot {@code @ConfigurationProperties} converter for {@link ErrorStrategy}.
 */
final class StringToErrorStrategyConverter implements Converter<String, ErrorStrategy> {
    @Override
    public ErrorStrategy convert(@NonNull String source) {
        return ErrorStrategy.fromString(source);
    }
}

