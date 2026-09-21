package com.furtabs.lootbags.screen.custom

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import com.furtabs.lootbags.LootBags
import com.furtabs.lootbags.util.setShaderTexture

private val BAG_STORAGE_GUI_TEXTURE: ResourceLocation =
    ResourceLocation(LootBags.MOD_ID, "textures/gui/bag_storage_gui.png")

abstract class LootBagContainerScreen<T : LootBagContainerMenu>(
    menu: T,
    playerInventory: Inventory,
    title: Component
) : AbstractContainerScreen<T>(menu, playerInventory, title) {
    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        renderLootBagGhostSlot(guiGraphics)
        renderTooltip(guiGraphics, mouseX, mouseY)
    }

    private fun renderLootBagGhostSlot(guiGraphics: GuiGraphics) {
        for (slot in menu.slots) {
            if (slot !is LootBagSlotItemHandler || !slot.isOutputSlot) {
                continue
            }
            if (menu.targetBagAmount != 0) {
                continue
            }
            val stack = ItemStack(menu.targetBagType.asItem())
            guiGraphics.renderItem(stack, leftPos + slot.x, topPos + slot.y)
            setShaderTexture(0, BAG_STORAGE_GUI_TEXTURE)
            guiGraphics.blit(
                BAG_STORAGE_GUI_TEXTURE,
                leftPos + slot.x,
                topPos + slot.y,
                0,
                176f,
                0f,
                16,
                16,
                256,
                256
            )
        }
    }
}
