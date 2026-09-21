package com.furtabs.lootbags.datagen

import net.minecraft.data.loot.BlockLootSubProvider
import net.minecraft.world.flag.FeatureFlags
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.storage.loot.LootPool
import net.minecraft.world.level.storage.loot.LootTable
import net.minecraft.world.level.storage.loot.entries.LootItem
import net.minecraft.world.level.storage.loot.functions.CopyNbtFunction
import net.minecraft.world.level.storage.loot.functions.CopyNbtFunction.MergeStrategy
import net.minecraft.world.level.storage.loot.providers.nbt.ContextNbtProvider
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue
import com.furtabs.lootbags.LootBags
import com.furtabs.lootbags.block.ModBlocks
import net.minecraftforge.registries.ForgeRegistries

class ModBlockLootTableProvider : BlockLootSubProvider(setOf(), FeatureFlags.REGISTRY.allFlags()) {
    override fun generate() {
        dropSelf(ModBlocks.LOOT_RECYCLER.get())
        dropSelf(ModBlocks.BAG_OPENER.get())
        add(ModBlocks.BAG_STORAGE.get()) { block: Block ->
            LootTable.lootTable()
                .withPool(
                    applyExplosionDecay(
                        block,
                        LootPool.lootPool()
                            .setRolls(ConstantValue.exactly(1F))
                            .add(
                                LootItem.lootTableItem(block)
                                    .apply(
                                        CopyNbtFunction.copyData(ContextNbtProvider.BLOCK_ENTITY)
                                            .copy("inventory", "BlockEntityTag.inventory", MergeStrategy.REPLACE)
                                            .copy("stored_bag_amount", "BlockEntityTag.stored_bag_amount", MergeStrategy.REPLACE)
                                            .copy("target_bag_type", "BlockEntityTag.target_bag_type", MergeStrategy.REPLACE)
                                    )
                            )
                    )
                )
        }
    }

    override fun getKnownBlocks(): Iterable<Block> =
        ForgeRegistries.BLOCKS.values
            .filter { block -> ForgeRegistries.BLOCKS.getKey(block)?.namespace == LootBags.MOD_ID }
}
