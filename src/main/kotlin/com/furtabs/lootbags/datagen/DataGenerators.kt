package com.furtabs.lootbags.datagen

import net.minecraft.data.loot.LootTableProvider
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets
import net.minecraftforge.data.event.GatherDataEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import com.furtabs.lootbags.LootBags

@Mod.EventBusSubscriber(modid = LootBags.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
object DataGenerators {
    @SubscribeEvent
    fun gatherData(event: GatherDataEvent) {
        val generator = event.generator
        val packOutput = generator.packOutput
        val lookup = event.lookupProvider
        val existing = event.existingFileHelper
        if (event.includeServer()) {
            generator.addProvider(
                true,
                LootTableProvider(
                    packOutput,
                    setOf(),
                    listOf(
                        LootTableProvider.SubProviderEntry(
                            { ModBlockLootTableProvider() },
                            LootContextParamSets.BLOCK
                        )
                    )
                )
            )
            generator.addProvider(true, ModRecipeProvider(packOutput))
            val blockTags = ModBlockTagsProvider(packOutput, lookup, existing)
            generator.addProvider(true, blockTags)
            generator.addProvider(
                true,
                ModItemTagsProvider(packOutput, lookup, blockTags.contentsGetter(), existing)
            )
            generator.addProvider(true, ModGlobalLootModifierProvider(packOutput))
        }
    }
}
