package com.lazarus.stealth;

import static com.lazarus.stealth.StealthMath.*;

// Balance targets from the plan, checked with plain asserts (gradle testStealthMath, -ea).
public final class StealthMathTest {
    private static final double BASE = 2.5, RANGE = 24, FRONT = 55, PERI = 100;

    public static void main(String[] args) {
        // Sneak alto (Corpo Baixo 0.4) agachado ao lado de um zumbi a 2.5 blocos, de dia, andando: 3 s sem descoberta.
        double sideDay = visualRate(BASE, 2.5, RANGE, 90, FRONT, PERI, 1, 15, false, CROUCH, 0.06, 0.4, 1);
        assert sideDay * 3 < 0.3 : "side pass should stay below suspicious, got " + sideDay * 3;
        // Behind: invisible to sight at any distance.
        assert visualRate(BASE, 2.0, RANGE, 170, FRONT, PERI, 1, 15, false, STAND, 0.2, 0, 1) == 0;
        // Same side pass without skill, standing and walking: discovered within 3 s.
        double sideNoSkill = visualRate(BASE, 2.5, RANGE, 90, FRONT, PERI, 1, 15, false, STAND, 0.2, 0, 1);
        assert sideNoSkill * 3 >= 1 : "unskilled standing side pass should be seen, got " + sideNoSkill * 3;
        // Standing still in front, in daylight, 16 blocks away: discovered eventually (< 3 s), never instantly.
        double frontStill = visualRate(BASE, 16, RANGE, 0, FRONT, PERI, 1, 15, false, STAND, 0, 0, 1);
        assert frontStill > 1 / 3.0 && frontStill < 6 : "front still rate " + frontStill;
        // Even with max skill, crouching right in its face is found in about a second.
        double face = visualRate(BASE, 2, RANGE, 10, FRONT, PERI, 1, 15, false, CROUCH, 0.05, 0.6, 1);
        assert face >= 0.9 : "face rate " + face;
        // Darkness, crawl and cover each lower the rate; a held torch cancels the darkness.
        double dayCrouch = visualRate(BASE, 8, RANGE, 0, FRONT, PERI, 1, 15, false, CROUCH, 0.05, 0, 1);
        double nightCrouch = visualRate(BASE, 8, RANGE, 0, FRONT, PERI, 1, 2, false, CROUCH, 0.05, 0, 1);
        double nightCrawl = visualRate(BASE, 8, RANGE, 0, FRONT, PERI, 1, 2, false, CRAWL, 0.05, 0, 1);
        double nightTorch = visualRate(BASE, 8, RANGE, 0, FRONT, PERI, 1, 2, true, CROUCH, 0.05, 0, 1);
        double halfCover = visualRate(BASE, 8, RANGE, 0, FRONT, PERI, 1 / 3.0, 15, false, CROUCH, 0.05, 0, 1);
        assert nightCrouch < dayCrouch && nightCrawl < nightCrouch && nightTorch > dayCrouch && halfCover < dayCrouch;
        assert visualRate(BASE, 8, RANGE, 0, FRONT, PERI, 0, 15, false, STAND, 0.2, 0, 1) == 0 : "full cover";
        // Alerted zombies see faster.
        assert visualRate(BASE, 8, RANGE, 0, FRONT, PERI, 1, 15, false, CROUCH, 0.05, 0, 2.5) > dayCrouch * 2;

        // Surfaces: metal and glass louder than grass; walking on metal is Medium, running High; glass High even crouched.
        double grassWalk = footstepRadius(5, 1, false, 0, 0, 0, 0);
        double metalWalk = footstepRadius(12, 1, false, 0, 0, 0, 0);
        double metalRun = footstepRadius(12, 1.7, false, 0, 0, 0, 0);
        double glassCrouch = footstepRadius(14, 0.4, true, 0, 0, 0, 0);
        assert noiseClass(grassWalk) == LOW && noiseClass(metalWalk) == MEDIUM && noiseClass(metalRun) == HIGH;
        assert noiseClass(glassCrouch) == HIGH : "glass crouched " + glassCrouch;
        // Slow crouched step on grass with Passos Leves becomes None.
        assert noiseClass(footstepRadius(5, 0.4, false, 0, 0, 0.3, 0)) == NONE;
        // Load and exhaustion: neutral at <=50% / level 2, capped penalties, skill softens but does not cancel.
        assert loadMultiplier(0.5) == 1 && Math.abs(loadMultiplier(0.8) - 1.15) < 1e-9 && Math.abs(loadMultiplier(2) - 1.3) < 1e-9;
        assert exhaustionMultiplier(2) == 1 && exhaustionMultiplier(5) == 1.4;
        double heavyTired = footstepRadius(8, 1, false, 1.0, 5, 0, 0);
        double heavyTiredSkill = footstepRadius(8, 1, false, 1.0, 5, 0, 0.5);
        assert heavyTired > 8 * 1.7 && heavyTiredSkill < heavyTired && heavyTiredSkill > 8 : heavyTired + " / " + heavyTiredSkill;
        // Floors and caps: never zero, never more than 2.5x.
        assert footstepRadius(8, 0.01, false, 0, 0, 0.6, 0) >= 8 * 0.15 - 1e-9;
        assert footstepRadius(8, 4, false, 2, 5, 0, 0) <= 8 * 2.5 + 1e-9;

        // Extreme is reserved for gunshots: unsilenced 64 (48 with Disciplina) is Extreme (silenced shots are forced Low),
        // and no footstep can get there even sprinting on glass, overloaded and exhausted (cap 2.5x).
        assert noiseClass(64) == EXTREME && noiseClass(48) == EXTREME;
        assert noiseClass(footstepRadius(14, 1.7, true, 2, 5, 0, 0)) == HIGH;
        assert noiseClass(18) == HIGH : "glass break";

        // HUD: holds the last class, then decays one class per step.
        assert displayedClass(HIGH, 10, 30, 20) == HIGH;
        assert displayedClass(HIGH, 31, 30, 20) == MEDIUM;
        assert displayedClass(HIGH, 75, 30, 20) == NONE;
        assert displayedClass(EXTREME, 31, 30, 20) == HIGH;
        // Sprint = walk + 1 class on every surface and with skills/load; never EXTREME (glass stays HIGH)
        double[] walks = {footstepRadius(5, 1, false, 0, 0, 0, 0), footstepRadius(8, 1, false, 0, 0, 0, 0),
                footstepRadius(12, 1, false, 0, 0, 0, 0), footstepRadius(8, 1, false, 0, 0, 0.45, 0),
                footstepRadius(8, 1, false, 1.0, 5, 0, 0), footstepRadius(5, 1, false, 0, 0, 0.6, 0), 2.0, 5.99, 6.0, 13.99};
        for (double walk : walks) {
            int expected = Math.min(HIGH, noiseClass(walk) + 1);
            assert noiseClass(sprintRadius(walk, 1.7)) == expected : "walk " + walk + " sprint " + sprintRadius(walk, 1.7);
        }
        assert noiseClass(8) == MEDIUM && noiseClass(sprintRadius(8, 1.7)) == HIGH : "concrete: the reported bug";
        assert sprintRadius(14, 1.7) == 14 * 1.7 && noiseClass(sprintRadius(14, 1.7)) == HIGH : "glass sprint stays HIGH";
        assert sprintRadius(5, 1.7) == 8.5 : "inside the next class the multiplier still decides the radius";
        assert noiseClass(sprintRadius(footstepRadius(14, 1, true, 1.0, 5, 0, 0), 1.7)) == HIGH : "heavy, tired on glass: still not a gunshot";
        System.out.println("StealthMathTest OK");
    }
}
