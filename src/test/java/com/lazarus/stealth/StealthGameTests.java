package com.lazarus.stealth;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// In-world checks with real husks (zombies that do not burn by day) and fake server players.
// Arena: 32x8x32 stone floor; entities stand at relative y = FLOOR. Husks face +Z (south) when spawned.
@GameTestHolder(LazarusStealth.ID)
@PrefixGameTestTemplate(false)
public final class StealthGameTests {
    private static final int FLOOR = 2;
    private static int players;

    private StealthGameTests() {}

    // 1. High Sneak, crouched, on grass: passing 2.5 blocks behind an unaware zombie is possible.
    @GameTest(template = "arena", batch = "pass_behind", timeoutTicks = 200)
    public static void skilledCrouchPassesBehind(GameTestHelper h) {
        TestArena.fresh(h);
        floor(h, Blocks.GRASS_BLOCK);
        Husk husk = frozenHusk(h, 16, 16);
        ServerPlayer p = player(h, 11, 13);
        skills(p, 0.3, 0.4);
        crouch(p);
        walk(h, p, new Vec3(11.5, FLOOR, 14.0), new Vec3(21.5, FLOOR, 14.0), 0.07, 20);
        h.runAtTickTime(180, () -> {
            h.assertTrue(husk.getTarget() == null, "husk targeted a skilled crouched player passing behind");
            h.assertTrue(Perception.state(husk, p) != Perception.State.PURSUING, "pair reached PURSUING");
            finish(h, p);
        });
    }

    // 1b. Same pass standing, walking, without skills and in front-side view: discovered.
    @GameTest(template = "arena", batch = "pass_seen", timeoutTicks = 200)
    public static void unskilledStandingPassIsSeen(GameTestHelper h) {
        TestArena.fresh(h);
        floor(h, Blocks.GRASS_BLOCK);
        Husk husk = frozenHusk(h, 16, 12);
        ServerPlayer p = player(h, 11, 15);
        walk(h, p, new Vec3(11.5, FLOOR, 15.5), new Vec3(21.5, FLOOR, 15.5), 0.2, 20);
        h.runAtTickTime(160, () -> {
            h.assertTrue(husk.getTarget() == p, "standing player crossing in front was not discovered: " + Perception.state(husk, p));
            finish(h, p);
        });
    }

    // 2. Standing still in front: discovered over time, not instantly.
    @GameTest(template = "arena", batch = "front_detect", timeoutTicks = 140)
    public static void frontStandingIsDiscoveredGradually(GameTestHelper h) {
        TestArena.fresh(h);
        Husk husk = frozenHusk(h, 16, 10);
        ServerPlayer p = player(h, 16, 16);
        h.runAtTickTime(4, () -> h.assertTrue(husk.getTarget() == null, "discovered instantly"));
        h.runAtTickTime(120, () -> {
            h.assertTrue(husk.getTarget() == p, "not discovered after 6 s in plain view: " + Perception.state(husk, p) + " " + Perception.suspicion(husk, p));
            finish(h, p);
        });
    }

    // 3. Gate: a direct setTarget (Dual Hitbox noise, Zombie Awareness sight) is refused while unaware.
    @GameTest(template = "arena", batch = "gate", timeoutTicks = 40)
    public static void directSetTargetIsRefusedWhileUnaware(GameTestHelper h) {
        TestArena.fresh(h);
        Husk husk = frozenHusk(h, 16, 16);
        ServerPlayer p = player(h, 16, 6);
        p.setGameMode(GameType.SURVIVAL);
        husk.setTarget(p);
        h.assertTrue(husk.getTarget() == null, "unaware husk accepted setTarget(player)");
        finish(h, p);
    }

    // 4. Crouching does not erase a chase.
    @GameTest(template = "arena", batch = "chase_persist", timeoutTicks = 120)
    public static void crouchDoesNotClearChase(GameTestHelper h) {
        TestArena.fresh(h);
        Husk husk = frozenHusk(h, 16, 16);
        husk.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        ServerPlayer p = player(h, 16, 18);
        p.attack(husk);
        h.runAtTickTime(5, () -> {
            h.assertTrue(husk.getTarget() == p, "hit did not start a chase");
            crouch(p);
            skills(p, 0.6, 0.6);
            teleport(h, p, new Vec3(16.5, FLOOR, 10.5));
        });
        h.runAtTickTime(80, () -> {
            h.assertTrue(husk.getTarget() == p, "crouching cleared the chase: " + Perception.state(husk, p));
            finish(h, p);
        });
    }

