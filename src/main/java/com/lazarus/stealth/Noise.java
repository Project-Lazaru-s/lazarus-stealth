package com.lazarus.stealth;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.List;

// Server-authoritative noise: footsteps from distance walked, surface, posture, load and exhaustion; actions;
// routed to nearby infected as a lead to the noise position (never as a target).
@Mod.EventBusSubscriber(modid = LazarusStealth.ID)
public final class Noise {
    public record Event(ServerLevel level, Vec3 pos, double radius, ServerPlayer source, boolean route, String kind) {}

    private static final double STRIDE = 1.6;
    private static final int HUD_HOLD_TICKS = 30, HUD_STEP_TICKS = 20;

    private Noise() {}

    public static void emit(Event e) {
        int cls = StealthMath.noiseClass(e.radius());
        if (cls == StealthMath.NONE) return;
        long now = e.level().getGameTime();
        if (e.source() != null) {
            PlayerState s = PlayerState.of(e.source());
            // The HUD keeps the loudest recent class instead of letting a quiet step hide a loud one.
            int shown = StealthMath.displayedClass(s.lastNoiseClass, now - s.lastNoiseTick, HUD_HOLD_TICKS, HUD_STEP_TICKS);
            if (cls >= shown) {
                s.lastNoiseClass = cls;
                s.lastNoiseTick = now;
            }
        }
        if (!e.route() || !StealthConfig.ENABLED.get()) return;
        double r = e.radius();
        List<Mob> mobs = e.level().getEntitiesOfClass(Mob.class,
                new net.minecraft.world.phys.AABB(e.pos(), e.pos()).inflate(r, Math.max(4, r / 2), r),
                m -> m.isAlive() && m.getTarget() == null && Perception.covered(m));
        mobs.sort(Comparator.comparingDouble(m -> m.distanceToSqr(e.pos())));
        int max = StealthConfig.MAX_MOBS_PER_NOISE.get(), heard = 0;
        for (Mob mob : mobs) {
            if (heard >= max) break;
            double dist = mob.position().distanceTo(e.pos());
            double reach = occluded(e.level(), e.pos(), mob) ? r * StealthConfig.OCCLUSION.get() : r;
            if (dist > reach) continue;
            Perception.hear(mob, e, now);
            heard++;
        }
    }

    static void emit(ServerPlayer player, Vec3 pos, double radius, String kind) {
        emit(new Event(player.serverLevel(), pos, radius, player, true, kind));
    }

    private static boolean occluded(ServerLevel level, Vec3 from, Mob mob) {
        Vec3 start = from.add(0, 0.5, 0);
        return level.clip(new ClipContext(start, mob.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob))
                .getType() != HitResult.Type.MISS;
    }

    // ---------- footsteps ----------

    // Surface under the feet: a carpet/snow layer in the feet block counts first, then the block below.
    static double surfaceRadius(ServerLevel level, ServerPlayer player, boolean[] glass) {
        BlockState at = level.getBlockState(player.blockPosition());
        BlockState below = level.getBlockState(BlockPos.containing(player.getX(), player.getY() - 0.2, player.getZ()));
        BlockState s = at.is(LazarusStealth.SURFACE_SOFT) || at.is(LazarusStealth.SURFACE_METAL) || at.is(LazarusStealth.SURFACE_GLASS) ? at : below;
        glass[0] = s.is(LazarusStealth.SURFACE_GLASS);
        if (glass[0]) return StealthConfig.GLASS_RADIUS.get();
        if (s.is(LazarusStealth.SURFACE_METAL)) return StealthConfig.METAL_RADIUS.get();
        if (s.is(LazarusStealth.SURFACE_SOFT)) return StealthConfig.SOFT_RADIUS.get();
        return StealthConfig.MEDIUM_RADIUS.get();
    }

