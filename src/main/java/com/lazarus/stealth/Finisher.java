package com.lazarus.stealth;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

// Silent takedown: a normal melee hit with a blade/blunt weapon on an infected that has not noticed the attacker.
// Validated on the server when the attack starts; the lethal (or critical) amount is applied to that same hit in
// LivingDamageEvent, so the damage source, kill credit, drops and statistics are the vanilla ones and nothing is
// dealt twice. Stamina and cooldown are only spent when the hit actually lands.
@Mod.EventBusSubscriber(modid = LazarusStealth.ID)
public final class Finisher {
    record Pending(ServerPlayer player, long tick, boolean special) {}

    private static final Map<Mob, Pending> PENDING = new WeakHashMap<>();

    private Finisher() {}

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getTarget() instanceof LivingEntity victim)) return;
        if (event.isCanceled() || !Perception.eligible(player)) return;
        boolean takedown = victim instanceof Mob mob && check(player, mob) == null;
        if (takedown) {
            Mob mob = (Mob) victim;
            PENDING.put(mob, new Pending(player, player.serverLevel().getGameTime(), special(mob)));
        }
        // Validation happens before the noise, so the victim hearing its own hit cannot spoil the takedown.
        Noise.emit(player, victim.position(), takedown ? StealthConfig.MELEE_RADIUS.get() * 0.75 : StealthConfig.MELEE_RADIUS.get(), "melee");
    }

    // Null when the takedown is allowed, otherwise the reason (shown by /lazarusstealth debug and used by tests).
    static String check(ServerPlayer player, Mob mob) {
        if (!StealthConfig.ENABLED.get() || !StealthConfig.FINISHER_ENABLED.get()) return "disabled";
        if (!Perception.covered(mob) || Perception.human(mob) || mob.getType().is(LazarusStealth.FINISHER_IMMUNE)) return "not eligible";
        if (!weapon(player.getMainHandItem())) return "weapon";
        if (player.getAttackStrengthScale(0.5f) < StealthConfig.FINISHER_CHARGE.get()) return "charge";
        if (!Perception.unawareOf(mob, player)) return "noticed";
        double mastery = LazarusStealth.attr(player, LazarusStealth.FINISHER_MASTERY);
        double minAngle = mastery > 0 ? StealthConfig.FINISHER_MASTERY_ANGLE.get() : StealthConfig.FINISHER_MIN_ANGLE.get();
        if (angleFromView(mob, player) < minAngle) return "angle";
        if (player.distanceTo(mob) > 3.5 || !player.hasLineOfSight(mob)) return "reach";
        PlayerState s = PlayerState.of(player);
        if (player.serverLevel().getGameTime() < s.finisherReadyAt) return "cooldown";
        double stamina = Integrations.stamina(player);
        if (!Double.isNaN(stamina) && stamina < cost(player)) return "stamina";
        return null;
    }

    static boolean weapon(ItemStack stack) {
        return stack.is(LazarusStealth.FINISHER_WEAPONS) || stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem;
    }

    static boolean special(Mob mob) {
        return mob.getType().is(LazarusStealth.FINISHER_SPECIAL) || mob.getMaxHealth() > StealthConfig.SPECIAL_HEALTH.get();
    }

    // Degrees between where the zombie looks and where the attacker stands: 180 = right behind it.
    static double angleFromView(Mob mob, ServerPlayer player) {
        Vec3 to = player.position().subtract(mob.position()).multiply(1, 0, 1);
        Vec3 view = mob.getViewVector(1f).multiply(1, 0, 1);
        if (to.lengthSqr() < 1e-6 || view.lengthSqr() < 1e-6) return 0;
        return Math.toDegrees(Math.acos(Mth.clamp(view.normalize().dot(to.normalize()), -1, 1)));
    }

    private static double mastery(ServerPlayer player) {
        return LazarusStealth.attr(player, LazarusStealth.FINISHER_MASTERY);
    }

    private static double cost(ServerPlayer player) {
        return StealthConfig.FINISHER_STAMINA.get() * (1 - 0.4 * mastery(player));
    }

    // Dual Hitbox zombies: their hurt is always cancelled by DHZ (HIGHEST), so the takedown goes to the head pool.
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onHurt(LivingHurtEvent event) {
        if (!event.isCanceled() || !(event.getEntity() instanceof Mob mob)) return;
        Pending pending = take(mob, event.getSource());
        if (pending == null) return;
        float amount = pending.special()
                ? (float) (mob.getMaxHealth() * critFraction(pending.player()))
                : Float.MAX_VALUE / 4;
        if (Integrations.dhzHeadDamage(mob, event.getSource(), amount)) succeed(pending, mob);
        else PENDING.put(mob, pending); // not a DHZ zombie: let LivingDamageEvent handle it
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || event.isCanceled()) return;
        Pending pending = take(mob, event.getSource());
        if (pending == null) return;
        if (pending.special()) {
            event.setAmount((float) Math.max(event.getAmount(), mob.getMaxHealth() * critFraction(pending.player())));
        } else {
            event.setAmount(Math.max(event.getAmount(), mob.getHealth() + 1));
        }
        succeed(pending, mob);
    }

    private static Pending take(Mob mob, DamageSource source) {
        Pending pending = PENDING.remove(mob);
        if (pending == null) return null;
        if (pending.tick() != pending.player().serverLevel().getGameTime() || source.getEntity() != pending.player()) return null;
        return pending;
    }

    private static double critFraction(ServerPlayer player) {
        return StealthConfig.SPECIAL_CRIT_FRACTION.get() * (1 + 0.5 * mastery(player));
    }

    private static void succeed(Pending pending, Mob mob) {
        ServerPlayer player = pending.player();
        long now = player.serverLevel().getGameTime();
        if (pending.special()) mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3, false, false));
        Integrations.spendStamina(player, cost(player));
        PlayerState.of(player).finisherReadyAt = now + Math.round(StealthConfig.FINISHER_COOLDOWN.get() * 20 * (1 - 0.4 * mastery(player)));
        player.serverLevel().playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.5f, 0.7f);
        player.serverLevel().sendParticles(ParticleTypes.CRIT, mob.getX(), mob.getY() + mob.getBbHeight() * 0.7, mob.getZ(), 8, 0.2, 0.2, 0.2, 0.1);
        player.displayClientMessage(Component.translatable(pending.special()
                ? "lazarusstealth.finisher.special" : "lazarusstealth.finisher.success"), true);
        SneakXp.finisher(player, mob);
    }
}
