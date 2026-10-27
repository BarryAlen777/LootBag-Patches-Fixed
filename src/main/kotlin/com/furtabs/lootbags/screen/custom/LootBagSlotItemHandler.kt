package com.furtabs.lootbags.screen.custom

import com.furtabs.lootbags.util.VirtualOutputHandler
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

    /**
     * The output slot of the storage/recycler is a virtual display of a counter. The number-key
     * swap path in vanilla calls [setByPlayer] instead of [remove], which would bypass
     * `extractItem` and let the counter stay untouched (infinite bags). Route those writes through
     * the handler so the counter is deducted exactly like a normal pickup.
     */
    override fun setByPlayer(stack: ItemStack) {
        val handler = getItemHandler()
        if (isOutputSlot && handler is VirtualOutputHandler) {
            handler.onVirtualOutputWrite(getItem(), stack)
            return
        }
        super.setByPlayer(stack)
    }
}
