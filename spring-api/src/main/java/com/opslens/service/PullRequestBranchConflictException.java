package com.opslens.service;

/**
 *  These exceptions represent business failures, not programming failures.
 *  The controller maps them to HTTP 409 conflict
 */
public class PullRequestBranchConflictException extends RuntimeException{

    public PullRequestBranchConflictException(String message){
        super(message);
    }
}
