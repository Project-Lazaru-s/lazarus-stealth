package com.lazarus.stealth;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

// Runs with the pack's real JARs on the dev runtime (build.gradle runtimeOnly): Zombie Awareness + CoroUtil,
// Dual Hitbox Zombies, Encumbered, PeakStamina, Puffish Skills (with run/config/puffish_skills incl. furtividade),
// Lazarus Moodles 1.15.1, Apocalypse Now and TaCZ.
@GameTestHolder(LazarusStealth.ID)
@PrefixGameTestTemplate(false)
public final class IntegrationGameTests {
    private static final int FLOOR = 2;
    private static int players;

    private IntegrationGameTests() {}

    // Zombie Awareness (active after day 3) targets any player in line of sight within 16 blocks and paths to him.
    // A player 8 blocks behind the husk must stay untargeted and the husk must not walk to him.
    @GameTest(template = "arena", batch = "int_za", timeoutTicks = 140)
    public static void zombieAwarenessSightIsGated(GameTestHelper h) {
        TestArena.fresh(h);
        h.assertTrue(ModList.get().isLoaded("zombieawareness"), "Zombie Awareness not on the runtime");
        Husk husk = h.spawn(EntityType.HUSK, 16, FLOOR, 18);
        husk.goalSelector.removeAllGoals(g -> g instanceof RandomStrollGoal);
        ServerPlayer p = player(h, 16, 10);
        h.getLevel().setDayTime(4 * 24000L + 6000); // past daysBeforeFeaturesActivate = 3
        Vec3 start = husk.position();
        int deniedBefore = Perception.zombieAwarenessDenials;
        // ZA scans every 40 ticks; 100 ticks covers two scans before the husk's idle glances could turn it around.
        h.runAtTickTime(100, () -> {
            h.assertTrue(Perception.zombieAwarenessDenials > deniedBefore, "Zombie Awareness never tried to target (inactive?)");
            h.assertTrue(husk.getTarget() == null, "ZA sight targeted a player behind the husk: " + Perception.reason(husk, p));
            h.assertTrue(husk.position().distanceTo(start) < 1.5, "husk walked toward the unseen player: " + husk.position().distanceTo(start));
            finish(h, p);
        });
    }

