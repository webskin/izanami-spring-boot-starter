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
public class IzanamiProperties {

    private String baseUrl = "http://localhost:9000";
    private String apiPath = "/api";
    private String clientId;
    private String clientSecret;
    private Cache cache = new Cache();

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiPath() {
        return apiPath;
    }

    public void setApiPath(String apiPath) {
        this.apiPath = apiPath;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public Cache getCache() {
        return cache;
    }

    public void setCache(Cache cache) {
        this.cache = cache;
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
     */
    public static class Cache {

        private Boolean enabled = true;
        private Duration refreshInterval = Duration.ofMinutes(5);
        private Sse sse = new Sse();

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getRefreshInterval() {
            return refreshInterval;
        }

        public void setRefreshInterval(Duration refreshInterval) {
            this.refreshInterval = refreshInterval;
        }

        public Sse getSse() {
            return sse;
        }

        public void setSse(Sse sse) {
            this.sse = sse;
        }

        /**
         * Server-Sent Events configuration for receiving updates from Izanami.
         */
        public static class Sse {

            private Boolean enabled = true;
            private Duration keepAliveInterval = Duration.ofSeconds(25);

            public Boolean getEnabled() {
                return enabled;
            }

            public void setEnabled(Boolean enabled) {
                this.enabled = enabled;
            }

            public Duration getKeepAliveInterval() {
                return keepAliveInterval;
            }

            public void setKeepAliveInterval(Duration keepAliveInterval) {
                this.keepAliveInterval = keepAliveInterval;
            }
        }
    }
}
