package com.lazarus.stealth;

import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;

// TaCZ shots: unsilenced = Extreme (64 blocks, alerts); silenced = Low (16 blocks, infected only investigate the spot).
// This is the only owner of gunshot attraction (the old kubejs lazarus_gunshot_noise.js was retired 2026-10-18):
// Noise.emit routes the shot to the nearest infected (occlusion + maxMobsPerNoise), as a lead, never a target.
// Registered only when TaCZ is loaded.
final class TaczCompat {
    private TaczCompat() {}

    @SubscribeEvent
    public static void onFire(GunFireEvent event) {
        if (!event.getLogicalSide().isServer() || !(event.getShooter() instanceof ServerPlayer player) || !Perception.eligible(player)) return;
        boolean silenced = silenced(event.getGunItemStack());
        double radius = silenced ? StealthConfig.SILENCED_GUN_RADIUS.get() : StealthConfig.LOUD_GUN_RADIUS.get();
        if (player.getTags().contains("lazarus_disciplina")) radius *= 0.75; // same Combat perk as the script
        Noise.emit(new Noise.Event(player.serverLevel(), player.position(), radius, player, true, "gunshot",
                silenced ? StealthMath.LOW : StealthMath.EXTREME));
    }

    static boolean silenced(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) return false;
        for (Object id : new Object[]{gun.getAttachmentId(stack, AttachmentType.MUZZLE), gun.getBuiltInAttachmentId(stack, AttachmentType.MUZZLE)}) {
            String s = String.valueOf(id);
            if (s.contains("silencer") || s.contains("suppressor")) return true;
        }
        return false;
    }
}
