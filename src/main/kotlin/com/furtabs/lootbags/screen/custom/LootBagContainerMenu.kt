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

    private fun isOutputPlaceholderSlot(slot: net.minecraft.world.inventory.Slot): Boolean =
        slot is LootBagSlotItemHandler && slot.isOutputSlot && targetBagAmount == 0

    private fun addPlayerInventorySlots(inv: Inventory) {
        addPlayerInventorySlots(inv, playerInventorySlotsOriginX, playerInventorySlotsOriginY, ::addSlot)
    }

    private fun addPlayerHotbarSlots(inv: Inventory) {
        addPlayerHotbarSlots(inv, playerHotbarSlotsOriginX, playerHotbarSlotsOriginY, ::addSlot)
    }

    override fun quickMoveStack(player: Player, index: Int): ItemStack =
        quickMoveStack(this, player, index, 2, ::moveItemStackTo).also {
            broadcastChanges()
        }

    override fun moveItemStackTo(stack: ItemStack, startIndex: Int, endIndex: Int, reverseDirection: Boolean): Boolean {
        /*
         * Due to some bugs inside the vanilla code, we need to override this method to reimplement the logic of
         * moving items inside the container.
         */

        var flag = false
        var i = startIndex
        if (reverseDirection) {
            i = endIndex - 1
        }

        if (stack.isStackable) {
            while (!stack.isEmpty && (if (reverseDirection) i >= startIndex else i < endIndex)) {
                val slot = slots[i]
                // If this slot is marked as fake, we skip it. (This judgement does not exist in vanilla code,
                // preventing the item stack from being put into fake slots.)
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

                if (reverseDirection) {
                    i--
                } else {
                    i++
                }
            }

            if (!stack.isEmpty) {
                if (reverseDirection) {
                    i = endIndex - 1
                } else {
                    i = startIndex
                }

                while (if (reverseDirection) i >= startIndex else i < endIndex) {
                    val slot = slots[i]
                    // If this slot is an output slot, we skip it. (This judgement does not exist in vanilla code,
                    // preventing the item stack from being put into slots used for outputting purposes.)
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

                    if (reverseDirection) {
                        i--
                    } else {
                        i++
                    }
                }
            }
        }

        return flag
    }
}