package com.opslens.repository;

import com.opslens.model.PullRequestRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PullRequestRecordRepository extends JpaRepository<PullRequestRecord, Long> {

    Optional<PullRequestRecord> findByPatchSuggestionId(
            Long patchSuggestionId
    );

    Optional<PullRequestRecord> findByRepositoryAndHeadBranch(
            String repository,
            String headBranch
    );

    Optional<PullRequestRecord> findByRepositoryAndPullRequestNumber(
            String repository,
            Long pullRequestNumber
    );
}
