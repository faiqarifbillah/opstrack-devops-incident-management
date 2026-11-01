# OpsTrack: DevOps Incident Management System

A Spring Boot REST API for recording and managing IT incidents such as application downtime, API failures and database connection errors.

This is a portfolio project in active development. A full README follows when the project is complete.

## Current status

| Area | Status |
|---|---|
| Incident REST API with validation and JSON errors | Working |
| API key authentication and rate limiting | Working |
| PostgreSQL | Working in Docker Compose |
| Docker and Docker Compose | Working locally |
| GitHub Actions CI | Workflow written, not yet run |
| AWS deployment | Not started |

## Tech stack

Java 17, Spring Boot 4.1, Maven, Spring Data JPA, Bean Validation, Spring Boot Actuator, PostgreSQL 17, Docker, Docker Compose, GitHub Actions. Automated tests use an in-memory H2 database.

## Run locally

Requires Docker with Docker Compose.

```bash
cp .env.example .env
```

Replace every `change-me` in `.env`. See [SECURITY.md](SECURITY.md).

```bash
docker compose up --build
curl http://localhost:8080/actuator/health
```

On Windows PowerShell, use `Copy-Item .env.example .env` and `curl.exe`.

## Run the tests

Requires JDK 17. No database or `.env` file is needed.

```bash
./mvnw clean verify
```

On Windows PowerShell, use `.\mvnw.cmd clean verify`.

## Documentation

- [Docker guide](docs/DOCKER_GUIDE.md)
- [Linux operations and troubleshooting](docs/LINUX_GUIDE.md)
- [Testing the API](docs/API_TESTING.md)
- [Security design and secrets](SECURITY.md)
- [Git workflow](docs/GIT_GUIDE.md)
