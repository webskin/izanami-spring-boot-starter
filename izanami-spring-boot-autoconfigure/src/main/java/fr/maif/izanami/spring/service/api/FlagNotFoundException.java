package fr.maif.izanami.spring.service.api;

/**
 * Exception thrown when a feature flag configuration is not found.
 * <p>
 * This exception indicates that the requested flag key or name does not exist
 * in the configured {@code openfeature.flags} list.
 */
public class FlagNotFoundException extends RuntimeException {

    private final String flagIdentifier;
    private final IdentifierType identifierType;

    /**
     * Type of identifier used to look up the flag.
     */
    public enum IdentifierType {
        KEY,
        NAME
    }

    /**
     * Create a new exception for a missing flag.
     *
     * @param flagIdentifier the flag key or name that was not found
     * @param identifierType the type of identifier (KEY or NAME)
     */
    public FlagNotFoundException(String flagIdentifier, IdentifierType identifierType) {
        super(buildMessage(flagIdentifier, identifierType));
        this.flagIdentifier = flagIdentifier;
        this.identifierType = identifierType;
    }

    private static String buildMessage(String flagIdentifier, IdentifierType identifierType) {
        return switch (identifierType) {
            case KEY -> "Flag with key '" + flagIdentifier + "' not found in configuration. "
                + "Please ensure this flag is configured in openfeature.flags.";
            case NAME -> "Flag with name '" + flagIdentifier + "' not found in configuration. "
                + "Please ensure this flag is configured in openfeature.flags.";
        };
    }

    /**
     * @return the flag identifier (key or name) that was not found
     */
    public String getFlagIdentifier() {
        return flagIdentifier;
    }

    /**
     * @return the type of identifier used in the lookup
     */
    public IdentifierType getIdentifierType() {
        return identifierType;
    }
}
