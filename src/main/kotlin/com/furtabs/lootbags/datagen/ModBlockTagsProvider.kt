package com.furtabs.lootbags.datagen

import net.minecraft.core.HolderLookup
import net.minecraft.data.PackOutput
import net.minecraft.tags.BlockTags
import net.minecraftforge.common.data.BlockTagsProvider
import net.minecraftforge.common.data.ExistingFileHelper
import com.furtabs.lootbags.LootBags
import com.furtabs.lootbags.block.ModBlocks
import java.util.concurrent.CompletableFuture

class ModBlockTagsProvider(
    output: PackOutput,
    lookupProvider: CompletableFuture<HolderLookup.Provider>,
    existing: ExistingFileHelper
) : BlockTagsProvider(output, lookupProvider, LootBags.MOD_ID, existing) {
    override fun addTags(provider: HolderLookup.Provider) {
        tag(BlockTags.MINEABLE_WITH_PICKAXE)
            .add(ModBlocks.LOOT_RECYCLER.get())
            .add(ModBlocks.BAG_OPENER.get())
            .add(ModBlocks.BAG_STORAGE.get())
    }
}
