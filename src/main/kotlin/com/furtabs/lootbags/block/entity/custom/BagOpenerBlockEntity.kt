package com.furtabs.lootbags.block.entity.custom

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.Containers
import net.minecraft.world.MenuProvider
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerData
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.level.storage.loot.parameters.LootContextParams
import net.minecraft.world.phys.Vec3
import net.minecraft.core.Direction
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.common.util.LazyOptional
import net.minecraftforge.items.ItemStackHandler
import com.furtabs.lootbags.block.entity.ModBlockEntities
import com.furtabs.lootbags.item.custom.LootBagItem
import com.furtabs.lootbags.screen.custom.BagOpenerMenu
import com.furtabs.lootbags.util.isOpenedBag
import com.furtabs.lootbags.util.readStoredOpenLoot
import com.furtabs.lootbags.util.rollLootBagDisplayedItemCount
import com.furtabs.lootbags.util.setChangedAndUpdateBlock

class BagOpenerBlockEntity(
    pos: BlockPos,
    blockState: BlockState
) : BlockEntity(ModBlockEntities.BAG_OPENER.get(), pos, blockState), MenuProvider {
    enum class ContainerDataType {
        PROGRESS,
        MAX_PROGRESS
    }

    private inner class BagOpenerItemHandler : LootBagItemHandler(INPUT_SLOTS_COUNT), SilentlyClearable {
        override fun isInputSlot(slot: Int): Boolean = true

        override fun onContentsChanged(slot: Int) {
            setChangedAndUpdateBlock()
        }

        /** Empties every slot without running [onContentsChanged]; used while the block is removed. */
        override fun clearAllSilently() {
            for (i in 0 ..< stacks.size) {
                stacks[i] = ItemStack.EMPTY
            }
        }
    }

    companion object {
        const val INPUT_SLOTS_COUNT = 9
        const val OUTPUT_SLOTS_COUNT = 9 * 3
        const val SLOTS_COUNT = INPUT_SLOTS_COUNT + OUTPUT_SLOTS_COUNT
    }

    val inputItemHandler: ItemStackHandler = BagOpenerItemHandler()
    val outputItemHandler: ItemStackHandler = object : ItemStackHandler(OUTPUT_SLOTS_COUNT), SilentlyClearable {
        override fun isItemValid(slot: Int, stack: ItemStack): Boolean = false
        override fun insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack = stack.copy()

        override fun onContentsChanged(slot: Int) {
            setChangedAndUpdateBlock()
        }

        /** Empties every slot without running [onContentsChanged]; used while the block is removed. */
        override fun clearAllSilently() {
            for (i in 0 ..< stacks.size) {
                stacks[i] = ItemStack.EMPTY
            }
        }
    }

    private val lazyInputCap: LazyOptional<net.minecraftforge.items.IItemHandler> =
        LazyOptional.of { inputItemHandler }
    private val lazyOutputCap: LazyOptional<net.minecraftforge.items.IItemHandler> =
        LazyOptional.of { outputItemHandler }

    private var progress = 0
    private var maxProgress = 60
    private val data = object : ContainerData {
        override fun get(index: Int): Int = when (index) {
            ContainerDataType.PROGRESS.ordinal -> progress
            ContainerDataType.MAX_PROGRESS.ordinal -> maxProgress
            else -> 0
        }

        override fun set(index: Int, value: Int) {
            when (index) {
                ContainerDataType.PROGRESS.ordinal -> progress = value
                ContainerDataType.MAX_PROGRESS.ordinal -> maxProgress = value
            }
        }

        override fun getCount(): Int = ContainerDataType.entries.size
    }

    override fun getDisplayName(): Component = Component.translatable("block.lootbags.bag_opener")

    override fun createMenu(containerId: Int, playerInventory: Inventory, player: Player): AbstractContainerMenu =
        BagOpenerMenu(containerId, playerInventory, player.level(), this, data)

    /** Drops both the input and the output inventories (called from [BagOpenerBlock.onRemove]). */
    fun drops() {
        val level = level ?: return
        // Containers.dropItemStack constructs ItemEntity objects on the client too (no side guard
        // in 1.20.1) where they are merely discarded -- skip the whole loop client-side.
        if (level.isClientSide) {
            return
        }
        for (handler in listOf(inputItemHandler, outputItemHandler)) {
            val inventory = SimpleContainer(handler.slots)
            for (i in 0 ..< handler.slots) {
                inventory.setItem(i, handler.getStackInSlot(i))
            }
            Containers.dropContents(level, worldPosition, inventory)
            // dropContents splits the *live* stacks down to empty; replace them with proper
            // EMPTY instances (silently: the block entity is going away, no sync needed).
            (handler as? SilentlyClearable)?.clearAllSilently()
        }
    }

    /** Implemented by this machine's handlers so [drops] can empty them without a sync packet. */
    private interface SilentlyClearable {
        fun clearAllSilently()
    }

    override fun saveAdditional(tag: CompoundTag) {
        tag.put("input_inventory", inputItemHandler.serializeNBT())
        tag.put("output_inventory", outputItemHandler.serializeNBT())
        tag.putInt("progress", progress)
        tag.putInt("max_progress", maxProgress)
        super.saveAdditional(tag)
    }

    override fun load(tag: CompoundTag) {
        super.load(tag)
        inputItemHandler.deserializeNBT(tag.getCompound("input_inventory"))
        outputItemHandler.deserializeNBT(tag.getCompound("output_inventory"))
        progress = tag.getInt("progress")
        maxProgress = tag.getInt("max_progress")
    }

    fun tick(level: Level, pos: BlockPos, state: BlockState) {
        val inputSlot = availableInputSlotForCrafting
        if (inputSlot == null) {
            decreaseCraftingProgress()
        } else {
            increaseCraftingProgress()
            setChanged(level, pos, state)

            if (hasCraftingFinished) {
                craftItem(inputSlot)
                resetProgress()
            }
        }
    }

    private val availableInputSlotForCrafting: Int?
        get() {
            var allOccupied = true
            for (i in 0 ..< outputItemHandler.slots) {
                allOccupied = allOccupied && !outputItemHandler.getStackInSlot(i).isEmpty
            }
            if (allOccupied) return null

            for (i in 0 ..< inputItemHandler.slots) {
                if (!inputItemHandler.getStackInSlot(i).isEmpty) return i
            }
            return null
        }

    private fun increaseCraftingProgress() {
        if (progress < maxProgress) progress++
    }

    private fun decreaseCraftingProgress() {
        if (progress > 0) progress--
    }

    private val hasCraftingFinished: Boolean
        get() = progress >= maxProgress

    private fun craftItem(inputSlot: Int) {
        if (inputSlot < 0 || inputSlot >= inputItemHandler.slots) return

        val inputItemStack = inputItemHandler.getStackInSlot(inputSlot)
        if (inputItemStack.isEmpty) return

        val bagItem = inputItemStack.item as? LootBagItem ?: return
        val level = this.level ?: return
        val serverLevel = level as? ServerLevel ?: return

        val loots: List<ItemStack> = if (isOpenedBag(inputItemStack)) {
            // This bag was already opened/viewed; hand out exactly what is left in it.
            readStoredOpenLoot(inputItemStack) ?: emptyList()
        } else {
            // This is a fresh bag; generate loot once and remember how many stacks it should hold.
            val bagType = bagItem.type
            bagType.lootGenerator.generateLoot(
                serverLevel,
                LootParams.Builder(serverLevel)
                    .withParameter(LootContextParams.BLOCK_STATE, blockState)
                    .withParameter(LootContextParams.BLOCK_ENTITY, this)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(blockPos)),
                maxStacks = rollLootBagDisplayedItemCount(serverLevel.random)
            )
        }

        // Consume the bag even if it turned out to hold nothing. Leaving a spent bag in the input
        // slot made the machine spin at 0 -> 60 -> reset forever and kept the slot occupied.
        val extracted = inputItemHandler.extractItem(inputSlot, 1, false)
        if (extracted.isEmpty) return

        for (loot in loots) {
            if (!putIntoOutputSlots(loot)) {
                // If the output is full, drop the remaining items on the ground instead of voiding them.
                Containers.dropItemStack(
                    level,
                    worldPosition.x + 0.5,
                    worldPosition.y + 0.5,
                    worldPosition.z + 0.5,
                    loot
                )
            }
        }
    }

    private fun putIntoOutputSlots(stack: ItemStack): Boolean {
        val remainingStack = stack.copy()
        for (i in 0 ..< outputItemHandler.slots) {
            val currentStack = outputItemHandler.getStackInSlot(i)
            if (currentStack.isEmpty) {
                outputItemHandler.setStackInSlot(i, remainingStack.copy())
                remainingStack.count = 0
                break
            }
            if (ItemStack.isSameItemSameTags(remainingStack, currentStack)) {
                val maxCapacity = currentStack.maxStackSize
                val amountToPutInto = maxCapacity - currentStack.count
                val remainingAfterPut = remainingStack.count - amountToPutInto
                if (remainingAfterPut < 0) {
                    outputItemHandler.setStackInSlot(i, currentStack.copy().also { it.count += remainingStack.count })
                    remainingStack.count = 0
                } else {
                    outputItemHandler.setStackInSlot(i, currentStack.copy().also { it.count = maxCapacity })
                    remainingStack.count = remainingAfterPut
                }
            }
            if (remainingStack.isEmpty) break
        }
        return remainingStack.isEmpty
    }

    private fun resetProgress() {
        progress = 0
        maxProgress = 60
    }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener> =
        ClientboundBlockEntityDataPacket.create(this)

    override fun getUpdateTag(): CompoundTag = saveWithoutMetadata()

    private fun setChangedAndUpdateBlock() {
        setChangedAndUpdateBlock(level)
    }

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