package fr.maif.izanami.spring.openfeature;

/**
 * Indicates where a flag value originates from.
 */
public enum FlagValueSource {
    /**
     * Value retrieved from Izanami.
     */
    IZANAMI,
    /**
     * Value produced by Izanami client's error strategy (Izanami was queried, but returned an error).
     */
    IZANAMI_ERROR_STRATEGY,
    /**
     * Value taken from the application's configuration (Izanami client not configured/available or evaluation failed).
     */
    APPLICATION_ERROR_STRATEGY
}
