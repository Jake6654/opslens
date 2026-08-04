package com.opslens.github;

/**
 * This exception preserves the GitHub HTTP status without exposing the token.
 * Status 0 means the request failed before a GitHub response was received
 */
public class GitHubApiException extends RuntimeException {

    private final int statusCode;


    public GitHubApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public GitHubApiException(String message, Throwable cause){
        super(message, cause);
        this.statusCode = 0;
    }

    public int getStatusCode(){
        return statusCode;
    }
}
