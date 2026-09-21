package com.furtabs.lootbags.screen.custom

import com.furtabs.lootbags.util.MAX_LOOT_BAG_ITEM_STACKS
import com.furtabs.lootbags.util.addPlayerHotbarSlots
import com.furtabs.lootbags.util.addPlayerInventorySlots
import com.furtabs.lootbags.util.clearStoredOpenLoot
import com.furtabs.lootbags.util.quickMoveStack
import com.furtabs.lootbags.util.writeStoredOpenLoot
import com.furtabs.lootbags.item.custom.LootBagItem
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.ItemStack
import net.minecraftforge.items.ItemStackHandler
import net.minecraftforge.items.SlotItemHandler

class OpenLootBagMenu : AbstractContainerMenu {
    val lootHandler: ItemStackHandler
    private val usedHand: InteractionHand

    constructor(menuType: MenuType<out OpenLootBagMenu>, containerId: Int, inv: Inventory, buf: FriendlyByteBuf) : super(
        menuType,
        containerId
    ) {
        val handIdx = buf.readByte().toInt().coerceIn(0, InteractionHand.entries.size - 1)
        usedHand = InteractionHand.entries[handIdx]
        lootHandler = newLootResultHandler()
        for (i in 0 until MAX_LOOT_BAG_ITEM_STACKS) {
            lootHandler.setStackInSlot(i, buf.readItem())
        }
        addSlots(inv)
    }

    constructor(
        menuType: MenuType<out OpenLootBagMenu>,
        containerId: Int,
        inv: Inventory,
        loot: ItemStackHandler,
        usedHand: InteractionHand
    ) : super(menuType, containerId) {
        this.lootHandler = loot
        this.usedHand = usedHand
        addSlots(inv)
    }

    private fun addSlots(inv: Inventory) {
        addPlayerInventorySlots(inv, PLAYER_INV_X, PLAYER_INV_Y, ::addSlot)
        addPlayerHotbarSlots(inv, PLAYER_HOTBAR_X, PLAYER_HOTBAR_Y, ::addSlot)
        for (i in 0 until MAX_LOOT_BAG_ITEM_STACKS) {
            addSlot(
                object : SlotItemHandler(lootHandler, i, LOOT_START_X + i * 18, LOOT_Y) {
                    override fun mayPlace(stack: ItemStack): Boolean = false
                }
            )
        }
    }

    override fun stillValid(player: Player): Boolean = !player.isRemoved

    override fun quickMoveStack(player: Player, index: Int): ItemStack =
        quickMoveStack(this, player, index, MAX_LOOT_BAG_ITEM_STACKS, ::moveItemStackTo).also { broadcastChanges() }

    override fun removed(player: Player) {
        super.removed(player)
        if (player !is ServerPlayer) return

        val bagStack = player.getItemInHand(usedHand)
        if (bagStack.isEmpty || bagStack.item !is LootBagItem) return

        // Check if all slots are empty
        val allTaken = (0 until MAX_LOOT_BAG_ITEM_STACKS).all { lootHandler.getStackInSlot(it).isEmpty }
        
        if (allTaken) {
            clearStoredOpenLoot(bagStack)
            if (!player.abilities.instabuild) {
                bagStack.shrink(1)
            }
        } else {
            // FIX: Convert the ItemStackHandler content into a List<ItemStack> 
            // to match the utility function signature
            val itemsToSave = mutableListOf<ItemStack>()
            for (i in 0 until MAX_LOOT_BAG_ITEM_STACKS) {
                val stack = lootHandler.getStackInSlot(i)
                if (!stack.isEmpty) {
                    itemsToSave.add(stack)
                }
            }
            writeStoredOpenLoot(bagStack, itemsToSave)
        }
    }

    companion object {
        const val PLAYER_INV_X: Int = 8
        const val PLAYER_INV_Y: Int = 46
        const val PLAYER_HOTBAR_X: Int = 8
        const val PLAYER_HOTBAR_Y: Int = 103
        const val LOOT_START_X: Int = 44
        const val LOOT_Y: Int = 15
    }
}

fun newLootResultHandlerWithLoot(
    loot: List<ItemStack>,
    onChanged: ((ItemStackHandler) -> Unit)? = null
): ItemStackHandler {
    val h = newLootResultHandler(onChanged)
    for ((i, item) in loot.withIndex()) {
        if (i < MAX_LOOT_BAG_ITEM_STACKS) {
            h.setStackInSlot(i, item.copy())
        }
    }
    return h
}

private fun newLootResultHandler(onChanged: ((ItemStackHandler) -> Unit)? = null): ItemStackHandler =
    object : ItemStackHandler(MAX_LOOT_BAG_ITEM_STACKS) {
        override fun onContentsChanged(slot: Int) {
            super.onContentsChanged(slot)
            onChanged?.invoke(this)
        }

        override fun isItemValid(slot: Int, stack: ItemStack): Boolean = false
    }