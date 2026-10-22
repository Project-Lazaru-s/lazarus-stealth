package com.lazarus.stealth;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The loudest voice moment of each player since the server last looked, written from Simple Voice Chat's network
 * thread (VoiceNoise) and taken on the server thread (Noise). No voice chat types here, so it loads without the mod.
 */
final class VoiceLevels {
    record Heard(double db, boolean whisper) {}

    private static final Map<UUID, Heard> HEARD = new ConcurrentHashMap<>();

    private VoiceLevels() {}

    /** Keeps the loudest frame; a whisper only counts while nothing louder was said in the same moment. */
    static void record(UUID player, double db, boolean whisper) {
        HEARD.merge(player, new Heard(db, whisper), (a, b) -> b.db() > a.db() ? b : a);
    }

    static Heard take(UUID player) { return HEARD.remove(player); }

    static void forget(UUID player) { HEARD.remove(player); }
}
