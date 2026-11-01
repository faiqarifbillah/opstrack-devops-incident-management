# Security

This document describes how OpsTrack protects its API and how to handle its secrets.

## Design

| Control | Implementation |
|---|---|
| Authentication | `X-API-Key` header required on `/api/**`; missing or wrong key returns 401 |
| Key comparison | Constant-time, using `MessageDigest.isEqual` |
| Weak configuration | The application refuses to start if the key is missing or under 32 characters |
| Rate limiting | Per client IP, 60 requests per minute by default, then 429 with `Retry-After` |
| Filter order | Rate limiting runs before authentication, so key guessing is limited too |
| Public endpoints | Only `/actuator/health`, showing `UP` or `DOWN` without details |
| Security headers | `nosniff`, framing denied, restrictive CSP, no referrer, no caching, on every application response |
| CORS | Not enabled, so browsers block pages on other sites from reading responses |
| XSS | The API returns only JSON and never builds HTML. Any future frontend must encode stored text when displaying it |
| SQL injection | Queries are parameterized by Spring Data JPA; no SQL is built from input |
| Error responses | Fixed messages; no stack traces, SQL or submitted values |
| Logging | Rejected requests log the client IP and the reason, never the key |

## API keys are credentials

An API key works like a password: whoever holds it has full access. Encrypting it does not change that, because a program that uses the key must be able to read it, and anyone who gets the decrypted value can use it. Protect it this way instead:

- Store it only in a secret store or an environment variable, never in source code.
- Send it only over HTTPS. Over plain HTTP, anyone on the network path can read it.
- Do not put it in frontend code. Anything sent to a browser can be read by its user.
- Do not put it in a URL, where it would end up in logs and browser history.
- Replace it immediately if it may have been exposed.

## Configuring secrets

Local development uses a `.env` file, which `.gitignore` excludes:

```bash
cp .env.example .env
openssl rand -hex 32
```

Put a separately generated value in `.env` for each of `OPSTRACK_API_KEY`, `DB_PASSWORD` and `DB_ADMIN_PASSWORD`. Do not use quotes around values.

On Windows PowerShell, generate a key with:

```powershell
$b = New-Object byte[] 32; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); -join ($b | ForEach-Object { $_.ToString('x2') })
```

| Variable | Required | Purpose |
|---|---|---|
| `OPSTRACK_API_KEY` | Yes | API key, at least 32 characters |
| `DB_PASSWORD` | Yes | Password for the application's database user |
| `DB_ADMIN_PASSWORD` | Yes | PostgreSQL administrator password, used only by the database container |
| `DB_USERNAME` | No | Application's database user, created without superuser rights; defaults to `opstrack` |
| `OPSTRACK_RATE_LIMIT_PER_MINUTE` | No | Defaults to 60 |
| `APP_PORT` | No | Port on this computer for the API; defaults to 8080 |

In production, use a secret store such as AWS Systems Manager Parameter Store rather than a file on disk.

## Never commit

`.env` files, API keys, database passwords, AWS credentials or SSH private keys. If a secret reaches Git, deleting the file is not enough, because it stays in the history. Replace the secret first, then clean up the repository.

## Requirements for production

- Terminate HTTPS in front of the application. It serves plain HTTP on its own.
- Do not make PostgreSQL publicly reachable. Allow connections only from the application.
- Use a database user without superuser rights.

## Known limitations

- `GET /api/incidents` has no pagination, so a very large table would produce a very large response.
- There is one shared key, with no per-user identity or permissions. Replacing it requires a restart.
- Rate limit counts are in memory, so they reset on restart and are not shared between instances.
- Behind a reverse proxy, every client appears to have the proxy's IP and shares one limit. Fix this by configuring trusted proxy handling; do not simply trust `X-Forwarded-For`, which clients can forge.
- Only `/api/**` is rate limited. `/actuator/health` is not.

## Status of these controls

In Docker Compose, PostgreSQL has no published port, the application's database user has no superuser rights, and the application container runs as a non-root user.

HTTPS and Parameter Store are planned for the deployment phase and are not yet in place. `docker inspect` displays a container's environment variables, so anyone with access to Docker on the host can read these secrets.
