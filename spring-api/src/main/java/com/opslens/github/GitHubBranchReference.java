package com.opslens.github;

/**
 *  A Java record is a concise immutable data carrier. Java automatically creates
 *  the constructor and accessor method.
 * @param ref
 * @param sha
 * @param url
 */
public record GitHubBranchReference (
        String ref,
        String sha,
        String url
){

}
