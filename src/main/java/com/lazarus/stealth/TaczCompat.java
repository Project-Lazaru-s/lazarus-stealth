package com.lazarus.stealth;

import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;

// TaCZ shots: Extreme noise when unsilenced (64), High when silenced (16); both alert nearby infected to the shot.
// Ranges mirror kubejs lazarus_gunshot_noise.js (64 / 16 silenced), which keeps sending zombies there; our
// investigate goal keeps any path that already ends near the shot, so the two do not fight.
// Registered only when TaCZ is loaded.
final class TaczCompat {
    private TaczCompat() {}

    @SubscribeEvent
    public static void onFire(GunFireEvent event) {
        if (!event.getLogicalSide().isServer() || !(event.getShooter() instanceof ServerPlayer player) || !Perception.eligible(player)) return;
        double radius = silenced(event.getGunItemStack()) ? StealthConfig.SILENCED_GUN_RADIUS.get() : StealthConfig.LOUD_GUN_RADIUS.get();
        if (player.getTags().contains("lazarus_disciplina")) radius *= 0.75; // same Combat perk as the script
        Noise.emit(new Noise.Event(player.serverLevel(), player.position(), Math.max(radius, StealthMath.HIGH_RADIUS), player, true, "gunshot"));
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
