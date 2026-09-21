package com.furtabs.lootbags.util

import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.Mth
import net.minecraft.util.RandomSource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.storage.loot.LootDataId
import net.minecraft.world.level.storage.loot.LootDataType
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.level.storage.loot.LootTable
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets
import java.util.concurrent.ConcurrentHashMap

const val MAX_LOOT_BAG_ITEM_STACKS: Int = 5
private val STACK_COUNT_WEIGHTS: IntArray = intArrayOf(5, 4, 3, 2, 1)

/** Cache to prevent repeated string parsing on every loot roll. */
private val TIER_CACHE = ConcurrentHashMap<ResourceLocation, Int>()

fun rollLootBagDisplayedItemCount(random: RandomSource): Int {
    val weights = STACK_COUNT_WEIGHTS
    val totalWeight = weights.sum()
    var roll = random.nextInt(totalWeight)
    for (i in weights.indices) {
        roll -= weights[i]
        if (roll < 0) return i + 1
    }
    return 1
}

class LootGenerator(private val bagType: LootBagType) {

    fun generateLoot(
        level: ServerLevel,
        lootParamsBuilder: LootParams.Builder = LootParams.Builder(level),
        maxStacks: Int = MAX_LOOT_BAG_ITEM_STACKS
    ): List<ItemStack> {
        val rolledCount = rollLootBagDisplayedItemCount(level.random)
        val cap = rolledCount.coerceAtMost(maxStacks)
        val tier = bagType.rarity.toInt().coerceIn(0, 8)

        return when (bagType) {
            LootBagType.COMMON -> sampleFromRandomLootTables(
                level, lootParamsBuilder, cap,
                luckMin = -1.5f, luckMax = 0.5f, maxTablePicks = 8, bagTier = tier,
                contentTierMin = 0, contentTierMax = 0
            )
            LootBagType.UNCOMMON -> sampleFromRandomLootTables(
                level, lootParamsBuilder, cap,
                luckMin = 0.0f, luckMax = 2.0f, maxTablePicks = 16, bagTier = tier,
                contentTierMin = 0, contentTierMax = 1
            )
            LootBagType.RARE -> sampleFromRandomLootTables(
                level, lootParamsBuilder, cap,
                luckMin = 1.0f, luckMax = 5.0f, maxTablePicks = 32, bagTier = tier,
                contentTierMin = 1, contentTierMax = 2
            )
            LootBagType.EPIC -> sampleFromRandomLootTables(
                level, lootParamsBuilder, cap,
                luckMin = 5.0f, luckMax = 12.0f, maxTablePicks = 48, bagTier = tier,
                contentTierMin = 2, contentTierMax = 3
            )
            LootBagType.LEGENDARY -> sampleFromRandomLootTables(
                level, lootParamsBuilder, cap,
                luckMin = 12.0f, luckMax = 30.0f, maxTablePicks = 80, bagTier = tier,
                contentTierMin = 3, contentTierMax = 4
            )
        }
    }
}

private fun sampleFromRandomLootTables(
    level: ServerLevel,
    lootParamsBuilder: LootParams.Builder,
    maxStacks: Int,
    luckMin: Float,
    luckMax: Float,
    maxTablePicks: Int,
    bagTier: Int,
    contentTierMin: Int,
    contentTierMax: Int
): List<ItemStack> {
    // Purge blocks to keep the loot pool clean and relevant
    val allIds = level.server.lootData.getKeys(LootDataType.TABLE)
        .toList()
        .filter { !it.path.startsWith("blocks/") }

    if (allIds.isEmpty()) return emptyList()

    var ids = filterLootTablesByContentTier(allIds, contentTierMin, contentTierMax)
    
    if (ids.isEmpty()) ids = allIds

    val random = level.random
    val result = mutableListOf<ItemStack>()
    var picks = 0
    
    while (result.size < maxStacks && picks < maxTablePicks) {
        picks++
        val id = pickLootTableId(ids, random, bagTier)
        val lootTable = level.server.lootData.getElement(LootDataId(LootDataType.TABLE, id))
        
        if (lootTable == null || lootTable === LootTable.EMPTY) continue

        val luck = if (luckMin >= luckMax) luckMax else Mth.randomBetween(random, luckMin, luckMax)
        val params = lootParamsBuilder.withLuck(luck).create(LootContextParamSets.CHEST)
        
        for (stack in lootTable.getRandomItems(params)) {
            if (result.size >= maxStacks) break
            if (!stack.isEmpty) {
                result.add(stack.copy())
            }
        }
    }

    return result
}

