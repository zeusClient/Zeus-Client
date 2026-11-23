package com.zlorbie788.zeus.core;

import net.minecraft.util.Mth;

import java.util.Locale;

public final class SelfCheck {
    private SelfCheck() {}

    private static int fails;

    public static void main(String[] args) {
        gridStepsAreWhole();
        sensitivitySurvivesGcdRecovery();
        fadeNeverOvershoots();
        movingAimNeverStalls();
        aurasTakeTurns();
        System.out.println(fails == 0 ? "SELF CHECK PASS" : "SELF CHECK FAIL (" + fails + ")");
        if (fails != 0) System.exit(1);
    }

    private static void aurasTakeTurns() {
        AuraSync.selfCheck(true, 1000L);
        boolean took = AuraSync.allow("crystal");
        boolean waited = !AuraSync.allow("anchor");
        AuraSync.hold("crystal");
        boolean sequenceKept = !AuraSync.allow("anchor");
        AuraSync.finished("crystal");
        boolean handover = AuraSync.allow("anchor");
        boolean oldWaits = !AuraSync.allow("crystal");
        AuraSync.idle("anchor");
        boolean freedAtOnce = AuraSync.allow("crystal");
        AuraSync.idle("crystal");
        boolean otherTakes = AuraSync.allow("anchor");
        AuraSync.hold("anchor");
        AuraSync.clock(5000L);   // four seconds into a sequence that never finishes
        boolean stuckFreed = AuraSync.allow("crystal");
        AuraSync.selfCheck(false, 6000L);
        boolean alone = !AuraSync.shared() && AuraSync.allow("anchor");
        AuraSync.selfCheckEnd();

        check("one aura takes the field and the other waits", took && waited, "");
        check("a half finished sequence is not interrupted", sequenceKept, "");
        check("a finished sequence hands the field over", handover && oldWaits, "");
        check("an aura with nothing to do frees the field at once", freedAtOnce && otherTakes, "");
        check("a sequence that never finishes cannot block the field", stuckFreed, "");
        check("with one aura on there is no turn taking", alone, "");
    }

    private static void check(String what, boolean ok, String detail) {
        System.out.println((ok ? "  ok    " : "  FAIL  ") + what + (detail.isEmpty() ? "" : "   [" + detail + "]"));
        if (!ok) fails++;
    }

    private static void gridStepsAreWhole() {
        int bad = 0;
        int n = 0;
        for (int s = 0; s <= 100; s++) {
            double grid = Stealth.gridFor(s / 100.0);
            for (int i = 0; i < 40; i++) {
                float last = (float) (Math.random() * 360 - 180);
                float target = last + (float) (Math.random() * 40 - 20);
                float out = Stealth.stepTo(last, target, grid, false);
                double d = Mth.wrapDegrees(out - last);
                n++;
                if (Math.abs(d / grid - Math.round(d / grid)) > 0.02) bad++;
            }
        }
        check("rotation deltas land on whole mouse steps", bad == 0, bad + " of " + n + " off grid");
    }

    private static void sensitivitySurvivesGcdRecovery() {
        double worst = 0;
        double worstSens = 0;
        boolean integral = true;
        for (int s = 10; s <= 100; s += 5) {
            double sens = s / 100.0;
            double grid = Stealth.gridFor(sens);
            // Whole steps of 1, 2 and 3 pixels: the GCD of the stream is the step, as a mouse leaves it.
            double gcd = grid;
            for (int k = 1; k <= 3; k++) gcd = gcd(gcd, k * grid);
            double recovered = (Math.cbrt(gcd / 0.15 / 8.0) - 0.2) / 0.6;
            double err = Math.abs(recovered - sens);
            if (err > worst) {
                worst = err;
                worstSens = sens;
            }
            if (Math.abs(recovered * 100 - Math.round(recovered * 100)) > 0.5) integral = false;
        }
        check("sensitivity recovered from the GCD is the player's own", worst < 1e-3,
                String.format(Locale.ROOT, "worst %.6f at %.2f", worst, worstSens));
        check("recovered sensitivity is a whole slider percent", integral, "");
    }

    private static double gcd(double a, double b) {
        for (int i = 0; i < 80; i++) {
            if (b < 1e-7) break;
            double t = a % b;
            a = b;
            b = t;
        }
        return a;
    }

    private static void fadeNeverOvershoots() {
        int subGrid = 0;
        int overshoot = 0;
        int missed = 0;
        int n = 0;
        for (int s = 10; s <= 100; s += 10) {
            double grid = Stealth.gridFor(s / 100.0);
            for (int i = 0; i < 60; i++) {
                float last = (float) (Math.random() * 360 - 180);
                float target = last + (float) (Math.random() * 120 - 60);
                float at = last;
                double start = Math.abs(Mth.wrapDegrees(target - last));
                for (int tick = Stealth.FADE_TICKS; tick >= 1; tick--) {
                    float next = Stealth.fadeStep(at, target, tick, grid, false);
                    double step = Mth.wrapDegrees(next - at);
                    if (step != 0.0 && Math.abs(step) < grid - 1e-4) subGrid++;
                    double now = Math.abs(Mth.wrapDegrees(target - next));
                    if (now > start + 1e-3) overshoot++;
                    at = next;
                }
                n++;
                if (Math.abs(Mth.wrapDegrees(target - at)) > grid * 0.5 + 1e-4) missed++;
            }
        }
        check("a fade home never sends less than one mouse step", subGrid == 0, subGrid + " sub-grid deltas");
        check("a fade home never overshoots the view", overshoot == 0, overshoot + " overshoots");
        check("a fade home ends within half a step of the view", missed == 0, missed + " of " + n + " too far");
    }

    private static void movingAimNeverStalls() {
        int bad = 0;
        for (int s = 10; s <= 100; s += 10) {
            double grid = Stealth.gridFor(s / 100.0);
            for (int i = 0; i < 200; i++) {
                float last = (float) (Math.random() * 360 - 180);
                float target = last + (float) (Math.random() * 60 - 30);
                if (Math.abs(Mth.wrapDegrees(target - last)) < grid * 0.4) continue;
                if (Stealth.stepTo(last, target, grid, false) == last) bad++;
            }
        }
        check("a moving aim never sends the angle it already sent", bad == 0, bad + " stalls");
    }
}