    // 5. A low noise sends the zombie to the noise position without revealing the player.
    @GameTest(template = "arena", batch = "noise_lead", timeoutTicks = 200)
    public static void lowNoiseLeadsToPositionNotPlayer(GameTestHelper h) {
        TestArena.fresh(h);
        Husk husk = h.spawn(EntityType.HUSK, 8, FLOOR, 16);
        husk.goalSelector.removeAllGoals(g -> g instanceof RandomStrollGoal);
        ServerPlayer p = player(h, 28, 28);
        p.setGameMode(GameType.CREATIVE); // only hearing is under test
        Vec3 noise = h.absoluteVec(new Vec3(12.5, FLOOR, 16.5));
        h.runAtTickTime(2, () -> Noise.emit(new Noise.Event(h.getLevel(), noise, 5, p, true, "test")));
        h.runAtTickTime(6, () -> {
            Vec3 lead = Perception.lead(husk);
            h.assertTrue(lead != null && lead.distanceTo(noise) < 0.1, "no lead at the noise position: " + lead);
        });
        h.succeedWhen(() -> {
            h.assertTrue(husk.getTarget() == null, "noise revealed the player");
            h.assertTrue(husk.position().distanceTo(noise) < 2.5, "husk did not reach the noise, at " + husk.position().distanceTo(noise));
        });
    }

    // 5b. A loud noise from a hidden survival player alerts, still without a target.
    @GameTest(template = "arena", batch = "noise_high", timeoutTicks = 60)
    public static void highNoiseAlertsWithoutTarget(GameTestHelper h) {
        TestArena.fresh(h);
        for (int x = 4; x < 28; x++) for (int y = 0; y < 4; y++) h.setBlock(x, FLOOR + y, 22, Blocks.STONE);
        Husk husk = h.spawn(EntityType.HUSK, 16, FLOOR, 12);
        ServerPlayer p = player(h, 16, 26);
        h.runAtTickTime(2, () -> Noise.emit(p, p.position(), 30, "test"));
        h.runAtTickTime(30, () -> {
            h.assertTrue(husk.getTarget() == null, "loud noise gave the player as target");
            h.assertTrue(Perception.state(husk, p) == Perception.State.ALERTED, "expected ALERTED, got " + Perception.state(husk, p));
            finish(h, p);
        });
    }

    // 6. Surfaces: metal and glass louder than grass; glass stays High even crouched.
    @GameTest(template = "arena", batch = "surfaces", timeoutTicks = 20)
    public static void surfacesChangeFootstepNoise(GameTestHelper h) {
        TestArena.fresh(h);
        h.setBlock(4, FLOOR - 1, 4, Blocks.GRASS_BLOCK);
        h.setBlock(8, FLOOR - 1, 4, Blocks.IRON_BLOCK);
        h.setBlock(12, FLOOR - 1, 4, Blocks.GLASS);
        ServerPlayer p = player(h, 4, 4);
        PlayerState s = PlayerState.of(p);
        double grass = Noise.footstepRadius(h.getLevel(), p, s);
        teleport(h, p, new Vec3(8.5, FLOOR, 4.5));
        double metal = Noise.footstepRadius(h.getLevel(), p, s);
        teleport(h, p, new Vec3(12.5, FLOOR, 4.5));
        crouch(p);
        double glass = Noise.footstepRadius(h.getLevel(), p, s);
        h.assertTrue(metal > grass * 2, "metal " + metal + " vs grass " + grass);
        h.assertTrue(StealthMath.noiseClass(glass) == StealthMath.HIGH, "crouched glass " + glass);
        finish(h, p);
    }

    // 7. Forced crawl under a one-block ceiling is read as crawling (real pose, not the key).
    @GameTest(template = "arena", batch = "crawl_gap", timeoutTicks = 40)
    public static void oneBlockGapForcesCrawl(GameTestHelper h) {
        TestArena.fresh(h);
        h.setBlock(10, FLOOR + 1, 10, Blocks.STONE);
        ServerPlayer p = player(h, 10, 10);
        h.onEachTick(p::doTick);
        h.runAtTickTime(10, () -> {
            h.assertTrue(Perception.posture(p) == StealthMath.CRAWL, "pose " + p.getPose() + " height " + p.getBbHeight());
            finish(h, p);
        });
    }

    // 8. Takedown on an unaware common zombie: lethal, credited to the player, counted in statistics.
    @GameTest(template = "arena", batch = "finisher_kill", timeoutTicks = 60)
    public static void takedownKillsUnawareZombie(GameTestHelper h) {
        TestArena.fresh(h);
        Husk husk = frozenHusk(h, 16, 16);
        ServerPlayer p = player(h, 16, 14);
        crouch(p);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        h.onEachTick(p::doTick);
        h.runAtTickTime(30, () -> {
            h.assertTrue(Finisher.check(p, husk) == null, "takedown refused: " + Finisher.check(p, husk));
            p.attack(husk);
        });
        h.runAtTickTime(34, () -> {
            h.assertTrue(!husk.isAlive(), "unaware husk survived the takedown");
            h.assertTrue(husk.getKillCredit() == p, "kill not credited to the player");
            h.assertTrue(p.getStats().getValue(Stats.ENTITY_KILLED.get(EntityType.HUSK)) == 1, "kill statistic missing");
            finish(h, p);
        });
    }

