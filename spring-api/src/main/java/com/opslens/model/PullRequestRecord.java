package com.opslens.model;


import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "pull_request_records",
        uniqueConstraints = {
                // prevent DB from creating multiple PR record from the same patch suggestion
                @UniqueConstraint(
                        name = "uk_pull_request_patch_suggestion",
                        columnNames = "patch_suggestion_id"
                ),
                // 같은 리포와 브랜치 조합으로 여러 PR record 가 만들어지는것을 막는다
                @UniqueConstraint(
                        name = "uk_pull_request_repository_head",
                        columnNames = {
                                "repository",
                                "head_branch"
                        }
                )
        }
)
public class PullRequestRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patch_suggestion_id", nullable = false)
    private Long patchSuggestionId;

    @Column(name = "incident_id", nullable = false)
    private Long incidentId;

    @Column(nullable = false, length = 200)
    private String repository;

    @Column(name = "base_branch", nullable = false, length = 255)
    private String baseBranch;

    // branch where the AI patch is committed
    @Column(name = "head_branch", nullable = false, length = 255)
    private String headBranch;

    @Column(name = "commit_sha", nullable = false, length = 64)
    private String commitSha;

    @Column(name = "pull_request_number", nullable = false)
    private Long pullRequestNumber;

    @Column(name = "pull_request_url", nullable = false, length = 2048)
    private String pullRequestUrl;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public PullRequestRecord() {
    }

    public PullRequestRecord(Long patchSuggestionId, Long incidentId, String repository, String baseBranch, String headBranch, String commitSha, Long pullRequestNumber, String pullRequestUrl, String status) {
        this.patchSuggestionId = patchSuggestionId;
        this.incidentId = incidentId;
        this.repository = repository;
        this.baseBranch = baseBranch;
        this.headBranch = headBranch;
        this.commitSha = commitSha;
        this.pullRequestNumber = pullRequestNumber;
        this.pullRequestUrl = pullRequestUrl;
        this.status = status;
    }

    // JPA 가 실제 insert SQL 을 실행하기 직전에 automatically call PrePersist Method
    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    // JPA automatically call this method when it notic the entity is updated
    @PreUpdate
    public void onUpdate(){
        this.updatedAt = LocalDateTime.now();
    }

    public void updateStatus(String status){
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public Long getPatchSuggestionId() {
        return patchSuggestionId;
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public String getRepository() {
        return repository;
    }

    public String getBaseBranch() {
        return baseBranch;
    }

    public String getHeadBranch() {
        return headBranch;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public Long getPullRequestNumber() {
        return pullRequestNumber;
    }

    public String getPullRequestUrl() {
        return pullRequestUrl;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}


