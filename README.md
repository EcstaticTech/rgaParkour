# rgaParkour

**Version**: `1.0.0`  
**Target Server**: PaperMC 26.2 (Build 71+)  
**Java Standard**: Java 25 (Bytecode Version 69)  
**Core Framework**: Ronlab Game Assistant (`rga-core` / `rga-api:1.13.1`)  

`rgaParkour` is a native companion minigame plugin built on the **Micro-Companion Architecture (CPMK)** framework engine (`ronlabgameassistant` / `rga-core`). It provides real-time procedural parkour match management, checkpoint tracking, fall and liquid recovery, multi-spawn dispatching, collision suppression, pre-match frozen countdowns, spectator transitions, and packet-based FastBoard sidebar scoreboards with isolated scoreboards and teams.

---

## 1. Description & Mechanics

The `rgaParkour` companion delivers 100% native parkour mechanics while operating as an event-driven module:

- **CPMK Event Bus Integration**: Listens strictly for `MinigameStartEvent` and `MinigameConcludeEvent` from `rga-api` (`com.ronlab.rga.api.event.*`) to initialize, track, and clean up active sessions.
- **Multi-Player Concurrency & Collision Isolation**: Eliminates player jump bumping by registering runners into an isolated session team (`pk_runners`) with `Option.COLLISION_RULE = OptionStatus.NEVER` and `canSeeFriendlyInvisibles = true`. Maintains standard player rendering with zero packet-level translucency spoofing overhead.
- **Multi-Spawn Vector Dispatcher**: Automatically scans and parses `spawn-vectors` coordinate lists from `map.yml` (handling `"X, Y, Z"` and `"X, Y, Z, Yaw, Pitch"` strings and YAML map objects). Dispatches runners round-robin (`spawnVectors.get(i % spawnVectors.size())`) to prevent initial spawn congestion.
- **Checkpoint Detection & Personal Metrics**: Tracks runner progress across configured checkpoint materials (`GOLD_PRESSURE_PLATE`, `LIGHT_WEIGHTED_PRESSURE_PLATE`), playing audio cues (`ENTITY_EXPERIENCE_ORB_PICKUP`), recording unique checkpoint locations, and tracking personal fall counts.
- **Fail, Fall & Void Interception**: Intercepts Y-level fall thresholds ($Y \le -60.0$), hazard blocks (`WATER`, `LAVA`), `DamageCause.VOID`, and lethal damage. Suppresses vanilla player death events and avoids server hub respawn routing by asynchronously teleporting runners (`teleportAsync`) to their latest checkpoint with Resistance invulnerability.
- **Finish Line Mechanics**: Registers match finishes upon stepping on finish plates (`HEAVY_WEIGHTED_PRESSURE_PLATE`, `IRON_PRESSURE_PLATE`), calculates exact split times (`MM:SS ✔`), transitions runners to spectator mode via RGA (`setSpectator`), and triggers party completion.
- **3-Second Pre-Match Countdown**: Holds players at spawn for 3 seconds upon session start for chunk loading, playing note block audio prompts, locking X/Y/Z movement while preserving camera pitch/yaw control, and synchronizing the match timer precisely to the `GO!` tick.
- **Isolated Scoreboard & 2-Tick Updater**: Instantiates an isolated Bukkit `Scoreboard` per session and drives FastBoard packets on a high-frequency **2-tick (100ms / 10 Hz)** update loop for smooth elapsed time rendering without frame stripping or sidebar flicker. Displays personal metrics (`Checkpoints`, `Falls`) alongside live race standings.

---

## 2. CPMK Architecture Alignment (5 Pillars)

`rgaParkour` strictly adheres to the 5 core CPMK integration pillars:

1. **Core Gameplay Function Retention**: Preserves 100% of native parkour game loops, local FastBoard scoreboards, checkpoint lists, and spectator transitions without modification from `rga-core`.
2. **Ronlab Integration Standard**: Listens strictly for CPMK event payloads (`MinigameStartEvent` and `MinigameConcludeEvent`). `paper-plugin.yml` specifies `api-version: '26.2'`, lists `RonlabGameAssistant` under `dependencies.server` with `required: true` and `join-classpath: true`, and contains NO invalid `load: BEFORE` directives.
3. **Baseline Structure & Rules Provision**: Implements margin-number suppression on sidebar lines via FastBoard packet construction. Allocates dedicated session scoreboards and pushes baseline frames synchronously during `MinigameStartEvent`. Teardown routines restore players to the main server scoreboard on `MinigameConcludeEvent`.
4. **Companion-Type Agnostic Design**: Operates as a self-contained companion module decoupled from `rga-core` internals, communicating solely over the `rga-api` event bus.
5. **Feature Implementation & Modification Specs**: Operates command-lessly via event lifecycle triggers. Implements **Solo QA Developer Mode** (`initialPlayerCount == 1`) in default configuration schemas and user documentation.

---

## 3. Solo QA Developer Mode (`initialPlayerCount == 1`)

When a minigame session is initiated with a single player (`initialPlayerCount == 1`):
- **Frozen Win Conditions**: Win conditions and automatic session conclusion are suspended, preventing premature session teardown when the sole runner finishes.
- **Continuous Testing**: QA developers can continuously test map resets, checkpoint detection, multi-spawn vector fallback, fall recovery thresholds, split timer formatting, and spectator transitions without restarting the server or re-queuing matches.

---

## 4. Commands & Permissions

`rgaParkour` is designed to be **command-less** and **permission-less** for regular players and administrators:
- Lifecycle triggers are driven completely by `rga-core` via `MinigameStartEvent` and `MinigameConcludeEvent`.
- No local administrative commands or custom permission nodes are required or registered.

---

## 5. Configuration Reference (`config.yml` / `settings.yml`)

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

# Game Session Rules & Thresholds (Legacy Fallback Alignment)
game:
  fall-threshold-y: -60.0
  max-match-duration-seconds: 0
  invulnerability-seconds-on-fail: 1
```

### Configuration Key Details

- `parkour.time-limit-seconds` (`int`): Maximum session duration in seconds before match concludes. Set to `0` for unlimited (default: `0`).
- `parkour.fall-threshold-y` (`double`): Y-level coordinate threshold ($Y \le -60.0$) at or below which a fail/reset is triggered. Default is `-60.0`.
- `parkour-kit.checkpoint-materials` (`List<String>`): Block materials that act as parkour checkpoints.
- `parkour-kit.finish-materials` (`List<String>`): Block materials that mark the parkour finish line.
- `parkour-kit.fail-materials` (`List<String>`): Block materials that trigger fail/reset effects upon contact.
- `game.invulnerability-seconds-on-fail` (`int`): Duration of Resistance invulnerability applied upon fail reset. Default is `1`.

---

## 6. Map Template Configuration (`map.yml`)

Map templates can optionally define multi-spawn locations in `map.yml`:

```yaml
# Multi-player spawn pads (round-robin assigned across active runners)
spawn-vectors:
  - "10.5, 64.0, 10.5, 0.0, 0.0"
  - "12.5, 64.0, 10.5, 0.0, 0.0"
  - "14.5, 64.0, 10.5, 0.0, 0.0"
  - "16.5, 64.0, 10.5, 0.0, 0.0"
```

---

## 7. Build from Source

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

## 8. Project Structure

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
    │   │   │   ├── ParkourKitConfig.java
    │   │   │   └── SpawnVectorParser.java
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
            ├── ParkourSessionTest.java
            └── SpawnVectorParserTest.java
```

---

## 9. License

This project is licensed under the Apache License 2.0. See the [LICENSE](LICENSE) file for details.
