package com.lazarus.stealth;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

// Per zombie-player awareness. Every target acquisition by an infected (vanilla goals, Zombie Awareness sight,
// Dual Hitbox noise, Apocalypse Now / Undead Revamped goals) goes through Mob.setTarget, which Forge turns into
// the cancelable LivingChangeTargetEvent: a player can only become the target once this pair is PURSUING.
@Mod.EventBusSubscriber(modid = LazarusStealth.ID)
public final class Perception {
    public enum State { UNAWARE, SUSPICIOUS, INVESTIGATING, ALERTED, PURSUING }

    static final class Pair {
        State state = State.UNAWARE;
        double suspicion;
        Vec3 lastKnown;
        long since;
        boolean pursuedSinceLead;
        int noiseHits;
        long noiseWindow;
        String reason = "";
    }

    // Mob-level lead: where it is walking to (a noise or the last place it saw someone), then searching around.
    static final class Mind {
        final Map<UUID, Pair> pairs = new HashMap<>(2);
        Vec3 lead;
        long leadUntil;
        double leadSpeed;
        int leadClass;
        UUID leadCause;
        boolean arrived;
        long searchUntil;
        Vec3 lookAt;
        long lookUntil;
        long headlessCheck = Long.MIN_VALUE;
        boolean headless;

        Pair pair(UUID id) {
            return pairs.computeIfAbsent(id, k -> new Pair());
        }

        boolean hasLead(long now) {
            return lead != null && (now < leadUntil || arrived && now < searchUntil);
        }

        void setLead(Vec3 pos, int ticks, double speed, int cls, UUID cause, long now) {
            lead = pos;
            leadUntil = now + ticks;
            leadSpeed = speed;
            leadClass = cls;
            leadCause = cause;
            arrived = false;
            if (cause != null) pair(cause).pursuedSinceLead = false;
        }

        void clearLead() {
            lead = null;
            leadCause = null;
            arrived = false;
        }
    }

    private static final Map<Mob, Mind> MINDS = new WeakHashMap<>();
    private static final Map<Mob, Boolean> GOAL_ADDED = new WeakHashMap<>();
    private static final double CRAWL_HEIGHT = 1.0;

    private Perception() {}

    // ---------- queries used by Noise, Finisher, SneakXp and tests ----------

    static boolean covered(Entity entity) {
        return entity instanceof Mob && entity.getType().is(LazarusStealth.INFECTED);
    }

