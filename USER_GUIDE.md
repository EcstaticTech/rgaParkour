# rgaParkour Companion User Guide

**Target Environment**: PaperMC 26.2 (Build 71+)  
**Java Standard**: Java 25 (Bytecode Version 69)  
**Core Framework Engine**: `ronlabgameassistant` / `rga-core` (`com.ronlab:rga-api:1.13.1`)

---

## 1. Overview & CPMK Architectural Pillars

`rgaParkour` is a native companion minigame plugin designed under the **Micro-Companion Architecture (CPMK)** integration standard established in `rga-core`. It operates strictly through event-driven mechanics decoupled from core internals.

### The 5 CPMK Pillars

1. **Core Gameplay Function Retention**:
   - Preserves 100% of native parkour game mechanics, custom tick loops, checkpoint registries, and spectator transitions.
   - Operates independent local FastBoard scoreboards with isolated session scoreboards and teams for active participants.

2. **Ronlab Integration Standard**:
   - Listens strictly for CPMK event payloads: `MinigameStartEvent` and `MinigameConcludeEvent` from `com.ronlab.rga.api.event.*`.
   - `paper-plugin.yml` specifies `api-version: '26.2'`, registers `RonlabGameAssistant` under `dependencies.server` with `required: true` and `join-classpath: true`, and contains NO invalid `load: BEFORE` directives.

