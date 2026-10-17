package com.lazarus.stealth;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

// Short-lived server state per online player (nothing is saved; the Sneak XP itself lives in Puffish Skills).
final class PlayerState {
    private static final Map<ServerPlayer, PlayerState> STATES = new WeakHashMap<>();

    // Movement
    Vec3 lastTickPos;
    double stepDistance;
    Vec3 lastSamplePos;
    long lastSampleTick;
    double sampleSpeed;      // horizontal blocks per tick between vision samples
    double sampleMoved;      // horizontal blocks moved since the previous sample
    long lastBreath;
    long lastPickupNoise;
    // Cached integrations (refreshed once per second, not per step)
    double loadFraction;
    int exhaustion;
    long cacheTick = Long.MIN_VALUE / 2; // half range: `now - cacheTick` must not overflow
    // HUD
    int lastNoiseClass;
    long lastNoiseTick = Long.MIN_VALUE / 2;
    int threat;
    int sentNoise = -1, sentThreat = -1;
    // Takedown
    long finisherReadyAt;
    // Sneak XP bookkeeping
    final Map<UUID, SneakXp.Encounter> encounters = new HashMap<>();
    final Map<UUID, Long> rewardedMobs = new HashMap<>();
    final ArrayDeque<long[]> areaRewards = new ArrayDeque<>();
    long xpDay = -1;
    int xpToday;
    // Debug overlay (/lazarusstealth debug)
    boolean debug;
    String debugLine = "";

    static PlayerState of(ServerPlayer player) {
        return STATES.computeIfAbsent(player, p -> new PlayerState());
    }

    static void forget(ServerPlayer player) {
        STATES.remove(player);
    }

    void refreshCache(ServerPlayer player, long now) {
        if (now - cacheTick < 20) return;
        cacheTick = now;
        loadFraction = Integrations.loadFraction(player);
        exhaustion = Integrations.exhaustion(player);
    }
}
