# FT8 Monitor — web application

A real-time monitoring web app for FT8 radio decodes. It consumes decodes
from Kafka and shows them in the browser as a **master-detail** view:

- **Live feed (left):** decodes streaming in, newest on top.
- **QSL detail (right):** click a message → the QSO ("QSL") it belongs to,
  i.e. the ordered list of all messages exchanged between the two stations.

This repo is the **web app only**. It is a downstream consumer of an
existing pipeline — it does not touch the radio or the decoder.

## Upstream context (already built, separate repo)

Decoder + tooling live in **ft8mon**: https://github.com/cbockermann/ft8mon
(C++ FT8 decoder, extended fork of Robert Morris / AB1HL). Relevant pieces:

Data pipeline already running:

```
ft8mon (-json host:port)  →  UDP JSON datagrams
   →  scripts/udp_kafka_bridge.py  →  Kafka topic  "ft8.decodes"
```

- ft8mon emits **one JSON object per decode, one UDP datagram each**.
- A Python bridge forwards each datagram to Kafka. The user has a **running
  Kafka broker**. The bridge can set the Kafka message **key = the `band`
  tag** (`--key band`).
- So this web app just needs to be **another Kafka consumer**. The grouper is
  always fed **from Kafka**, never from UDP directly.
- **Bundled bridge (optional):** ft8web can *also* run the UDP→Kafka bridge
  itself (`ft8.bridge.enabled=true`, see `UdpKafkaBridge`), a drop-in for
  `scripts/udp_kafka_bridge.py` so the monitor host is one process. It only
  **produces** to Kafka; the consumer path is unchanged. Off by default.

### Decode JSON schema (Kafka message value)

```json
{
  "time": "2026-07-21T22:04:30Z",   // decode time, UTC ISO-8601
  "unix": 1784671470,                // decode time, unix epoch seconds
  "snr": -12,                        // signal-to-noise ratio (int dB)
  "dt": 0.2,                         // time offset (seconds)
  "freq": 1083.0,                    // audio frequency (Hz)
  "correct_bits": 174,               // FEC correct-bit count
  "msg": "CQ DX AB1HL FN42"          // the decoded FT8 message text
}
```

Plus any `-tag key:value` fields ft8mon was started with, e.g.
`"band": "40m"`, `"site": "home"`. Fields beyond `msg`/`unix` may be absent
for some sources; code defensively.

Note: messages whose text begins with `i3=` are ft8mon's fallback for
message types it does not format; they are usually false decodes of noise
and should be ignored / filtered out.

## QSO ("QSL") grouping — the core domain logic

Reference implementation: `scripts/qsls.py` in the ft8mon repo. Port this to
Java. Algorithm:

1. Parse callsigns out of each `msg`:
   - Tokens are whitespace-separated. A callsign contains only `A-Z 0-9 /`
     (drop surrounding `<...>` hash brackets), has at least one letter AND
     one digit, and is NOT a grid (`[A-R]{2}[0-9]{2}` optionally + 2 more
     letters), NOT a report (`R?[-+]\d{1,2}`), NOT a sign-off
     (`RRR RR73 73 R73 TU TU73 QSL DE CQ DX`).
   - `CQ ...` → one caller (the first callsign after CQ, skipping a directive
     like DX/region).
   - Otherwise the first two callsign-like tokens are the two stations.
2. Group directed (two-call) messages by the **unordered pair** of calls.
3. Split messages of the same pair into **separate QSOs** when consecutive
   messages are more than `gap` seconds apart (default 180 s). A full FT8
   QSO runs ~1-2 minutes.
4. Attach each `CQ` to the earliest following QSO of its caller (within
   `gap`), so the QSO starts with the CQ that opened it.
5. A QSL = the two calls + the ordered list of its messages.

The backend should maintain this grouping **incrementally** as decodes arrive
(a rolling in-memory window, e.g. last ~60 min), and give each decode a
**stable id** (e.g. Kafka `partition@offset`, or a hash of `unix+freq+msg`)
so the frontend can request "the QSL for this message".

## Chosen architecture

```
Kafka topic ft8.decodes
        │  (spring-kafka @KafkaListener, own consumer group)
        ▼
Spring Boot backend
   ├─ QSO grouper: rolling in-memory view (~1h), evict older decodes
   ├─ SSE   GET /api/stream        live decode feed (server → browser)
   ├─ REST  GET /api/qsl/{decodeId}  the QSO for a given decode
   └─ serves the static frontend
        │
        ▼
Browser SPA (master-detail)
   ├─ EventSource(/api/stream) → left column: live messages
   └─ click message → fetch /api/qsl/{id} → right column: QSL history
```

Decisions:
- **Backend:** Spring Boot + `spring-kafka`. Live push via **Server-Sent
  Events** (simplest one-way feed); QSL detail via plain **REST**. WebSocket
  only if real bidirectional interaction is needed later.
- **State:** start **in-memory** (monitoring = recent activity). Add
  PostgreSQL / Kafka-Streams materialized view later only if history/search
  is needed. Do not add a DB up front.
- **Kafka consumer:** own consumer group; `auto.offset.reset` = `latest`
  (live) or `earliest` (to backfill the window on startup) — decide with user.
- **Frontend:** lightweight SPA (Svelte or React) or vanilla JS + `EventSource`;
  served from Spring Boot `static/`. Two-column master-detail layout.

## First tasks (scaffold)

1. Confirm with the user: build tool (**Gradle** vs Maven), Java version
   (default **21 LTS**), frontend flavor (Svelte / React / vanilla).
2. Generate a Spring Boot project (spring-web, spring-kafka; actuator
   optional). Sensible package root e.g. `de.bockermann.ft8monitor`.
3. Implement:
   - `Decode` record + a Kafka `@KafkaListener` on `ft8.decodes` (JSON
     deserialize), assigning each decode a stable id.
   - `QsoGrouper` (port of qsls.py) maintaining the rolling in-memory view.
   - `SseController` `/api/stream` broadcasting new decodes to subscribers.
   - `QslController` `/api/qsl/{decodeId}` returning the QSO's messages.
   - A minimal two-column frontend wired to both endpoints.
4. `application.yml`: Kafka bootstrap servers, topic, consumer group,
   offset reset, window size / eviction interval — all configurable.

Keep it runnable end-to-end early (even with a fake decode published to the
topic) before polishing the UI.

## User / working style

- Christian (callsign in his own decodes: he monitors amateur-radio FT8).
- Strong **Java** background; comfortable with Spring/Kafka.
- Workflow: for each change, create a **feature branch**, then commit + push,
  and open a PR (he merges). Do not commit straight to the default branch.
- End commit messages with the Co-Authored-By trailer; end PR bodies with the
  Claude Code generated-with line.
```
