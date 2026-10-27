package com.furtabs.lootbags.block.entity.custom

import com.furtabs.lootbags.block.entity.ModBlockEntities
import com.furtabs.lootbags.item.custom.LootBagItem
import com.furtabs.lootbags.screen.custom.LootRecyclerMenu
import com.furtabs.lootbags.util.InputOnlyItemHandler
import com.furtabs.lootbags.util.LootBagType
import com.furtabs.lootbags.util.LootValueRegistry
import com.furtabs.lootbags.util.OutputOnlyItemHandler
import com.furtabs.lootbags.util.VirtualOutputHandler
import com.furtabs.lootbags.util.setChangedAndUpdateBlock
import kotlin.math.min
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.Containers
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

class LootRecyclerBlockEntity(pos: BlockPos, blockState: BlockState) :
    BlockEntity(ModBlockEntities.LOOT_RECYCLER.get(), pos, blockState), MenuProvider {

    enum class ContainerDataType { STORED_BAG_AMOUNT }

    companion object {
        const val INPUT_SLOT = 0
        const val OUTPUT_SLOT = 1
        const val SLOTS_COUNT = 2

        /** Largest number of bags handed to a single dropped item entity. */
        private const val MAX_DROP_STACK = 64
    }

    var storedBagAmount: Int = 0
    var accumulation: Double = 0.0
    val targetBagType: LootBagType = LootBagType.COMMON

    /**
     * The output slot is only a display of [storedBagAmount]. See [VirtualOutputHandler] for why
     * GUI writes have to go through [onVirtualOutputWrite].
     */
    inner class LootRecyclerItemHandler : ItemStackHandler(SLOTS_COUNT), VirtualOutputHandler {
        override fun isItemValid(slot: Int, stack: ItemStack): Boolean {
            if (slot != INPUT_SLOT) return false
            // Loot bags are this machine's *product*; recycling them into bags would be a loss
            // (and a way to accidentally destroy a bag). The bag storage block handles conversion.
            if (stack.item is LootBagItem) return false
            return LootValueRegistry.getValue(stack) > 0.0
        }

        override fun onContentsChanged(slot: Int) = setChangedAndUpdateBlock()

        override fun extractItem(slot: Int, amount: Int, simulate: Boolean): ItemStack {
            val extracted = super.extractItem(slot, amount, simulate)
            if (!simulate && slot == OUTPUT_SLOT && !extracted.isEmpty) {
                storedBagAmount = (storedBagAmount - extracted.count).coerceAtLeast(0)
                refreshOutputSlot()
            }
            return extracted
        }

        override fun onVirtualOutputWrite(current: ItemStack, incoming: ItemStack) {
            if (!current.isEmpty) {
                val kept = if (ItemStack.isSameItemSameTags(current, incoming)) incoming.count else 0
                val removed = current.count - kept
                if (removed > 0) {
                    storedBagAmount = (storedBagAmount - removed).coerceAtLeast(0)
                    setChangedAndUpdateBlock()
                }
            }
            refreshOutputSlot()
        }

        /** Rebuilds the display stack from the counter. Does not touch the counter. */
        fun refreshOutputSlot() {
            val item = targetBagType.asItem()
            val cap = ItemStack(item).maxStackSize
            val displayAmount = min(storedBagAmount, cap).coerceAtLeast(0)
            val newStack = if (displayAmount > 0) ItemStack(item, displayAmount) else ItemStack.EMPTY
            if (!ItemStack.matches(getStackInSlot(OUTPUT_SLOT), newStack)) {
                setChangedAndUpdateBlock()
            }
            stacks[OUTPUT_SLOT] = newStack
        }

        /** Empties a slot without running [onContentsChanged]; used while the block is being removed. */
        fun clearSlotSilently(slot: Int) {
            stacks[slot] = ItemStack.EMPTY
        }
    }

    val itemHandler: ItemStackHandler = LootRecyclerItemHandler()

    private val lazyInputCap = LazyOptional.of { InputOnlyItemHandler(itemHandler, INPUT_SLOT) }
    private val lazyOutputCap = LazyOptional.of { OutputOnlyItemHandler(itemHandler, OUTPUT_SLOT) }

    private val data = object : ContainerData {
        override fun get(index: Int): Int =
            if (index == ContainerDataType.STORED_BAG_AMOUNT.ordinal) storedBagAmount else 0

        override fun set(index: Int, value: Int) {
            if (index == ContainerDataType.STORED_BAG_AMOUNT.ordinal) {
                storedBagAmount = value.coerceAtLeast(0)
            }
        }

        override fun getCount(): Int = ContainerDataType.entries.size
    }

    override fun getDisplayName(): Component = Component.translatable("block.lootbags.loot_recycler")

    override fun createMenu(id: Int, inv: Inventory, player: Player): AbstractContainerMenu =
        LootRecyclerMenu(id, inv, player.level(), this, data)

    fun tick(level: Level, pos: BlockPos, state: BlockState) {
        if (level.isClientSide) return

        val inputStack = itemHandler.getStackInSlot(INPUT_SLOT)
        // Re-run the insertion check instead of only trusting the slot: bags (and anything else
        // that is not recyclable) may still sit in an input slot saved by an older version. The
        // recycler used to grind those down to 0.05 of a bag without asking.
        if (!inputStack.isEmpty && itemHandler.isItemValid(INPUT_SLOT, inputStack)) {
            val valuePerItem = LootValueRegistry.getValue(inputStack)
            if (valuePerItem > 0) {
                val amountToProcess = inputStack.count
                itemHandler.extractItem(INPUT_SLOT, amountToProcess, false)
                accumulation += valuePerItem * amountToProcess
                if (accumulation >= 1.0) {
                    val fullBags = accumulation.toInt()
                    storedBagAmount += fullBags
                    accumulation -= fullBags.toDouble()
                }
                setChangedAndUpdateBlock()
            }
        }

        // Only touch the display when the counter actually changed; this used to write (and
        // therefore broadcast the block entity) on every single tick.
        val handler = itemHandler as LootRecyclerItemHandler
        handler.refreshOutputSlot()
    }

    /**
     * Drops the input slot and every finished bag (called from [LootRecyclerBlock.onRemove]).
     *
     * The bags are a counter, so they are turned into real bag items here, in groups of
     * [MAX_DROP_STACK] instead of one item entity per bag (a hopper-fed recycler can bank
     * thousands of bags). The partial accumulation (less than one common bag) is discarded,
     * matching the "used up" fraction. Running this on removal keeps the contents alive through
     * explosions, `/setblock` and creative-mode breaking.
     */
    fun drops() {
        val level = level ?: return
        if (level.isClientSide) {
            // Containers.dropItemStack still *constructs* ItemEntity objects on the client and
            // addFreshEntity merely discards them; skip the loop entirely instead.
            return
        }
        val handler = itemHandler as LootRecyclerItemHandler
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
        var remaining = storedBagAmount
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
        accumulation = 0.0
        // The virtual output slot mirrors the counter; clear its stale display stack so it cannot
        // be harvested for one extra tick after the counter has been zeroed.
        handler.clearSlotSilently(OUTPUT_SLOT)
    }

    override fun saveAdditional(tag: CompoundTag) {
        tag.put("inventory", itemHandler.serializeNBT())
        tag.putInt("stored_bags", storedBagAmount)
        tag.putDouble("accumulation", accumulation)
        super.saveAdditional(tag)
    }

    override fun load(tag: CompoundTag) {
        super.load(tag)
        itemHandler.deserializeNBT(tag.getCompound("inventory"))
        storedBagAmount = tag.getInt("stored_bags").coerceAtLeast(0)
        accumulation = tag.getDouble("accumulation").coerceAtLeast(0.0)
    }

    private fun setChangedAndUpdateBlock() = setChangedAndUpdateBlock(level)

    override fun getUpdatePacket(): Packet<ClientGamePacketListener> =
        ClientboundBlockEntityDataPacket.create(this)

    override fun getUpdateTag(): CompoundTag = saveWithoutMetadata()

    override fun <T> getCapability(cap: Capability<T>, side: Direction?): LazyOptional<T> {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return if (side == Direction.DOWN) lazyOutputCap.cast() else lazyInputCap.cast()
        }
        return super.getCapability(cap, side)
    }

    override fun invalidateCaps() {
        super.invalidateCaps()
        lazyInputCap.invalidate()
        lazyOutputCap.invalidate()
    }
}
