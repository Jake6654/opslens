# Bash Scripts

OpsLens includes several Bash scripts to simplify local development and operational workflows.

These scripts automate repetitive tasks such as:

* Starting containers
* Rebuilding services
* Running health checks
* Viewing container logs
* Sending test logs to the backend

The goal is to make the local workflow:

* Consistent
* Repeatable
* Easier to debug
* Easier to automate later in CI/CD and deployment environments

---

# Scripts Folder

```text
scripts/
  start.sh
  stop.sh
  rebuild.sh
  health-check.sh
  logs.sh
  test-ingestion.sh
```

---

# Before Running Scripts

Make all scripts executable:

```bash
chmod +x scripts/*.sh
```

---

# Script Descriptions

## `start.sh`

Starts all OpsLens services using Docker Compose.

What it does:

1. Runs Docker Compose containers in the background
2. Waits for services to start
3. Runs the health check script
4. Prints frontend and backend URLs

Command:

```bash
./scripts/start.sh
```

Example output:

```text
Starting OpsLens with Docker Compose...
Running health check...
OpsLens is running.
Frontend: http://localhost:3000
Backend:  http://localhost:8080
```

---

## `stop.sh`

Stops all running Docker Compose services.

Command:

```bash
./scripts/stop.sh
```

Example output:

```text
Stopping OpsLens...
OpsLens stopped.
```

---

## `rebuild.sh`

Rebuilds and restarts all Docker containers.

Use this after:

* Changing Dockerfiles
* Updating dependencies
* Changing environment variables
* Modifying build configurations

What it does:

1. Stops existing containers
2. Rebuilds Docker images
3. Restarts services
4. Runs health checks

Command:

```bash
./scripts/rebuild.sh
```

---

## `health-check.sh`

Checks whether the frontend and backend services are reachable.

What it checks:

* Backend health endpoint
* Frontend availability

Command:

```bash
./scripts/health-check.sh
```

Example output:

```text
Backend is healthy.
Frontend is reachable.
OpsLens health check passed.
```

This script uses:

```bash
curl -f
```

which fails automatically if the service returns an error response.

---

## `logs.sh`

Streams Docker container logs.

Show logs for all services:

```bash
./scripts/logs.sh
```

Show logs for a specific service:

```bash
./scripts/logs.sh backend
```

```bash
./scripts/logs.sh frontend
```

This script internally uses:

```bash
docker compose logs -f
```

where:

* `logs` displays container logs
* `-f` means follow mode

Follow mode continuously prints new logs as they appear.

Press:

```text
Ctrl + C
```

to stop watching logs.

---

## `test-ingestion.sh`

Sends a test log to the OpsLens backend.

What it does:

1. Reads the API key from `.env`
2. Sends a POST request to `/logs`
3. Inserts a test log into the system

Command:

```bash
./scripts/test-ingestion.sh
```

Example output:

```text
Sending test log to OpsLens...
Test log sent.
```

This script is useful for validating the full ingestion pipeline:

```text
Bash script
→ Spring Boot backend
→ API authentication
→ Supabase Postgres
→ OpsLens dashboard
```

---

# Recommended Testing Flow

Start the platform:

```bash
./scripts/start.sh
```

Run health checks:

```bash
./scripts/health-check.sh
```

Send a test log:

```bash
./scripts/test-ingestion.sh
```

Watch backend logs:

```bash
./scripts/logs.sh backend
```

Stop the platform:

```bash
./scripts/stop.sh
```

---

# DevOps Concepts Practiced

These scripts help practice practical DevOps concepts such as:

* Operational automation
* Health checks
* Environment variable usage
* Service lifecycle management
* Container log inspection
* API testing with curl
* Repeatable local workflows
* Bash scripting fundamentals

The same operational concepts later scale into:

* CI/CD pipelines
* Cloud deployment scripts
* Automated health monitoring
* Production deployment workflows
