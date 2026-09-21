package com.furtabs.lootbags.datagen

import net.minecraft.data.PackOutput
import net.minecraftforge.common.data.GlobalLootModifierProvider
import com.furtabs.lootbags.LootBags

class ModGlobalLootModifierProvider(
    output: PackOutput
) : GlobalLootModifierProvider(output, LootBags.MOD_ID) {
    override fun start() {}
}
