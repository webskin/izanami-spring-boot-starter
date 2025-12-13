package fr.maif.izanami.spring.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for the Izanami client.
 * <p>
 * This starter never fails the Spring application context startup when Izanami is unreachable.
 * If the client cannot be created or cannot reach the server, evaluations fall back to the
 * configured OpenFeature flag defaults.
 */
@ConfigurationProperties(prefix = "izanami")
public record IzanamiProperties(
    String baseUrl,
    String apiPath,
    String clientId,
    String clientSecret,
    Cache cache
) {
    /**
     * Compact constructor setting sensible defaults.
     */
    public IzanamiProperties {
        if (baseUrl == null) {
            baseUrl = "http://localhost:9000";
        }
        if (apiPath == null || apiPath.isBlank()) {
            apiPath = "/api";
        }
        if (cache == null) {
            cache = new Cache(null, null, null);
        }
    }

    /**
     * @return the full Izanami API URL (base URL + api path), or {@code null} if {@code baseUrl} is not set.
     */
    public String url() {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        String cleanBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String cleanApiPath = apiPath == null ? "" : apiPath.trim();
        if (cleanApiPath.isEmpty()) {
            return cleanBaseUrl;
        }
        cleanApiPath = cleanApiPath.startsWith("/") ? cleanApiPath : "/" + cleanApiPath;
        return cleanBaseUrl + cleanApiPath;
    }

    /**
     * Cache configuration for the Izanami client.
     *
     * @param enabled         enables the client cache (defaults to {@code true})
     * @param refreshInterval cache refresh interval when polling (defaults to 5 minutes)
     * @param sse             Server-Sent Events configuration (defaults: enabled, keep-alive 25 seconds)
     */
    public record Cache(
        Boolean enabled,
        Duration refreshInterval,
        Sse sse
    ) {
        /**
         * Compact constructor setting defaults compatible with the Izanami showcase.
         */
        public Cache {
            if (enabled == null) {
                enabled = true;
            }
            if (refreshInterval == null) {
                refreshInterval = Duration.ofMinutes(5);
            }
            if (sse == null) {
                sse = new Sse(null, null);
            }
        }

        /**
         * Server-Sent Events configuration for receiving updates from Izanami.
         *
         * @param enabled           enables SSE (defaults to {@code true})
         * @param keepAliveInterval keep-alive interval (defaults to 25 seconds)
         */
        public record Sse(
            Boolean enabled,
            Duration keepAliveInterval
        ) {
            /**
             * Compact constructor setting defaults compatible with the Izanami showcase.
             */
            public Sse {
                if (enabled == null) {
                    enabled = true;
                }
                if (keepAliveInterval == null) {
                    keepAliveInterval = Duration.ofSeconds(25);
                }
            }
        }
    }
}
