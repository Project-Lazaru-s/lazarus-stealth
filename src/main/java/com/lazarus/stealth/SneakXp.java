package com.lazarus.stealth;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

import java.util.Iterator;

// Sneak (furtividade) XP for real risk only: sneaking past an infected that could perceive you and leaving
// without being chased, making one investigate your noise and slipping away, or a takedown.
// Limits: once per zombie per cooldown, a few rewards per 16x16 area per 5 minutes, a daily cap,
// nothing for creative/spectator, spawner/egg (and by default /summon) zombies, no-AI, leashed or riding mobs.
final class SneakXp {
    static final class Encounter {
        long lastUpdate;
        double minDist = Double.MAX_VALUE;
        double sneakDistance;
        boolean failed;
    }

    private static final String FARM_TAG = "lazarusstealth_farm";
    static final int EVASION_XP = 2, CLOSE_EVASION_XP = 4, DIVERTED_XP = 2, FINISHER_XP = 3;
    private static final int AREA_WINDOW_TICKS = 6000, ENCOUNTER_TIMEOUT_TICKS = 2400;

    private SneakXp() {}

    static void markFarm(Mob mob) {
        mob.getPersistentData().putBoolean(FARM_TAG, true);
    }

    static boolean farm(Mob mob) {
        return mob.getPersistentData().getBoolean(FARM_TAG) || mob.isNoAi() || mob.isLeashed() || mob.isPassenger();
    }

    // Called once per vision sample for every infected near the player.
    static void observe(ServerPlayer player, PlayerState s, Mob mob, Perception.Pair pair, double dist, int posture, long now) {
        if (!StealthConfig.XP_ENABLED.get() || farm(mob)) return;
        boolean sneaking = posture != StealthMath.STAND;
        Encounter e = s.encounters.get(mob.getUUID());
        if (e == null) {
            if (!sneaking || dist > 5 || pair.state.ordinal() > Perception.State.SUSPICIOUS.ordinal()) return;
            e = new Encounter();
            s.encounters.put(mob.getUUID(), e);
        }
        e.lastUpdate = now;
        e.minDist = Math.min(e.minDist, dist);
        if (sneaking) e.sneakDistance += s.sampleMoved;
        if (pair.state == Perception.State.PURSUING) e.failed = true;
        if (dist > 10) {
            s.encounters.remove(mob.getUUID());
            if (!e.failed && e.sneakDistance >= 4 && e.minDist <= 4)
                reward(player, s, mob, e.minDist <= 3 ? CLOSE_EVASION_XP : EVASION_XP);
        }
    }

    static void sweep(PlayerState s, long now) {
        for (Iterator<Encounter> it = s.encounters.values().iterator(); it.hasNext(); ) {
            if (now - it.next().lastUpdate > ENCOUNTER_TIMEOUT_TICKS) it.remove();
        }
    }

    static void diverted(ServerPlayer player, Mob mob) {
        if (StealthConfig.XP_ENABLED.get() && !farm(mob)) reward(player, PlayerState.of(player), mob, DIVERTED_XP);
    }

    static void finisher(ServerPlayer player, Mob mob) {
        if (StealthConfig.XP_ENABLED.get() && !farm(mob)) reward(player, PlayerState.of(player), mob, FINISHER_XP);
    }

    // Returns the XP actually granted after all limits (0 when blocked).
    static int reward(ServerPlayer player, PlayerState s, Mob mob, int amount) {
        if (!Perception.eligible(player)) return 0;
        long now = player.serverLevel().getGameTime();
        Long last = s.rewardedMobs.get(mob.getUUID());
        long mobCooldown = StealthConfig.XP_MOB_COOLDOWN_MINUTES.get() * 1200L;
        if (last != null && now - last < mobCooldown) return 0;
        long cell = ((long) (mob.getBlockX() >> 4) << 32) ^ (mob.getBlockZ() >> 4 & 0xFFFFFFFFL);
        while (!s.areaRewards.isEmpty() && now - s.areaRewards.peekFirst()[1] > AREA_WINDOW_TICKS) s.areaRewards.pollFirst();
        int inCell = 0;
        for (long[] r : s.areaRewards) if (r[0] == cell) inCell++;
        if (inCell >= StealthConfig.XP_PER_AREA.get()) return 0;
        long day = now / 24000;
        if (day != s.xpDay) {
            s.xpDay = day;
            s.xpToday = 0;
        }
        int granted = Math.min(amount, StealthConfig.XP_DAILY_CAP.get() - s.xpToday);
        if (granted <= 0) return 0;
        s.xpToday += granted;
        s.rewardedMobs.put(mob.getUUID(), now);
        s.areaRewards.addLast(new long[]{cell, now});
        if (s.rewardedMobs.size() > 256) s.rewardedMobs.entrySet().removeIf(en -> now - en.getValue() >= mobCooldown);
        Integrations.grantXp(player, granted);
        if (StealthConfig.DEBUG_LOG.get())
            LazarusStealth.LOG.info("[lazarusstealth] Sneak XP +{} to {} (today {})", granted, player.getGameProfile().getName(), s.xpToday);
        return granted;
    }
}