    static double footstepRadius(ServerLevel level, ServerPlayer player, PlayerState s) {
        boolean[] glass = new boolean[1];
        double base = surfaceRadius(level, player, glass);
        int posture = Perception.posture(player);
        double gait = posture == StealthMath.CRAWL ? StealthConfig.CRAWL_GAIT.get()
                : posture == StealthMath.CROUCH ? StealthConfig.CROUCH_GAIT.get()
                : player.isSprinting() ? StealthConfig.SPRINT_GAIT.get() : 1.0;
        double burden = LazarusStealth.attr(player, LazarusStealth.BURDEN_CONTROL);
        // Leitura de Rotas also softens the extra radius of metal and glass (by half the burden control).
        if (base > StealthConfig.MEDIUM_RADIUS.get())
            base = StealthConfig.MEDIUM_RADIUS.get() + (base - StealthConfig.MEDIUM_RADIUS.get()) * (1 - burden * 0.5);
        return StealthMath.footstepRadius(base, gait, glass[0], s.loadFraction, s.exhaustion,
                LazarusStealth.attr(player, LazarusStealth.FOOTSTEP_REDUCTION), burden);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        PlayerState s = PlayerState.of(player);
        s.refreshCache(player, now);
        Vec3 pos = player.position();
        boolean counts = Perception.eligible(player) && player.onGround() && !player.isPassenger() && !player.getAbilities().flying;
        if (s.lastTickPos != null && counts) {
            double moved = Math.hypot(pos.x - s.lastTickPos.x, pos.z - s.lastTickPos.z);
            if (moved < 2) s.stepDistance += moved;
            if (s.stepDistance >= STRIDE) {
                s.stepDistance -= STRIDE;
                emit(player, pos, footstepRadius(level, player, s), "footstep");
            }
        }
        s.lastTickPos = pos;
        // Heavy breathing when very exhausted and standing still.
        if (counts && s.exhaustion >= 4 && s.sampleSpeed < 0.01 && now - s.lastBreath >= 80) {
            s.lastBreath = now;
            double burden = LazarusStealth.attr(player, LazarusStealth.BURDEN_CONTROL);
            emit(player, pos, (s.exhaustion >= 5 ? 4.0 : 2.5) * (1 - burden * 0.5), "breathing");
        }
        if (now % 5 == 0) syncHud(player, s, now);
    }

    private static void syncHud(ServerPlayer player, PlayerState s, long now) {
        int shown = StealthMath.displayedClass(s.lastNoiseClass, now - s.lastNoiseTick, HUD_HOLD_TICKS, HUD_STEP_TICKS);
        if (shown == s.sentNoise && s.threat == s.sentThreat) return;
        s.sentNoise = shown;
        s.sentThreat = s.threat;
        StealthNetwork.sendHud(player, shown, s.threat);
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !Perception.eligible(player) || event.getDistance() < 2) return;
        boolean[] glass = new boolean[1];
        double base = surfaceRadius(player.serverLevel(), player, glass);
        double skill = LazarusStealth.attr(player, LazarusStealth.FOOTSTEP_REDUCTION) * 0.5;
        emit(player, player.position(), base * Math.min(2.5, 0.6 + 0.12 * event.getDistance()) * (1 - skill), "landing");
    }

    // ---------- actions ----------

    // Mining and breaking get no posture or skill discount (Dual Hitbox's own setTarget is cancelled by the gate).
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player) || !Perception.eligible(player)) return;
        BlockState state = event.getState();
        boolean glass = state.is(Tags.Blocks.GLASS) || state.is(Tags.Blocks.GLASS_PANES) || state.is(LazarusStealth.SURFACE_GLASS);
        emit(player, Vec3.atCenterOf(event.getPos()), glass ? StealthConfig.GLASS_BREAK_RADIUS.get() : StealthConfig.BREAK_RADIUS.get(),
                glass ? "glass break" : "block break");
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player) || !Perception.eligible(player)) return;
        BlockState state = event.getLevel().getBlockState(event.getPos());
        double radius;
        if (state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.FENCE_GATES)) radius = StealthConfig.DOOR_RADIUS.get();
        else if (event.getLevel().getBlockEntity(event.getPos()) instanceof Container) radius = StealthConfig.CONTAINER_RADIUS.get();
        else return;
        if (Perception.posture(player) != StealthMath.STAND) radius *= 0.5;
        emit(player, Vec3.atCenterOf(event.getPos()), radius, "door/container");
    }

    @SubscribeEvent
    public static void onPickup(EntityItemPickupEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !Perception.eligible(player)) return;
        PlayerState s = PlayerState.of(player);
        long now = player.serverLevel().getGameTime();
        if (now - s.lastPickupNoise < 20) return;
        s.lastPickupNoise = now;
        double radius = StealthConfig.PICKUP_RADIUS.get() * (Perception.posture(player) != StealthMath.STAND ? 0.5 : 1);
        emit(player, player.position(), radius, "pickup");
    }
}
