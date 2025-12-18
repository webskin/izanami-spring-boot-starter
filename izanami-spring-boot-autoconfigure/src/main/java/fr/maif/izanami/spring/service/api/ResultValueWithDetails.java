package fr.maif.izanami.spring.service.api;

import fr.maif.izanami.spring.service.IzanamiServiceImpl;

import java.util.Map;

/**
 * Result wrapper that includes both the extracted typed value and metadata about the evaluation.
 * <p>
 * This record is returned by the {@code *ValueDetails()} methods in
 * {@link IzanamiServiceImpl.FeatureRequestBuilder} and provides:
 * <ul>
 *   <li>The typed value extracted from the Izanami result</li>
 *   <li>Metadata about the flag configuration and evaluation source</li>
 * </ul>
 * <p>
 * The metadata map includes keys defined in
 * {@link fr.maif.izanami.spring.openfeature.FlagMetadataKeys}:
 * <ul>
 *   <li>{@code FLAG_CONFIG_KEY} - the Izanami feature key (UUID)</li>
 *   <li>{@code FLAG_CONFIG_NAME} - the human-friendly flag name</li>
 *   <li>{@code FLAG_CONFIG_DESCRIPTION} - the flag description</li>
 *   <li>{@code FLAG_CONFIG_VALUE_TYPE} - the configured value type</li>
 *   <li>{@code FLAG_CONFIG_DEFAULT_VALUE} - the configured default value</li>
 *   <li>{@code FLAG_CONFIG_ERROR_STRATEGY} - the configured error strategy</li>
 *   <li>{@code FLAG_VALUE_SOURCE} - where the value originated (IZANAMI, IZANAMI_ERROR_STRATEGY, or APPLICATION_ERROR_STRATEGY)</li>
 *   <li>{@code FLAG_EVALUATION_REASON} - evaluation reason ("DISABLED", "ORIGIN_OR_CACHE", or "ERROR")</li>
 * </ul>
 *
 * @param value    the typed evaluation result (may be null for disabled non-boolean features)
 * @param metadata metadata about the flag configuration and evaluation source
 * @param <T>      the value type (Boolean, String, or BigDecimal)
 * @see IzanamiServiceImpl.FeatureRequestBuilder#booleanValueDetails()
 * @see IzanamiServiceImpl.FeatureRequestBuilder#stringValueDetails()
 * @see IzanamiServiceImpl.FeatureRequestBuilder#numberValueDetails()
 */
public record ResultValueWithDetails<T>(
    T value,
    Map<String, String> metadata
) {}
