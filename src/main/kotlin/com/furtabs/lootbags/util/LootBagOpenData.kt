package com.furtabs.lootbags.util

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.world.item.ItemStack
import net.minecraftforge.items.ItemStackHandler

private const val KEY_LOOT_SLOTS: String = "LootBagOpenItems"

/**
 * Returns up to [MAX_LOOT_BAG_ITEM_STACKS] stacks read from the bag's NBT, or null if nothing stored.
 */
@Suppress("DEPRECATION")
fun writeStoredOpenLoot(stack: ItemStack, loots: List<ItemStack>) {
    val tag = stack.orCreateTag
    val listTag = ListTag()
    for (loot in loots) {
        if (!loot.isEmpty) {
            val lootTag = CompoundTag()
            loot.save(lootTag)
            listTag.add(lootTag)
        }
    }
    tag.put("Items", listTag)
    tag.putBoolean("opened", true) 
}

@Suppress("DEPRECATION")
fun readStoredOpenLoot(stack: ItemStack): List<ItemStack>? {
    val tag = stack.tag ?: return null
    if (!tag.contains("Items", 9)) return null
    
    val listTag = tag.getList("Items", 10)
    val loots = mutableListOf<ItemStack>()
    for (i in 0 until listTag.size) {
        loots.add(ItemStack.of(listTag.getCompound(i)))
    }
    return if (loots.isEmpty()) null else loots
}

@Suppress("DEPRECATION")
fun clearStoredOpenLoot(bagStack: ItemStack) {
    val root = bagStack.tag ?: return
    root.remove(KEY_LOOT_SLOTS)
    if (root.isEmpty) {
        bagStack.tag = null
    }
}
