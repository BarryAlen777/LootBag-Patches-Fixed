package com.furtabs.lootbags.datagen

import net.minecraft.core.HolderLookup
import net.minecraft.data.PackOutput
import net.minecraft.data.tags.ItemTagsProvider
import net.minecraft.data.tags.TagsProvider
import net.minecraft.world.level.block.Block
import net.minecraftforge.common.data.ExistingFileHelper
import com.furtabs.lootbags.LootBags
import java.util.concurrent.CompletableFuture

class ModItemTagsProvider(
    output: PackOutput,
    lookupProvider: CompletableFuture<HolderLookup.Provider>,
    blockTags: CompletableFuture<TagsProvider.TagLookup<Block>>,
    existing: ExistingFileHelper
) : ItemTagsProvider(output, lookupProvider, blockTags, LootBags.MOD_ID, existing) {
    override fun addTags(provider: HolderLookup.Provider) {}
}
