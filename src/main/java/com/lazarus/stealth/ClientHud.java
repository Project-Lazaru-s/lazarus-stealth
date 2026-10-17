package com.lazarus.stealth;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// Noise indicator: a speaker with 0..4 sound waves (None, Low, Medium, High, Extreme), bottom-left above the hotbar
// line, away from the Moodles column, the crosshair and the TaCZ ammo HUD. The wave count reads without colour.
@Mod.EventBusSubscriber(modid = LazarusStealth.ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientHud {
    private static final ResourceLocation ICONS = LazarusStealth.rl("textures/gui/noise.png");
    private static final int FRAME_W = 88, FRAME_H = 54, FRAMES = 5;
    private static final int DRAW_W = 26, DRAW_H = 16;
    private static final long SHOW_AFTER_MS = 3000;
    private static int noise;
    static int threat; // reserved for the future Investigating/Pursuing indicator
    private static long changedAt;

    private ClientHud() {}

    static void set(int nextNoise, int nextThreat) {
        if (nextNoise != noise) changedAt = Util.getMillis();
        noise = Math.max(0, Math.min(FRAMES - 1, nextNoise));
        threat = nextThreat;
    }

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("noise", (gui, graphics, partialTick, width, height) -> render(graphics, height));
    }

    private static void render(GuiGraphics g, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (!StealthConfig.HUD_ENABLED.get() || mc.player == null || mc.options.hideGui || mc.screen != null) return;
        long since = Util.getMillis() - changedAt;
        if (noise == 0 && !StealthConfig.HUD_ALWAYS.get() && since > SHOW_AFTER_MS) return;
        int x = StealthConfig.HUD_X.get(), y = height - StealthConfig.HUD_Y.get();
        RenderSystem.enableBlend();
        g.setColor(1, 1, 1, noise == 0 ? 0.5f : 0.85f);
        g.blit(ICONS, x, y, DRAW_W, DRAW_H, noise * FRAME_W, 0, FRAME_W, FRAME_H, FRAME_W * FRAMES, FRAME_H);
        g.setColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }
}