3. **Baseline Structure & Rules Provision**:
   - Local scoreboards utilize FastBoard packet rendering to ensure sidebar margin numbers are suppressed (equivalent to PaperMC's `objective.numberFormat(NumberFormat.blank())`).
   - Player scoreboard assignment (`setupPlayerBoard`) occurs strictly during post-teleport spawn phases to prevent chunk-loading hangs.
   - Teardown routines invoke `removeSessionScoreboards`, unregister session teams, and restore players to the server main scoreboard on `MinigameConcludeEvent`.

4. **Companion-Type Agnostic Design**:
   - Built as a self-contained module that communicates with `rga-core` exclusively through the `rga-api` event bus.

5. **Feature Implementation & Modification Specs**:
   - Fully documents configuration schemas and administrative models.
   - Supports **Solo QA Developer Mode** (`initialPlayerCount == 1`) with frozen win conditions for continuous map and mechanics testing.

---

## 2. Minigame Mechanics & Rules

`rgaParkour` provides procedural, real-time multiplayer parkour racing:

1. **Multi-Spawn Vector Dispatching**:
   - Upon match start, `SpawnVectorParser` reads `spawn-vectors` defined in the map's `map.yml`.
   - Players are dispatched round-robin across starting pads (`spawnVectors.get(i % spawnVectors.size())`), eliminating spawn overlap and starting congestion.

2. **Collision Isolation (`pk_runners`)**:
   - Active runners are registered into the session team `pk_runners` with `COLLISION_RULE = OptionStatus.NEVER` and `canSeeFriendlyInvisibles = true`.
   - Players cannot push or displace each other while attempting single-block jumps. Standard player models are preserved without packet ghosting overhead.

3. **Pre-Match Freeze & Countdown (3 Seconds)**:
   - When a session starts, players are placed at their assigned spawn and frozen for 3 seconds to allow client chunk rendering.
   - Displays a title countdown (`3`, `2`, `1`, `GO!`) and plays note block audio prompts (`BLOCK_NOTE_BLOCK_PLING` / `HARP`).
   - X/Y/Z position is locked while camera pitch and yaw controls remain free.

4. **Checkpoint Detection & Fall Tracking**:
   - Stepping on configured pressure plates (`LIGHT_WEIGHTED_PRESSURE_PLATE`, `GOLD_PRESSURE_PLATE`) registers a checkpoint.
   - Plays an audio cue (`ENTITY_EXPERIENCE_ORB_PICKUP`) and snapshots the checkpoint location for recovery.
   - Personal checkpoints and fall counts are recorded in real time.

5. **Fail, Fall Bounds & Void Interception**:
   - If a runner falls below Y-level $-60.0$ (`game.fall-threshold-y`), contacts hazards (`LAVA`, `WATER`), takes `DamageCause.VOID`, or encounters lethal damage, a fail reset is triggered.
   - Bypasses vanilla player death screens and server hub respawn logic by immediately executing an asynchronous teleport (`player.teleportAsync(checkpoint)`).
   - Grants temporary Resistance invulnerability for 1 second (`game.invulnerability-seconds-on-fail`) and plays `ENTITY_ENDERMAN_TELEPORT`.

6. **Finish Line & Split Timing**:
   - Stepping on finish plates (`HEAVY_WEIGHTED_PRESSURE_PLATE`, `IRON_PRESSURE_PLATE`) records exact match finish time in milliseconds.
   - Formats split time (`MM:SS ✔`) on the live FastBoard scoreboard.
   - Transitions finished runners into spectator mode (`rga.setSpectator(player, true)`).
   - Triggers `requestSessionConclude("Party Completion")` once all active runners finish.

---

## 3. Scoreboard Lifecycle & High-Frequency (2-Tick) Updates

`rgaParkour` manages sidebar scoreboards via shaded FastBoard (`fr.mrmicky.fastboard`):
- **Isolated Scoreboard Assignment**: Each session allocates an isolated Bukkit `Scoreboard` instance, preventing sidebar stripping or conflict with `RonlabAnnouncer` during world transitions.
- **High-Frequency Update Loop (2 Ticks / 100ms)**: Updates match elapsed time and split times smoothly without FastBoard packet flicker.
- **Personal Metrics Rendering**:
  - Top Section: Match Elapsed Time (`PARKOUR RACE (MM:SS)`)
  - Personal Stats: `Checkpoints: X`, `Falls: Y`
  - Standings: Active runner progression and finished split times (`01:12 ✔`) with 14-character name truncation.
- **Clean Teardown**: Upon `MinigameConcludeEvent` or player disconnection, player boards are deleted, teams unregistered, and players restored to the main server scoreboard.

---

## 4. Solo QA Developer Mode (`initialPlayerCount == 1`)

### Testing Workflow
When a minigame match is initialized with a single player (`initialPlayerCount == 1`):
- **Win Condition Freeze**: The session does NOT auto-conclude upon reaching the finish line.
- **Continuous Map Testing**: QA developers can test checkpoint triggers, fall reset boundaries, Y-thresholds, and finish line logic repeatedly in a single session without requiring server restarts or match re-queuing.

---

## 5. Administrative Commands & Permission Nodes

`rgaParkour` is completely **command-less** and **permission-less**:
- All session lifecycles are controlled automatically by `rga-core` via `MinigameStartEvent` and `MinigameConcludeEvent`.
- No local player or admin slash commands are registered in `paper-plugin.yml`.
- Access control and match creation are handled centrally by the `rga-core` engine.

---

## 6. Configuration Reference

### Plugin Configuration (`config.yml` / `settings.yml`)

```yaml
# ==============================================================================
# rgaParkour v1.0.0 Default Configuration Schema (rga-api:1.13.1 compliant)
# Companion Plugin for Ronlab Game Assistant (RGA) - PaperMC 26.2 / Java 25
# ==============================================================================

# rga-api:1.13.1 Compliant Session Configuration
parkour:
  # Maximum time in seconds before match concludes. Set to 0 for unlimited.
  time-limit-seconds: 0

  # Fall threshold Y-level before teleporting player back to checkpoint
  fall-threshold-y: -60.0

parkour-kit:
  # Block materials that trigger checkpoint registration and audio cues when stepped on
  checkpoint-materials:
    - "LIGHT_WEIGHTED_PRESSURE_PLATE"
    - "GOLD_PRESSURE_PLATE"

  # Block materials that trigger finish line completion and spectator transition
  finish-materials:
    - "HEAVY_WEIGHTED_PRESSURE_PLATE"
    - "IRON_PRESSURE_PLATE"

  # Fluid or hazard materials that trigger immediate fail reset to last checkpoint
  fail-materials:
    - "LAVA"
    - "WATER"

game:
  # Y-coordinate threshold below which players are instantly teleported to their active checkpoint
  fall-threshold-y: -60.0

  # Maximum duration in seconds before match times out automatically (Set to 0 for unlimited)
  max-match-duration-seconds: 0

  # Duration in seconds of Resistance invulnerability applied to players upon fail reset
  invulnerability-seconds-on-fail: 1
```

### Map Template Configuration (`map.yml`)

Place `map.yml` inside the parkour map template folder:

```yaml
# Multi-player spawn coordinates (X, Y, Z or X, Y, Z, Yaw, Pitch)
spawn-vectors:
  - "10.5, 64.0, 10.5, 0.0, 0.0"
  - "12.5, 64.0, 10.5, 0.0, 0.0"
  - "14.5, 64.0, 10.5, 0.0, 0.0"
  - "16.5, 64.0, 10.5, 0.0, 0.0"
```
