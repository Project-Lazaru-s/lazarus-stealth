package com.lazarus.stealth;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

// Human profile with Lazarus NPCs 1.1.0 and Apocalypse Now on the dev runtime. Humans face +Z when spawned.
@GameTestHolder(LazarusStealth.ID)
@PrefixGameTestTemplate(false)
public final class HumanGameTests {
    private static final int FLOOR = 2;
    private static int players;

    private HumanGameTests() {}

    // An unseen player behind a looter gets no warning and no attack (the warning used to reveal the player).
    @GameTest(template = "arena", batch = "human_unseen", timeoutTicks = 140)
    public static void unseenPlayerIsNotWarned(GameTestHelper h) {
        TestArena.fresh(h);
        Mob looter = frozen(h, "looter_2", 16, 18);
        ServerPlayer p = player(h, 16, 10);
        h.runAtTickTime(120, () -> {
            h.assertTrue(looter.getTarget() == null, "looter attacked an unseen player");
            h.assertTrue(!warned(looter, p), "looter warned a player it never saw");
            finish(h, p);
        });
    }

    // Seen in front: the looter notices, Lazarus NPCs gives its warning and holds the attack (refused target).
    @GameTest(template = "arena", batch = "human_seen", timeoutTicks = 120)
    public static void seenPlayerIsWarnedThenHeld(GameTestHelper h) {
        TestArena.fresh(h);
        Mob looter = frozen(h, "looter_2", 16, 6);
        ServerPlayer p = player(h, 16, 16);
        h.runAtTickTime(100, () -> {
            h.assertTrue(warned(looter, p), "looter did not warn a player standing in front of it: " + Perception.state(looter, p));
            h.assertTrue(looter.getTarget() == null, "looter attacked during the warning");
            h.assertTrue(Perception.state(looter, p) == Perception.State.PURSUING, "spotted player not tracked: " + Perception.state(looter, p));
            h.assertTrue(StealthApi.aware(looter, p), "aware() false for a spotted player");
            finish(h, p);
        });
    }

    // Humans see farther than zombies (24): at 30 blocks a looter spots a standing player.
    // (No husk nearby: Apocalypse Now looters target infected, and a busy human ignores the player.)
    @GameTest(template = "arena", batch = "human_range", timeoutTicks = 160)
    public static void humansSeeFartherThanZombies(GameTestHelper h) {
        TestArena.fresh(h);
        Mob looter = frozen(h, "looter_2", 16, 0);
        ServerPlayer p = player(h, 16, 30);
        h.runAtTickTime(140, () -> {
            h.assertTrue(Perception.state(looter, p) == Perception.State.PURSUING, "looter did not see 30 blocks: " + Perception.state(looter, p)
                    + " susp=" + Perception.suspicion(looter, p) + " target=" + looter.getTarget());
            finish(h, p);
        });
    }

    // One looter spots the player and calls out: a looter facing away is alerted with the position.
    @GameTest(template = "arena", batch = "human_callout", timeoutTicks = 120)
    public static void spotterAlertsAllies(GameTestHelper h) {
        TestArena.fresh(h);
        Mob spotter = frozen(h, "looter_2", 12, 6);
        Mob other = frozen(h, "looter_3", 22, 20);
        ServerPlayer p = player(h, 12, 16);
        h.runAtTickTime(100, () -> {
            h.assertTrue(Perception.state(spotter, p) == Perception.State.PURSUING, "spotter did not see: " + Perception.state(spotter, p));
            Perception.State s = Perception.state(other, p);
            h.assertTrue(s.ordinal() >= Perception.State.ALERTED.ordinal(), "ally not alerted: " + s + " " + Perception.reason(other, p));
            finish(h, p);
        });
    }

