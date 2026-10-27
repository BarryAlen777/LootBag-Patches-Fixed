package com.furtabs.lootbags.util

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.world.item.ItemStack

/**
 * Dedicated key for the loot stored inside a bag.
 *
 * The old code used `Items`, which is the vanilla convention for *container* block entities and
 * container items. Keeping a private key avoids mods / tools that sniff `Items` mistaking an
 * opened bag for a container. Bags saved by older versions (`Items`) are still read, and are
 * migrated the next time the bag is written.
 */
private const val KEY_LOOT_ITEMS: String = "LootItems"
private const val LEGACY_KEY_LOOT_ITEMS: String = "Items"
private const val KEY_OPENED: String = "opened"

/**
 * Returns true when this bag has already been rolled at least once.
 *
 * Both the [KEY_OPENED] marker and the serialized loot list are honoured, so a spent bag
 * (opened, but with an empty loot list) is still recognised as "opened" and can never be
 * rolled a second time.
 */
@Suppress("DEPRECATION")
fun isOpenedBag(stack: ItemStack): Boolean {
    val tag = stack.tag ?: return false
    return tag.getBoolean(KEY_OPENED) ||
        tag.contains(KEY_LOOT_ITEMS, 9) ||
        tag.contains(LEGACY_KEY_LOOT_ITEMS, 9)
}

/**
 * Writes the rolled/remaining loot into the bag's NBT and marks it as opened.
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
    tag.put(KEY_LOOT_ITEMS, listTag)
    tag.remove(LEGACY_KEY_LOOT_ITEMS)
    tag.putBoolean(KEY_OPENED, true)
}

/**
 * Returns the stored loot from the bag's NBT, or null when the bag holds nothing.
 */
@Suppress("DEPRECATION")
fun readStoredOpenLoot(stack: ItemStack): List<ItemStack>? {
    val tag = stack.tag ?: return null
    val key = when {
        tag.contains(KEY_LOOT_ITEMS, 9) -> KEY_LOOT_ITEMS
        tag.contains(LEGACY_KEY_LOOT_ITEMS, 9) -> LEGACY_KEY_LOOT_ITEMS
        else -> return null
    }

    val listTag = tag.getList(key, 10)
    val loots = mutableListOf<ItemStack>()
    for (i in 0 until listTag.size) {
        loots.add(ItemStack.of(listTag.getCompound(i)))
    }
    return if (loots.isEmpty()) null else loots
}

/**
 * Wipes the roll state so the stack counts as a fresh, unopened bag again.
 */
@Suppress("DEPRECATION")
fun clearStoredOpenLoot(bagStack: ItemStack) {
    val root = bagStack.tag ?: return
    root.remove(KEY_LOOT_ITEMS)
    root.remove(LEGACY_KEY_LOOT_ITEMS)
    root.remove(KEY_OPENED)
    if (root.isEmpty) {
        bagStack.tag = null
    }
}
