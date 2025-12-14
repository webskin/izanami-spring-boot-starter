package fr.maif.izanami.spring.autoconfigure;

import dev.openfeature.sdk.FlagValueType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;

/**
 * Spring Boot {@code @ConfigurationProperties} converter for {@link FlagValueType}.
 */
final class StringToFlagValueTypeConverter implements Converter<String, FlagValueType> {
    @Override
    public FlagValueType convert(@NonNull String source) {
        return FlagValueTypeParser.fromString(source);
    }
}
