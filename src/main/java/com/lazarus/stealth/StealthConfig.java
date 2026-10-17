package com.lazarus.stealth;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.BooleanValue;
import net.minecraftforge.common.ForgeConfigSpec.DoubleValue;
import net.minecraftforge.common.ForgeConfigSpec.IntValue;

// SERVER config (per world, copied from defaultconfigs on new worlds) and CLIENT config (HUD only).
public final class StealthConfig {
    public static final ForgeConfigSpec SERVER_SPEC, CLIENT_SPEC;

    // Perception
    public static BooleanValue ENABLED;
    public static IntValue SAMPLE_TICKS, MAX_MOBS_PER_PLAYER;
    public static DoubleValue SIGHT_RANGE, FRONT_CONE, PERIPHERAL_CONE, DETECTION_RATE, SUSPICION_DECAY,
            CONTACT_DISTANCE, ALERT_VISION_MULT;
    public static IntValue MEMORY_SECONDS, SEARCH_SECONDS, INVESTIGATE_SECONDS;
    // Noise
    public static DoubleValue SOFT_RADIUS, MEDIUM_RADIUS, METAL_RADIUS, GLASS_RADIUS, CROUCH_GAIT, CRAWL_GAIT,
            SPRINT_GAIT, BREAK_RADIUS, GLASS_BREAK_RADIUS, MELEE_RADIUS, DOOR_RADIUS, CONTAINER_RADIUS,
            PICKUP_RADIUS, OCCLUSION, LOUD_GUN_RADIUS, SILENCED_GUN_RADIUS;
    public static IntValue MAX_MOBS_PER_NOISE;
    // Finisher
    public static BooleanValue FINISHER_ENABLED;
    public static DoubleValue FINISHER_STAMINA, FINISHER_MIN_ANGLE, FINISHER_MASTERY_ANGLE, SPECIAL_CRIT_FRACTION,
            SPECIAL_HEALTH, FINISHER_CHARGE;
    public static IntValue FINISHER_COOLDOWN;
    // XP
    public static BooleanValue XP_ENABLED, XP_FROM_COMMAND_SPAWNS;
    public static IntValue XP_DAILY_CAP, XP_MOB_COOLDOWN_MINUTES, XP_PER_AREA;
    public static BooleanValue DEBUG_LOG;
    // Client
    public static BooleanValue HUD_ENABLED, HUD_ALWAYS;
    public static IntValue HUD_X, HUD_Y;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("perception");
        ENABLED = b.comment("Master switch. Off = zombies use their normal targeting again.").define("enabled", true);
        SAMPLE_TICKS = b.comment("Ticks between vision checks for each player (staggered per player).").defineInRange("sampleTicks", 6, 2, 20);
        MAX_MOBS_PER_PLAYER = b.comment("Nearest infected checked per player per sample.").defineInRange("maxMobsPerPlayer", 48, 4, 256);
        SIGHT_RANGE = b.comment("Blocks; nothing is seen beyond this.").defineInRange("sightRange", 24.0, 4, 64);
        FRONT_CONE = b.comment("Full angle (degrees) of the fast frontal cone.").defineInRange("frontConeDegrees", 110.0, 30, 180);
        PERIPHERAL_CONE = b.comment("Full angle of slow peripheral vision; behind it nothing is seen.").defineInRange("peripheralConeDegrees", 200.0, 60, 360);
        DETECTION_RATE = b.comment("Suspicion per second at ideal exposure (1.0 suspicion = discovered).").defineInRange("detectionRate", 2.5, 0.1, 20);
        SUSPICION_DECAY = b.comment("Suspicion lost per second while nothing is seen.").defineInRange("suspicionDecay", 0.12, 0.01, 2);
        CONTACT_DISTANCE = b.comment("Closer than this (blocks, horizontal) = discovered by touch.").defineInRange("contactDistance", 1.2, 0.5, 3);
        ALERT_VISION_MULT = b.comment("Vision speed multiplier for zombies already alerted or searching.").defineInRange("alertVisionMultiplier", 2.5, 1, 10);
        MEMORY_SECONDS = b.comment("After losing its target, seconds spent going to the last known position.").defineInRange("memorySeconds", 6, 1, 60);
        SEARCH_SECONDS = b.comment("Seconds of local search after arriving at a lead.").defineInRange("searchSeconds", 8, 1, 60);
        INVESTIGATE_SECONDS = b.comment("Seconds a noise lead stays valid.").defineInRange("investigateSeconds", 15, 2, 120);
        b.pop();

