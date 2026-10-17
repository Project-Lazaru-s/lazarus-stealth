package com.lazarus.stealth;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

// Soft bridges to the pack's mods, all checked against the installed JARs (see README "Integrações").
// Every bridge falls back to a neutral value and warns once when its mod or member is missing.
final class Integrations {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Set<String> WARNED = new HashSet<>();

    // Lazarus Moodles >= 1.15.1: public API with the effective Exhausted level (0..5, includes temporary bonuses).
    private static final Method MOODLES_EXHAUSTION = method("lazarusmoodles",
            "com.lazarus.moodles.LazarusMoodlesApi", "exhaustionLevel", ServerPlayer.class);
    // Encumbered 0.99: carried weight and the over-encumbered capacity (boost items and enchantment included).
    private static final Method ENCUMBER_WEIGHT = method("encumber",
            "net.khofo.encumber.events.WeightEvent", "calculateWeight", Player.class);
    private static final Method ENCUMBER_CAPACITY = method("encumber",
            "net.khofo.encumber.events.WeightEvent", "getWeightWithBoostItem", Player.class, int.class);
    // Dual Hitbox Zombies 1.0.0: headless variant flag.
    private static final Method DHZ_HEADLESS = method("dualhitboxzombies",
            "com.example.dualhitboxzombies.util.ZombieVariantHelper", "hasMissingHead", Zombie.class);
    // Dual Hitbox Zombies 1.0.0: public head damage (a brain kill when it empties the head pool), the only way to
    // really kill its zombies, since it cancels every LivingHurtEvent on them and applies damage to its own pools.
    private static final Method DHZ_HEAD_DAMAGE = method("dualhitboxzombies",
            "com.example.dualhitboxzombies.event.ZombieCombatEvents", "applyHeadDamage", Zombie.class, DamageSource.class, float.class);
    // PeakStamina 2.2.2: StaminaCapability.INSTANCE with public float `stamina`.
    private static final Capability<?> STAMINA_CAP = staminaCap();
    private static final Field STAMINA_FIELD = field("peakstamina", "com.peakstamina.capabilities.StaminaCapability", "stamina");

    private Integrations() {}

    static int exhaustion(ServerPlayer player) {
        Object v = invoke("lazarusmoodles", MOODLES_EXHAUSTION, null, player);
        return v instanceof Integer i ? i : 0;
    }

    // Weight / effective capacity; 0 when Encumbered is missing or the capacity is not positive.
    static double loadFraction(ServerPlayer player) {
        Object w = invoke("encumber", ENCUMBER_WEIGHT, null, player);
        Object c = invoke("encumber", ENCUMBER_CAPACITY, null, player, 1);
        if (!(w instanceof Double weight) || !(c instanceof Double capacity) || capacity <= 0) return 0;
        return weight / capacity;
    }

    static boolean headless(Mob mob) {
        if (!(mob instanceof Zombie zombie)) return false;
        return Boolean.TRUE.equals(invoke("dualhitboxzombies", DHZ_HEADLESS, null, zombie));
    }

    // True when Dual Hitbox took the hit as head damage (false for mobs it does not manage).
    static boolean dhzHeadDamage(Mob mob, DamageSource source, float amount) {
        if (!(mob instanceof Zombie zombie)) return false;
        return Boolean.TRUE.equals(invoke("dualhitboxzombies", DHZ_HEAD_DAMAGE, null, zombie, source, amount));
    }

    // NaN when PeakStamina is not installed (costs are then skipped).
    static double stamina(ServerPlayer player) {
        Object cap = staminaInstance(player);
        if (cap == null) return Double.NaN;
        try {
            return STAMINA_FIELD.getFloat(cap);
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn("peakstamina", e);
            return Double.NaN;
        }
    }

    static void spendStamina(ServerPlayer player, double amount) {
        Object cap = staminaInstance(player);
        if (cap == null) return;
        try {
            STAMINA_FIELD.setFloat(cap, (float) Math.max(0, STAMINA_FIELD.getFloat(cap) - amount));
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn("peakstamina", e);
        }
    }

    // Puffish Skills XP through the same command the pack's other trees use; logs once if the command fails.
    static void grantXp(ServerPlayer player, int amount) {
        if (amount <= 0 || !ModList.get().isLoaded("puffish_skills")) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        int result = server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput().withPermission(4),
                "puffish_skills experience add " + player.getGameProfile().getName() + " puffish_skills:furtividade " + amount);
        if (result <= 0 && WARNED.add("puffish_xp"))
            LOG.warn("[lazarusstealth] 'puffish_skills experience add' failed; is config/puffish_skills/categories/furtividade installed and listed in config.json?");
    }

    static String report() {
        return "moodles=" + (MOODLES_EXHAUSTION != null) + " encumbered=" + (ENCUMBER_WEIGHT != null && ENCUMBER_CAPACITY != null)
                + " peakstamina=" + (STAMINA_CAP != null && STAMINA_FIELD != null) + " dhz=" + (DHZ_HEADLESS != null)
                + " puffish=" + ModList.get().isLoaded("puffish_skills") + " tacz=" + ModList.get().isLoaded("tacz");
    }

    private static Object staminaInstance(ServerPlayer player) {
        if (STAMINA_CAP == null || STAMINA_FIELD == null) return null;
        return player.getCapability(STAMINA_CAP).resolve().orElse(null);
    }

    private static Object invoke(String mod, Method m, Object self, Object... args) {
        if (m == null) return null;
        try {
            return m.invoke(self, args);
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn(mod, e);
            return null;
        }
    }

    private static Method method(String mod, String cls, String name, Class<?>... params) {
        if (!ModList.get().isLoaded(mod)) return null;
        try {
            return Class.forName(cls).getMethod(name, params);
        } catch (ReflectiveOperationException | LinkageError e) {
            warn(mod, e);
            return null;
        }
    }

    private static Field field(String mod, String cls, String name) {
        if (!ModList.get().isLoaded(mod)) return null;
        try {
            return Class.forName(cls).getField(name);
        } catch (ReflectiveOperationException | LinkageError e) {
            warn(mod, e);
            return null;
        }
    }

    private static Capability<?> staminaCap() {
        if (!ModList.get().isLoaded("peakstamina")) return null;
        try {
            return (Capability<?>) Class.forName("com.peakstamina.capabilities.StaminaCapability").getField("INSTANCE").get(null);
        } catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
            warn("peakstamina", e);
            return null;
        }
    }

    private static void warn(String mod, Throwable e) {
        if (WARNED.add(mod)) LOG.warn("[lazarusstealth] {} integration unavailable, using neutral values: {}", mod, e.toString());
    }
}
