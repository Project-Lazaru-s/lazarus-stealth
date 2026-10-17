package com.lazarus.stealth;

import com.mojang.brigadier.arguments.BoolArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// /lazarusstealth debug [true|false] - action bar with posture, noise, load/exhaustion and the nearest infected's state
// /lazarusstealth status             - integrations found and average perception cost
// Operators only (permission 2).
@Mod.EventBusSubscriber(modid = LazarusStealth.ID)
public final class StealthCommands {
    private static long perfNanos, perfSamples;

    private StealthCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(LazarusStealth.ID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("debug")
                        .executes(c -> debug(c.getSource().getPlayerOrException(), !PlayerState.of(c.getSource().getPlayerOrException()).debug))
                        .then(Commands.argument("on", BoolArgumentType.bool())
                                .executes(c -> debug(c.getSource().getPlayerOrException(), BoolArgumentType.getBool(c, "on")))))
                .then(Commands.literal("status").executes(c -> {
                    String perf = perfSamples == 0 ? "no samples yet"
                            : String.format("%.3f ms per player sample (%d samples)", averageMillis(), perfSamples);
                    c.getSource().sendSuccess(() -> Component.literal("Lazarus Stealth: " + Integrations.report() + " | " + perf
                            + " | Zombie Awareness targets refused: " + Perception.zombieAwarenessDenials), false);
                    return 1;
                })));
    }

    private static int debug(ServerPlayer player, boolean on) {
        PlayerState.of(player).debug = on;
        player.displayClientMessage(Component.literal("Lazarus Stealth debug " + (on ? "on" : "off")), false);
        return 1;
    }

    static void addPerf(long nanos) {
        perfNanos += nanos;
        perfSamples++;
    }

    static double averageMillis() {
        return perfSamples == 0 ? 0 : perfNanos / 1e6 / perfSamples;
    }

    static void resetPerf() {
        perfNanos = perfSamples = 0;
    }

    static void showDebug(ServerPlayer player, PlayerState s) {
        String posture = new String[]{"stand", "crouch", "crawl"}[Perception.posture(player)];
        int shown = StealthMath.displayedClass(s.lastNoiseClass, player.serverLevel().getGameTime() - s.lastNoiseTick, 30, 20);
        player.displayClientMessage(Component.literal(String.format("%s noise=%d load=%.0f%% exh=%d | %s",
                posture, shown, s.loadFraction * 100, s.exhaustion, s.debugLine)), true);
    }
}
