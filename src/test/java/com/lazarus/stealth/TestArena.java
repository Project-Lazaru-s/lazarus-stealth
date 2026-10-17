package com.lazarus.stealth;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

import java.util.ArrayList;

// Batches reuse the same arena position, so a test that failed or ended early leaves mobs and fake players behind
// that would see, hear and group with the next test. Every test starts by clearing them.
final class TestArena {
    private TestArena() {}

    static void fresh(GameTestHelper h) {
        for (Mob mob : h.getLevel().getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(h.absolutePos(net.minecraft.core.BlockPos.ZERO)).expandTowards(32, 8, 32).inflate(48))) mob.discard();
        var list = h.getLevel().getServer().getPlayerList();
        for (ServerPlayer p : new ArrayList<>(list.getPlayers())) list.remove(p);
    }
}