private fun filterLootTablesByContentTier(ids: List<ResourceLocation>, min: Int, max: Int): List<ResourceLocation> {
    return ids.filter { getOrCacheTier(it) in min..max }
}

private fun getOrCacheTier(id: ResourceLocation): Int {
    return TIER_CACHE.getOrPut(id) { calculateLootTableTier(id) }
}

private fun calculateLootTableTier(id: ResourceLocation): Int {
    val path = id.path.lowercase()
    val ns = id.namespace.lowercase()
    
    if (ns != "minecraft") return modLootTableContentTier(path)
    
    // 1.20.1 Archaeology
    if (path.startsWith("archaeology/")) {
        return when {
            path.contains("trail_ruins_rare") -> 3
            path.contains("ocean_ruin_warm") -> 2
            path.contains("desert_pyramid") || path.contains("trail_ruins_common") -> 1
            else -> 0
        }
    }

    if (path.startsWith("chests/")) {
        return when {
            path.contains("village") || path.contains("spawn_bonus") || 
            path.contains("igloo") || path.contains("shipwreck_supply") ||
            path.contains("underwater_ruin_small") -> 0

            path.contains("desert_pyramid") || path.contains("jungle_temple") || 
            path.contains("mineshaft") || path.contains("pillager_outpost") || 
            path.contains("shipwreck_map") || path.contains("simple_dungeon") -> 1

            path.contains("nether_bridge") || path.contains("bastion_bridge") || 
            path.contains("stronghold_corridor") || path.contains("stronghold_crossing") ||
            path.contains("shipwreck_treasure") || path.contains("underwater_ruin_big") ||
            path.contains("ruined_portal") -> 2

            path.contains("ancient_city") || path.contains("stronghold_library") || 
            path.contains("bastion_hoglin_stable") || path.contains("bastion_other") -> 3

            path.contains("bastion_treasure") || path.contains("end_city") || 
            path.contains("buried_treasure") || path.contains("woodland_mansion") -> 4

            else -> 1
        }
    }

    if (path.startsWith("entities/")) {
        return if (entityLootPathLikelyRewarding(path)) 2 else 0
    }

    return 1
}

private fun modLootTableContentTier(path: String): Int = when {
    path.contains("legendary") || path.contains("treasure") || path.contains("mythic") -> 4
    path.contains("ancient") || path.contains("stronghold") || path.contains("epic") -> 3
    path.contains("nether") || path.contains("bastion") || path.contains("rare") -> 2
    path.contains("dungeon") || path.contains("temple") || path.contains("uncommon") -> 1
    else -> 0
}

private fun pickLootTableId(ids: List<ResourceLocation>, random: RandomSource, bagTier: Int): ResourceLocation {
    if (ids.size <= 1) return ids.firstOrNull() ?: ResourceLocation("minecraft", "empty")
    
    var total = 0f
    val weights = FloatArray(ids.size)
    for (i in ids.indices) {
        val w = lootTableTierBias(ids[i], bagTier)
        weights[i] = w
        total += w
    }
    
    var roll = random.nextFloat() * total
    for (i in weights.indices) {
        roll -= weights[i]
        if (roll <= 0f) return ids[i]
    }
    return ids.last()
}

private fun lootTableTierBias(id: ResourceLocation, bagTier: Int): Float {
    val path = id.path.lowercase()
    
    if (bagTier == 0) {
        return if (path.contains("village") || path.contains("fishing/junk")) 5.0f else 1.0f
    }

    var score = 1.0f
    val t = bagTier.toFloat()

    if (path.contains("chests/")) score += 1.5f * t
    if (path.contains("treasure") || path.contains("city") || path.contains("bastion")) score += 3.0f * t
    
    if (bagTier >= 3 && (path.contains("village") || path.contains("spawn_bonus") || path.contains("fishing/junk"))) {
        score *= 0.05f
    }

    return score.coerceIn(0.01f, 200f)
}

private fun entityLootPathLikelyRewarding(path: String): Boolean =
    path.contains("equipment") || path.contains("raid") || path.contains("boss") || 
    path.contains("wither") || path.contains("warden") || path.contains("evoker")