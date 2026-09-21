package com.furtabs.lootbags.event

import net.minecraft.util.Mth
import net.minecraft.util.RandomSource
import net.minecraft.world.item.ItemStack
import net.minecraftforge.event.entity.living.LivingDropsEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import com.furtabs.lootbags.LootBags
import com.furtabs.lootbags.util.LootBagType
import com.furtabs.lootbags.util.newItemEntitiesForDropping

@Mod.EventBusSubscriber(modid = LootBags.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
object ModEvents {
    @SubscribeEvent
    fun onLivingDrops(event: LivingDropsEvent) {
        for (bagType in LootBagType.entries) {
            if (!bagType.droppable) {
                continue
            }

            val random: RandomSource = event.entity.random
            val rand = Mth.nextDouble(random, 0.0, 1.0)
            if (rand <= bagType.dropChance) {
                val entityPos = event.entity.getPosition(0F)
                // Drop a single loot bag per successful roll.
                val amount = 1
                val stack = ItemStack(bagType.asItem(), amount)
                val entities = newItemEntitiesForDropping(event.entity.level(), entityPos, stack)
                event.drops.addAll(entities)

                LootBags.LOGGER.debug(
                    "Generated loot bags of type {} amount {} for LivingEntity {} at position {}, level {}",
                    bagType,
                    amount,
                    event.entity.type.description.string,
                    entityPos,
                    event.entity.level().dimension().location()
                )
                break
            }
        }
    }
}