    // 8b. No takedown on a zombie that is investigating.
    @GameTest(template = "arena", batch = "finisher_noticed", timeoutTicks = 60)
    public static void noTakedownWhenInvestigating(GameTestHelper h) {
        TestArena.fresh(h);
        Husk husk = frozenHusk(h, 16, 16);
        husk.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        ServerPlayer p = player(h, 16, 14);
        crouch(p);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        h.onEachTick(p::doTick);
        h.runAtTickTime(20, () -> Noise.emit(new Noise.Event(h.getLevel(), h.absoluteVec(new Vec3(16.5, FLOOR, 22.5)), 8, null, true, "test")));
        h.runAtTickTime(30, () -> {
            h.assertTrue("noticed".equals(Finisher.check(p, husk)), "expected 'noticed', got " + Finisher.check(p, husk));
            p.attack(husk);
        });
        h.runAtTickTime(34, () -> {
            h.assertTrue(husk.isAlive(), "investigating husk was finished");
            finish(h, p);
        });
    }

    // 8c. Specials (more than 40 max health or tagged) take a heavy hit but do not die outright.
    @GameTest(template = "arena", batch = "finisher_special", timeoutTicks = 60)
    public static void specialTakesCriticalNotDeath(GameTestHelper h) {
        TestArena.fresh(h);
        // Not a husk: Dual Hitbox (on the dev runtime) moves Zombie-class damage to its own head/body pools.
        @SuppressWarnings("unchecked")
        EntityType<net.minecraft.world.entity.Mob> walker = (EntityType<net.minecraft.world.entity.Mob>)
                net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(new net.minecraft.resources.ResourceLocation("apocalypsenow", "walker_1"));
        var husk = walker != null ? h.spawn(walker, 16, FLOOR, 16) : frozenHusk(h, 16, 16);
        husk.goalSelector.removeAllGoals(g -> g instanceof RandomStrollGoal);
        husk.setYRot(0);
        husk.setYHeadRot(0);
        husk.yBodyRot = 0;
        husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(80);
        husk.setHealth(80);
        ServerPlayer p = player(h, 16, 14);
        crouch(p);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        h.onEachTick(p::doTick);
        h.runAtTickTime(30, () -> p.attack(husk));
        h.runAtTickTime(34, () -> {
            h.assertTrue(husk.isAlive(), "special died to a takedown");
            h.assertTrue(husk.getHealth() <= 80 - 80 * 0.35 + 0.01, "special crit too weak: " + husk.getHealth());
            finish(h, p);
        });
    }

    // 9. Multiplayer: the noisy player in plain view is chased, the skilled one crouched behind is not.
    @GameTest(template = "arena", batch = "two_players", timeoutTicks = 140)
    public static void perceptionIsPerPlayer(GameTestHelper h) {
        TestArena.fresh(h);
        Husk husk = frozenHusk(h, 16, 12);
        ServerPlayer loud = player(h, 16, 17);
        ServerPlayer quiet = player(h, 16, 9);
        skills(quiet, 0.3, 0.4);
        crouch(quiet);
        h.runAtTickTime(120, () -> {
            h.assertTrue(husk.getTarget() == loud, "visible player not chased: target=" + husk.getTarget() + " loud=" + Perception.state(husk, loud) + "/" + Perception.suspicion(husk, loud) + " " + Perception.reason(husk, loud) + " quiet=" + Perception.state(husk, quiet));
            h.assertTrue(Perception.state(husk, quiet) != Perception.State.PURSUING, "hidden player was also discovered");
            finish(h, loud);
            remove(h, quiet);
        });
    }

    // 10. Sneak XP: real evasion pays, the same zombie and the same area stop paying, daily cap holds.
    @GameTest(template = "arena", batch = "xp", timeoutTicks = 400)
    public static void evasionXpWithAntiFarm(GameTestHelper h) {
        TestArena.fresh(h);
        StealthConfig.DEBUG_LOG.set(true);
        floor(h, Blocks.GRASS_BLOCK);
        Husk husk = frozenHusk(h, 16, 16);
        ServerPlayer p = player(h, 11, 13);
        skills(p, 0.3, 0.4);
        crouch(p);
        walk(h, p, new Vec3(11.5, FLOOR, 14.0), new Vec3(21.5, FLOOR, 14.0), 0.07, 10);
        h.runAtTickTime(170, () -> teleport(h, p, new Vec3(28.5, FLOOR, 26.5)));
        h.runAtTickTime(200, () -> {
            PlayerState s = PlayerState.of(p);
            StealthConfig.DEBUG_LOG.set(false);
            h.assertTrue(s.xpToday == SneakXp.CLOSE_EVASION_XP, "evasion XP " + s.xpToday);
            h.assertTrue(SneakXp.reward(p, s, husk, 4) == 0, "same zombie paid twice");
            Husk other = h.spawn(EntityType.HUSK, 16, FLOOR, 16); // same 16x16 area as the first reward
            int second = SneakXp.reward(p, s, other, 4);
            Husk third = h.spawn(EntityType.HUSK, 16, FLOOR, 16);
            int thirdXp = SneakXp.reward(p, s, third, 4);
            Husk fourth = h.spawn(EntityType.HUSK, 16, FLOOR, 16);
            h.assertTrue(second > 0 && thirdXp > 0 && SneakXp.reward(p, s, fourth, 4) == 0, "area limit not applied");
            SneakXp.markFarm(fourth);
            h.assertTrue(SneakXp.farm(fourth), "spawner/egg marker ignored");
            finish(h, p);
        });
    }

