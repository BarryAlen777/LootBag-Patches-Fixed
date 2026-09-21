package com.furtabs.lootbags.screen.custom

import net.minecraft.core.NonNullList
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.*
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import com.furtabs.lootbags.block.ModBlocks
import com.furtabs.lootbags.block.entity.custom.BagStorageBlockEntity
import com.furtabs.lootbags.network.ModNetworks
import com.furtabs.lootbags.network.custom.ServerboundSelectLootBagTypePacket
import com.furtabs.lootbags.screen.ModMenuTypes
import com.furtabs.lootbags.util.LootBagType

class BagStorageMenu(
    containerId: Int,
    inv: Inventory,
    level: Level,
    val blockEntity: BagStorageBlockEntity,
    val data: ContainerData
) : LootBagContainerMenu(
    ModMenuTypes.BAG_STORAGE.get(),
    containerId,
    inv,
    level,
    data,
    blockEntity.itemHandler,
    blockEntity.itemHandler,
    26, 16,
    135, 16,
    8, 66,
    8, 123
) {
    constructor(
        containerId: Int,
        inv: Inventory,
        level: Level,
        extraData: FriendlyByteBuf
    ) : this(containerId, inv, level, level.getBlockEntity(extraData.readBlockPos()) as BagStorageBlockEntity,
        SimpleContainerData(BagStorageBlockEntity.ContainerDataType.entries.size).also { data ->
            data.set(BagStorageBlockEntity.ContainerDataType.TARGET_BAG_TYPE.ordinal, 114514)
        })

    init {
        setSynchronizer(object : ContainerSynchronizer {
            override fun sendInitialData(
                container: AbstractContainerMenu,
                items: NonNullList<ItemStack>,
                carriedItem: ItemStack,
                initialData: IntArray
            ) {}

            override fun sendSlotChange(container: AbstractContainerMenu, slot: Int, itemStack: ItemStack) {}

            override fun sendCarriedChange(containerMenu: AbstractContainerMenu, stack: ItemStack) {}

            override fun sendDataChange(container: AbstractContainerMenu, id: Int, value: Int) {
                if (id == BagStorageBlockEntity.ContainerDataType.TARGET_BAG_TYPE.ordinal &&
                    level.isClientSide) {
                    sendSelectLootBagTypePacketToServer(value)
                }
            }
        })
    }

    val storedBagAmount: Int
        get() = data.get(BagStorageBlockEntity.ContainerDataType.STORED_BAG_AMOUNT.ordinal)

    override var targetBagType: LootBagType
        get() = LootBagType.entries[data.get(BagStorageBlockEntity.ContainerDataType.TARGET_BAG_TYPE.ordinal) %
                LootBagType.entries.size]
        set(value) {
            data.set(BagStorageBlockEntity.ContainerDataType.TARGET_BAG_TYPE.ordinal, value.ordinal)
            broadcastChanges()
        }

    override val targetBagAmount: Int
        get() = (storedBagAmount.toFloat() * LootBagType.COMMON.amountFactorEquivalentTo(targetBagType)).toInt()

    override fun stillValid(player: Player): Boolean =
        stillValid(ContainerLevelAccess.create(level, blockEntity.blockPos), player, ModBlocks.BAG_STORAGE.get())
}

private fun sendSelectLootBagTypePacketToServer(value: Int) {
    ModNetworks.CHANNEL.sendToServer(ServerboundSelectLootBagTypePacket(value))
}
