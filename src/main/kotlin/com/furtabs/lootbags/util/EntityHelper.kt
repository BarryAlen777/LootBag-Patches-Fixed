package com.furtabs.lootbags.util

import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import kotlin.math.floor

fun newItemEntitiesForDropping(level: Level, location: Vec3, stack: ItemStack): List<ItemEntity> {
    val entityLoc = calcItemEntityLocationForDropping(location, level.random)

    val remainingStack = stack.copy()
    val result = mutableListOf<ItemEntity>()
    while (!remainingStack.isEmpty) {
        val entity = ItemEntity(level, entityLoc.x, entityLoc.y, entityLoc.z,
            remainingStack.split(level.random.nextInt(21) + 10))
        entity.setDeltaMovement(
            level.random.triangle(0.0, 0.11485000171139836),
            level.random.triangle(0.2, 0.11485000171139836),
            level.random.triangle(0.0, 0.11485000171139836)
        )
        result.add(entity)
    }
    return result
}

fun newItemEntitiesForDropping(level: Level, location: Vec3, stacks: List<ItemStack>): List<ItemEntity> =
    mutableListOf<ItemEntity>().also { result ->
        for (stack in stacks) {
            result.addAll(newItemEntitiesForDropping(level, location, stack))
        }
    }

private fun calcItemEntityLocationForDropping(location: Vec3, random: net.minecraft.util.RandomSource): Vec3 {
    val d0 = EntityType.ITEM.width
    val d1 = 1.0 - d0
    val d2 = d0 / 2.0
    val x = floor(location.x) + random.nextDouble() * d1 + d2
    val y = floor(location.y) + random.nextDouble() * d1
    val z = floor(location.z) + random.nextDouble() * d1 + d2

    return Vec3(x, y, z)
}
