package com.furtabs.lootbags.util

import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import com.furtabs.lootbags.LootBags

// CREDIT GOES TO: diesieben07 | https://github.com/diesieben07/SevenCommons
// must assign a slot number to each of the slots used by the GUI.
// For this container, we can see both the tile inventory's slots as well as the player inventory slots and the hotbar.
// Each time we add a Slot to the container, it automatically increases the slotIndex, which means
//  0 - 8 = hotbar slots (which will map to the InventoryPlayer slot numbers 0 - 8)
//  9 - 35 = player inventory slots (which map to the InventoryPlayer slot numbers 9 - 35)
//  36 - 44 = TileInventory slots, which map to our TileEntity slot numbers 0 - 8)
private const val HOTBAR_SLOT_COUNT = 9
private const val PLAYER_INVENTORY_ROW_COUNT = 3
private const val PLAYER_INVENTORY_COLUMN_COUNT = 9
private const val PLAYER_INVENTORY_SLOT_COUNT = PLAYER_INVENTORY_ROW_COUNT * PLAYER_INVENTORY_COLUMN_COUNT
private const val VANILLA_SLOT_COUNT = HOTBAR_SLOT_COUNT + PLAYER_INVENTORY_SLOT_COUNT
private const val VANILLA_FIRST_SLOT_INDEX = 0
private const val BE_INVENTORY_FIRST_SLOT_INDEX = VANILLA_FIRST_SLOT_INDEX + VANILLA_SLOT_COUNT

/**
 * NOTE: This function **assumes** that the beginning indices of slots are supposed to be **vanilla** slots!
 * Ensure your slots' indices' order before calling this function.
 *
 * The move works on a *copy* of the source stack, so virtual output slots (which only mirror a
 * counter) are not modified behind the handler's back. Once the target slots have accepted part of
 * the stack, the same amount is removed from the source slot through [Slot.remove], which is the
 * path that runs the handler's bookkeeping (counter deduction for virtual slots).
 * Removing the whole source stack with [Slot.set] instead used to duplicate items whenever only a
 * fraction of the stack could be moved.
 */
fun quickMoveStack(
    menu: AbstractContainerMenu,
    player: Player,
    index: Int,
    beInventorySlotCount: Int,
    moveItemStackTo: (ItemStack, Int, Int, Boolean) -> Boolean
): ItemStack {
    val sourceSlot = menu.slots.getOrNull(index) ?: return ItemStack.EMPTY
    if (!sourceSlot.hasItem()) {
        return ItemStack.EMPTY
    }

    val originalStack = sourceSlot.item.copy()
    val movingStack = originalStack.copy()

    val moved = when {
        // A vanilla (player inventory / hotbar) slot: move into the block entity inventory.
        index < VANILLA_FIRST_SLOT_INDEX + VANILLA_SLOT_COUNT ->
            moveItemStackTo(movingStack, BE_INVENTORY_FIRST_SLOT_INDEX,
                BE_INVENTORY_FIRST_SLOT_INDEX + beInventorySlotCount, false)
        // A block entity slot: move back into the player inventory.
        index < BE_INVENTORY_FIRST_SLOT_INDEX + beInventorySlotCount ->
            moveItemStackTo(movingStack, VANILLA_FIRST_SLOT_INDEX,
                VANILLA_FIRST_SLOT_INDEX + VANILLA_SLOT_COUNT, false)
        else -> {
            LootBags.LOGGER.warn("Invalid slot index: {}", index)
            return ItemStack.EMPTY
        }
    }

    if (!moved) {
        return ItemStack.EMPTY
    }

    val movedCount = originalStack.count - movingStack.count
    if (movedCount > 0) {
        // Always remove through the slot: this is the only path that also updates virtual counters.
        sourceSlot.remove(movedCount)
        sourceSlot.setChanged()
    }

    return originalStack
}

fun addPlayerInventorySlots(inv: Inventory, left: Int, top: Int, addSlot: (Slot) -> Slot) {
    for (i in 0 ..< 3) {
        for (j in 0 ..< 9) {
            addSlot(Slot(inv, j + i * 9 + 9, left + j * 18, top + i * 18))
        }
    }
}

fun addPlayerHotbarSlots(inv: Inventory, left: Int, top: Int, addSlot: (Slot) -> Slot) {
    for (i in 0 ..< 9) {
        addSlot(Slot(inv, i, left + i * 18, top))
    }
}