    // A friendly survivor (neutral reputation) ignores stealth: no lead from the player's noise, no gate.
    @GameTest(template = "arena", batch = "human_friendly", timeoutTicks = 40)
    public static void friendlySurvivorIsNotEngaged(GameTestHelper h) {
        TestArena.fresh(h);
        Mob survivor = frozen(h, "survivor_1", 16, 16);
        ServerPlayer p = player(h, 16, 12);
        h.assertTrue(!Perception.engaged(survivor, p), "neutral survivor counted as hostile");
        h.runAtTickTime(2, () -> Noise.emit(p, p.position(), 8, "test"));
        h.runAtTickTime(6, () -> {
            h.assertTrue(Perception.lead(survivor) == null, "friendly survivor investigated the player's noise");
            finish(h, p);
        });
    }

    // Gunshots: humans flank (the lead is beside the shot, not on it).
    @GameTest(template = "arena", batch = "human_flank", timeoutTicks = 40)
    public static void humansFlankGunshots(GameTestHelper h) {
        TestArena.fresh(h);
        Mob looter = frozen(h, "looter_2", 16, 4);
        ServerPlayer p = player(h, 16, 24);
        p.setGameMode(GameType.SURVIVAL);
        h.runAtTickTime(2, () -> Noise.emit(new Noise.Event(h.getLevel(), p.position(), 64, p, true, "gunshot", StealthMath.EXTREME)));
        h.runAtTickTime(4, () -> {
            Vec3 lead = Perception.lead(looter);
            h.assertTrue(lead != null, "no lead from a gunshot: d=" + looter.distanceTo(p) + " engaged=" + Perception.engaged(looter, p) + " target=" + looter.getTarget() + " state=" + Perception.state(looter, p));
            double off = lead.distanceTo(p.position());
            h.assertTrue(off > 3 && off < 8, "lead not beside the shot: " + off);
            finish(h, p);
        });
    }

    // Hitting a looter from hiding: it and its group retaliate (reveal lets the NPC mod's setTarget through).
    @GameTest(template = "arena", batch = "human_hurt", timeoutTicks = 40)
    public static void hitRevealsAttacker(GameTestHelper h) {
        TestArena.fresh(h);
        Mob looter = frozen(h, "looter_2", 16, 16);
        ServerPlayer p = player(h, 16, 14);
        p.attack(looter);
        h.runAtTickTime(5, () -> {
            h.assertTrue(looter.getTarget() == p, "looter did not retaliate: " + Perception.state(looter, p));
            finish(h, p);
        });
    }

    // ---------- helpers ----------

    private static boolean warned(Mob mob, ServerPlayer p) {
        return mob.getPersistentData().getCompound("LazarusHumanEncounter").getCompound(p.getUUID().toString()).contains("WarnedAt");
    }

    @SuppressWarnings("unchecked")
    private static Mob frozen(GameTestHelper h, String path, int x, int z) {
        h.assertTrue(ModList.get().isLoaded("lazarusnpcs"), "Lazarus NPCs not on the runtime");
        EntityType<Mob> type = (EntityType<Mob>) ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("apocalypsenow", path));
        Mob mob = h.spawn(type, x, FLOOR, z);
        mob.goalSelector.removeAllGoals(g -> g instanceof RandomStrollGoal || g instanceof RandomLookAroundGoal);
        mob.setYRot(0);
        mob.setYHeadRot(0);
        mob.yBodyRot = 0;
        mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0); // vision tests: no tactics walking it around
        return mob;
    }

    private static ServerPlayer player(GameTestHelper h, int x, int z) {
        var server = h.getLevel().getServer();
        server.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        server.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        h.getLevel().setDayTime(6_000);
        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), "human" + ++players);
        ServerPlayer player = new ServerPlayer(server, h.getLevel(), profile);
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player);
        player.setGameMode(GameType.SURVIVAL);
        Vec3 at = h.absoluteVec(new Vec3(x + 0.5, FLOOR, z + 0.5));
        player.teleportTo(h.getLevel(), at.x, at.y, at.z, 0, 0);
        return player;
    }

    private static void finish(GameTestHelper h, ServerPlayer p) {
        h.getLevel().getServer().getPlayerList().remove(p);
        h.killAllEntities(); // leftovers would see, hear and group with the next tests
        h.succeed();
    }
}
