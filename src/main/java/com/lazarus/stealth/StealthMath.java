package com.lazarus.stealth;

// Balance formulas with primitive inputs only, so StealthMathTest can check them without a game.
public final class StealthMath {
    public static final int STAND = 0, CROUCH = 1, CRAWL = 2;
    public static final int NONE = 0, LOW = 1, MEDIUM = 2, HIGH = 3, EXTREME = 4;
    // Noise radius (blocks) at which each class starts; below LOW_RADIUS nothing is emitted.
    // EXTREME is above anything a body can make (footsteps cap at 2.5x the loudest surface = 35): gunshots.
    public static final double LOW_RADIUS = 1.5, MEDIUM_RADIUS = 6, HIGH_RADIUS = 14, EXTREME_RADIUS = 40;
    private static final double[] POSTURE_VISIBILITY = {1.0, 0.5, 0.3};

    private StealthMath() {}

    // Suspicion gained per second by one zombie looking at one player.
    // angle: degrees between the zombie's view and the direction to the player (0 = straight ahead).
    // exposure: fraction of body sample points with a clear line of sight. light: 0..15 at the player.
    public static double visualRate(double base, double dist, double range, double angle, double frontHalf,
                                    double peripheralHalf, double exposure, double light, boolean lightHeld,
                                    int posture, double speed, double visualSkill, double alertMult) {
        return visualRate(base, dist, range, angle, frontHalf, peripheralHalf, exposure, light, lightHeld, 0.2,
                posture, speed, visualSkill, alertMult);
    }

    // darkFloor: how much is still seen in total darkness (0.2 for zombies; humans see better in the dark).
    public static double visualRate(double base, double dist, double range, double angle, double frontHalf,
                                    double peripheralHalf, double exposure, double light, boolean lightHeld, double darkFloor,
                                    int posture, double speed, double visualSkill, double alertMult) {
        if (exposure <= 0 || dist >= range) return 0;
        double angleF;
        if (angle <= frontHalf) angleF = angle < 25 && dist < 3 ? 3.0 : 1.0; // right in its face
        else if (angle <= peripheralHalf) angleF = lerp((angle - frontHalf) / (peripheralHalf - frontHalf), 0.35, 0.08);
        else return 0;
        double distF = clamp((range - dist) / (range - 2), 0, 1);
        double lightF = lightHeld ? 1.2 : darkFloor + (1 - darkFloor) * clamp(light, 0, 15) / 15;
        double moveF = speed < 0.01 ? 0.55 : speed < 0.1 ? 0.8 : speed < 0.24 ? 1.0 : 1.5;
        // The Sneak skill works fully while crouched or crawling and only a little while standing.
        double skillF = 1 - clamp(visualSkill, 0, 0.6) * (posture == STAND ? 0.3 : 1.0);
        return base * distF * angleF * exposure * lightF * POSTURE_VISIBILITY[posture] * moveF * skillF * alertMult;
    }

    // Load fraction of Encumbered capacity -> footstep multiplier (1.0 up to 50%, +15% at 80%, +30% at 100%+).
    public static double loadMultiplier(double fraction) {
        if (fraction <= 0.5) return 1;
        if (fraction <= 0.8) return 1 + 0.15 * (fraction - 0.5) / 0.3;
        return 1.15 + 0.15 * Math.min(1, (fraction - 0.8) / 0.2);
    }

    private static final double[] EXHAUSTION = {1, 1, 1, 1.1, 1.25, 1.4};

    public static double exhaustionMultiplier(int level) {
        return EXHAUSTION[Math.max(0, Math.min(5, level))];
    }

    // Footstep radius. surfaceBase is the walking radius of the surface; loud surfaces (glass) ignore posture.
    // burdenControl (skill) removes part of the load/exhaustion penalty; footstepSkill shrinks the radius.
    public static double footstepRadius(double surfaceBase, double gait, boolean ignorePosture, double loadFraction,
                                        int exhaustion, double footstepSkill, double burdenControl) {
        double penalty = loadMultiplier(loadFraction) * exhaustionMultiplier(exhaustion) - 1;
        double burden = 1 + penalty * (1 - clamp(burdenControl, 0, 0.8));
        double skill = 1 - clamp(footstepSkill, 0, 0.6) * (ignorePosture ? 0.5 : 1);
        double mult = clamp((ignorePosture ? Math.max(gait, 1) : gait) * burden * skill, 0.15, 2.5);
        return surfaceBase * mult;
    }

    public static int noiseClass(double radius) {
        return radius < LOW_RADIUS ? NONE : radius < MEDIUM_RADIUS ? LOW : radius < HIGH_RADIUS ? MEDIUM : radius < EXTREME_RADIUS ? HIGH : EXTREME;
    }

    // What the HUD shows: the last class for holdTicks, then one class less every stepTicks.
    public static int displayedClass(int lastClass, long ticksSince, int holdTicks, int stepTicks) {
        if (ticksSince <= holdTicks) return lastClass;
        return (int) Math.max(NONE, lastClass - 1 - (ticksSince - holdTicks) / stepTicks);
    }

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    static double lerp(double t, double a, double b) {
        return a + (b - a) * clamp(t, 0, 1);
    }
}