    static boolean eligible(Player player) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator();
    }

    public static State state(Mob mob, Player player) {
        Mind m = MINDS.get(mob);
        Pair p = m == null ? null : m.pairs.get(player.getUUID());
        return p == null ? State.UNAWARE : p.state;
    }

    public static double suspicion(Mob mob, Player player) {
        Mind m = MINDS.get(mob);
        Pair p = m == null ? null : m.pairs.get(player.getUUID());
        return p == null ? 0 : p.suspicion;
    }

    public static String reason(Mob mob, Player player) {
        Mind m = MINDS.get(mob);
        Pair p = m == null ? null : m.pairs.get(player.getUUID());
        return p == null ? "" : p.reason;
    }

    public static Vec3 lead(Mob mob) {
        Mind m = MINDS.get(mob);
        return m == null || !m.hasLead(mob.level().getGameTime()) ? null : m.lead;
    }

    // True only for a zombie with no target, no lead and no suspicion of this player (takedown condition).
    static boolean unawareOf(Mob mob, Player player) {
        if (mob.getTarget() != null) return false;
        Mind m = MINDS.get(mob);
        if (m == null) return true;
        if (m.hasLead(mob.level().getGameTime())) return false;
        for (Map.Entry<UUID, Pair> e : m.pairs.entrySet()) {
            Pair p = e.getValue();
            if (p.state.ordinal() >= State.INVESTIGATING.ordinal()) return false;
            if (e.getKey().equals(player.getUUID()) && (p.state != State.UNAWARE || p.suspicion >= 0.3)) return false;
        }
        return true;
    }

    static int posture(Player player) {
        boolean low = player.getPose() == Pose.SWIMMING || player.getBbHeight() < CRAWL_HEIGHT;
        if (low && !player.isInWater() && !player.isFallFlying()) return StealthMath.CRAWL;
        if (player.getPose() == Pose.CROUCHING || player.isShiftKeyDown()) return StealthMath.CROUCH;
        return StealthMath.STAND;
    }

    static boolean headless(Mob mob, Mind m, long now) {
        if (mob.getType().is(LazarusStealth.NO_VISION)) return true;
        if (now - m.headlessCheck >= 40) {
            m.headlessCheck = now;
            m.headless = Integrations.headless(mob);
        }
        return m.headless;
    }

    private static Mind mind(Mob mob) {
        return MINDS.computeIfAbsent(mob, k -> new Mind());
    }

    // ---------- target gate ----------

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.isCanceled() || event.getTargetType() != LivingChangeTargetEvent.LivingTargetType.MOB_TARGET) return;
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide) return;
        if (!(event.getNewTarget() instanceof ServerPlayer player)) return;
        if (!StealthConfig.ENABLED.get() || !covered(mob) || !eligible(player)) return;
        long now = mob.level().getGameTime();
        Mind m = mind(mob);
        if (headless(mob, m, now)) return; // Dual Hitbox decides for headless zombies (noise-only targeting)
        Pair pair = m.pairs.get(player.getUUID());
        if (pair != null && pair.state == State.PURSUING) return;
        if (mob.getLastHurtByMob() == player && mob.tickCount - mob.getLastHurtByMobTimestamp() < 200) {
            transition(mob, m.pair(player.getUUID()), State.PURSUING, "hurt", now);
            return;
        }
        event.setCanceled(true);
        // Zombie Awareness (huntTarget) starts a long-distance path toward the player right before setTarget, often
        // to an intermediate point; drop that path so a refused target does not still walk to the player.
        if (calledFromZombieAwareness()) {
            zombieAwarenessDenials++;
            if (!m.hasLead(now)) mob.getNavigation().stop();
        }
    }

    static int zombieAwarenessDenials; // shown by /lazarusstealth status

    private static final StackWalker STACK = StackWalker.getInstance();

    private static boolean calledFromZombieAwareness() {
        return STACK.walk(frames -> frames.limit(40).anyMatch(f -> f.getClassName().startsWith("com.corosus.zombieawareness.")));
    }

    // Being hurt by a player always reveals them (some Apocalypse Now zombies have no HurtByTargetGoal).
    // receiveCanceled: Dual Hitbox cancels every hurt on its zombies and applies the damage itself.
    @SubscribeEvent(receiveCanceled = true)
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !covered(mob) || !StealthConfig.ENABLED.get()) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || !eligible(player)) return;
        Mind m = mind(mob);
        pursue(mob, m, m.pair(player.getUUID()), player, "hurt", mob.level().getGameTime());
    }

    // ---------- vision sampling ----------

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        if (!StealthConfig.ENABLED.get()) return;
        long now = level.getGameTime();
        int interval = StealthConfig.SAMPLE_TICKS.get();
        for (ServerPlayer player : level.players()) {
            if ((now + player.getId()) % interval != 0) continue;
            long start = System.nanoTime();
            samplePlayer(level, player, now, interval);
            StealthCommands.addPerf(System.nanoTime() - start);
        }
    }

    private static void samplePlayer(ServerLevel level, ServerPlayer player, long now, int interval) {
        PlayerState s = PlayerState.of(player);
        Vec3 pos = player.position();
        if (s.lastSamplePos != null && now > s.lastSampleTick) {
            double moved = Math.hypot(pos.x - s.lastSamplePos.x, pos.z - s.lastSamplePos.z);
            if (moved > 8) moved = 0; // teleport
            s.sampleMoved = moved;
            s.sampleSpeed = moved / (now - s.lastSampleTick);
        }
        s.lastSamplePos = pos;
        s.lastSampleTick = now;
        if (!eligible(player)) {
            s.threat = 0;
            return;
        }
        double range = StealthConfig.SIGHT_RANGE.get();
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(range, range / 2, range),
                m -> m.isAlive() && covered(m));
        if (mobs.size() > StealthConfig.MAX_MOBS_PER_PLAYER.get()) {
            mobs = new ArrayList<>(mobs);
            mobs.sort(Comparator.comparingDouble(m -> m.distanceToSqr(player)));
            mobs = mobs.subList(0, StealthConfig.MAX_MOBS_PER_PLAYER.get());
        }
        int threat = 0;
        double nearestDist = Double.MAX_VALUE;
        double dt = interval / 20.0;
        for (Mob mob : mobs) {
            Mind m = mind(mob);
            Pair pair = m.pair(player.getUUID());
            double rate = evaluate(level, mob, m, pair, player, s, dt, now);
            double dist = mob.distanceTo(player);
            SneakXp.observe(player, s, mob, pair, dist, posture(player), now);
            if (pair.state == State.PURSUING) threat = 2;
            else if (threat < 1 && (pair.state.ordinal() >= State.INVESTIGATING.ordinal()
                    || player.getUUID().equals(m.leadCause) && m.hasLead(now))) threat = 1;
            if (s.debug && dist < nearestDist) {
                nearestDist = dist;
                s.debugLine = String.format("%s %s susp=%.2f rate=%.2f d=%.1f %s",
                        mob.getType().toShortString(), pair.state, pair.suspicion, rate, dist, pair.reason);
            }
        }
        s.threat = threat;
        SneakXp.sweep(s, now);
        if (s.debug) StealthCommands.showDebug(player, s);
    }

    // Returns the suspicion rate used this sample (for the debug line).
    static double evaluate(ServerLevel level, Mob mob, Mind m, Pair pair, ServerPlayer player, PlayerState s, double dt, long now) {
        if (m.lead != null && !m.hasLead(now)) leadFinished(mob, m);
        LivingEntity target = mob.getTarget();
        if (target == player) {
            pair.state = State.PURSUING;
            if (mob.getSensing().hasLineOfSight(player)) pair.lastKnown = player.position();
            return 0;
        }
        if (pair.state == State.PURSUING) {
            // Lost (or switched) target: go to the last known position, then search; posture never clears a chase.
            transition(mob, pair, State.ALERTED, "lost target", now);
            pair.suspicion = Math.max(pair.suspicion, 0.6);
            if (target == null)
                m.setLead(pair.lastKnown != null ? pair.lastKnown : player.position(),
                        StealthConfig.MEMORY_SECONDS.get() * 20, 1.1, StealthMath.HIGH, player.getUUID(), now);
        }
        if (target != null) return 0; // busy with someone else
        if (headless(mob, m, now)) {
            decay(mob, m, pair, dt, now);
            return 0;
        }
        Vec3 mobEye = mob.getEyePosition();
        double dx = player.getX() - mob.getX(), dz = player.getZ() - mob.getZ();
        if (Math.hypot(dx, dz) < StealthConfig.CONTACT_DISTANCE.get() + mob.getBbWidth() / 2
                && Math.abs(player.getY() - mob.getY()) < 1.5) {
            pursue(mob, m, pair, player, "contact", now);
            return 0;
        }
        Vec3 toPlayer = player.getEyePosition().subtract(mobEye);
        double dist = toPlayer.length();
        double range = StealthConfig.SIGHT_RANGE.get();
        double angle = dist < 1e-3 ? 0 : Math.toDegrees(Math.acos(Mth.clamp(mob.getViewVector(1f).dot(toPlayer.scale(1 / dist)), -1, 1)));
        double frontHalf = StealthConfig.FRONT_CONE.get() / 2, periHalf = StealthConfig.PERIPHERAL_CONE.get() / 2;
        double rate = 0;
        if (dist < range && angle <= periHalf) {
            int posture = posture(player);
            double exposure = exposure(level, mob, mobEye, player, posture);
            if (exposure > 0) {
                BlockPos eyePos = BlockPos.containing(player.getEyePosition());
                boolean lit = player.isOnFire() || player.isCurrentlyGlowing()
                        || player.getMainHandItem().is(LazarusStealth.LIGHT_SOURCES) || player.getOffhandItem().is(LazarusStealth.LIGHT_SOURCES);
                double light = level.getMaxLocalRawBrightness(eyePos);
                boolean alert = pair.state.ordinal() >= State.INVESTIGATING.ordinal() || m.hasLead(now);
                rate = StealthMath.visualRate(StealthConfig.DETECTION_RATE.get(), dist, range, angle, frontHalf, periHalf,
                        exposure, light, lit, posture, s.sampleSpeed, LazarusStealth.attr(player, LazarusStealth.VISUAL_STEALTH),
                        alert ? StealthConfig.ALERT_VISION_MULT.get() : 1);
            }
        }
        if (rate <= 0) {
            decay(mob, m, pair, dt, now);
            return 0;
        }
        pair.suspicion = Math.min(1, pair.suspicion + rate * dt);
        pair.lastKnown = player.position();
        if (pair.suspicion >= 1) {
            pursue(mob, m, pair, player, "sight", now);
        } else if (pair.suspicion >= 0.6 && pair.state.ordinal() < State.INVESTIGATING.ordinal()) {
            transition(mob, pair, State.INVESTIGATING, "glimpse", now);
            m.setLead(player.position(), StealthConfig.INVESTIGATE_SECONDS.get() * 20, 0.9, StealthMath.LOW, player.getUUID(), now);
        } else if (pair.suspicion >= 0.3 && pair.state == State.UNAWARE) {
            transition(mob, pair, State.SUSPICIOUS, "glimpse", now);
        }
        if (pair.state == State.SUSPICIOUS) {
            m.lookAt = player.getEyePosition();
            m.lookUntil = now + 40;
        }
        return rate;
    }

    private static void decay(Mob mob, Mind m, Pair pair, double dt, long now) {
        double rate = StealthConfig.SUSPICION_DECAY.get() * (pair.state.ordinal() >= State.ALERTED.ordinal() ? 0.5 : 1);
        pair.suspicion = Math.max(0, pair.suspicion - rate * dt);
        boolean lead = m.hasLead(now);
        switch (pair.state) {
            case ALERTED -> {
                if (!lead && now - pair.since > StealthConfig.MEMORY_SECONDS.get() * 20L)
                    transition(mob, pair, State.INVESTIGATING, "no clues", now);
            }
            case INVESTIGATING -> {
                if (!lead && pair.suspicion < 0.6) transition(mob, pair, State.SUSPICIOUS, "search over", now);
            }
            case SUSPICIOUS -> {
                if (pair.suspicion < 0.15 && !lead) transition(mob, pair, State.UNAWARE, "calm", now);
            }
            default -> {}
        }
    }

    // Fraction of body points visible from the zombie's eyes: head/torso/hips standing, three low points crawling.
    // Blocks tagged see_through (fences, bars, glass) do not hide anyone.
    static double exposure(ServerLevel level, Mob mob, Vec3 eye, Player player, int posture) {
        double y = player.getY(), h = player.getBbHeight();
        double[] heights = posture == StealthMath.CRAWL ? new double[]{h * 0.85, h * 0.5, 0.1}
                : new double[]{player.getEyeHeight(), h * 0.6, h * 0.25};
        int visible = 0;
        for (double off : heights) {
            Vec3 to = new Vec3(player.getX(), y + off, player.getZ());
            if (level.clip(new SightClip(eye, to, mob)).getType() == HitResult.Type.MISS) visible++;
        }
        return visible / (double) heights.length;
    }

    static final class SightClip extends ClipContext {
        SightClip(Vec3 from, Vec3 to, Entity entity) {
            super(from, to, Block.COLLIDER, Fluid.NONE, entity);
        }

        @Override
        public VoxelShape getBlockShape(BlockState state, BlockGetter level, BlockPos pos) {
            return state.is(LazarusStealth.SEE_THROUGH) ? Shapes.empty() : super.getBlockShape(state, level, pos);
        }
    }

    // ---------- hearing (called by Noise) ----------

    static void hear(Mob mob, Noise.Event noise, long now) {
        if (mob.getTarget() != null) return;
        Mind m = mind(mob);
        int cls = noise.cls();
        if (!m.hasLead(now) || cls >= m.leadClass) {
            double speed = cls == StealthMath.LOW ? 0.9 : cls == StealthMath.MEDIUM ? 1.05 : 1.2;
            UUID cause = noise.source() == null ? null : noise.source().getUUID();
            m.setLead(noise.pos(), StealthConfig.INVESTIGATE_SECONDS.get() * 20, speed, cls, cause, now);
        }
        if (noise.source() == null) return;
        Pair pair = m.pair(noise.source().getUUID());
        if (pair.state == State.PURSUING) return;
        if (cls >= StealthMath.HIGH) {
            transition(mob, pair, State.ALERTED, "heard " + noise.kind() + " (high)", now);
        } else if (cls == StealthMath.MEDIUM) {
            if (now - pair.noiseWindow > 200) {
                pair.noiseWindow = now;
                pair.noiseHits = 0;
            }
            pair.suspicion = Math.min(0.95, pair.suspicion + 0.15);
            if (++pair.noiseHits >= 3) transition(mob, pair, State.ALERTED, "repeated noise", now);
            else if (pair.state.ordinal() < State.INVESTIGATING.ordinal()) transition(mob, pair, State.INVESTIGATING, "heard " + noise.kind(), now);
        } else if (pair.state.ordinal() < State.INVESTIGATING.ordinal()) {
            transition(mob, pair, State.INVESTIGATING, "heard " + noise.kind() + " (low)", now);
        } else {
            pair.since = now; // a fresh lead keeps the alert alive
        }
    }

    // ---------- transitions ----------

    static void pursue(Mob mob, Mind m, Pair pair, ServerPlayer player, String reason, long now) {
        transition(mob, pair, State.PURSUING, reason, now);
        pair.pursuedSinceLead = true;
        pair.lastKnown = player.position();
        m.clearLead();
        if (mob.getTarget() != player) mob.setTarget(player);
    }

    private static void transition(Mob mob, Pair pair, State next, String reason, long now) {
        if (pair.state == next) {
            pair.since = now;
            return;
        }
        if (StealthConfig.DEBUG_LOG.get())
            LazarusStealth.LOG.info("[lazarusstealth] {} {} -> {} ({})", mob.getType().toShortString(), pair.state, next, reason);
        pair.state = next;
        pair.since = now;
        pair.reason = reason;
    }

    // Lead finished without finding anyone: a player who caused it and was not caught earns "diverted" XP.
    private static void leadFinished(Mob mob, Mind m) {
        UUID cause = m.leadCause;
        m.clearLead();
        if (cause == null || !(mob.level() instanceof ServerLevel level)) return;
        Pair pair = m.pairs.get(cause);
        if (level.getPlayerByUUID(cause) instanceof ServerPlayer player && pair != null && !pair.pursuedSinceLead
                && mob.distanceTo(player) < 20 && eligible(player)) {
            SneakXp.diverted(player, mob);
        }
    }

    // ---------- lifecycle ----------

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob) || !covered(mob)) return;
        if (GOAL_ADDED.put(mob, Boolean.TRUE) != null) return;
        mob.goalSelector.addGoal(0, new InvestigateGoal(mob));
        mob.goalSelector.addGoal(0, new IdleGazeGoal(mob));
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof Mob mob) MINDS.remove(mob);
    }

    // Spawner, spawn egg (and optionally /summon) infected never give Sneak XP.
    @SubscribeEvent
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (!covered(event.getEntity())) return;
        switch (event.getSpawnType()) {
            case SPAWNER, SPAWN_EGG, DISPENSER, BUCKET -> SneakXp.markFarm(event.getEntity());
            case COMMAND -> {
                if (!StealthConfig.XP_FROM_COMMAND_SPAWNS.get()) SneakXp.markFarm(event.getEntity());
            }
            default -> {}
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        forgetPlayer(event.getEntity().getUUID());
        if (event.getEntity() instanceof ServerPlayer player) PlayerState.forget(player);
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        forgetPlayer(event.getEntity().getUUID());
    }

    private static void forgetPlayer(UUID id) {
        for (Mind m : MINDS.values()) m.pairs.remove(id);
    }

    // ---------- movement: walk to the lead, look at suspicious glimpses, search around ----------

    static final class InvestigateGoal extends Goal {
        private final Mob mob;
        private long nextRepath;
        private Vec3 searchPoint;
        private int failedPaths;

        InvestigateGoal(Mob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private Mind active() {
            if (!StealthConfig.ENABLED.get() || mob.getTarget() != null) return null;
            Mind m = MINDS.get(mob);
            long now = mob.level().getGameTime();
            return m != null && (m.hasLead(now) || m.lookAt != null && now < m.lookUntil) ? m : null;
        }

        @Override
        public boolean canUse() {
            return active() != null;
        }

        @Override
        public boolean canContinueToUse() {
            return active() != null;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            nextRepath = 0;
            searchPoint = null;
            failedPaths = 0;
        }

        @Override
        public void tick() {
            Mind m = MINDS.get(mob);
            if (m == null) return;
            long now = mob.level().getGameTime();
            if (!m.hasLead(now)) {
                if (m.lead != null) leadFinished(mob, m);
                if (m.lookAt != null && now < m.lookUntil) {
                    mob.getNavigation().stop();
                    mob.getLookControl().setLookAt(m.lookAt.x, m.lookAt.y, m.lookAt.z);
                }
                return;
            }
            if (!m.arrived && mob.position().distanceToSqr(m.lead) < 4) {
                m.arrived = true;
                m.searchUntil = now + StealthConfig.SEARCH_SECONDS.get() * 20L;
                searchPoint = null;
            }
            if (m.arrived) {
                if (now >= nextRepath && (searchPoint == null || mob.getNavigation().isDone())) {
                    var r = mob.getRandom();
                    searchPoint = m.lead.add(r.nextInt(11) - 5, 0, r.nextInt(11) - 5);
                    mob.getNavigation().moveTo(searchPoint.x, searchPoint.y, searchPoint.z, 0.7);
                    nextRepath = now + 40 + r.nextInt(40);
                }
                return;
            }
            if (now >= nextRepath) {
                nextRepath = now + 20;
                BlockPos end = mob.getNavigation().getTargetPos();
                // Keep paths that already end near the lead (e.g. the gunshot script's scattered destinations).
                if (mob.getNavigation().isDone() || end == null || end.distToCenterSqr(m.lead) > 36) {
                    if (!mob.getNavigation().moveTo(m.lead.x, m.lead.y, m.lead.z, m.leadSpeed) && ++failedPaths >= 3) {
                        m.arrived = true; // unreachable: search where it stands
                        m.searchUntil = now + StealthConfig.SEARCH_SECONDS.get() * 20L;
                    }
                }
            }
            mob.getLookControl().setLookAt(m.lead.x, m.lead.y + 1, m.lead.z);
        }
    }

    // Vanilla LookAtPlayerGoal turns a zombie's head toward any player within 8 blocks it has line of sight to,
    // even one standing behind it, which would defeat the field of view. While the zombie has no target and no
    // lead, this goal holds the LOOK flag and glances around within 60 degrees of its body instead.
    static final class IdleGazeGoal extends Goal {
        private final Mob mob;
        private long nextGlance;
        private float offset;

        IdleGazeGoal(Mob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!StealthConfig.ENABLED.get() || mob.getTarget() != null) return false;
            Mind m = MINDS.get(mob);
            long now = mob.level().getGameTime();
            return m == null || !m.hasLead(now) && (m.lookAt == null || now >= m.lookUntil);
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            offset = 0;
            nextGlance = mob.level().getGameTime() + 60 + mob.getRandom().nextInt(60);
        }

        @Override
        public void tick() {
            long now = mob.level().getGameTime();
            if (now >= nextGlance) {
                nextGlance = now + 60 + mob.getRandom().nextInt(60);
                offset = (mob.getRandom().nextFloat() - 0.5f) * 120;
            }
            double rad = Math.toRadians(mob.yBodyRot + offset);
            mob.getLookControl().setLookAt(mob.getX() - Math.sin(rad) * 4, mob.getEyeY(), mob.getZ() + Math.cos(rad) * 4);
        }
    }
}
