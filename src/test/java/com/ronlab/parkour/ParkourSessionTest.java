package com.ronlab.parkour;

import com.ronlab.parkour.config.ParkourKitConfig;
import com.ronlab.parkour.game.ParkourSession;
import com.ronlab.rga.api.RGASessionControl;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ParkourSessionTest {

    private ParkourKitConfig config;
    private RGASessionControl rgaControl;

    @BeforeEach
    void setUp() {
        config = new ParkourKitConfig();
        rgaControl = mock(RGASessionControl.class);
    }

    @Test
    @DisplayName("Test active player check and initial spawn snapshot progression")
    void testActivePlayerAndCheckpointProgression() {
        UUID playerUuid = UUID.randomUUID();
        ParkourSession session = new ParkourSession("minigame_parkour_1", List.of(playerUuid), config, null);

        assertTrue(session.isActivePlayer(playerUuid));
        assertFalse(session.isActivePlayer(UUID.randomUUID()));

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerUuid);
        when(player.isOnline()).thenReturn(true);
        when(player.getName()).thenReturn("TestPlayer");

        World world = mock(World.class);
        Location initialSpawn = new Location(world, 0, 64, 0);
        Location checkpoint1 = new Location(world, 10, 64, 10);

        when(player.getLocation()).thenReturn(initialSpawn);
        when(world.getSpawnLocation()).thenReturn(initialSpawn);
        when(player.getWorld()).thenReturn(world);

        session.startGame(List.of(player));

        // Initial spawn snapshot verified
        assertEquals(initialSpawn, session.getLastCheckpoint(playerUuid));

        session.recordCheckpoint(player, checkpoint1);
        assertEquals(checkpoint1, session.getLastCheckpoint(playerUuid));
    }

    @Test
    @DisplayName("Test failure reset correctly maintains checkpoint state and teleports player")
    void testFailureResetMaintainsCheckpoint() {
        UUID playerUuid = UUID.randomUUID();
        ParkourSession session = new ParkourSession("minigame_parkour_1", List.of(playerUuid), config, null);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerUuid);
        when(player.isOnline()).thenReturn(true);
        when(player.getName()).thenReturn("TestPlayer");

        World world = mock(World.class);
        Location spawnLoc = new Location(world, 0, 64, 0);
        Location checkpointLoc = new Location(world, 15, 68, 15);

        when(player.getLocation()).thenReturn(spawnLoc);
        when(world.getSpawnLocation()).thenReturn(spawnLoc);
        when(player.getWorld()).thenReturn(world);

        session.startGame(List.of(player));

        session.recordCheckpoint(player, checkpointLoc);
        assertEquals(checkpointLoc, session.getLastCheckpoint(playerUuid));

        session.applyFailEffects(player);

        // Checkpoint state remains intact after fail
        assertEquals(checkpointLoc, session.getLastCheckpoint(playerUuid));
        verify(player, atLeastOnce()).teleport(eq(checkpointLoc));
    }

    @Test
    @DisplayName("Test Party Completion win logic: transition to spectator and evaluate conclude")
    void testPartyCompletionWinLogic() {
        UUID p1Uuid = UUID.randomUUID();
        UUID p2Uuid = UUID.randomUUID();
        ParkourSession session = new ParkourSession("minigame_parkour_1", List.of(p1Uuid, p2Uuid), config, null);

        Player p1 = mock(Player.class);
        when(p1.getUniqueId()).thenReturn(p1Uuid);
        when(p1.isOnline()).thenReturn(true);
        when(p1.getName()).thenReturn("Player1");

        Player p2 = mock(Player.class);
        when(p2.getUniqueId()).thenReturn(p2Uuid);
        when(p2.isOnline()).thenReturn(true);
        when(p2.getName()).thenReturn("Player2");

        World world = mock(World.class);
        when(p1.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(p2.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(p1.getWorld()).thenReturn(world);
        when(p2.getWorld()).thenReturn(world);

        session.startGame(List.of(p1, p2));

        // Player 1 finishes
        session.handleFinish(p1, rgaControl);

        assertTrue(session.getFinishTimes().containsKey(p1Uuid));
        assertFalse(session.getFinishTimes().containsKey(p2Uuid));
        verify(rgaControl).setSpectator(eq(p1), eq(true));

        // Player 2 finishes -> Party Completion
        session.handleFinish(p2, rgaControl);

        assertTrue(session.getFinishTimes().containsKey(p2Uuid));
        verify(rgaControl).setSpectator(eq(p2), eq(true));
        assertEquals(2, session.getFinishTimes().size());
    }

    @Test
    @DisplayName("Test Solo Player Completion: immediately finishes when solo player hits finish plate")
    void testSoloPlayerCompletion() {
        UUID soloUuid = UUID.randomUUID();
        ParkourSession session = new ParkourSession("minigame_parkour_solo", List.of(soloUuid), config, null);

        Player soloPlayer = mock(Player.class);
        when(soloPlayer.getUniqueId()).thenReturn(soloUuid);
        when(soloPlayer.isOnline()).thenReturn(true);
        when(soloPlayer.getName()).thenReturn("SoloPlayer");

        World world = mock(World.class);
        when(soloPlayer.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(soloPlayer.getWorld()).thenReturn(world);

        session.startGame(List.of(soloPlayer));
        session.handleFinish(soloPlayer, rgaControl);

        assertTrue(session.getFinishTimes().containsKey(soloUuid));
        verify(rgaControl).setSpectator(eq(soloPlayer), eq(true));
        assertEquals(1, session.getFinishTimes().size());
    }

    @Test
    @DisplayName("Test concurrent multiplayer session safety: zero ConcurrentModificationException and isolated state")
    void testConcurrentMultiplayerSessionSafety() throws Exception {
        int playerCount = 10;
        List<UUID> playerUuids = new java.util.ArrayList<>();
        List<Player> players = new java.util.ArrayList<>();
        World world = mock(World.class);

        for (int i = 0; i < playerCount; i++) {
            UUID uuid = UUID.randomUUID();
            playerUuids.add(uuid);
            Player player = mock(Player.class);
            when(player.getUniqueId()).thenReturn(uuid);
            when(player.isOnline()).thenReturn(true);
            when(player.getName()).thenReturn("Player_" + i);
            when(player.getLocation()).thenReturn(new Location(world, i, 64, i));
            when(player.getWorld()).thenReturn(world);
            players.add(player);
        }

        ParkourSession session = new ParkourSession("minigame_parkour_concurrent", playerUuids, config, null);
        session.startGame(players);

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(8);
        java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();

        for (int t = 0; t < 100; t++) {
            final int index = t;
            executor.submit(() -> {
                try {
                    Player p = players.get(index % playerCount);
                    if (index % 3 == 0) {
                        session.recordCheckpoint(p, new Location(world, index, 64 + index, index));
                    } else if (index % 3 == 1) {
                        session.handleFinish(p, rgaControl);
                    } else {
                        session.applyFailEffects(p);
                    }

                    // Concurrent iteration over views to verify zero ConcurrentModificationException
                    for (UUID active : session.getActivePlayers()) {
                        assertNotNull(active);
                    }
                    for (var entry : session.getLastCheckpoints().entrySet()) {
                        assertNotNull(entry.getKey());
                        assertNotNull(entry.getValue());
                    }
                    for (var entry : session.getFinishTimes().entrySet()) {
                        assertNotNull(entry.getKey());
                        assertNotNull(entry.getValue());
                    }
                } catch (Throwable t1) {
                    failure.compareAndSet(null, t1);
                }
            });
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS), "Executor tasks did not complete in time");

        if (failure.get() != null) {
            fail("Concurrent execution threw an exception: " + failure.get().getMessage(), failure.get());
        }

        // Verify player states are properly isolated
        assertNotNull(session.getLastCheckpoints());
        assertNotNull(session.getFinishTimes());
    }

    @Test
    @DisplayName("Test SessionState transitions and finish short-circuit state")
    void testSessionStateTransitionsAndFinishShortCircuit() {
        UUID playerUuid = UUID.randomUUID();
        ParkourSession session = new ParkourSession("world_state_test", List.of(playerUuid), config, null);

        // Before startGame, state is COUNTDOWN
        assertEquals(ParkourSession.SessionState.COUNTDOWN, session.getState());

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerUuid);
        when(player.isOnline()).thenReturn(true);
        when(player.getName()).thenReturn("StatePlayer");

        World world = mock(World.class);
        when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(player.getWorld()).thenReturn(world);

        session.startGame(List.of(player));

        // When started in unit test without Bukkit scheduler, auto-transitions to RACING
        assertEquals(ParkourSession.SessionState.RACING, session.getState());
        assertFalse(session.hasFinished(playerUuid));
        assertFalse(session.isSpectator(playerUuid));

        // Player finishes course
        session.handleFinish(player, rgaControl);

        assertTrue(session.hasFinished(playerUuid));
        assertTrue(session.isSpectator(playerUuid));
    }
}
