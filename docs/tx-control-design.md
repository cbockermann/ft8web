# FT8 transmit control — design & MVP plan

Status: **design / MVP spike**. This document is the reference for extending
ft8web from a passive monitor into an operator console that can **transmit** FT8
(auto-respond now, auto-CQ later), driving an IC-7300.

It is intentionally built around a clean **backend abstraction** so the MVP
(driving a running **WSJT-X**) can be swapped, piece by piece, for an own
**ft8-tx-daemon** (Hamlib + `ft8_lib`) without touching the UI, the QSO logic or
the logging.

---

## 1. What changes

ft8web today is a passive downstream consumer (Kafka → decodes → SSE/REST → UI;
"does not touch the radio"). Transmitting FT8 means: encode a message
(77-bit → LDPC → 79 Costas symbols), generate audio **UTC-slot-aligned** (every
15 s), play it into the rig's sound card, and key PTT/CAT — plus it enters the
regulated territory of **(semi-)unattended transmit operation**. So the TX part
is kept separate and behind hard safety guards.

**MVP decision:** run the TX through the user's **already-working WSJT-X** on the
same Mac as ft8web (IC-7300 over USB). WSJT-X owns all hardware-near concerns
(CAT, USB audio, encode/modulate/decode, slot timing, an auto-sequencer) and
exposes an open **UDP protocol**. ft8web becomes a pure UDP client — no DSP, no
hardware contention.

**Accepted MVP limitation:** the WSJT-X UDP protocol can start/continue a QSO by
replying to a decode, halt TX, and report rich status — but it **cannot start a
CQ, send free text, or set dial-QRG/mode/power**. Therefore:

- **Auto-respond** (answer CQs / callers) — fully supported in the MVP.
- **Auto-CQ** — comes with the own daemon in a later phase.

This is fine for the MVP: the goal is to gather UX experience for the final
implementation.

---

## 2. Architecture

### MVP (now): drive WSJT-X over UDP

```
  IC-7300 ──USB── WSJT-X (CAT + USB audio + encode/decode + auto-seq)   [Mac]
                     │  UDP 127.0.0.1:2237  (WSJT-X UDP Message Protocol)
                     ▼
                  ft8web  [same Mac]
                   ├─ WsjtxBackend / WsjtxUdpDecodeSource   (UDP client only)
                   ├─ QSO state machine + automation policies
                   ├─ Operator view (React): status, PTT, pick target, STOP
                   └─ QSO log (ADIF) in the existing SQLite
```

ft8web speaks **only UDP** to WSJT-X; it does not touch serial/audio, so WSJT-X
keeps exclusive radio/audio ownership — no contention.

### Target (later): own ft8-tx-daemon

```
  IC-7300 ──USB── rigctld (Hamlib: CAT/PTT/QRG/mode/power/S-meter)
                ├─ ft8mon  → UDP JSON → bridge → Kafka   (RX, exists)
                └─ ft8-tx-daemon (NEW): ft8_lib encode+modulate, slot-aligned
                     audio out, PTT via Hamlib, watchdog/inhibit
                        │  control channel (Kafka ft8.tx.commands/.status, or MQTT)
                        ▼
                     ft8web  (operator console + automation — UNCHANGED above the port)
```

The daemon is deliberately **dumb and deterministic** ("transmit exactly this
message in this slot at this audio freq"); ft8web keeps the intelligence (who to
answer, QSO flow, dupe check). Slot timing is owned locally by WSJT-X / the
daemon; ft8web only has to deliver the intent *before* the slot starts (~1–2 s of
slack on a LAN/localhost).

### Middleware

- **Hamlib / `rigctld`** is the CAT middleware for the daemon phase (IC-7300 well
  supported; a later rig = just another Hamlib model). This is the hardware-level
  "Zwischenschicht".
- **`StationBackend`** (below) is the software-level abstraction inside ft8web, so
  the app is decoupled from *which* backend fulfils the intents (WSJT-X now,
  daemon later, something else in future).

---

## 3. The abstraction

Two distinct things get ports; the **encoder is not a separate port** — it lives
inside the TX backend and is reached through high-level intents + capability
flags. The backend-independent *message string* layer is factored out for reuse.

### Normalised domain (backend-independent)

```java
record Decode(Instant time, int snr, double dt, double audioHz,
              long dialHz, String mode, String message, String id) {}

enum Parity { EVEN, ODD }   // 15-s slot parity (:00/:30 vs :15/:45)

record StationStatus(long dialHz, String mode, boolean ptt, boolean transmitting,
                     boolean txEnabled, Parity txParity, String dxCall,
                     Integer clockOffsetMs, boolean backendUp) {}
```

### Decoder behind a port

