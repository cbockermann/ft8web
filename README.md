# FT8 Monitor — web application

A real-time monitoring web app for FT8 radio decodes. It is a downstream Kafka
consumer of the ft8mon pipeline and shows decodes in the browser as a
**master-detail** view:

- **Live feed (left):** decodes streaming in, newest on top.
- **QSL detail (right):** click a message → the QSO ("QSL") it belongs to.

See [CLAUDE.md](CLAUDE.md) for the full domain and pipeline context.

## Architecture

```
Kafka topic ft8.decodes
        │  (spring-kafka @KafkaListener, own consumer group)
        ▼
Spring Boot backend (Java 21)
   ├─ QsoGrouper: rolling in-memory view (~1h), evict older decodes
   ├─ SSE   GET /api/stream         live decode feed (server → browser)
   ├─ REST  GET /api/qsl/{decodeId} the QSO for a given decode
   └─ serves the static React SPA
        ▼
Browser SPA (React) — master-detail
```

- **Backend:** Spring Boot + spring-kafka. Live push via Server-Sent Events;
  QSL detail via REST. Package root `de.bockermann.ft8monitor`.
- **State:** in-memory rolling window (no DB).
- **Frontend:** React + Vite, built into `src/main/resources/static/`.

## Build & run

Full build (bundles the React SPA into the jar via the `frontend` Maven
profile, which downloads its own Node):

```bash
./mvnw clean package
java -jar target/ft8-monitor-0.0.1-SNAPSHOT.jar
```

Backend only (skip the frontend build — useful when the SPA is already built or
during backend work):

```bash
./mvnw -P '!frontend' spring-boot:run
```

Then open http://localhost:8080.

### Frontend dev server (hot reload)

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173, proxies /api → :8080
```

Run the backend (`spring-boot:run`) alongside it.

## Configuration

All settings are overridable via environment variables (see
[application.yml](src/main/resources/application.yml)):

| Env var              | Default        | Meaning                                   |
|----------------------|----------------|-------------------------------------------|
| `KAFKA_BOOTSTRAP`    | localhost:9092 | Kafka bootstrap servers                   |
| `KAFKA_GROUP`        | ft8-monitor    | consumer group id                         |
| `KAFKA_OFFSET_RESET` | latest         | `latest` (live) or `earliest` (backfill)  |
| `FT8_TOPIC`          | ft8.decodes    | topic to consume                          |
| `FT8_WINDOW_SECONDS` | 3600           | rolling retention window                  |
| `FT8_GAP_SECONDS`    | 180            | QSO split threshold                       |

## Testing without the real pipeline

Run under the `dev` profile to enable a helper that publishes decodes onto the
topic (requires a reachable Kafka broker):

```bash
./mvnw -P '!frontend' spring-boot:run -Dspring-boot.run.profiles=dev
```

```bash
curl -X POST localhost:8080/api/dev/publish \
     -H 'Content-Type: application/json' \
     -d '{"msg":"CQ DX AB1HL FN42","band":"40m","snr":-12,"freq":1083}'
```

## Tests

```bash
./mvnw -P '!frontend' test
```

Unit tests cover the callsign parser and the QSO grouper (port of ft8mon's
`scripts/qsls.py`); an `@EmbeddedKafka` smoke test verifies the Spring wiring.
