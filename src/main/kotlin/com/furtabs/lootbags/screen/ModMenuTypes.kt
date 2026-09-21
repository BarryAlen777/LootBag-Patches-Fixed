package com.furtabs.lootbags.screen

import com.furtabs.lootbags.LootBags
import com.furtabs.lootbags.screen.custom.BagOpenerMenu
import com.furtabs.lootbags.screen.custom.BagStorageMenu
import com.furtabs.lootbags.screen.custom.LootRecyclerMenu
import com.furtabs.lootbags.screen.custom.OpenLootBagMenu
import net.minecraft.core.registries.Registries
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.MenuType
import net.minecraftforge.common.extensions.IForgeMenuType
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.RegistryObject

object ModMenuTypes {
    @JvmField
    val MENUS: DeferredRegister<MenuType<*>> =
        DeferredRegister.create(Registries.MENU, LootBags.MOD_ID)

    @JvmField
    val BAG_STORAGE: RegistryObject<MenuType<BagStorageMenu>> =
        MENUS.register("bag_storage") {
            IForgeMenuType.create { containerId, inv, buf ->
                BagStorageMenu(containerId, inv, inv.player.level(), buf)
            }
        }

    @JvmField
    val BAG_OPENER: RegistryObject<MenuType<BagOpenerMenu>> =
        MENUS.register("bag_opener") {
            IForgeMenuType.create { containerId, inv, buf ->
                BagOpenerMenu(containerId, inv, inv.player.level(), buf)
            }
        }

    @JvmField
    val LOOT_RECYCLER: RegistryObject<MenuType<LootRecyclerMenu>> =
        MENUS.register("loot_recycler") {
            IForgeMenuType.create { containerId, inv, buf ->
                LootRecyclerMenu(containerId, inv, inv.player.level(), buf)
            }
        }

    @JvmField
    val OPEN_LOOT_BAG: RegistryObject<MenuType<OpenLootBagMenu>> =
        MENUS.register("open_loot_bag") {
            IForgeMenuType.create { containerId, inv, buf ->
                OpenLootBagMenu(OPEN_LOOT_BAG.get(), containerId, inv, buf)
            }
        }

    fun register(eventBus: IEventBus) {
        MENUS.register(eventBus)
    }
}
