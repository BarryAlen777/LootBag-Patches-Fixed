package com.furtabs.lootbags.util

import net.minecraft.world.item.ItemStack
import net.minecraftforge.items.IItemHandler

/**
 * Marks an item handler whose output slot is only a *display copy* of a counter.
 *
 * Some vanilla GUI paths (most notably the number-key swap in [net.minecraft.world.inventory.AbstractContainerMenu])
 * write the slot through [net.minecraftforge.items.IItemHandlerModifiable.setStackInSlot] instead of
 * `extractItem`. On a virtual slot that would silently leave the counter untouched, which used to let
 * players pull infinite bags out of the storage/recycler. Slots that wrap such a handler call
 * [onVirtualOutputWrite] instead, and the handler turns the write into a counter deduction.
 */
interface VirtualOutputHandler : IItemHandler {
    /**
     * @param current  the stack that was displayed in the output slot before the GUI write
     * @param incoming the stack the GUI tried to leave in the slot (usually empty after a swap)
     */
    fun onVirtualOutputWrite(current: ItemStack, incoming: ItemStack)
}
