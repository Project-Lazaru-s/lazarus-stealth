package com.lazarus.stealth;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;

// Public API for other mods (Lazarus NPCs). Call it only when ModList says "lazarusstealth" is loaded.
// Everything is server-side. Hooks are set once during mod construction or common setup.
public final class StealthApi {
    public interface Listener {
        // A human's awareness of a player changed (states: UNAWARE, SUSPICIOUS, INVESTIGATING, ALERTED, PURSUING).
        void changed(Mob mob, ServerPlayer player, Perception.State from, Perception.State to, String reason);
    }

    private static volatile BiPredicate<Mob, Player> hostility = (mob, player) -> true;
    private static volatile BiPredicate<Mob, Mob> allies = (a, b) -> a.getType() == b.getType();
    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    private StealthApi() {}

    // Which #lazarusstealth:human mobs may be hostile to a player right now; the others ignore stealth entirely.
    public static void setHostility(BiPredicate<Mob, Player> test) {
        hostility = test;
    }

    // Which humans share what they see and hear (callouts).
    public static void setAllies(BiPredicate<Mob, Mob> test) {
        allies = test;
    }

    public static void addListener(Listener listener) {
        LISTENERS.add(listener);
    }

    // The mob has noticed the player and still remembers where they are.
    public static boolean aware(Mob mob, Player player) {
        return Perception.aware(mob, player);
    }

    // Tells the mob where the player is (a hit, an ally's warning, a planned ambush), so its next setTarget passes.
    public static void reveal(Mob mob, ServerPlayer player, String reason) {
        Perception.reveal(mob, player, reason);
    }

    public static Perception.State state(Mob mob, Player player) {
        return Perception.state(mob, player);
    }

    static BiPredicate<Mob, Player> hostility() {
        return hostility;
    }

    static BiPredicate<Mob, Mob> allies() {
        return allies;
    }

    static void fire(Mob mob, ServerPlayer player, Perception.State from, Perception.State to, String reason) {
        for (Listener l : LISTENERS) {
            try {
                l.changed(mob, player, from, to, reason);
            } catch (RuntimeException e) {
                LazarusStealth.LOG.warn("[lazarusstealth] stealth listener failed: {}", e.toString());
            }
        }
    }
}
