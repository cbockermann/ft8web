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

## Report statistics (SQLite)

The spots the feeder sends are aggregated into a small embedded **SQLite**
database (`FT8_STATS_FILE`, default `ft8web-stats.db`) — counts per minute,
broken down by **band** and **Maidenhead grid**. Raw spots are never stored, and
old rows are pruned after `FT8_STATS_RETENTION_DAYS` (default 365).

The **Stats** view shows, over a selectable range (hour / day / week / month):
reports over time, a per-band breakdown, and an activity heatmap of where you
reported from (field or square resolution, optional band filter). The data is
served from `GET /api/stats`. The SQLite file is also available for ad-hoc
queries and future features.

## Live report diagnostics (PSKReporter MQTT)

The **Reports** view is a diagnostic tool: enter any callsign and ft8web
subscribes to PSKReporter's public **MQTT** gateway (`mqtt.pskreporter.info`)
and streams that station's live reports into the page — who is hearing it
(**Heard**), who it is hearing (**Hearing**), or **Both**.

The results are shown two ways:

- A **reception map** of the Maidenhead cells (field or square resolution) where
  the callsign was received — i.e. the locators of the *other* station in each
  report — so you get an at-a-glance picture of where the call is getting out.
  Each cell is coloured by the **mean reception level (SNR)** on a sequential
  warm scale (see the legend); hovering a cell shows its **min / max / avg dB**
  and a small bar chart of every report's dB in arrival order. Pan/zoom and
  World/Europe presets, same map as the overview.
- A **live list** below it: time, sender → receiver (with optional country
  flags), band, mode, SNR and grids.

The backend keeps one shared MQTT connection and reference-counts the per-call
topic subscriptions, relaying matching reports to the browser over SSE
(`GET /api/mqtt/reports?call=DL1ABC&role=both`). It only **subscribes**; nothing
is published. PSKReporter delivers per station in bursts (stations upload every
few minutes), so a freshly watched call may sit idle a short while before its
next batch arrives. Broker host/port are configurable (`PSKREPORTER_MQTT_HOST` /
`PSKREPORTER_MQTT_PORT`).

## Locator / callsign lookup

Two offline lookups power the feed annotations and the REST API:

- **Locator → place** by point-in-polygon against bundled Natural Earth borders
  (`regions.json`, 10m admin-1), giving country **and finest region**:
  - `GET /api/locator/JO62` → `{ grid, lat, lon, country, region, cc, flag }`
  - `GET /api/locators?grids=JO62,FN42,...` → a map (used to annotate the feed)
- **Callsign → country** by prefix (table derived from DXCC `cty.dat`):
  - `GET /api/callsign/DL1ABC` → `{ call, cc, country, flag }`
  - `GET /api/callsigns?calls=DL1ABC,W1AW,...` → a map

In the **feed** and the overview **mini-feed**, two checkboxes toggle:

- **Flags** — a country flag next to each callsign (from its prefix), e.g.
  `CQ DL1ABC🇩🇪 JO31`.
- **Meta** — the locator's country + region in parentheses (kept out of the
  message text), e.g. `… JO62 (🇩🇪 Germany · Brandenburg)`.

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
| `FT8_BACKFILL_MINUTES` | 15           | default startup replay minutes (0 = off); editable on the Config page |
| `FT8_STATS_FILE`     | ft8web-stats.db | SQLite file for report statistics       |
| `FT8_STATS_RETENTION_DAYS` | 365      | how long to keep report statistics        |
| `FT8_BRIDGE_ENABLED` | false          | run the bundled UDP→Kafka bridge          |
| `FT8_BRIDGE_BIND`    | 0.0.0.0        | bridge: interface to listen on            |
| `FT8_BRIDGE_PORT`    | 5001           | bridge: UDP port (ft8mon `-json` target)  |
| `FT8_BRIDGE_KEY`     | band           | bridge: JSON field → Kafka key (blank for none) |
| `FT8_BRIDGE_VALIDATE`| false          | bridge: skip non-JSON datagrams           |
| `PSKREPORTER_KAFKA_GROUP` | pskreporter-_&lt;hostname&gt;_ | feeder's own consumer group    |
| `PSKREPORTER_SOFTWARE` | ft8web       | decoder name reported to PSKReporter      |
| `PSKREPORTER_MQTT_HOST` | mqtt.pskreporter.info | MQTT gateway for the live-report diagnostics |
| `PSKREPORTER_MQTT_PORT` | 1883         | MQTT gateway port (plain TCP)             |

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
