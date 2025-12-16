package fr.maif.izanami.spring.openfeature.api;

/**
 * Factory for creating {@link OpenFeatureClient} instances.
 * <p>
 * This factory mirrors the {@code OpenFeatureAPI.getClient()} methods but returns
 * {@link OpenFeatureClient} instead of the standard SDK {@code Client}, providing
 * auto-computed default value functionality.
 */
public interface OpenFeatureClientFactory {

    /**
     * Create a new OpenFeature client using the default provider.
     *
     * @return a new client instance
     */
    OpenFeatureClient getClient();

    /**
     * Create a new OpenFeature client bound to the specified domain.
     * <p>
     * If there is already a provider bound to this domain, that provider will be used.
     * Otherwise, the default provider is used until a provider is assigned to that domain.
     *
     * @param domain an identifier which logically binds clients with providers
     * @return a new client instance
     */
    OpenFeatureClient getClient(String domain);

    /**
     * Create a new OpenFeature client bound to the specified domain and version.
     * <p>
     * If there is already a provider bound to this domain, that provider will be used.
     * Otherwise, the default provider is used until a provider is assigned to that domain.
     *
     * @param domain  an identifier which logically binds clients with providers
     * @param version a version identifier
     * @return a new client instance
     */
    OpenFeatureClient getClient(String domain, String version);
}
