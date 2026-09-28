# Phase 5F: Complete Pull Request Workflow Verification

Phase 5F verifies that the GitHub automation built in Phases 5A through 5E is
safe, observable, and repeatable. It does not merge or deploy AI-generated
code.

## Workflow under verification

```text
validated patch and passing tests
  -> pull request preflight
  -> safe ai-fix branch
  -> patch commit
  -> draft pull request
  -> local PR record
  -> GitHub status synchronization
  -> dashboard status and link
```

## Automated unit tests

`PullRequestStatusServiceTests` verifies that:

- stored status reads never call GitHub;
- GitHub refreshes persist the latest status and head SHA;
- merged pull requests resolve to `MERGED`;
- repository and branch identity mismatches stop before database mutation;
- invalid patch suggestion IDs stop before repository or GitHub access.

`PullRequestControllerTests` verifies the public HTTP contract:

- stored and refreshed status requests return `200`;
- missing local records return `404`;
- identity conflicts return `409`;
- GitHub failures return `502`.

Run the focused tests with:

```bash
cd spring-api
./gradlew test \
  --tests com.opslens.service.PullRequestStatusServiceTests \
  --tests com.opslens.controller.PullRequestControllerTests
```

## Live verification script

The script uses an existing completed patch suggestion and pull request. It is
non-destructive: it does not create a new branch or commit, merge a pull
request, or write to a protected branch. The GitHub refresh updates only the
stored status and head SHA. Repeating PR creation must recover the existing
record and return `ALREADY_EXISTS`.

Prerequisites:

- the OpsLens backend is running;
- the selected patch suggestion already has a persisted GitHub PR;
- `curl` and `jq` are installed;
- the backend has a GitHub token with repository read/write and pull-request
  permissions.

Run with the default patch suggestion `20`:

```bash
./scripts/verify-phase-5.sh
```

Run with another patch suggestion or backend URL:

```bash
PATCH_SUGGESTION_ID=25 \
API_BASE_URL=http://localhost:8081 \
./scripts/verify-phase-5.sh
```

## Manual state-transition check

To verify state synchronization, close or merge the draft PR on GitHub, then
run:

```bash
curl -s -X POST \
  http://localhost:8081/patch-suggestions/20/pull-request/status/refresh | jq
```

The result should be `CLOSED` or `MERGED`. A following stored-status request
must return the same status with `refreshedFromGitHub` set to `false`, because
that second request reads PostgreSQL rather than GitHub.

## Safety guarantees verified by Phase 5

- AI changes never target the protected base branch directly.
- Invalid patches and failed tests block pull-request automation.
- Branch and commit SHAs are checked before mutation.
- Repeated requests do not create duplicate pull requests.
- Repository, PR number, head branch, and base branch must match before sync.
- GitHub failures are visible and do not silently overwrite local state.
- Pull requests remain subject to human review and manual merge.