        b.push("noise");
        SOFT_RADIUS = b.comment("Walking footstep radius (blocks) on #lazarusstealth:surface_soft (grass, dirt, leaves...).").defineInRange("softRadius", 5.0, 0, 64);
        MEDIUM_RADIUS = b.comment("Walking footstep radius on anything not tagged (wood, stone, concrete, asphalt).").defineInRange("mediumRadius", 8.0, 0, 64);
        METAL_RADIUS = b.comment("Walking footstep radius on #lazarusstealth:surface_metal.").defineInRange("metalRadius", 12.0, 0, 64);
        GLASS_RADIUS = b.comment("Footstep radius on #lazarusstealth:surface_glass; posture does not reduce it.").defineInRange("glassRadius", 14.0, 0, 64);
        CROUCH_GAIT = b.defineInRange("crouchMultiplier", 0.4, 0, 2);
        CRAWL_GAIT = b.defineInRange("crawlMultiplier", 0.3, 0, 2);
        SPRINT_GAIT = b.defineInRange("sprintMultiplier", 1.7, 0, 4);
        BREAK_RADIUS = b.comment("Breaking a block (no posture or skill discount). Matches Dual Hitbox noiseAttractionRange.").defineInRange("blockBreakRadius", 14.0, 0, 64);
        GLASS_BREAK_RADIUS = b.comment("Breaking glass (#forge:glass, #forge:glass_panes, surface_glass).").defineInRange("glassBreakRadius", 18.0, 0, 64);
        MELEE_RADIUS = b.comment("Melee hit on a living entity.").defineInRange("meleeRadius", 8.0, 0, 64);
        DOOR_RADIUS = b.comment("Opening/closing doors, trapdoors and gates (halved while crouching).").defineInRange("doorRadius", 6.0, 0, 64);
        CONTAINER_RADIUS = b.comment("Opening a container (halved while crouching).").defineInRange("containerRadius", 4.0, 0, 64);
        PICKUP_RADIUS = b.comment("Picking up items (halved while crouching).").defineInRange("pickupRadius", 2.0, 0, 64);
        LOUD_GUN_RADIUS = b.comment("TaCZ shot, only for the HUD/alert state; attraction stays with kubejs lazarus_gunshot_noise.js.").defineInRange("loudGunRadius", 64.0, 0, 256);
        SILENCED_GUN_RADIUS = b.defineInRange("silencedGunRadius", 16.0, 0, 256);
        OCCLUSION = b.comment("Radius multiplier when a solid block lies between the noise and the zombie.").defineInRange("occlusionMultiplier", 0.6, 0.1, 1);
        MAX_MOBS_PER_NOISE = b.comment("Nearest infected that react to one noise.").defineInRange("maxMobsPerNoise", 10, 1, 64);
        b.pop();

        b.push("finisher");
        FINISHER_ENABLED = b.define("enabled", true);
        FINISHER_STAMINA = b.comment("PeakStamina cost (ignored without PeakStamina).").defineInRange("staminaCost", 15.0, 0, 100);
        FINISHER_COOLDOWN = b.comment("Seconds between takedowns per player.").defineInRange("cooldownSeconds", 6, 0, 120);
        FINISHER_MIN_ANGLE = b.comment("Minimum angle (degrees) between the zombie's view and the attacker: 180 = straight behind.").defineInRange("minAngle", 100.0, 0, 180);
        FINISHER_MASTERY_ANGLE = b.comment("Minimum angle with the Finalizador skill.").defineInRange("masteryMinAngle", 80.0, 0, 180);
        SPECIAL_CRIT_FRACTION = b.comment("Specials take at least this fraction of max health instead of dying.").defineInRange("specialCritFraction", 0.35, 0, 1);
        SPECIAL_HEALTH = b.comment("Infected with more max health than this are treated as specials even if untagged.").defineInRange("specialHealthThreshold", 40.0, 1, 10000);
        FINISHER_CHARGE = b.comment("Minimum attack charge (0..1).").defineInRange("minAttackCharge", 0.9, 0, 1);
        b.pop();

        b.push("xp");
        XP_ENABLED = b.define("enabled", true);
        XP_DAILY_CAP = b.comment("Max Sneak XP per in-game day per player.").defineInRange("dailyCap", 60, 0, 10000);
        XP_MOB_COOLDOWN_MINUTES = b.comment("The same zombie gives XP again only after this many real minutes.").defineInRange("mobCooldownMinutes", 10, 0, 1440);
        XP_PER_AREA = b.comment("Max rewards per 16x16 area every 5 minutes.").defineInRange("rewardsPerArea", 3, 1, 100);
        XP_FROM_COMMAND_SPAWNS = b.comment("Allow XP from /summon'ed zombies (spawner and spawn egg zombies never give XP).").define("xpFromCommandSpawns", false);
        b.pop();
        DEBUG_LOG = b.comment("Log every perception transition (very verbose).").define("debugLog", false);
        SERVER_SPEC = b.build();

        ForgeConfigSpec.Builder c = new ForgeConfigSpec.Builder();
        c.comment("Noise indicator: shows the noise YOU make (None/Low/Medium/High/Extreme).",
                "It does not measure light, blood scent or how visible you are.").push("hud");
        HUD_ENABLED = c.define("showNoiseIndicator", true);
        HUD_ALWAYS = c.comment("Keep the indicator visible even while silent.").define("alwaysShowNoiseIndicator", false);
        HUD_X = c.comment("Distance from the left edge to the icon (GUI pixels).").defineInRange("offsetLeft", 14, 0, 4000);
        HUD_Y = c.comment("Distance from the bottom edge to the top of the icon (GUI pixels).").defineInRange("offsetBottom", 50, 16, 4000);
        c.pop();
        CLIENT_SPEC = c.build();
    }

    private StealthConfig() {}
}
