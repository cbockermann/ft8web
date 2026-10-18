# FT8 Monitor — web application

A real-time monitoring web app for FT8 radio decodes. It is a downstream Kafka
consumer of the ft8mon pipeline and shows decodes in the browser as a
**master-detail** view:

- **Live feed (left):** decodes streaming in, newest on top.
- **QSL detail (right):** click a message → the QSO ("QSL") it belongs to.

See [CLAUDE.md](CLAUDE.md) for the full domain and pipeline context.

## Architecture

```
ft8mon -json host:5001
        │  (UDP JSON datagrams)
        ▼
[ optional bundled UDP→Kafka bridge ]  ──►  Kafka topic ft8
        │  (spring-kafka @KafkaListener, own consumer group)
        ▼
Spring Boot backend (Java 21)
   ├─ UdpKafkaBridge: optional UDP→Kafka bridge (ft8.bridge.enabled)
   ├─ QsoGrouper: rolling in-memory view (~1h), evict older decodes
   ├─ GridFieldAggregator: per-field/square activity for the overview map
   ├─ PskReporterFeeder: optional spot reporter (own consumer group)
   ├─ SSE   GET /api/stream         live decode feed (server → browser)
   ├─ REST  GET /api/qsl/{decodeId} the QSO for a given decode
   └─ serves the static React SPA
        ▼
Browser SPA (React) — overview map · feed · config
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

## Bundled UDP→Kafka bridge (optional)

Normally ft8mon's UDP decodes reach Kafka via ft8mon's own
`scripts/udp_kafka_bridge.py`. ft8web can run that bridge itself, so the monitor
host is a single process instead of app + Python script. The data path is
unchanged (UDP → Kafka → consumer); Kafka stays the backbone.

Enable it on the host that receives ft8mon's `-json` output:

```bash
FT8_BRIDGE_ENABLED=true java -jar target/ft8-monitor-0.0.1-SNAPSHOT.jar
```

Point ft8mon at it (default UDP port 5001):

```bash
./ft8mon -listen :8073 -json <monitor-host>:5001 -tag band:40m
```

Off by default — when disabled, no UDP port is opened and ft8web is a pure Kafka
consumer as before. See the `FT8_BRIDGE_*` settings below.

## PSKReporter feeder (optional)

ft8web can report the stations it hears to [PSKReporter](https://pskreporter.info)
as an additional, switchable Kafka consumer (its own consumer group, so it never
disturbs ingest or the UI). It builds standard IPFIX datagrams (the WSJT-X
format, PSKReporter enterprise number 30351) and sends them over UDP — no
account or authentication is needed; your identity is just your callsign and
locator.

It is **configured on the Config page** (persisted in the settings file next to
callsign/locator), so it can be toggled at runtime without a restart:

- **Enable** — report observed stations.
- **Antenna** — optional free-text.
- **Host / Port** — `report.pskreporter.info` : `4739` (use test port `14739`
  first), **Interval** — ≥ 120 s (default 300).

It reports only decodes that carry a real transmit frequency (`rf_hz`, i.e. SDR
sources that know their dial), de-duplicates to one spot per station+band per
interval, and applies a strict callsign filter so `i3=` mis-decodes and noise
are never reported. Your own callsign is never self-reported. Disabled by
default. Verify incoming spots by filtering for your call on pskreporter.info.

## Configuration

All settings are overridable via environment variables (see
[application.yml](src/main/resources/application.yml)):

| Env var              | Default        | Meaning                                   |
|----------------------|----------------|-------------------------------------------|
| `SERVER_PORT`        | 8080           | HTTP port                                 |
| `SERVER_SERVLET_CONTEXT_PATH` | _(root)_ | mount everything under a path, e.g. `/ft8` |
| `KAFKA_BOOTSTRAP`    | 192.168.50.59:9092 | Kafka bootstrap servers               |
| `KAFKA_GROUP`        | ft8-monitor-_&lt;hostname&gt;_ | consumer group id (per-host by default) |
| `KAFKA_OFFSET_RESET` | latest         | `latest` (live) or `earliest` (backfill)  |
| `FT8_TOPIC`          | ft8            | topic to consume (bridge produces here too) |
| `FT8_WINDOW_SECONDS` | 3600           | rolling retention window                  |
| `FT8_GAP_SECONDS`    | 180            | QSO split threshold                       |
| `FT8_BACKFILL_MINUTES` | 15           | replay this many minutes on startup (0 = off) |
| `FT8_BRIDGE_ENABLED` | false          | run the bundled UDP→Kafka bridge          |
| `FT8_BRIDGE_BIND`    | 0.0.0.0        | bridge: interface to listen on            |
| `FT8_BRIDGE_PORT`    | 5001           | bridge: UDP port (ft8mon `-json` target)  |
| `FT8_BRIDGE_KEY`     | band           | bridge: JSON field → Kafka key (blank for none) |
| `FT8_BRIDGE_VALIDATE`| false          | bridge: skip non-JSON datagrams           |
| `PSKREPORTER_KAFKA_GROUP` | pskreporter-_&lt;hostname&gt;_ | feeder's own consumer group    |
| `PSKREPORTER_SOFTWARE` | ft8web       | decoder name reported to PSKReporter      |

PSKReporter's user-facing settings (enable, antenna, host, port, interval) are
edited on the **Config page**, not via env vars — they live in the settings file
with callsign/locator.

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