    // 11. Cost: 80 infected around two players, average perception cost per player sample.
    @GameTest(template = "arena", batch = "perf", timeoutTicks = 260)
    public static void perceptionCostStaysSmall(GameTestHelper h) {
        TestArena.fresh(h);
        List<Husk> husks = new ArrayList<>();
        for (int i = 0; i < 80; i++) husks.add(h.spawn(EntityType.HUSK, 2 + (i % 10) * 3, FLOOR, 2 + (i / 10) * 3 + 2));
        ServerPlayer a = player(h, 15, 15);
        ServerPlayer b = player(h, 20, 20);
        crouch(b);
        a.setGameMode(GameType.SURVIVAL);
        h.runAtTickTime(20, StealthCommands::resetPerf);
        h.runAtTickTime(240, () -> {
            double ms = StealthCommands.averageMillis();
            LazarusStealth.LOG.info("[lazarusstealth] perf: {} infected, {} ms per player sample", husks.size(), String.format("%.3f", ms));
            h.assertTrue(ms < 2.0, "perception too slow: " + ms + " ms per player sample");
            finish(h, a);
            remove(h, b);
        });
    }

    // ---------- helpers ----------

    // A husk that keeps facing +Z: no random stroll turning its body.
    private static Husk frozenHusk(GameTestHelper h, int x, int z) {
        Husk husk = h.spawn(EntityType.HUSK, x, FLOOR, z);
        husk.goalSelector.removeAllGoals(g -> g instanceof RandomStrollGoal);
        husk.setYRot(0);
        husk.setYHeadRot(0);
        husk.yBodyRot = 0;
        return husk;
    }

    private static void floor(GameTestHelper h, Block block) {
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(x, FLOOR - 1, z, block);
    }

    private static void skills(ServerPlayer p, double footsteps, double visual) {
        p.getAttribute(LazarusStealth.FOOTSTEP_REDUCTION.get()).setBaseValue(footsteps);
        p.getAttribute(LazarusStealth.VISUAL_STEALTH.get()).setBaseValue(visual);
    }

    private static void crouch(ServerPlayer p) {
        p.setShiftKeyDown(true);
        p.setPose(Pose.CROUCHING);
    }

    // Moves the player along a line at `speed` blocks per tick starting at tick `from`, ticking it so footsteps fire.
    private static void walk(GameTestHelper h, ServerPlayer p, Vec3 a, Vec3 b, double speed, int from) {
        int ticks = (int) Math.ceil(a.distanceTo(b) / speed);
        for (int t = 0; t <= ticks; t++) {
            Vec3 at = a.lerp(b, Math.min(1, t / (double) ticks));
            h.runAtTickTime(from + t, () -> {
                teleport(h, p, at);
                p.doTick();
            });
        }
    }

    private static void teleport(GameTestHelper h, ServerPlayer p, Vec3 relative) {
        Vec3 at = h.absoluteVec(relative);
        p.teleportTo(h.getLevel(), at.x, at.y, at.z, p.getYRot(), p.getXRot());
    }

    private static ServerPlayer player(GameTestHelper h, int x, int z) {
        var server = h.getLevel().getServer();
        server.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        server.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        h.getLevel().setDayTime(6_000);
        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), "sneaker" + ++players);
        ServerPlayer player = new ServerPlayer(server, h.getLevel(), profile);
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player);
        player.setGameMode(GameType.SURVIVAL);
        teleport(h, player, new Vec3(x + 0.5, FLOOR, z + 0.5));
        return player;
    }

    private static void remove(GameTestHelper h, ServerPlayer p) {
        h.getLevel().getServer().getPlayerList().remove(p);
    }

    private static void finish(GameTestHelper h, ServerPlayer p) {
        remove(h, p);
        h.killAllEntities(); // leftovers would see, hear and group with the next tests
        h.succeed();
    }
}
