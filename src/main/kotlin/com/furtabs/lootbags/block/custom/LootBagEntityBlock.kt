package com.furtabs.lootbags.block.custom

import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.item.BlockItem
import com.furtabs.lootbags.util.LootBagType

abstract class LootBagEntityBlock(properties: Properties) : BaseEntityBlock(properties) {
    override fun appendHoverText(
        stack: ItemStack,
        level: BlockGetter?,
        tooltipComponents: MutableList<Component>,
        tooltipFlag: TooltipFlag
    ) {
        super.appendHoverText(stack, level, tooltipComponents, tooltipFlag)

        val beTag = BlockItem.getBlockEntityData(stack) ?: return
        val stored = beTag.getInt("stored_bag_amount")
        if (stored <= 0) {
            return
        }
        val targetOrdinal = beTag.getInt("target_bag_type")
        val type = if (targetOrdinal in LootBagType.entries.indices) {
            LootBagType.entries[targetOrdinal]
        } else {
            LootBagType.COMMON
        }
        if (tooltipFlag.isAdvanced) {
            tooltipComponents.add(
                Component.translatable(
                    "tooltip.lootbags.bag_storage.stored_with_count",
                    stored
                )
            )
            tooltipComponents.add(
                Component.translatable(
                    "tooltip.lootbags.bag_storage.output_type",
                    Component.translatable(type.asItem().descriptionId).string
                )
            )
        } else {
            tooltipComponents.add(Component.translatable("tooltip.lootbags.press_shift_for_details"))
        }
    }
}
