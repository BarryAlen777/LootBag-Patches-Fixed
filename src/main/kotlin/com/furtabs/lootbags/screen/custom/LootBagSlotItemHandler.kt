package com.furtabs.lootbags.screen.custom

import net.minecraft.world.item.ItemStack
import net.minecraftforge.items.IItemHandler
import net.minecraftforge.items.SlotItemHandler

open class LootBagSlotItemHandler(
    itemHandler: IItemHandler,
    index: Int,
    xPosition: Int,
    yPosition: Int,
    val isOutputSlot: Boolean
) : SlotItemHandler(itemHandler, index, xPosition, yPosition) {
    fun extractItem(amount: Int, simulate: Boolean): ItemStack =
        getItemHandler().extractItem(getSlotIndex(), amount, simulate)
}
