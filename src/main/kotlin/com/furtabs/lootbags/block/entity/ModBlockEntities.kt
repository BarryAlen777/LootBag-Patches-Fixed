package com.furtabs.lootbags.block.entity

import com.furtabs.lootbags.LootBags
import com.furtabs.lootbags.block.ModBlocks
import com.furtabs.lootbags.block.entity.custom.BagOpenerBlockEntity
import com.furtabs.lootbags.block.entity.custom.BagStorageBlockEntity
import com.furtabs.lootbags.block.entity.custom.LootRecyclerBlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject

object ModBlockEntities {
    @JvmField
    val BLOCK_ENTITIES: DeferredRegister<BlockEntityType<*>> =
        DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, LootBags.MOD_ID)

    @JvmField
    val BAG_STORAGE: RegistryObject<BlockEntityType<BagStorageBlockEntity>> =
        BLOCK_ENTITIES.register("bag_storage") {
            BlockEntityType.Builder.of(::BagStorageBlockEntity, ModBlocks.BAG_STORAGE.get()).build(null)
        }

    @JvmField
    val BAG_OPENER: RegistryObject<BlockEntityType<BagOpenerBlockEntity>> =
        BLOCK_ENTITIES.register("bag_opener") {
            BlockEntityType.Builder.of(::BagOpenerBlockEntity, ModBlocks.BAG_OPENER.get()).build(null)
        }

    @JvmField
    val LOOT_RECYCLER: RegistryObject<BlockEntityType<LootRecyclerBlockEntity>> =
        BLOCK_ENTITIES.register("loot_recycler") {
            BlockEntityType.Builder.of(::LootRecyclerBlockEntity, ModBlocks.LOOT_RECYCLER.get()).build(null)
        }

    fun register(eventBus: IEventBus) {
        BLOCK_ENTITIES.register(eventBus)
    }
}
