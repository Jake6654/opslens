package com.opslens.service;

/**
 * Indicates that a validated diff could not be applied to GitHub file content.
 */
public class PatchMaterializationException extends RuntimeException{

    public PatchMaterializationException(String message){
        super(message);
    }

    public PatchMaterializationException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
