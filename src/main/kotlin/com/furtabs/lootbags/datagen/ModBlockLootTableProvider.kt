package com.furtabs.lootbags.datagen

import net.minecraft.data.loot.BlockLootSubProvider
import net.minecraft.world.flag.FeatureFlags
import net.minecraft.world.level.block.Block
import com.furtabs.lootbags.LootBags
import com.furtabs.lootbags.block.ModBlocks
import net.minecraftforge.registries.ForgeRegistries

class ModBlockLootTableProvider : BlockLootSubProvider(setOf(), FeatureFlags.REGISTRY.allFlags()) {
    override fun generate() {
        // The machines only drop themselves. Their contents are dropped by `onRemove` (like a
        // vanilla chest), not copied into the item: a `copy_nbt` loot table returns nothing when
        // the block is blown up, replaced by `/setblock`, broken in creative mode or broken
        // without the correct tool, which used to delete everything stored inside.
        dropSelf(ModBlocks.BAG_OPENER.get())
        dropSelf(ModBlocks.BAG_STORAGE.get())
        dropSelf(ModBlocks.LOOT_RECYCLER.get())
    }

    override fun getKnownBlocks(): Iterable<Block> =
        ForgeRegistries.BLOCKS.values
            .filter { block -> ForgeRegistries.BLOCKS.getKey(block)?.namespace == LootBags.MOD_ID }
}
