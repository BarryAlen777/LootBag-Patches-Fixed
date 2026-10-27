package com.furtabs.lootbags.block.entity.custom

import com.furtabs.lootbags.block.entity.ModBlockEntities
import com.furtabs.lootbags.item.custom.LootBagItem
import com.furtabs.lootbags.screen.custom.BagStorageMenu
import com.furtabs.lootbags.util.InputOnlyItemHandler
import com.furtabs.lootbags.util.LootBagType
import com.furtabs.lootbags.util.OutputOnlyItemHandler
import com.furtabs.lootbags.util.VirtualOutputHandler
import com.furtabs.lootbags.util.isOpenedBag
import com.furtabs.lootbags.util.setChangedAndUpdateBlock
import kotlin.math.min
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.Containers
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerData
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.common.util.LazyOptional
import net.minecraftforge.items.ItemStackHandler

class BagStorageBlockEntity(
    pos: BlockPos,
    blockState: BlockState
) : BlockEntity(ModBlockEntities.BAG_STORAGE.get(), pos, blockState), MenuProvider {

    enum class ContainerDataType {
        STORED_BAG_AMOUNT,
        TARGET_BAG_TYPE
    }

    /**
     * The output slot is a virtual display of [storedBagAmount]: it is always rebuilt from the
     * counter, never written to directly. [VirtualOutputHandler] lets the GUI's number-key swap
     * (which bypasses `extractItem`) be converted into a proper counter deduction.
     */
    private inner class BagStorageItemHandler : LootBagItemHandler(SLOTS_COUNT), VirtualOutputHandler {
        override fun isInputSlot(slot: Int): Boolean = slot == INPUT_SLOT

        /**
         * Logic to reject "dirty" bags from entering storage.
         */
        override fun isItemValid(slot: Int, stack: ItemStack): Boolean {
            if (!isInputSlot(slot)) return false
            if (stack.item !is LootBagItem) return false
            // Bags that were already opened (and therefore carry loot in their NBT) must not be
            // stored, otherwise their contents could be duplicated by the bag storage.
            return !isOpenedBag(stack)
        }

        override fun extractItem(slot: Int, amount: Int, simulate: Boolean): ItemStack {
            if (simulate) {
                return super.extractItem(slot, amount, true)
            }
            val resultStack = super.extractItem(slot, amount, false)
            if (slot == OUTPUT_SLOT && !resultStack.isEmpty) {
                // Deduct using the type of the stack that actually left the slot (C-07): the target
                // type may have changed while an old display stack was still sitting in the slot.
                storedBagAmount = (storedBagAmount - bagUnits(resultStack)).coerceAtLeast(0)
            }
            refreshOutputSlot()
            return resultStack
        }

        override fun onVirtualOutputWrite(current: ItemStack, incoming: ItemStack) {
            if (!current.isEmpty) {
                val kept = if (ItemStack.isSameItemSameTags(current, incoming)) incoming.count else 0
                val removed = current.count - kept
                if (removed > 0) {
                    storedBagAmount = (storedBagAmount - removed * bagUnits(current)).coerceAtLeast(0)
                    setChangedAndUpdateBlock()
                }
            }
            refreshOutputSlot()
        }

        override fun onContentsChanged(slot: Int) {
            if (slot == OUTPUT_SLOT) {
                return
            }
            setChangedAndUpdateBlock()
            refreshOutputSlot()
        }

        /** Rebuilds the display stack from the counter. Does not touch the counter. */
        fun refreshOutputSlot() {
            val item = targetBagType.asItem()
            val cap = ItemStack(item).maxStackSize
            val displayAmount = min(targetBagAmount, cap).coerceAtLeast(0)
            val newStack = if (displayAmount > 0) ItemStack(item, displayAmount) else ItemStack.EMPTY
            val oldStack = stacks[OUTPUT_SLOT]
            if (!ItemStack.matches(oldStack, newStack)) {
                setChangedAndUpdateBlock()
            }
            // Always store a fresh instance: the swap/pickup path hands slot contents to the
            // player inventory, and sharing the same ItemStack object would corrupt both sides.
            stacks[OUTPUT_SLOT] = newStack
        }

        /** Empties a slot without running [onContentsChanged]; used while the block is being removed. */
        fun clearSlotSilently(slot: Int) {
            stacks[slot] = ItemStack.EMPTY
        }
    }

    companion object {
        const val INPUT_SLOT = 0
        const val OUTPUT_SLOT = 1
        const val SLOTS_COUNT = OUTPUT_SLOT + 1

        /** Largest number of bag units handed to a single dropped item entity. */
        private const val MAX_DROP_STACK = 64

        /** How many "common bag units" this bag stack is worth. */
        fun bagUnits(stack: ItemStack): Int =
            ((stack.item as? LootBagItem)?.type ?: LootBagType.COMMON)
                .amountFactorEquivalentTo(LootBagType.COMMON).toInt()

        /** How many common-bag units one bag of [type] is worth (integer, no float drift). */
        fun unitsPerBag(type: LootBagType): Int = type.amountFactorEquivalentTo(LootBagType.COMMON).toInt()
    }

    val itemHandler: ItemStackHandler = BagStorageItemHandler()
    val inputItemHandler = InputOnlyItemHandler(itemHandler, INPUT_SLOT)
    val outputItemHandler = OutputOnlyItemHandler(itemHandler, OUTPUT_SLOT)

    private val lazyInputCap: LazyOptional<net.minecraftforge.items.IItemHandler> =
        LazyOptional.of { inputItemHandler }
    private val lazyOutputCap: LazyOptional<net.minecraftforge.items.IItemHandler> =
        LazyOptional.of { outputItemHandler }

    var storedBagAmount: Int = 0
    private var targetBagType: LootBagType = LootBagType.COMMON
    private val targetBagAmount: Int
        get() = storedBagAmount / unitsPerBag(targetBagType)

    private val data = object : ContainerData {
        override fun get(index: Int): Int = when (index) {
            ContainerDataType.STORED_BAG_AMOUNT.ordinal -> storedBagAmount
            ContainerDataType.TARGET_BAG_TYPE.ordinal -> targetBagType.ordinal
            else -> 0
        }

        override fun set(index: Int, value: Int) {
            when (index) {
                ContainerDataType.STORED_BAG_AMOUNT.ordinal ->
                    storedBagAmount = value.coerceAtLeast(0)
                ContainerDataType.TARGET_BAG_TYPE.ordinal ->
                    if (value in LootBagType.entries.indices) {
                        targetBagType = LootBagType.entries[value]
                    }
            }
        }

        override fun getCount(): Int = ContainerDataType.entries.size
    }

    override fun getDisplayName(): Component = Component.translatable("block.lootbags.bag_storage")

    override fun createMenu(containerId: Int, playerInventory: Inventory, player: Player): AbstractContainerMenu =
        BagStorageMenu(containerId, playerInventory, player.level(), this, data)

    override fun saveAdditional(tag: CompoundTag) {
        tag.put("inventory", itemHandler.serializeNBT())
        tag.putInt("stored_bag_amount", storedBagAmount)
        tag.putInt("target_bag_type", targetBagType.ordinal)
        super.saveAdditional(tag)
    }

    override fun load(tag: CompoundTag) {
        super.load(tag)
        itemHandler.deserializeNBT(tag.getCompound("inventory"))
        storedBagAmount = tag.getInt("stored_bag_amount").coerceAtLeast(0)
        val targetBagTypeOrdinal = tag.getInt("target_bag_type")
        if (targetBagTypeOrdinal in LootBagType.entries.indices) {
            targetBagType = LootBagType.entries[targetBagTypeOrdinal]
        }
    }

    /**
     * Drops the input slot and every stored bag (called from [BagStorageBlock.onRemove]).
     *
     * The stored amount is a counter of *common-bag units*, so it has to be turned into real bags
     * here. Instead of one item entity per unit (a full machine used to spawn 16,384 entities for
     * 64 legendary stores, and the client ran the same pointless loop because
     * Containers.dropItemStack has no side guard in 1.20.1), the amount is converted back into
     * full target-type bags plus a common-bag remainder, dropped in groups of [MAX_DROP_STACK].
     *
     * This runs on every removal of the block, so the contents survive explosions, `/setblock`
     * and creative-mode breaking too -- the same way a vanilla chest drops its contents.
     */
    fun drops() {
        val level = level ?: return
        if (level.isClientSide) {
            // Containers.dropItemStack still *constructs* ItemEntity objects on the client and
            // addFreshEntity merely discards them; skip the loop entirely instead.
            return
        }
        val handler = itemHandler as BagStorageItemHandler
        val input = handler.getStackInSlot(INPUT_SLOT)
        if (!input.isEmpty) {
            Containers.dropItemStack(
                level,
                worldPosition.x.toDouble(),
                worldPosition.y.toDouble(),
                worldPosition.z.toDouble(),
                input.copy()
            )
            handler.clearSlotSilently(INPUT_SLOT)
        }
        if (storedBagAmount > 0) {
            val unitsPerTargetBag = unitsPerBag(targetBagType)
            var remaining = storedBagAmount
            var fullBags = remaining / unitsPerTargetBag
            remaining %= unitsPerTargetBag
            while (fullBags > 0) {
                val n = min(fullBags, MAX_DROP_STACK)
                Containers.dropItemStack(
                    level,
                    worldPosition.x.toDouble(),
                    worldPosition.y.toDouble(),
                    worldPosition.z.toDouble(),
                    ItemStack(targetBagType.asItem(), n)
                )
                fullBags -= n
            }
            while (remaining > 0) {
                val n = min(remaining, MAX_DROP_STACK)
                Containers.dropItemStack(
                    level,
                    worldPosition.x.toDouble(),
                    worldPosition.y.toDouble(),
                    worldPosition.z.toDouble(),
                    ItemStack(LootBagType.COMMON.asItem(), n)
                )
                remaining -= n
            }
            storedBagAmount = 0
        }
        // The virtual output slot mirrors the counter; clear its stale display stack so it cannot
        // be harvested for one extra tick after the counter has been zeroed.
        handler.clearSlotSilently(OUTPUT_SLOT)
    }

    /**
     * Updated tick to validate bags before adding them to stored volume.
     */
    fun tick(level: Level, pos: BlockPos, state: BlockState) {
        val inputStack = itemHandler.getStackInSlot(INPUT_SLOT)

        // CHECK: Don't bypass the isItemValid check!
        if (!inputStack.isEmpty && itemHandler.isItemValid(INPUT_SLOT, inputStack)) {
            val inputItem = inputStack.item as? LootBagItem
            if (inputItem != null) {
                val count = inputStack.count
                val bagType = inputItem.type

                // Safe to extract now
                itemHandler.extractItem(INPUT_SLOT, count, false)

                storedBagAmount += (count.toFloat() * bagType.amountFactorEquivalentTo(LootBagType.COMMON)).toInt()
                setChangedAndUpdateBlock()
            }
        }

        (itemHandler as BagStorageItemHandler).refreshOutputSlot()
    }

    private fun setChangedAndUpdateBlock() = setChangedAndUpdateBlock(level)

    override fun getUpdatePacket(): Packet<ClientGamePacketListener> =
        ClientboundBlockEntityDataPacket.create(this)

    override fun getUpdateTag(): CompoundTag = saveWithoutMetadata()

    override fun <T> getCapability(cap: Capability<T>, side: Direction?): LazyOptional<T> {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return if (side == Direction.DOWN) {
                lazyOutputCap.cast()
            } else {
                lazyInputCap.cast()
            }
        }
        return super.getCapability(cap, side)
    }

    override fun invalidateCaps() {
        super.invalidateCaps()
        lazyInputCap.invalidate()
        lazyOutputCap.invalidate()
    }
}
