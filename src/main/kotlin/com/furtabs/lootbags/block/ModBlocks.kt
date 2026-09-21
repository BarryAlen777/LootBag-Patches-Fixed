package com.furtabs.lootbags.block

import com.furtabs.lootbags.LootBags
import com.furtabs.lootbags.block.custom.BagOpenerBlock
import com.furtabs.lootbags.block.custom.BagStorageBlock
import com.furtabs.lootbags.block.custom.LootRecyclerBlock
import com.furtabs.lootbags.item.ModItems
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject
import java.util.function.Supplier

object ModBlocks {
    @JvmField
    val BLOCKS: DeferredRegister<Block> =
        DeferredRegister.create(ForgeRegistries.BLOCKS, LootBags.MOD_ID)

    @JvmField
    val LOOT_RECYCLER: RegistryObject<Block> =
        registerBlockWithItem("loot_recycler", Supplier { LootRecyclerBlock() })

    @JvmField
    val BAG_OPENER: RegistryObject<Block> =
        registerBlockWithItem("bag_opener", Supplier { BagOpenerBlock() })

    @JvmField
    val BAG_STORAGE: RegistryObject<Block> =
        registerBlockWithItem("bag_storage", Supplier { BagStorageBlock() })

    private fun registerBlockWithItem(name: String, block: Supplier<out Block>): RegistryObject<Block> {
        val defBlock = BLOCKS.register(name, block)
        ModItems.ITEMS.register(name) {
            BlockItem(defBlock.get(), Item.Properties())
        }
        return defBlock
    }

    fun register(eventBus: IEventBus) {
        BLOCKS.register(eventBus)
    }
}
