package com.opslens.config;


import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * Why this file exists?
 * It converts the github.* YAML properties into a typed Java object
 * Instead of writing this throughout the code like @Value("${github.owner}")
 */
@Component
// this configuration maps properties in yaml file to private String baseBranch
@ConfigurationProperties(prefix = "github")
public class GitHubProperties {

    private String owner;
    private String repository;
    private String baseBranch;
    private String branchPrefix;

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getRepository() {
        return repository;
    }

    public void setRepository(String repository) {
        this.repository = repository;
    }

    public String getBaseBranch() {
        return baseBranch;
    }

    public void setBaseBranch(String baseBranch) {
        this.baseBranch = baseBranch;
    }

    public String getBranchPrefix() {
        return branchPrefix;
    }

    public void setBranchPrefix(String branchPrefix) {
        this.branchPrefix = branchPrefix;
    }
}
