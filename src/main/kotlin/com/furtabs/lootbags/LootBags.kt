package com.furtabs.lootbags

import com.furtabs.lootbags.block.ModBlocks
import com.furtabs.lootbags.block.entity.ModBlockEntities
import com.furtabs.lootbags.item.ModCreativeModeTabs
import com.furtabs.lootbags.item.ModItems
import com.furtabs.lootbags.network.ModNetworks
import com.furtabs.lootbags.screen.ModMenuTypes
import com.furtabs.lootbags.screen.custom.BagOpenerScreen
import com.furtabs.lootbags.screen.custom.BagStorageScreen
import com.furtabs.lootbags.screen.custom.LootRecyclerScreen
import com.furtabs.lootbags.screen.custom.OpenLootBagScreen
import net.minecraft.client.gui.screens.MenuScreens
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.Mod.EventBusSubscriber
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import thedarkcolour.kotlinforforge.forge.MOD_BUS

@Mod(LootBags.MOD_ID)
object LootBags {
    const val MOD_ID = "lootbags"

    val LOGGER: Logger = LogManager.getLogger(MOD_ID)

    init {
        LOGGER.info("$MOD_ID is loading...")

        MOD_BUS.addListener(::commonSetup)
        MOD_BUS.addListener(::clientSetup)

        ModCreativeModeTabs.register(MOD_BUS)
        ModItems.register(MOD_BUS)
        ModBlocks.register(MOD_BUS)
        ModBlockEntities.register(MOD_BUS)
        ModMenuTypes.register(MOD_BUS)
    }

    private fun commonSetup(event: FMLCommonSetupEvent) {
        event.enqueueWork { ModNetworks.register() }
    }

    private fun clientSetup(event: FMLClientSetupEvent) {
        event.enqueueWork {
            MenuScreens.register(ModMenuTypes.BAG_STORAGE.get(), ::BagStorageScreen)
            MenuScreens.register(ModMenuTypes.BAG_OPENER.get(), ::BagOpenerScreen)
            MenuScreens.register(ModMenuTypes.LOOT_RECYCLER.get(), ::LootRecyclerScreen)
            MenuScreens.register(ModMenuTypes.OPEN_LOOT_BAG.get(), ::OpenLootBagScreen)
        }
    }
}
