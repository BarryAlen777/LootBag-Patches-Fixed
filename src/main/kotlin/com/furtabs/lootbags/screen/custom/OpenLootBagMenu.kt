package com.furtabs.lootbags.screen.custom

import com.furtabs.lootbags.item.custom.LootBagItem
import com.furtabs.lootbags.util.MAX_LOOT_BAG_ITEM_STACKS
import com.furtabs.lootbags.util.clearStoredOpenLoot
import com.furtabs.lootbags.util.isOpenedBag
import com.furtabs.lootbags.util.quickMoveStack
import com.furtabs.lootbags.util.writeStoredOpenLoot
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraftforge.items.ItemStackHandler
import net.minecraftforge.items.SlotItemHandler

class OpenLootBagMenu : AbstractContainerMenu {
    val lootHandler: ItemStackHandler
    private val usedHand: InteractionHand

    /**
     * The exact bag stack that was opened. Captured on purpose: if the player drags the bag to
     * another slot while the GUI is open, looking it up again by hand would miss it and leave a
     * free bag behind. Null on the client side, which never consumes anything.
     */
    private val bagStack: ItemStack?

    constructor(menuType: MenuType<out OpenLootBagMenu>, containerId: Int, inv: Inventory, buf: FriendlyByteBuf) : super(
        menuType,
        containerId
    ) {
        val handIdx = buf.readByte().toInt().coerceIn(0, InteractionHand.entries.size - 1)
        usedHand = InteractionHand.entries[handIdx]
        bagStack = null
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
        usedHand: InteractionHand,
        bagStack: ItemStack
    ) : super(menuType, containerId) {
        this.lootHandler = loot
        this.usedHand = usedHand
        this.bagStack = bagStack
        addSlots(inv)
    }

    private fun addSlots(inv: Inventory) {
        // Only the slot holding the bag being opened is locked. Locking every already-opened bag
        // (the previous behaviour) made the rest of the inventory unusable, and locking by object
        // identity broke whenever the synced stack object was replaced.
        val lockedPlayerSlot = when (usedHand) {
            InteractionHand.MAIN_HAND -> inv.selected
            InteractionHand.OFF_HAND -> Inventory.SLOT_OFFHAND
        }
        for (row in 0 until 3) {
            for (col in 0 until 9) {
                addSlot(
                    lockedSlot(
                        inv,
                        col + row * 9 + 9,
                        PLAYER_INV_X + col * 18,
                        PLAYER_INV_Y + row * 18,
                        lockedPlayerSlot
                    )
                )
            }
        }
        for (col in 0 until 9) {
            addSlot(lockedSlot(inv, col, PLAYER_HOTBAR_X + col * 18, PLAYER_HOTBAR_Y, lockedPlayerSlot))
        }
        for (i in 0 until MAX_LOOT_BAG_ITEM_STACKS) {
            addSlot(
                object : SlotItemHandler(lootHandler, i, LOOT_START_X + i * 18, LOOT_Y) {
                    override fun mayPlace(stack: ItemStack): Boolean = false
                }
            )
        }
    }

    private fun lockedSlot(
        inv: Inventory,
        inventoryIndex: Int,
        x: Int,
        y: Int,
        lockedPlayerSlot: Int
    ): Slot = object : Slot(inv, inventoryIndex, x, y) {
        private fun isLocked(): Boolean =
            inventoryIndex == lockedPlayerSlot && item.item is LootBagItem && isOpenedBag(item)

        override fun mayPickup(player: Player): Boolean = !isLocked()

        // The drop-key path (safeTake) never asks mayPickup, it only calls remove().
        override fun remove(amount: Int): ItemStack =
            if (isLocked()) ItemStack.EMPTY else super.remove(amount)
    }

    override fun stillValid(player: Player): Boolean = !player.isRemoved

    override fun quickMoveStack(player: Player, index: Int): ItemStack =
        quickMoveStack(this, player, index, MAX_LOOT_BAG_ITEM_STACKS, ::moveItemStackTo).also { broadcastChanges() }

    override fun removed(player: Player) {
        super.removed(player)
        if (player !is ServerPlayer) return

        // Use the exact stack captured when the bag was opened. Looking it up by hand again would
        // miss the bag if the player moved it to another slot, and could hit a different bag.
        val bag = bagStack ?: return
        if (bag.isEmpty || bag.item !is LootBagItem) return

        // Check if all slots are empty
        val allTaken = (0 until MAX_LOOT_BAG_ITEM_STACKS).all { lootHandler.getStackInSlot(it).isEmpty }

        if (allTaken) {
            clearStoredOpenLoot(bag)
            if (!player.abilities.instabuild) {
                bag.shrink(1)
            }
        } else {
            val itemsToSave = mutableListOf<ItemStack>()
            for (i in 0 until MAX_LOOT_BAG_ITEM_STACKS) {
                val stack = lootHandler.getStackInSlot(i)
                if (!stack.isEmpty) {
                    itemsToSave.add(stack.copy())
                }
            }
            writeStoredOpenLoot(bag, itemsToSave)
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
