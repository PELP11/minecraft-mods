package com.afjan.hatchery.spawner;

import java.util.List;

/** Translation keys of the module texts (the translation GameTest checks them all). */
public final class Messages {
    public static final String INSTALLED = "message.hatchery.module.installed";
    public static final String MAXED = "message.hatchery.module.maxed";
    public static final String NEEDS = "message.hatchery.module.needs";
    public static final String OVERVIEW = "message.hatchery.modules";
    public static final String TIP_STACKS = "tooltip.hatchery.module.stacks";
    public static final String TIP_ONCE = "tooltip.hatchery.module.once";
    public static final String TIP_USE = "tooltip.hatchery.module.use";

    public static final List<String> ALL = List.of(INSTALLED, MAXED, NEEDS, OVERVIEW, TIP_STACKS, TIP_ONCE, TIP_USE);

    private Messages() {
    }
}
