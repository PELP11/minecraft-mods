package com.afjan.oreborn.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

/**
 * Ability descriptions come from the language file: {@code <key>.name} plus as many {@code <key>.lineN} entries as
 * the ability needs.
 */
final class Tooltips {
    private Tooltips() {}

    static void ability(Consumer<Component> out, String key, int color) {
        out.accept(Component.translatable(key + ".name").withStyle(style -> style.withColor(color)));
        lines(out, key);
    }

    static void lines(Consumer<Component> out, String key) {
        Language language = Language.getInstance();
        for (int i = 1; language.has(key + ".line" + i); i++) {
            out.accept(Component.literal("  ").append(Component.translatable(key + ".line" + i)).withStyle(ChatFormatting.GRAY));
        }
    }

    /** A darker version of the material colour for secondary headings. */
    static int dim(int color) {
        int r = (color >> 16 & 255) * 3 / 4;
        int g = (color >> 8 & 255) * 3 / 4;
        int b = (color & 255) * 3 / 4;
        return r << 16 | g << 8 | b;
    }
}