```java
interface DecodeSource {
    AutoCloseable subscribe(Consumer<Decode> onDecode);
}
// impls: WsjtxUdpDecodeSource (UDP, drives TX decisions in the MVP),
//        KafkaDecodeSource     (existing ft8mon stream, feeds the monitor)
```

Both can run at once: WSJT-X decodes drive TX (a `Reply` needs WSJT-X's own view
of the decode), the existing Kafka stream keeps feeding the monitor UI. They
converge later.

### TX / station behind a port, capability-gated

```java
// What a backend can do — the UI greys out what it can't.
record Capabilities(boolean autoSequencer, boolean callCq, boolean freeText,
                    boolean setFrequency, boolean setMode, boolean setPower) {}

interface StationBackend {
    Capabilities capabilities();
    AutoCloseable onStatus(Consumer<StationStatus> listener);

    void replyTo(Decode target);   // start/continue a QSO with that station
    void halt();                   // stop TX now

    // capability-gated — throw UnsupportedOperationException if not supported:
    void callCq(CqPolicy policy);
    void transmit(Ft8Message msg, Slot slot, double audioHz);
    void setDialFreq(long hz);
    void setMode(String mode);
    void setPower(int watts);
}
// impls: WsjtxBackend     (UDP: replyTo/halt/status; setters + callCq unsupported)
//        Ft8DaemonBackend (later: everything)
```

WSJT-X capabilities: `autoSequencer=true`, `callCq=false`, `freeText=false`,
`setFrequency/Mode/Power=false`. Daemon: all `true`.

### Reusable, backend-independent FT8 message layer

```java
// Formats/parses the FT8 message STRINGS; used by the state machine, logging,
// and (later) the daemon. WSJT-X doesn't need it (it builds its own messages).
final class Ft8MessageBuilder {
    // "CQ MY GRID", "CALL MY GRID", "CALL MY R-12", "CALL MY RR73", "CALL MY 73"
    // + parse an incoming message into {caller, called, grid, report, kind}
}
record Ft8Message(String text, Ft8Message.Kind kind) { enum Kind { CQ, GRID, REPORT, R_REPORT, RR73, SEVENTY_THREE, FREE } }
```

**Why the encoder isn't its own port:** with WSJT-X we never encode — we express
*intent* (`replyTo`) and WSJT-X encodes+modulates. With the daemon, `ft8_lib`
encodes behind the *same* intents (`transmit` / `callCq`). The only genuinely
shared, backend-independent nugget is the message **string** formatting/parsing,
which is `Ft8MessageBuilder`.

---

## 4. QSO state machine

Sits **above** `StationBackend` + `DecodeSource`, backend-agnostic. It branches
on `capabilities().autoSequencer()`:

- **Delegate mode (WSJT-X, autoSequencer = true):** thin. Pick a target by
  policy → `replyTo(decode)` → observe `StationStatus` / `QSOLogged` → log &
  apply policy (next target, stop, etc.). WSJT-X runs the on-air sequence.
- **Drive mode (daemon, autoSequencer = false):** the same policy layer drives
  **every slot itself** via `transmit()` + `Ft8MessageBuilder`, holding TX parity
  and running the full sequence.

Standard sequences:

```
Auto-respond:  RX CQ [DX] CALL GRID → TX CALL MY GRID → RX MY CALL -rpt
            → TX CALL MY R-rpt → RX MY CALL RR73 → TX CALL MY 73 → log → next
Auto-CQ:       TX CQ MY GRID (each own slot) → caller → report exchange
            → RR73 → log → back to CQ            (daemon phase)
```

### Comfort policies (ft8web's strength — the lookups already exist)

- **Target selection:** first caller / best SNR / **only new DXCC or new grid**
  (via `GeoLookup` / `CallsignFlags`), whitelist / blacklist, prefer `CQ DX`.
- **Dupe protection:** skip calls already worked (today / on this band).
- **Limits:** max retries per step, per-QSO timeout, "stop after current QSO".
- **Operating mode (default = safe):**
  - **Monitor** (no TX),
  - **Semi-auto** (ft8web proposes the next action, operator confirms),
  - **Full-auto** (unattended) — only after explicit opt-in + confirmation.

---

## 5. Safety / legal / operational (not optional)

- (Semi-)unattended transmit is regulated (in DE: AFuG/AFuV); the licensed
  operator is responsible. → Full-auto **off by default**, explicit opt-in,
  callsign/band/QRG whitelist, confirm before the first transmission.
- **Guards:** watchdog halts TX if the control link / heartbeat drops; max TX
  duration; only transmit on confirmed PTT; global **TX-inhibit** (armed
  explicitly). WSJT-X's own **Tx Watchdog** complements this in the MVP.
