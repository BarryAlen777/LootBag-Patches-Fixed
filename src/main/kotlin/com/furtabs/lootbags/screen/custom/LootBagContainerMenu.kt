package com.furtabs.lootbags.screen.custom

import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerData
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraftforge.items.IItemHandler
import net.minecraftforge.items.SlotItemHandler
import com.furtabs.lootbags.util.LootBagType
import com.furtabs.lootbags.util.addPlayerHotbarSlots
import com.furtabs.lootbags.util.addPlayerInventorySlots
import com.furtabs.lootbags.util.quickMoveStack
import kotlin.math.min

abstract class LootBagContainerMenu(
    menuType: MenuType<*>?,
    containerId: Int,
    inv: Inventory,
    protected val level: Level,
    data: ContainerData?,
    inputSlotItemHandler: IItemHandler,
    outputSlotItemHandler: IItemHandler,
    inputSlotX: Int,
    inputSlotY: Int,
    outputSlotX: Int,
    outputSlotY: Int,
    private val playerInventorySlotsOriginX: Int = 8,
    private val playerInventorySlotsOriginY: Int = 66,
    private val playerHotbarSlotsOriginX: Int = 8,
    private val playerHotbarSlotsOriginY: Int = 123
) : AbstractContainerMenu(menuType, containerId) {
    init {
        addPlayerInventorySlots(inv)
        addPlayerHotbarSlots(inv)

        addSlot(SlotItemHandler(inputSlotItemHandler, 0, inputSlotX, inputSlotY))
        addSlot(object : LootBagSlotItemHandler(outputSlotItemHandler, 1, outputSlotX, outputSlotY, true) {
            // Prevent the slot from being picked up when there is no target bag to be obtained (i.e.
            // when the fake ItemStack is displayed).
            override fun mayPickup(playerIn: Player): Boolean = this@LootBagContainerMenu.targetBagAmount > 0
        })

        data?.let { addDataSlots(it) }
    }

    abstract val targetBagType: LootBagType
    abstract val targetBagAmount: Int

    private companion object {
        /**
         * Player main inventory (27) + hotbar (9). The hotbar is *part of* the player inventory,
         * so this is 36, not 45: passing a larger endIndex to [moveItemStackTo] walks off the end
         * of [slots] (38 here) and crashes the server through the "Container click" wrapper in
         * AbstractContainerMenu.clicked.
         */
        private const val PLAYER_SLOT_COUNT = 36

        /** Safety cap for "take everything"; a player inventory can never hold more than 36 bags. */
        private const val MAX_BULK_MOVE = 64
    }

    private fun isOutputPlaceholderSlot(slot: net.minecraft.world.inventory.Slot): Boolean =
        slot is LootBagSlotItemHandler && slot.isOutputSlot && targetBagAmount == 0

    private fun addPlayerInventorySlots(inv: Inventory) {
        addPlayerInventorySlots(inv, playerInventorySlotsOriginX, playerInventorySlotsOriginY, ::addSlot)
    }

    private fun addPlayerHotbarSlots(inv: Inventory) {
        addPlayerHotbarSlots(inv, playerHotbarSlotsOriginX, playerHotbarSlotsOriginY, ::addSlot)
    }

    override fun quickMoveStack(player: Player, index: Int): ItemStack {
        val sourceSlot = if (index in slots.indices) slots[index] else null
        if (sourceSlot is LootBagSlotItemHandler && sourceSlot.isOutputSlot) {
            return moveAllFromOutput(sourceSlot)
        }
        return quickMoveStack(this, player, index, 2, ::moveItemStackTo).also {
            broadcastChanges()
        }
    }

    /**
     * Shift-clicking the output takes *everything* that fits in the player inventory.
     *
     * A loot bag stacks to 1, so the vanilla "move one stack per click" behaviour meant one bag
     * per click: converting 64 common bags into 16 uncommon ones took 16 clicks.
     */
    private fun moveAllFromOutput(slot: LootBagSlotItemHandler): ItemStack {
        val first = slot.item.copy()
        if (first.isEmpty || targetBagAmount <= 0) {
            return ItemStack.EMPTY
        }

        var moved = 0
        while (moved < MAX_BULK_MOVE && targetBagAmount > 0) {
            val candidate = slot.item
            if (candidate.isEmpty) {
                break
            }
            val remaining = candidate.copy()
            val placed = moveItemStackTo(remaining, 0, PLAYER_SLOT_COUNT, false)
            val movedNow = candidate.count - remaining.count
            if (!placed || movedNow <= 0) {
                // Player inventory is full (nothing accepted): stop instead of looping on.
                break
            }
            // Always remove through the slot: this is the path that deducts the virtual counter.
            slot.remove(movedNow)
            moved += movedNow
        }

        if (moved > 0) {
            slot.setChanged()
            broadcastChanges()
        }
        return first
    }

    override fun moveItemStackTo(stack: ItemStack, startIndex: Int, endIndex: Int, reverseDirection: Boolean): Boolean {
        /*
         * Reimplementation of the vanilla logic with two extra guards:
         *  - "placeholder" output slots (a virtual display with nothing behind it) are never merged into;
         *  - the output slots are never used as a destination.
         *
         * The two loops must NOT be nested: loot bags have a max stack size of 1, so they are never
         * "stackable" and the placement loop has to run anyway (otherwise shift-click does nothing).
         */

        var flag = false
        var i = if (reverseDirection) endIndex - 1 else startIndex

        if (stack.isStackable) {
            while (!stack.isEmpty && (if (reverseDirection) i >= startIndex else i < endIndex)) {
                val slot = slots[i]
                if (!isOutputPlaceholderSlot(slot)) {
                    val itemStack = slot.item
                    if (!itemStack.isEmpty && ItemStack.isSameItemSameTags(stack, itemStack)) {
                        val j = itemStack.count + stack.count
                        val k = slot.getMaxStackSize(itemStack)
                        if (j <= k) {
                            stack.count = 0
                            itemStack.count = j
                            slot.setChanged()
                            flag = true
                        } else if (itemStack.count < k) {
                            stack.shrink(k - itemStack.count)
                            itemStack.count = k
                            slot.setChanged()
                            flag = true
                        }
                    }
                }

                i += if (reverseDirection) -1 else 1
            }
        }

        if (!stack.isEmpty) {
            var j = if (reverseDirection) endIndex - 1 else startIndex

            while (if (reverseDirection) j >= startIndex else j < endIndex) {
                val slot = slots[j]
                if (!(slot is LootBagSlotItemHandler && slot.isOutputSlot)) {
                    val itemStack = slot.item
                    if (itemStack.isEmpty && slot.mayPlace(stack)) {
                        val l = slot.getMaxStackSize(stack)
                        slot.setByPlayer(stack.split(min(stack.count, l)))
                        slot.setChanged()
                        flag = true
                        break
                    }
                }

                j += if (reverseDirection) -1 else 1
            }
        }

        return flag
    }
}