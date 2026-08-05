# rgaParkour Companion User Guide

**Target Environment**: PaperMC 26.2 (Build 71+)  
**Java Standard**: Java 25 (Bytecode Version 69)  
**Core Framework Engine**: `ronlabgameassistant` / `rga-core` (`com.ronlab:rga-api:1.13.0-SNAPSHOT`)

---

## 1. Overview & CPMK Architectural Pillars

`rgaParkour` is a native companion minigame plugin designed under the **Micro-Companion Architecture (CPMK)** integration standard established in `rga-core`. It operates strictly through event-driven mechanics decoupled from core internals.

### The 5 CPMK Pillars

1. **Core Gameplay Function Retention**:
   - Preserves 100% of native parkour game mechanics, custom tick loops, checkpoint registries, and spectator transitions.
   - Operates independent local FastBoard scoreboards for active session participants.

2. **Ronlab Integration Standard**:
   - Listens strictly for CPMK event payloads: `MinigameStartEvent` and `MinigameConcludeEvent` from `com.ronlab.rga.api.event.*`.
   - `paper-plugin.yml` specifies `api-version: '26.2'`, registers `RonlabGameAssistant` under `dependencies.server` with `required: true` and `join-classpath: true`, and contains NO invalid `load: BEFORE` directives.

3. **Baseline Structure & Rules Provision**:
   - Local scoreboards utilize FastBoard packet rendering to ensure sidebar margin numbers are suppressed (equivalent to PaperMC's `objective.numberFormat(NumberFormat.blank())`).
   - Player scoreboard assignment (`setupPlayerBoard`) occurs strictly during post-teleport spawn phases to prevent chunk-loading hangs.
   - Teardown routines invoke `removeSessionScoreboards` and unregister objectives on `MinigameConcludeEvent`.

4. **Companion-Type Agnostic Design**:
   - Built as a self-contained module that communicates with `rga-core` exclusively through the `rga-api` event bus.

5. **Feature Implementation & Modification Specs**:
   - Fully documents configuration schemas and administrative models.
   - Supports **Solo QA Developer Mode** (`initialPlayerCount == 1`) with frozen win conditions for continuous map and mechanics testing.

---

## 2. Minigame Mechanics & Rules

`rgaParkour` provides procedural, real-time parkour racing:

1. **Pre-Match Freeze & Countdown (3 Seconds)**:
   - When a session starts, players are centered at spawn and frozen for 3 seconds to allow chunk rendering.
   - Displays a title countdown (`3`, `2`, `1`, `GO!`) and plays note block audio prompts (`BLOCK_NOTE_BLOCK_PLING` / `HARP`).
   - X/Y/Z position is locked while camera pitch and yaw controls remain free.

2. **Checkpoint Detection**:
   - Stepping on configured pressure plates (`LIGHT_WEIGHTED_PRESSURE_PLATE`, `GOLD_PRESSURE_PLATE`) registers a checkpoint.
   - Plays an audio cue (`ENTITY_EXPERIENCE_ORB_PICKUP`) and snapshots the checkpoint location for recovery.

3. **Fail & Fall Recovery**:
   - If a runner falls below Y-level $-60.0$ (`game.fall-threshold-y`) or makes contact with hazard blocks (`LAVA`, `WATER`), a fail reset is triggered.
   - Teleports the player back to their latest checkpoint location with sound effect `ENTITY_ENDERMAN_TELEPORT`.
   - Grants temporary Resistance invulnerability for 1 second (`game.invulnerability-seconds-on-fail`) to prevent fall-damage feedback loops.

4. **Finish Line & Split Timing**:
   - Stepping on finish plates (`HEAVY_WEIGHTED_PRESSURE_PLATE`, `IRON_PRESSURE_PLATE`) records exact match finish time in milliseconds.
   - Formats split time (`MM:SS`) on the live FastBoard scoreboard (`01:12 ✔`).
   - Transitions finished runners into spectator mode (`rga.setSpectator(player, true)`).
   - Triggers `requestSessionConclude("Party Completion")` once all active runners finish.

---

## 3. Solo QA Developer Mode (`initialPlayerCount == 1`)

### Testing Workflow
When a minigame match is initialized with a single player (`initialPlayerCount == 1`):
- **Win Condition Freeze**: The session does NOT auto-conclude upon reaching the finish line.
- **Continuous Map Testing**: QA developers can test checkpoint triggers, fall reset boundaries, Y-thresholds, and finish line logic repeatedly in a single session without requiring server restarts or match re-queuing.

---

## 4. Administrative Commands & Permission Nodes

`rgaParkour` is completely **command-less** and **permission-less**:
- All session lifecycles are controlled automatically by `rga-core` via `MinigameStartEvent` and `MinigameConcludeEvent`.
- No local player or admin slash commands are registered in `paper-plugin.yml`.
- Access control and match creation are handled centrally by the `rga-core` engine.

---

## 5. Scoreboard Lifecycle & Rendering

`rgaParkour` manages sidebar scoreboards via shaded FastBoard (`fr.mrmicky.fastboard`):
- **Post-Teleport Assignment**: Scoreboard initialization is synchronized to the spawn phase post-teleport to prevent chunk-loading hangs.
- **Margin Number Suppression**: Uses FastBoard packet updates to eliminate default red sidebar margin numbers.
- **Clean Teardown**: Upon `MinigameConcludeEvent`, `removeSessionScoreboards` deletes player scoreboards and returns players to the main server scoreboard.

---

## 6. Configuration Reference (`config.yml` / `settings.yml`)

```yaml
# ==============================================================================
# rgaParkour v1.0.0 Default Configuration Schema
# Companion Plugin for Ronlab Game Assistant (RGA) - PaperMC 26.2 / Java 25
# ==============================================================================

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
  fall-threshold-y: -60

  # Maximum duration in seconds before match times out automatically (default: 300s / 5m)
  max-match-duration-seconds: 300

  # Duration in seconds of Resistance invulnerability applied to players upon fail reset
  invulnerability-seconds-on-fail: 1
```