- **Dry-run mode:** everything runs, no PTT — for development & first bring-up.
- **Duty cycle / PA:** FT8 is ~100 % duty → run the IC-7300 at **reduced power**
  (~25–40 W); warn in the UI. (Power set in WSJT-X for the MVP.)
- **Dummy load** for all transmit tests in early phases.
- **Audit log** of every transmission.

---

## 6. Timing & clock

- The station host must be **NTP/chrony-synced** (FT8 needs < ~1–2 s). The
  backend reports `clockOffsetMs`; ft8web warns on drift.
- Slot timing is owned by WSJT-X / the daemon; ft8web only delivers intent before
  the slot. A late intent → deferred to the next valid slot + reported.
- **Frequency ownership:** only one component sets CAT QRG. In the MVP that is
  WSJT-X (operator). With the daemon, the daemon/ft8web owns it; ft8mon must only
  decode, not also drive CAT, to avoid contention. In our own TX slot RX is muted
  anyway — parity discipline handles it.

---

## 7. WSJT-X UDP specifics (MVP)

- Enable in WSJT-X: *Settings → Reporting → UDP Server* = `127.0.0.1:2237`,
  **"Accept UDP requests"** on (so `Reply`/`Halt` work).
- Protocol: `QDataStream`, big-endian, magic `0xadbccbda`, schema 2+.
  - **In (WSJT-X → us):** Heartbeat(0), Status(1), Decode(2), Clear(3),
    QSOLogged(5), Close(6), LoggedADIF(12).
  - **Out (us → WSJT-X):** Reply(4), Halt Tx(8), (HighlightCallsign(13),
    optional). **No CQ-start, no free-text, no set-QRG** — see §1 limitation.
- A small `WsjtxUdpCodec` encodes/decodes these datagrams; `WsjtxBackend` maps
  intents to Reply/Halt and `onStatus`/`DecodeSource` to the inbound messages;
  `QSOLogged`/`LoggedADIF` feed our QSO log.

---

## 8. Phases

| Phase | Goal | Deliverable | TX? |
|---|---|---|---|
| **0 — WSJT-X link (read-only)** | `WsjtxUdpCodec` + `WsjtxBackend` / `WsjtxUdpDecodeSource`; show status + decodes in a new Operator view | Live status (QRG/mode/PTT/parity) + decode list from WSJT-X | ❌ |
| **1 — Auto-respond** | `replyTo()` + "work this caller/CQ" button, Halt button, QSO log from `QSOLogged`; semi-/full-auto respond with policies (new DXCC/grid, dupe, whitelist) | Comfortable auto-responding | ✅ |
| **2 — UX iteration** | Play with the operator UX on real QSOs, refine policies/controls, gather experience | Validated UX + requirements for the daemon | ✅ |
| **3 — Own daemon** | `Ft8DaemonBackend` (Hamlib + `ft8_lib`): auto-CQ, free text, QRG/mode/power — swap the backend only | Full auto-CQ, headless-capable (Pi) | ✅ |

The upper layers (state machine, policies, UI, logging) stay put across the
backend swap; only the `StationBackend` impl changes and capability flags flip on.

---

## 8a. Band selection & retuning (MVP)

Bands are configurable on the Config page (name + FT8 dial frequency + whether
shown); defaults are 80 m (3.573), 40 m (7.074), 20 m (14.074), 10 m (28.074),
stored in the settings file. The Control view offers the enabled bands and
highlights the one matching WSJT-X's current dial frequency.

Retuning the rig is **not** possible over the WSJT-X UDP protocol (no
set-frequency command). ft8web therefore sets the frequency via Hamlib
**rigctld** (`RigControl`, `ft8.rigctld.*`, off by default). This needs shared
CAT: run `rigctld` and point WSJT-X at "Hamlib NET rigctl" (same host:port) so
WSJT-X keeps working and follows the frequency we set — its Status then reports
the new QRG back to the Control view. Without rigctld the band picker explains
how to enable it. This is the first concrete use of the Hamlib middleware from
the daemon plan, introduced early just for the retune path.

## 9. Reused from ft8web

`Callsigns` (parsing), `QsoGrouper` (sequence/dupe logic), `GeoLookup` /
`CallsignFlags` (new DXCC/grid for target selection), `SettingsService` (TX
settings), the **SSE infra** (live operator status) and **SQLite** (QSO log /
stats).

---

## 10. Open items / notes

- Optional: a `WsjtxUdpDecodeSource` could also bridge WSJT-X decodes into the
  existing Kafka topic, unifying the monitor on one host — not required.
- Control channel for the daemon phase: **Kafka** (consistent with the pipeline)
  vs **MQTT** (lighter on the Pi; client dep already present) — decide at phase 3.
- Hard limits (power ceiling, max TX seconds, allowed bands) belong in
  `application.yml`; operator-facing policy in `SettingsService`.
