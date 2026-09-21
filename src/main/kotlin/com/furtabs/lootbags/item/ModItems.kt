package com.furtabs.lootbags.item

import com.furtabs.lootbags.LootBags
import com.furtabs.lootbags.item.custom.LootBagItem
import com.furtabs.lootbags.util.LootBagType
import net.minecraft.world.item.Item
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject

object ModItems {
    @JvmField
    val ITEMS: DeferredRegister<Item> =
        DeferredRegister.create(ForgeRegistries.ITEMS, LootBags.MOD_ID)

    @JvmField
    val COMMON_LOOT_BAG: RegistryObject<Item> = registerLootBagItem(LootBagType.COMMON)

    @JvmField
    val UNCOMMON_LOOT_BAG: RegistryObject<Item> = registerLootBagItem(LootBagType.UNCOMMON)

    @JvmField
    val RARE_LOOT_BAG: RegistryObject<Item> = registerLootBagItem(LootBagType.RARE)

    @JvmField
    val EPIC_LOOT_BAG: RegistryObject<Item> = registerLootBagItem(LootBagType.EPIC)

    @JvmField
    val LEGENDARY_LOOT_BAG: RegistryObject<Item> = registerLootBagItem(LootBagType.LEGENDARY)

    val LOOT_BAGS: List<RegistryObject<Item>> = listOf(
        COMMON_LOOT_BAG,
        UNCOMMON_LOOT_BAG,
        RARE_LOOT_BAG,
        EPIC_LOOT_BAG,
        LEGENDARY_LOOT_BAG
    )

    private fun registerLootBagItem(type: LootBagType): RegistryObject<Item> =
        ITEMS.register(type.itemId) { LootBagItem(type) }

    fun register(eventBus: IEventBus) {
        ITEMS.register(eventBus)
    }
}
