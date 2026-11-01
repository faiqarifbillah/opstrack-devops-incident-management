# Testing the API

## Automated tests

```bash
./mvnw clean verify
```

On Windows PowerShell, use `.\mvnw.cmd clean verify`. Tests use an in-memory H2 database and a fake API key, so they need no `.env` file or running database.

| Test class | What it covers |
|---|---|
| `IncidentControllerTests` | Create, read, update, delete, filters, invalid input, not found |
| `ApiKeyAuthenticationTests` | Missing, invalid and valid keys; public health; keys kept out of logs; startup check |
| `RateLimitTests` | 429 response, separate clients, failed guesses counted |
| `RateLimiterTests` | Limit, window reset, cleanup, simultaneous requests |
| `GlobalExceptionHandlerTests` | Unexpected errors return 500 without internal details |
| `WebSecurityTests` | Security headers, no CORS permission for other sites, Actuator endpoint list hidden |
| `OpsTrackApplicationTests` | Health details hidden, other Actuator endpoints not exposed |

To see a coverage report without changing `pom.xml`:

```bash
./mvnw clean org.jacoco:jacoco-maven-plugin:0.8.13:prepare-agent test org.jacoco:jacoco-maven-plugin:0.8.13:report
```

Then open `target/site/jacoco/index.html`.

## Start the application

Set the values in `.env` (see [SECURITY.md](../SECURITY.md)), then run:

```bash
docker compose up --build -d
```

Data is kept in PostgreSQL, so the IDs you receive may be higher than the examples below.

## Testing with curl

These commands use bash syntax and work in Ubuntu or Git Bash. Windows PowerShell handles quotes differently, so use Git Bash there.

Set these in the terminal you are testing from:

```bash
BASE=http://localhost:8080
read -rs -p "API key: " KEY; echo
```

`read -rs` keeps the key out of your shell history and off the screen.

### Health check (no key needed)

```bash
curl -i $BASE/actuator/health
```

Expected: `200` with `{"status":"UP"}`.

### Missing and invalid keys

```bash
curl -i $BASE/api/incidents
curl -i -H "X-API-Key: wrong-key" $BASE/api/incidents
```

Expected: `401` for both, with this body:

```json
{
  "timestamp": "2026-11-01T12:29:22.075875900Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Missing or invalid API key",
  "path": "/api/incidents"
}
```

### Create an incident

```bash
curl -i -X POST $BASE/api/incidents \
  -H "X-API-Key: $KEY" -H "Content-Type: application/json" \
  -d '{"title":"Payment API returning 500","description":"Spike in server errors","severity":"CRITICAL","assignedTo":"On-call engineer"}'
```

Expected: `201`. `status` defaults to `OPEN`, and the server sets `id` and the timestamps:

```json
{
  "id": 1,
  "title": "Payment API returning 500",
  "description": "Spike in server errors",
  "severity": "CRITICAL",
  "status": "OPEN",
  "assignedTo": "On-call engineer",
  "resolutionNotes": null,
  "createdAt": "2026-11-01T12:22:48.209754400Z",
  "updatedAt": "2026-11-01T12:22:48.209754400Z"
}
```

The commands below assume this incident has ID 1. Use the `id` you received if it differs.

### List, read and filter

```bash
curl -i -H "X-API-Key: $KEY" $BASE/api/incidents
curl -i -H "X-API-Key: $KEY" $BASE/api/incidents/1
curl -i -H "X-API-Key: $KEY" $BASE/api/incidents/severity/CRITICAL
curl -i -H "X-API-Key: $KEY" $BASE/api/incidents/status/OPEN
```

Expected: `200` for each. The list and filters return JSON arrays.

### Update an incident

`PUT` replaces the incident, so send every field you want to keep.

```bash
curl -i -X PUT $BASE/api/incidents/1 \
  -H "X-API-Key: $KEY" -H "Content-Type: application/json" \
  -d '{"title":"Payment API returning 500","description":"Spike in server errors","severity":"CRITICAL","status":"RESOLVED","assignedTo":"On-call engineer","resolutionNotes":"Restarted connection pool"}'
```

Expected: `200`, with `status` set to `RESOLVED`, a newer `updatedAt` and the same `createdAt`.

### Invalid input

```bash
curl -i -X POST $BASE/api/incidents \
  -H "X-API-Key: $KEY" -H "Content-Type: application/json" \
  -d '{"title":"Missing other fields"}'
```

Expected: `400`, naming each invalid field:

```json
{
  "timestamp": "2026-11-01T12:29:22.817092Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/incidents",
  "fieldErrors": {
    "severity": "severity is required",
    "description": "description is required"
  }
}
```

### Delete, then confirm it is gone

```bash
curl -i -X DELETE -H "X-API-Key: $KEY" $BASE/api/incidents/1
curl -i -H "X-API-Key: $KEY" $BASE/api/incidents/1
```

Expected: `204` with an empty body, then `404`.

### Rate limiting

Allow at least a minute after the earlier commands, then send 65 requests:

```bash
for i in $(seq 1 65); do curl -s -o /dev/null -w "%{http_code} " -H "X-API-Key: $KEY" $BASE/api/incidents; done; echo
```

Expected: 60 × `200` followed by 5 × `429`. A `429` response includes a `Retry-After` header giving the seconds to wait. If you made other requests in the same minute, the `429`s start sooner.

## Testing with Postman

1. Import `postman/OpsTrack.postman_collection.json`.
2. Open the collection's **Variables** tab and paste your key into the `apiKey` variable's **Current value**. Do not export or commit the collection with a real key in it.
3. Run the whole collection in order with the **Run collection** option. Requests share the created incident's ID, so running one alone may fail.

Expected: every test passes.

To run the same collection from the command line, which needs Node.js:

```bash
npx newman run postman/OpsTrack.postman_collection.json --env-var "apiKey=$KEY"
```
