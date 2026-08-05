# rgaParkour

**Version**: `1.0.0`  
**Target Server**: PaperMC 26.2 (Build 71+)  
**Java Standard**: Java 25 (Bytecode Version 69)  
**Core Framework**: Ronlab Game Assistant (`rga-core` / `rga-api:1.13.0-SNAPSHOT`)  

`rgaParkour` is a native companion minigame plugin built on the **Micro-Companion Architecture (CPMK)** framework engine (`ronlabgameassistant` / `rga-core`). It provides real-time procedural parkour match management, checkpoint tracking, fall and liquid recovery, pre-match frozen countdowns, spectator transitions, and packet-based FastBoard sidebar scoreboards.

---

## 1. Description & Mechanics

The `rgaParkour` companion delivers 100% native parkour mechanics while operating as an event-driven module:

- **CPMK Event Bus Integration**: Listens strictly for `MinigameStartEvent` and `MinigameConcludeEvent` from `rga-api` (`com.ronlab.rga.api.event.*`) to initialize, track, and clean up active sessions.
- **Checkpoint Detection**: Tracks player step actions on configured checkpoint materials (`GOLD_PRESSURE_PLATE`, `LIGHT_WEIGHTED_PRESSURE_PLATE`), rendering audio cues (`ENTITY_EXPERIENCE_ORB_PICKUP`) and recording unique checkpoint locations.
- **Fail & Fall Recovery**: Intercepts Y-level fall thresholds ($Y \le -60.0$) and hazard contact (`WATER`, `LAVA`), automatically teleporting players to their latest checkpoint while applying Resistance invulnerability.
- **Finish Line Mechanics**: Registers match finishes upon stepping on finish plates (`HEAVY_WEIGHTED_PRESSURE_PLATE`, `IRON_PRESSURE_PLATE`), calculates exact split times, transitions runners to spectator mode via RGA (`setSpectator`), and triggers party completion.
- **3-Second Pre-Match Countdown**: Holds players at spawn for 3 seconds upon session start for chunk loading, playing note block audio prompts, locking X/Y/Z movement while preserving camera pitch/yaw control, and synchronizing the match timer precisely to the `GO!` tick.
- **FastBoard Scoreboard**: Renders dynamic, packet-based sidebar scoreboards using shaded FastBoard (`fr.mrmicky.fastboard`), displaying live match time (`PARKOUR RACE (MM:SS)` or `STARTING (00:03)`), player checkpoint counts, finished split times (`01:12 ✔`), spectator status, and 14-character name truncation safeguards.

---

## 2. CPMK Architecture Alignment (5 Pillars)

`rgaParkour` strictly adheres to the 5 core CPMK integration pillars:

1. **Core Gameplay Function Retention**: Preserves 100% of native parkour game loops, local FastBoard scoreboards, checkpoint lists, and spectator transitions without modification from `rga-core`.
2. **Ronlab Integration Standard**: Listens strictly for CPMK event payloads (`MinigameStartEvent` and `MinigameConcludeEvent`). `paper-plugin.yml` specifies `api-version: '26.2'`, lists `RonlabGameAssistant` under `dependencies.server` with `required: true` and `join-classpath: true`, and contains NO invalid `load: BEFORE` directives.
3. **Baseline Structure & Rules Provision**: Implements margin-number suppression on sidebar lines via FastBoard packet construction. Scoreboard binding occurs during post-teleport spawn phases to prevent chunk-loading hangs, and teardown routines unregister objectives on `MinigameConcludeEvent`.
4. **Companion-Type Agnostic Design**: Operates as a self-contained companion module decoupled from `rga-core` internals, communicating solely over the `rga-api` event bus.
5. **Feature Implementation & Modification Specs**: Operates command-lessly via event lifecycle triggers. Implements **Solo QA Developer Mode** (`initialPlayerCount == 1`) in default configuration schemas and user documentation.

---

## 3. Solo QA Developer Mode (`initialPlayerCount == 1`)

When a minigame session is initiated with a single player (`initialPlayerCount == 1`):
- **Frozen Win Conditions**: Win conditions and automatic session conclusion are suspended, preventing premature session teardown when the sole runner finishes.
- **Continuous Testing**: QA developers can continuously test map resets, checkpoint detection, fall recovery thresholds, split timer formatting, and spectator transitions without restarting the server or re-queuing matches.

---

## 4. Commands & Permissions

`rgaParkour` is designed to be **command-less** and **permission-less** for regular players and administrators:
- Lifecycle triggers are driven completely by `rga-core` via `MinigameStartEvent` and `MinigameConcludeEvent`.
- No local administrative commands or custom permission nodes are required or registered.

---

## 5. Configuration Reference (`config.yml` / `settings.yml`)

```yaml
# ==============================================================================
# rgaParkour v1.0.0 Default Configuration Schema
# Companion Plugin for Ronlab Game Assistant (RGA) - PaperMC 26.2 / Java 25
# ==============================================================================

# Parkour Kit Material Definitions
parkour-kit:
  checkpoint-materials:
    - "LIGHT_WEIGHTED_PRESSURE_PLATE"
    - "GOLD_PRESSURE_PLATE"
  finish-materials:
    - "HEAVY_WEIGHTED_PRESSURE_PLATE"
    - "IRON_PRESSURE_PLATE"
  fail-materials:
    - "LAVA"
    - "WATER"

# Game Session Rules & Thresholds
game:
  fall-threshold-y: -60
  max-match-duration-seconds: 300
  invulnerability-seconds-on-fail: 1
```

### Configuration Key Details

- `parkour-kit.checkpoint-materials` (`List<String>`): Block materials that act as parkour checkpoints.
- `parkour-kit.finish-materials` (`List<String>`): Block materials that mark the parkour finish line.
- `parkour-kit.fail-materials` (`List<String>`): Block materials that trigger fail/reset effects upon contact.
- `game.fall-threshold-y` (`double` / `int`): Y-level coordinate threshold ($Y \le -60.0$) at or below which a fail/reset is triggered. Default is `-60`.
- `game.max-match-duration-seconds` (`int`): Maximum session duration in seconds before timing out. Default is `300` (5 minutes).
- `game.invulnerability-seconds-on-fail` (`int`): Duration of Resistance invulnerability applied upon fail reset. Default is `1`.

---

## 6. Build from Source

### Requirements
- **Java Development Kit (JDK)**: Version 25
- **Apache Maven**: Version 3.9 or newer

### Build Command

```bash
mvn clean package
```

The compiled plugin JAR with shaded FastBoard will be located at:
`target/rgaParkour-1.0.0.jar`

---

## 7. Project Structure

```
rgaParkour/
├── .gitignore
├── LICENSE
├── README.md
├── USER_GUIDE.md
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/ronlab/parkour/
    │   │   ├── ParkourPlugin.java
    │   │   ├── config/
    │   │   │   └── ParkourKitConfig.java
    │   │   ├── game/
    │   │   │   ├── ParkourScoreboardManager.java
    │   │   │   ├── ParkourSession.java
    │   │   │   └── ParkourSessionManager.java
    │   │   └── listener/
    │   │       ├── ParkourLifecycleListener.java
    │   │       └── ParkourPlayerListener.java
    │   └── resources/
    │       ├── config.yml
    │       ├── paper-plugin.yml
    │       └── settings.yml
    └── test/
        └── java/com/ronlab/parkour/
            ├── ParkourKitConfigTest.java
            ├── ParkourScoreboardTest.java
            └── ParkourSessionTest.java
```

---

## 8. License

This project is licensed under the Apache License 2.0. See the [LICENSE](LICENSE) file for details.

