# OpsLens Agentic DevOps Platform

[Website](https://frontend-mu-beige-83.vercel.app/) |
[GitHub](https://github.com/Jake6654/opslens)

OpsLens is an AI-assisted incident response platform for backend services. It
collects application logs, creates incidents from errors, analyzes likely root
causes, searches related source code, proposes patches, validates generated
diffs, runs project tests in an isolated workspace, and determines whether a
patch is ready to become a pull request.

The platform is designed around human review. AI-generated code is never pushed
directly to a production branch or deployed automatically.

## Current Scope: Single-Project Prototype

OpsLens is currently a single-project prototype integrated with
[`sketch-my-day`](https://github.com/Jake6654/sketch-my-day). The local Docker
environment mounts the `sketch-my-day` workspace, and the current repository
search, patch generation, and test commands are configured around that
application.

This limited scope is intentional. The immediate goal is to validate one
complete, real workflow before introducing multi-tenant platform complexity:

```text
sketch-my-day error
  -> OpsLens log ingestion
  -> automatic incident creation
  -> AI incident analysis
  -> local or GitHub code search
  -> patch generation and validation
  -> isolated test execution
  -> human-reviewed GitHub pull request
```

After this vertical slice is reliable, OpsLens will evolve into a multi-project
SaaS platform where teams can connect their own repositories and application
services.

## Current Development Status

OpsLens has completed the incident analysis, code search, patch generation, and
test-validation workflow for `sketch-my-day`. Development is now moving into
GitHub pull request automation.

| Phase | Scope | Status |
| --- | --- | --- |
| Phase 1 | Log ingestion, incidents, AI reports, dashboard | Complete |
| Phase 2 | GitHub and local workspace code search | Complete |
| Phase 3 | LangGraph workflow and AI patch suggestions | Complete |
| Phase 4 | Patch validation, isolated test execution, failure analysis, PR readiness | Complete |
| Phase 5 | GitHub branch, commit, and pull request automation | Next |
| Phase 6 | Kubernetes deployment and Terraform infrastructure | Planned |

Kubernetes, Terraform, and automated pull request creation are roadmap items and
are not yet part of the current production implementation.

## Key Capabilities

- Centralized JSON log ingestion secured with an API key
- Log filtering by level, project, and environment
- Automatic incident creation from `ERROR` logs
- Structured AI incident reports with summary, root cause, recommendation, and confidence
- Source-code search against GitHub or a mounted local workspace
- LangGraph-based incident repair workflow
- AI-assisted patch generation using complete source-file context
- Programmatic unified-diff generation
- Patch validation with `git apply --check`
- Isolated patch application and test execution
- Test timeout and command allow-list protection
- AI analysis of failed test output
- Pull request readiness checks with explicit blocking reasons
- Next.js dashboard for reports, related code, patches, test runs, and failure analysis

## Architecture

```text
Application Services
    |
    | POST /logs with x-api-key
    v
OpsLens Spring Boot API
    |
    |-- PostgreSQL
    |     |-- logs
    |     |-- incidents
    |     |-- incident reports
    |     |-- code search results
    |     |-- patch suggestions
    |     |-- test runs
    |     `-- test failure analyses
    |
    | HTTP requests
    v
FastAPI AI Orchestrator
    |
    |-- OpenAI analysis
    |-- LangGraph workflow
    |-- GitHub/local code search
    |-- patch generation and validation
    `-- isolated test runner

Next.js Dashboard
    |
    `-- Spring Boot API through Next.js API routes
```

## Incident Repair Workflow

```text
ERROR log received
    |
    v
Incident created automatically
    |
    v
AI incident report generated
    |
    v
Related source code searched
    |
    v
AI returns a complete modified source file
    |
    v
OpsLens generates a unified diff with Python difflib
    |
    v
git apply --check validates the patch
    |
    v
Repository copied to a temporary workspace
    |
    v
Patch applied and project tests executed
    |
    |-- failure -> save output and generate AI failure analysis
    |
    `-- success -> mark patch READY_FOR_PR
```

## Phase 4 Safety Gate

A patch becomes `READY_FOR_PR` only when all of these conditions pass:

```text
patchValid == true
suggestedDiff is not empty
riskLevel is not HIGH
latest test run status == PASSED
latest test run passed == true
```

The verification API returns one of the following states:

| Status | Meaning |
| --- | --- |
| `READY_FOR_PR` | Patch validation and tests passed |
| `PATCH_INVALID` | Diff is missing, malformed, or cannot be applied |
| `TESTS_NOT_RUN` | Patch is valid but has not been tested |
| `TESTS_FAILED` | Latest test run did not pass |
| `HIGH_RISK` | Patch requires manual investigation |

Human review remains required even when a patch reaches `READY_FOR_PR`.

## Tech Stack

| Layer | Technology |
| --- | --- |
| Frontend | Next.js, TypeScript, Tailwind CSS |
| Backend API | Java 21, Spring Boot, Spring Data JPA |
| AI orchestration | Python, FastAPI, LangGraph, OpenAI API |
| Persistence | PostgreSQL |
| Code integration | GitHub API, local workspace search |
| Patch validation | Git, Python `difflib` |
| Test execution | Python `subprocess`, Gradle |
| Local orchestration | Docker, Docker Compose, Bash |
| Planned infrastructure | Kubernetes, Terraform, AWS EKS, ECR, RDS |

## Repository Structure

```text
opslens/
├── frontend/                 Next.js dashboard and API proxy routes
├── spring-api/               Spring Boot API and PostgreSQL persistence
├── ai-orchestrator/          FastAPI, LangGraph, AI services, and test runner
├── scripts/                  Local lifecycle and ingestion scripts
├── docker-compose.yml        Local multi-service orchestration
└── README.md
```

For local source search and test execution, the target project is expected to
exist as a sibling directory:

```text
my-project/
├── opslens/
└── sketch-my-day/
```

Docker Compose mounts it as:

```text
../sketch-my-day -> /workspace/sketch-my-day
```

## Local Services

| Service | Local URL |
| --- | --- |
| Next.js dashboard | `http://localhost:3001` |
| Spring Boot API | `http://localhost:8081` |
| FastAPI orchestrator | `http://localhost:8002` |
| PostgreSQL | `localhost:5433` |

## Environment Configuration

Create a root `.env` file in `opslens/`. Do not commit real credentials.

```env
API_KEY=replace_with_a_local_api_key

OPENAI_API_KEY=replace_with_your_openai_api_key
OPENAI_MODEL=gpt-4o-mini

GITHUB_TOKEN=
GITHUB_REPOSITORY_OWNER=Jake6654
GITHUB_REPOSITORY_NAME=sketch-my-day
GITHUB_DEFAULT_BRANCH=main

CODE_SEARCH_MODE=local
LOCAL_REPOSITORY_PATH=/workspace/sketch-my-day
TEST_WORKING_DIRECTORY=/workspace/sketch-my-day/backend
TEST_TIMEOUT_SECONDS=120
```

Use a narrowly scoped GitHub credential and never place tokens in source code,
prompts, reports, patches, or pull request descriptions.

## Running Locally

Start every service:

```bash
docker compose up -d --build
```

Or use the helper script:

```bash
./scripts/start.sh
```

Check service status:

```bash
docker compose ps
./scripts/health-check.sh
```

Follow all logs:

```bash
./scripts/logs.sh
```

Follow one service:

```bash
./scripts/logs.sh backend
./scripts/logs.sh ai-orchestrator
```

Stop the platform:

```bash
./scripts/stop.sh
```

## Testing Log Ingestion

Send the provided test log:

```bash
./scripts/test-ingestion.sh
```

Or send a log manually:

```bash
set -a
source .env
set +a

curl -X POST http://localhost:8081/logs \
  -H "Content-Type: application/json" \
  -H "x-api-key: $API_KEY" \
  -d '{
    "timestamp": "2026-07-29T12:00:00",
    "level": "ERROR",
    "project": "sketch-my-day",
    "environment": "dev",
    "service": "DiaryService",
    "message": "Predictable test failure in DiaryService"
  }'
```

An `ERROR` log starts the incident workflow automatically.

## Spring Boot API

### Logs and incidents

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `GET` | `/health` | Backend health check |
| `POST` | `/logs` | Ingest an authenticated log |
| `GET` | `/logs` | Read and filter logs |
| `GET` | `/logs/summary` | Return log-level counts |
| `GET` | `/logs/{id}` | Read one log |
| `POST` | `/incidents/from-log/{logId}` | Create or retrieve an incident |
| `GET` | `/incidents` | List incidents |
| `GET` | `/incidents/{id}` | Read one incident |
| `GET` | `/incidents/{id}/report` | Read the AI incident report |

### Code repair and validation

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/incidents/{id}/code-search` | Run and save source-code search |
| `GET` | `/incidents/{id}/code-search` | Read saved code-search results |
| `POST` | `/incidents/{id}/suggest-patch` | Generate and validate a patch |
| `GET` | `/incidents/{id}/patch-suggestions` | Read patch suggestions |
| `POST` | `/patch-suggestions/{id}/run-tests` | Apply a patch and run tests |
| `GET` | `/patch-suggestions/{id}/test-runs` | Read test history |
| `GET` | `/test-runs/{id}/analysis` | Read AI test-failure analysis |
| `GET` | `/patch-suggestions/{id}/verification` | Check pull request readiness |

## FastAPI Orchestrator API

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `GET` | `/health` | Orchestrator health check |
| `POST` | `/analyze-log` | Generate structured incident analysis |
| `POST` | `/search-code` | Search GitHub or a local workspace |
| `POST` | `/suggest-patch` | Run the LangGraph patch workflow |
| `POST` | `/run-tests` | Validate, apply, and test a patch |
| `POST` | `/analyze-test-failure` | Analyze failed test output |

## Safety Principles

- Never push AI-generated changes directly to `main`, `master`, or production branches.
- Never deploy AI-generated code without human review.
- Validate every generated diff before applying it.
- Apply patches only inside temporary workspaces.
- Allow only predefined test commands.
- Enforce test timeouts.
- Block high-risk patches from automated PR creation.
- Exclude secrets and sensitive files from AI context and patches.
- Limit the number of files changed by an automated patch.

## Roadmap

### Phase 5: GitHub Pull Request Automation

- Generate a pull request plan without mutating GitHub
- Recheck Phase 4 readiness before every external action
- Create an `ai-fix/...` branch
- Commit only the validated patch
- Push the branch without touching protected branches
- Open a pull request containing incident, patch, and test details
- Persist the PR number, URL, branch, commit SHA, and status
- Display PR readiness and links in the dashboard

### Phase 6: Kubernetes and Terraform

- Harden production Docker images and run containers as non-root users
- Build and push versioned images to Amazon ECR
- Provision networking, EKS, RDS, Redis, IAM, and registries with Terraform
- Deploy frontend, backend, and orchestrator workloads to Kubernetes
- Add ConfigMaps, external secret references, probes, Ingress, and autoscaling
- Automate build and deployment workflows with GitHub Actions
- Add metrics, dashboards, and operational alerts

Terraform will provision and manage the cloud infrastructure. Kubernetes will
run and manage the OpsLens application workloads on that infrastructure.

### Post-Phase 6: Multi-Project SaaS

Once the end-to-end workflow and infrastructure are stable, the project will
be generalized from its `sketch-my-day` integration into a SaaS product:

- Add organization, project, environment, and repository connection models
- Replace hardcoded repository mappings with per-project configuration
- Use a GitHub App for repository access and pull request automation
- Issue separate ingestion API keys for each project and environment
- Add user authentication, team membership, and role-based authorization
- Isolate tenant data, repository access, secrets, and test workspaces
- Move long-running analysis and test execution to asynchronous job queues
- Support project-specific build systems, test commands, timeouts, and policies
- Add usage limits, audit history, observability, and operational controls

The prototype-first approach keeps the current system concrete and testable.
The SaaS phase will generalize proven workflows instead of designing
multi-tenant abstractions before the core incident repair flow is reliable.

## Project Positioning

Today, OpsLens is a single-project agentic DevOps prototype validated against
`sketch-my-day`. Its long-term direction is a multi-project SaaS platform for
backend engineering teams.

Its purpose is not autonomous production deployment. OpsLens shortens
investigation time and prepares a validated, tested pull request for developer
review, while keeping humans responsible for approval and deployment.
