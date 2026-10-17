package com.lazarus.stealth;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// Noise indicator: three bars (how many are filled = the class, so it reads without colour) near the bottom-right,
// away from the Moodles column (vertically centred on the right edge), the crosshair, the hotbar and the TaCZ ammo HUD.
@Mod.EventBusSubscriber(modid = LazarusStealth.ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientHud {
    private static final int[] COLORS = {0x9A9A9A, 0xA8C66C, 0xE0A63A, 0xD9483B};
    private static final long SHOW_AFTER_MS = 3000, LABEL_MS = 2500;
    private static int noise;
    static int threat; // reserved for the future Investigating/Pursuing indicator
    private static long changedAt;

    private ClientHud() {}

    static void set(int nextNoise, int nextThreat) {
        if (nextNoise != noise) changedAt = Util.getMillis();
        noise = Math.max(0, Math.min(3, nextNoise));
        threat = nextThreat;
    }

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("noise", (gui, graphics, partialTick, width, height) -> render(graphics, width, height));
    }

    private static void render(GuiGraphics g, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (!StealthConfig.HUD_ENABLED.get() || mc.player == null || mc.options.hideGui || mc.screen != null) return;
        long since = Util.getMillis() - changedAt;
        if (noise == 0 && !StealthConfig.HUD_ALWAYS.get() && since > SHOW_AFTER_MS) return;
        int x = width - StealthConfig.HUD_X.get(), y = height - StealthConfig.HUD_Y.get();
        g.fill(x - 2, y - 2, x + 18, y + 12, 0x70000000);
        for (int i = 0; i < 3; i++) {
            int h = 4 + i * 3, bx = x + i * 6, by = y + 10 - h;
            if (i < noise) g.fill(bx, by, bx + 4, by + h, 0xFF000000 | COLORS[noise]);
            else g.renderOutline(bx, by, 4, h, 0x80FFFFFF);
        }
        if (since < LABEL_MS) {
            String text = Component.translatable("lazarusstealth.noise.label",
                    Component.translatable("lazarusstealth.noise." + noise)).getString();
            g.drawString(mc.font, text, x - 6 - mc.font.width(text), y + 1, 0xFF000000 | COLORS[noise], true);
        }
    }
}
