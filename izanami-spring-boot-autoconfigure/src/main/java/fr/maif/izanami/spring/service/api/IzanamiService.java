package fr.maif.izanami.spring.service.api;

import fr.maif.IzanamiClient;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface IzanamiService {
    Optional<IzanamiClient> unwrapClient();

    CompletableFuture<Void> whenLoaded();

    boolean isConnected();

    FeatureRequestBuilder forFlagKey(String flagKey);

    FeatureRequestBuilder forFlagName(String flagName);
}
