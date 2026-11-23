package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Stealth;
import com.zlorbie788.zeus.core.Zeus;
import net.minecraft.client.Minecraft;

public final class Pvp18 extends Module {
    private final Setting.Num cps = add(new Setting.Num("Click rate", "Attacks a second while spamming.", 14, 6, 20, 1, " cps"));
    private final Setting.Bool crits = add(new Setting.Bool("Keep criticals", "Off: nothing waits for a fall, which is the point of spam mode. On: aura critical settings still apply.", false));

    public Pvp18() {
        super("18pvp", "1.8 PVP", "Puts every combat module on the 1.8 rhythm: spam clicks instead of waiting for the attack bar, no crit timing.",
                Category.COMBAT);
        alias("1.8pvp", "1.8", "spam", "oldpvp");
    }

    public static boolean spam() {
        Pvp18 m = Zeus.get(Pvp18.class);
        return m != null && m.enabled();
    }

    public static boolean critsKept() {
        Pvp18 m = Zeus.get(Pvp18.class);
        return m == null || !m.enabled() || m.crits.on();
    }

    public static int rate() {
        Pvp18 m = Zeus.get(Pvp18.class);
        return m == null ? 14 : (int) Math.max(6, Math.round(m.cps.get()));
    }

    @Override
    protected void onEnable() {
        Stealth.attackBudget(20);
    }

    @Override
    protected void onDisable() {
        Stealth.attackBudget(8);
    }

    @Override
    protected void onTick(Minecraft mc) {
        Stealth.attackBudget(20);
    }

    @Override
    public String info() {
        return "spam " + Math.round(cps.get()) + " cps";
    }
}
