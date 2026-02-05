package com.project.curve.core.key;

/**
 * Exception thrown when a KeyProvider operation fails.
 */
public class KeyProviderException extends RuntimeException {

    public KeyProviderException(String message) {
        super(message);
    }

    public KeyProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
