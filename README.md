# rgaParkour

**Version**: `1.0.0`  
**Target Server**: PaperMC 26.2 (Build 71+)  
**Java Standard**: Java 25 (Bytecode Version 69)

`rgaParkour` is a native companion minigame plugin for **Ronlab Game Assistant (RGA)**. It provides real-time procedural parkour match management, checkpoint tracking, fail recovery, pre-match frozen countdowns, spectator transitions, and packet-based FastBoard sidebar scoreboards.

---

## 1. Description

The `rgaParkour` plugin provides the following minigame mechanics:

- **RGA Lifecycle Integration**: Listens to `MinigameStartEvent` and `MinigameConcludeEvent` from `rga-api` (`com.ronlab:rga-api:1.13.0-SNAPSHOT`) to initialize and clean up parkour sessions.
- **Checkpoint Detection**: Tracks player step actions on configured checkpoint blocks (`GOLD_PRESSURE_PLATE`, `LIGHT_WEIGHTED_PRESSURE_PLATE`), playing audio cues and recording unique checkpoint positions.
- **Fail & Fall Recovery**: Intercepts Y-level fall thresholds ($Y \le -60.0$) and liquid contact (`WATER`, `LAVA`), automatically resetting players to their latest checkpoint with temporary resistance invulnerability.
- **Finish Line Mechanics**: Registers match finishes upon stepping on finish plates (`HEAVY_WEIGHTED_PRESSURE_PLATE`, `IRON_PRESSURE_PLATE`), calculates exact split times, transitions runners to spectator mode via RGA, and triggers party completion when all runners finish.
- **3-Second Pre-Match Countdown**: Holds players at spawn for 3 seconds upon session start for chunk loading, playing note block audio prompts, locking X/Y/Z movement while preserving camera pitch/yaw control, and synchronizing the match timer precisely to the `GO!` tick.
- **Live FastBoard Scoreboard**: Renders dynamic, packet-based sidebar scoreboards using shaded FastBoard (`fr.mrmicky.fastboard`), displaying live match time (`PARKOUR RACE (MM:SS)` or `STARTING (00:03)`), player checkpoint counts, finished split times (`01:12 ✔`), spectator status, and 14-character name truncation safeguards.

---

## 2. Requirements

- **Minecraft Server**: PaperMC 26.2 or newer
- **Java Runtime**: Java 25 or newer
- **Core Dependency**: `RonlabGameAssistant` (`com.ronlab:rga-api:1.13.0-SNAPSHOT` / `RonlabGameAssistant`)

---

## 3. Setup Directions

Follow these steps to install and configure `rgaParkour`:

1. **Build or Download**: Obtain `rgaParkour-1.0.0.jar`.
2. **Install Plugin**: Copy `rgaParkour-1.0.0.jar` into your Paper server's `plugins/` folder.
3. **Ensure Core Plugin**: Verify that `RonlabGameAssistant.jar` is also present in the `plugins/` directory.
4. **Start Server**: Start the server to load `rgaParkour` and generate default configuration files (`config.yml` and `settings.yml`).
5. **Verify Installation**: Confirm in the server logs that `rgaParkour` loaded successfully after `RonlabGameAssistant`.

---

## 4. Configuration Reference (`config.yml` / `settings.yml`)

```yaml
# rgaParkour v1.0.0 Configuration
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
game:
  fall-threshold-y: -60
  max-match-duration-seconds: 300
  invulnerability-seconds-on-fail: 1
```

### Configuration Parameters

- `parkour-kit.checkpoint-materials` (`List<String>`): Block materials that act as parkour checkpoints.
- `parkour-kit.finish-materials` (`List<String>`): Block materials that mark the parkour finish line.
- `parkour-kit.fail-materials` (`List<String>`): Block materials that trigger fail/reset effects upon contact.
- `game.fall-threshold-y` (`int`): Y-level coordinate threshold ($Y$) at or below which a fail/reset is triggered. Default is `-60`.
- `game.max-match-duration-seconds` (`int`): Maximum session duration in seconds before timing out. Default is `300` (5 minutes).
- `game.invulnerability-seconds-on-fail` (`int`): Duration of resistance invulnerability applied upon fail reset. Default is `1`.

---

## 5. Build from Source

### Requirements
- **Java Development Kit (JDK)**: Version 25
- **Apache Maven**: Version 3.9 or newer

### Build Command

Execute this command in the repository root directory:

```bash
mvn clean package
```

The shaded plugin JAR with FastBoard relocated will be compiled to:
`target/rgaParkour-1.0.0.jar`

---

## 6. Project Structure

```
rgaParkour/
├── .gitignore
├── LICENSE
├── README.md
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

## 7. License

This project is licensed under the Apache License 2.0. See the [LICENSE](LICENSE) file for details.
