package com.furtabs.lootbags.util

import com.furtabs.lootbags.item.custom.LootBagItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Rarity

object LootValueRegistry {
    fun getValue(stack: ItemStack): Double {
        if (stack.isEmpty) return 0.0

        // Map rarities to decimal points
        return when (stack.rarity) {
            Rarity.COMMON -> 0.05
            Rarity.UNCOMMON -> 0.15
            Rarity.RARE -> 0.5
            Rarity.EPIC -> 2.5
            else -> 0.0
        }
    }
}