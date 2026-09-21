package com.furtabs.lootbags.util

import net.minecraft.world.item.Item
import net.minecraft.world.level.ItemLike
import com.furtabs.lootbags.item.ModItems
import com.furtabs.lootbags.item.custom.LootBagItem
import kotlin.math.pow

enum class LootBagType(
    /**
     * The identifier of the loot bag type, used as the registry name of the item.
     */
    val itemId: String,
    /**
     * Rarity of the loot bag type, the higher the rarity is, the rarer the loot bag is.
     */
    val rarity: UInt,
    /**
     * Whether this type of loot bag can be dropped by living entities.
     *
     * If set to `false`, it can only be obtained through loot bag storage blocks or by crafting.
     */
    val droppable: Boolean,
    /**
     * Whether this type of loot bag is only available in creative mode (neither available in survival nor by crafting).
     */
    val creativeOnly: Boolean,
    /**
     * Chance for this loot bag type to be dropped, from 0.0 to 1.0 inclusive.
     */
    val dropChance: Double
) : ItemLike {
    COMMON("common_loot_bag", 0U, true, false, 0.4),
    UNCOMMON("uncommon_loot_bag", 1U, true, false, 0.1),
    RARE("rare_loot_bag", 2U, true, false, 0.025),
    EPIC("epic_loot_bag", 3U, true, false, 0.00625),
    LEGENDARY("legendary_loot_bag", 4U, true, false, 0.0015625);

    companion object {
        /**
         * The amount of loot bags required to reach the next rarity level.
         * (e.g. craft the loot bag of the next rarity level in crafting tables,
         * get one within the bag storage block, etc.)
         */
        const val AMOUNT_TO_NEXT_RARITY = 4
    }

    val lootGenerator = LootGenerator(this)

    override fun asItem(): Item = when (this) {
        COMMON -> ModItems.COMMON_LOOT_BAG.get()
        UNCOMMON -> ModItems.UNCOMMON_LOOT_BAG.get()
        RARE -> ModItems.RARE_LOOT_BAG.get()
        EPIC -> ModItems.EPIC_LOOT_BAG.get()
        LEGENDARY -> ModItems.LEGENDARY_LOOT_BAG.get()
    }

    /**
     * Calculate the amount factor number which is equivalent to the other loot bag type.
     *
     * For example, 1 uncommon loot bag is equivalent to 4 common loot bags, so calling
     * this method on UNCOMMON with COMMON as the parameter will return `4.0F`, and `0.25F`
     * vice versa.
     */
    fun amountFactorEquivalentTo(other: LootBagType): Float =
        AMOUNT_TO_NEXT_RARITY.toFloat().pow(this.rarity.toInt() - other.rarity.toInt())
}

fun LootBagItem.asLootBagType(): LootBagType = when (this) {
    ModItems.COMMON_LOOT_BAG.get() -> LootBagType.COMMON
    ModItems.UNCOMMON_LOOT_BAG.get() -> LootBagType.UNCOMMON
    ModItems.RARE_LOOT_BAG.get() -> LootBagType.RARE
    ModItems.EPIC_LOOT_BAG.get() -> LootBagType.EPIC
    ModItems.LEGENDARY_LOOT_BAG.get() -> LootBagType.LEGENDARY
    else -> throw IllegalArgumentException("Invalid loot bag item: $this")
}