package com.ronlab.parkour;

import com.ronlab.parkour.config.ParkourKitConfig;
import com.ronlab.parkour.game.ParkourScoreboardManager;
import com.ronlab.parkour.game.ParkourSession;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ParkourScoreboardTest {

    private ParkourKitConfig config;
    private ParkourScoreboardManager scoreboardManager;

    @BeforeEach
    void setUp() {
        config = new ParkourKitConfig();
        scoreboardManager = new ParkourScoreboardManager();
    }

    @Test
    @DisplayName("Test unique checkpoint discovery tracking per UUID")
    void testUniqueCheckpointDiscovery() {
        UUID uuid = UUID.randomUUID();
        ParkourSession session = new ParkourSession("world_test", List.of(uuid), config, null);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("Runner1");

        World world = mock(World.class);
        Location cp1 = new Location(world, 10, 64, 10);
        Location cp1Duplicate = new Location(world, 10, 64, 10);
        Location cp2 = new Location(world, 20, 64, 20);

        assertTrue(session.recordCheckpoint(player, cp1));
        assertEquals(1, session.getDiscoveredCheckpoints(uuid).size());

        // Duplicate checkpoint step should return false and not increment count
        assertFalse(session.recordCheckpoint(player, cp1Duplicate));
        assertEquals(1, session.getDiscoveredCheckpoints(uuid).size());

        // New checkpoint location
        assertTrue(session.recordCheckpoint(player, cp2));
        assertEquals(2, session.getDiscoveredCheckpoints(uuid).size());
    }

    @Test
    @DisplayName("Test formatted finish split time recording and spectator state")
    void testFinishSplitTimeAndState() {
        UUID uuid = UUID.randomUUID();
        ParkourSession session = new ParkourSession("world_test", List.of(uuid), config, null);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("FastRunner");

        assertFalse(session.hasFinished(uuid));

        session.setStartTime(System.currentTimeMillis() - 72000); // 72 seconds ago = 01:12
        session.handleFinish(player, null);

        assertTrue(session.hasFinished(uuid));
        assertEquals("01:12", session.getFormattedFinishTime(uuid));
        assertTrue(session.isSpectator(uuid));
    }

    @Test
    @DisplayName("Test title bar match timer formatting (MM:SS) during RACING state")
    void testTitleBarTimerFormatting() {
        UUID uuid = UUID.randomUUID();
        ParkourSession session = new ParkourSession("world_test", List.of(uuid), config, null);
        session.startRacingForTest();
        session.setStartTime(System.currentTimeMillis() - 165000); // 165s = 02:45

        Component title = scoreboardManager.buildTitleComponent(session);
        String plainTitle = PlainTextComponentSerializer.plainText().serialize(title);

        assertTrue(plainTitle.contains("PARKOUR RACE (02:45)"), "Title bar should contain formatted timer (02:45)");
    }

    @Test
    @DisplayName("Test title bar formatting during COUNTDOWN state")
    void testCountdownTitleBarTimerFormatting() {
        UUID uuid = UUID.randomUUID();
        ParkourSession session = new ParkourSession("world_test", List.of(uuid), config, null);
        assertEquals(ParkourSession.SessionState.COUNTDOWN, session.getState());

        Component title = scoreboardManager.buildTitleComponent(session);
        String plainTitle = PlainTextComponentSerializer.plainText().serialize(title);

        assertTrue(plainTitle.contains("STARTING (00:03)"), "Title bar should contain countdown timer (00:03)");
    }

    @Test
    @DisplayName("Test per-player scoreboard lines formatting including personal checkpoints, falls, and standings")
    void testPerPlayerLinesFormatting() {
        UUID p1Uuid = UUID.randomUUID();
        UUID p2Uuid = UUID.randomUUID();

        ParkourSession session = new ParkourSession("world_test", List.of(p1Uuid, p2Uuid), config, null);
        session.setStartTime(System.currentTimeMillis() - 30000);

        Player p1 = mock(Player.class);
        when(p1.getUniqueId()).thenReturn(p1Uuid);
        when(p1.getName()).thenReturn("LongPlayerName12345");

        Player p2 = mock(Player.class);
        when(p2.getUniqueId()).thenReturn(p2Uuid);
        when(p2.getName()).thenReturn("P2");

        World world = mock(World.class);
        session.recordCheckpoint(p1, new Location(world, 5, 64, 5));
        session.applyFailEffects(p1);
        session.applyFailEffects(p1);

        // Player 2 finishes
        session.handleFinish(p2, null);

        scoreboardManager.registerPlayerName(p1Uuid, "LongPlayerName12345");
        scoreboardManager.registerPlayerName(p2Uuid, "P2");

        List<Component> p1Lines = scoreboardManager.buildLinesForPlayer(p1Uuid, session);
        String allP1LinesText = String.join("\n", p1Lines.stream().map(c -> PlainTextComponentSerializer.plainText().serialize(c)).toList());

        // Verify Personal Metrics
        assertTrue(allP1LinesText.contains("Checkpoints: 1"), "Should display personal checkpoints");
        assertTrue(allP1LinesText.contains("Falls: 2"), "Should display personal falls");

        // Verify Standings
        assertTrue(allP1LinesText.contains("LongPlayerName"), "P1 name should be present in standings");
        assertTrue(allP1LinesText.contains("P2"), "P2 name should be present in standings");
        assertTrue(allP1LinesText.contains("✔"), "P2 should display checkmark");
    }

    @Test
    @DisplayName("Test leaderboard lines builder formatting for active, finished, and spectator players")
    void testLeaderboardLinesFormatting() {
        UUID p1Uuid = UUID.randomUUID();
        UUID p2Uuid = UUID.randomUUID();

        ParkourSession session = new ParkourSession("world_test", List.of(p1Uuid, p2Uuid), config, null);
        session.setStartTime(System.currentTimeMillis() - 30000);

        Player p1 = mock(Player.class);
        when(p1.getUniqueId()).thenReturn(p1Uuid);
        when(p1.getName()).thenReturn("LongPlayerName12345");

        Player p2 = mock(Player.class);
        when(p2.getUniqueId()).thenReturn(p2Uuid);
        when(p2.getName()).thenReturn("P2");

        World world = mock(World.class);
        session.recordCheckpoint(p1, new Location(world, 5, 64, 5));

        // Player 2 finishes
        session.handleFinish(p2, null);

        scoreboardManager.registerPlayerName(p1Uuid, "LongPlayerName12345");
        scoreboardManager.registerPlayerName(p2Uuid, "P2");

        List<Component> lines = scoreboardManager.buildLeaderboardLines(session);
        assertEquals(3, lines.size()); // Header + P1 line + P2 line

        String lineP1 = PlainTextComponentSerializer.plainText().serialize(lines.get(1));
        String lineP2 = PlainTextComponentSerializer.plainText().serialize(lines.get(2));

        // P1 should be truncated to 14 chars and display 1 checkpoint
        assertTrue(lineP1.contains("LongPlayerName"), "P1 name should be present");
        assertTrue(lineP1.contains("1"), "P1 should display 1 discovered checkpoint");

        // P2 should show finish split time and checkmark
        assertTrue(lineP2.contains("P2"), "P2 name should be present");
        assertTrue(lineP2.contains("✔"), "Finished player should display checkmark");
    }

    @Test
    @DisplayName("Test name truncation safeguard")
    void testNameTruncationSafeguard() {
        assertEquals("VeryLongPlayer", scoreboardManager.truncateName("VeryLongPlayerNameHere", 14));
        assertEquals("ShortName", scoreboardManager.truncateName("ShortName", 14));
    }
}
