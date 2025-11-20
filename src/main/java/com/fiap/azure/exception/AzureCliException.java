package com.fiap.azure.exception;

public class AzureCliException extends Exception {
    public AzureCliException(String message) {
        super(message);
    }

    public AzureCliException(String message, Throwable cause) {
        super(message, cause);
    }
}
