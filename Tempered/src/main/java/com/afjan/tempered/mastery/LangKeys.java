package com.afjan.tempered.mastery;

import java.util.List;

/** Fixed translation keys, in one place so the translation GameTest can check them all. */
public final class LangKeys {
    public static final String KEY_OVERVIEW = "key.tempered.overview";

    public static final String LEVEL_UP = "message.tempered.level_up";
    public static final String MASTERED = "message.tempered.mastered";
    public static final String NEXT = "message.tempered.next";
    public static final String BROKE = "message.tempered.broke";
    public static final String IS_BROKEN = "message.tempered.is_broken";

    public static final String TIP_MASTERY = "tooltip.tempered.mastery";
    public static final String TIP_MASTERED = "tooltip.tempered.mastered";
    public static final String TIP_UNTOUCHED = "tooltip.tempered.untouched";
    public static final String TIP_NEXT = "tooltip.tempered.next";
    public static final String TIP_REQUIREMENT = "tooltip.tempered.requirement";
    public static final String TIP_PERKS = "tooltip.tempered.perks";
    public static final String TIP_HOLD_SHIFT = "tooltip.tempered.hold_shift";
    public static final String TIP_OVERVIEW = "tooltip.tempered.overview";
    public static final String TIP_SNEAK = "tooltip.tempered.sneak";
    public static final String TIP_BROKEN = "tooltip.tempered.broken";

    public static final String GUI_TITLE = "gui.tempered.title";
    public static final String GUI_TOOLS = "gui.tempered.tools";
    public static final String GUI_SPECIAL = "gui.tempered.special";
    public static final String GUI_MILESTONES = "gui.tempered.milestones";
    public static final String GUI_YOURS = "gui.tempered.yours";
    public static final String GUI_NONE_OWNED = "gui.tempered.none_owned";
    public static final String GUI_DONE = "gui.tempered.done";
    public static final String GUI_REWARDS = "gui.tempered.rewards";
    public static final String GUI_HELP_1 = "gui.tempered.help.1";
    public static final String GUI_HELP_2 = "gui.tempered.help.2";
    public static final String GUI_HELP_3 = "gui.tempered.help.3";
    public static final String GUI_HELP_ELITE = "gui.tempered.help.elite";
    public static final String GUI_HELP_RAIDERS = "gui.tempered.help.raiders";
    public static final String GUI_SCROLL = "gui.tempered.scroll";

    public static final List<String> STATIC = List.of(
            KEY_OVERVIEW, LEVEL_UP, MASTERED, NEXT, BROKE, IS_BROKEN,
            TIP_MASTERY, TIP_MASTERED, TIP_UNTOUCHED, TIP_NEXT, TIP_REQUIREMENT, TIP_PERKS, TIP_HOLD_SHIFT, TIP_OVERVIEW,
            TIP_SNEAK, TIP_BROKEN,
            GUI_TITLE, GUI_TOOLS, GUI_SPECIAL, GUI_MILESTONES, GUI_YOURS, GUI_NONE_OWNED, GUI_DONE, GUI_REWARDS,
            GUI_HELP_1, GUI_HELP_2, GUI_HELP_3, GUI_HELP_ELITE, GUI_HELP_RAIDERS, GUI_SCROLL);

    private LangKeys() {
    }
}
