package com.afjan.oreborn.item;

import java.util.function.Consumer;

import com.afjan.oreborn.material.GearType;
import com.afjan.oreborn.material.OreMaterial;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** Every Oreborn armour piece; the passives and set bonuses run in {@link com.afjan.oreborn.ability.ArmorSets}. */
public class OrebornArmorItem extends Item {
    private final OreMaterial material;
    private final GearType type;

    public OrebornArmorItem(OreMaterial material, GearType type, Properties properties) {
        super(properties);
        this.material = material;
        this.type = type;
    }

    public OreMaterial material() {
        return material;
    }

    public GearType type() {
        return type;
    }

    /** Cryolite boots: frost walkers don't sink into powder snow. */
    @Override
    public boolean canWalkOnPowderedSnow(ItemStack stack, LivingEntity wearer) {
        return material == OreMaterial.CRYOLITE && type == GearType.BOOTS;
    }

    @Override
    public void appendHoverText(ItemStack itemStack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag tooltipFlag) {
        super.appendHoverText(itemStack, context, display, builder, tooltipFlag);
        Tooltips.ability(builder, "tooltip.oreborn." + material.id() + "_" + type.id(), material.color());
        Tooltips.ability(builder, "tooltip.oreborn." + material.id() + "_set", Tooltips.dim(material.color()));
    }
}