    // Dual Hitbox: breaking a block makes it call setTarget(player) on nearby zombies; the gate refuses it and the
    // zombie goes to the block instead. Headless flag is read through the DHZ helper.
    @GameTest(template = "arena", batch = "int_dhz", timeoutTicks = 60)
    public static void dualHitboxBreakNoiseBecomesLead(GameTestHelper h) throws Exception {
        TestArena.fresh(h);
        h.assertTrue(ModList.get().isLoaded("dualhitboxzombies"), "DHZ not on the runtime");
        Zombie zombie = h.spawn(EntityType.ZOMBIE, 16, FLOOR, 14);
        zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        zombie.goalSelector.removeAllGoals(g -> g instanceof RandomStrollGoal);
        ServerPlayer p = player(h, 16, 6);
        for (int x = 10; x < 23; x++) for (int y = 0; y < 4; y++) h.setBlock(x, FLOOR + y, 9, Blocks.STONE);
        BlockPos block = new BlockPos(16, FLOOR, 8);
        h.setBlock(block, Blocks.OAK_PLANKS);
        h.runAtTickTime(2, () -> p.gameMode.destroyBlock(h.absolutePos(block)));
        h.runAtTickTime(6, () -> {
            h.assertTrue(zombie.getTarget() == null, "DHZ noise attraction targeted the player directly");
            Vec3 lead = Perception.lead(zombie);
            h.assertTrue(lead != null && lead.distanceTo(Vec3.atCenterOf(h.absolutePos(block))) < 1, "no lead at the broken block: " + lead);
            h.assertTrue(Perception.state(zombie, p) == Perception.State.ALERTED, "block break should alert: " + Perception.state(zombie, p));
        });
        h.runAtTickTime(10, () -> {
            try {
                Class.forName("com.example.dualhitboxzombies.util.ZombieVariantHelper")
                        .getMethod("setVariantFlags", Zombie.class, byte.class).invoke(null, zombie, (byte) 0x10);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
            h.assertTrue(Integrations.headless(zombie), "headless flag not read");
            finish(h, p);
        });
    }

    // Encumbered, PeakStamina and Lazarus Moodles bridges return real values and change the footstep radius.
    @GameTest(template = "arena", batch = "int_bridges", timeoutTicks = 40)
    public static void weightStaminaExhaustionBridges(GameTestHelper h) {
        TestArena.fresh(h);
        ServerPlayer p = player(h, 8, 8);
        PlayerState s = PlayerState.of(p);
        double light = Noise.footstepRadius(h.getLevel(), p, s);
        double empty = Integrations.loadFraction(p);
        for (int i = 0; i < 20; i++) p.getInventory().add(new ItemStack(Items.IRON_BLOCK, 64));
        double loaded = Integrations.loadFraction(p);
        h.assertTrue(loaded > empty && loaded > 0, "Encumbered load not read: " + empty + " -> " + loaded);
        double stamina = Integrations.stamina(p);
        h.assertTrue(!Double.isNaN(stamina) && stamina > 0, "PeakStamina stamina not read: " + stamina);
        Integrations.spendStamina(p, 15);
        h.assertTrue(Math.abs(Integrations.stamina(p) - (stamina - 15)) < 0.01, "stamina not spent: " + Integrations.stamina(p));
        int result = h.getLevel().getServer().getCommands().performPrefixedCommand(
                p.createCommandSourceStack().withPermission(4).withSuppressedOutput(), "lazarusmoodles exhausted 5");
        h.assertTrue(result > 0, "moodles test command failed");
        h.assertTrue(Integrations.exhaustion(p) == 5, "Moodles exhaustion not read: " + Integrations.exhaustion(p));
        s.cacheTick = Long.MIN_VALUE / 2;
        s.refreshCache(p, h.getLevel().getGameTime());
        double heavyTired = Noise.footstepRadius(h.getLevel(), p, s);
        h.assertTrue(heavyTired > light * 1.5, "load+exhaustion did not raise footsteps: " + light + " -> " + heavyTired);
        LazarusStealth.LOG.info("[lazarusstealth] bridges: load {} stamina {} exhaustion {} footsteps {} -> {}",
                String.format("%.2f", loaded), stamina, Integrations.exhaustion(p), light, heavyTired);
        finish(h, p);
    }

    // Puffish Skills: XP command succeeds for furtividade, an unlocked skill applies its attribute, reset removes it.
    @GameTest(template = "arena", batch = "int_puffish", timeoutTicks = 40)
    public static void puffishTreeGrantsAndRevertsPerks(GameTestHelper h) {
        TestArena.fresh(h);
        ServerPlayer p = player(h, 8, 8);
        var server = h.getLevel().getServer();
        var source = server.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        String name = p.getGameProfile().getName();
        h.assertTrue(server.getCommands().performPrefixedCommand(source,
                "puffish_skills experience add " + name + " puffish_skills:furtividade 300") > 0, "experience add failed");
        h.assertTrue(server.getCommands().performPrefixedCommand(source,
                "puffish_skills skills unlock " + name + " puffish_skills:furtividade passos_leves") > 0, "unlock failed");
        h.runAtTickTime(2, () -> {
            double v = LazarusStealth.attr(p, LazarusStealth.FOOTSTEP_REDUCTION);
            h.assertTrue(Math.abs(v - 0.3) < 1e-6, "Passos Leves attribute " + v);
            server.getCommands().performPrefixedCommand(source, "puffish_skills skills reset " + name + " puffish_skills:furtividade");
        });
        h.runAtTickTime(4, () -> {
            h.assertTrue(LazarusStealth.attr(p, LazarusStealth.FOOTSTEP_REDUCTION) == 0, "perk survived the reset");
            finish(h, p);
        });
    }

    // Apocalypse Now walkers are covered: they get the stealth goals and their direct targeting is gated.
    @GameTest(template = "arena", batch = "int_an", timeoutTicks = 40)
    public static void apocalypseNowWalkerIsCovered(GameTestHelper h) {
        TestArena.fresh(h);
        @SuppressWarnings("unchecked")
        EntityType<Mob> type = (EntityType<Mob>) ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("apocalypsenow", "walker_1"));
        h.assertTrue(type != null, "apocalypsenow:walker_1 missing");
        Mob walker = h.spawn(type, 16, FLOOR, 16);
        ServerPlayer p = player(h, 16, 4);
        h.assertTrue(Perception.covered(walker), "walker not in #lazarusstealth:infected");
        walker.setTarget(p);
        h.assertTrue(walker.getTarget() == null, "unaware walker accepted setTarget");
        h.assertTrue(TaczCompat.silenced(ItemStack.EMPTY) == false, "TaCZ API link");
        finish(h, p);
    }

    // TaCZ: a real GunFireEvent (unsilenced) is Extreme noise on the HUD and alerts infected in range, never a target.
    @GameTest(template = "arena", batch = "int_tacz", timeoutTicks = 40)
    public static void gunshotIsHighNoiseAndAlerts(GameTestHelper h) {
        TestArena.fresh(h);
        for (int x = 4; x < 28; x++) for (int y = 0; y < 4; y++) h.setBlock(x, FLOOR + y, 20, Blocks.STONE);
        Husk husk = h.spawn(EntityType.HUSK, 16, FLOOR, 8);
        ServerPlayer p = player(h, 16, 26);
        h.runAtTickTime(2, () -> net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                new com.tacz.guns.api.event.common.GunFireEvent(p, ItemStack.EMPTY, net.minecraftforge.fml.LogicalSide.SERVER)));
        h.runAtTickTime(6, () -> {
            PlayerState s = PlayerState.of(p);
            h.assertTrue(s.lastNoiseClass == StealthMath.EXTREME, "shot not Extreme on the HUD: " + s.lastNoiseClass);
            h.assertTrue(Perception.state(husk, p) == Perception.State.ALERTED, "shot did not alert: " + Perception.state(husk, p));
            h.assertTrue(husk.getTarget() == null, "shot gave a target");
            finish(h, p);
        });
    }

    private static ServerPlayer player(GameTestHelper h, int x, int z) {
        var server = h.getLevel().getServer();
        server.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        server.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        h.getLevel().setDayTime(6_000);
        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), "integ" + ++players);
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
