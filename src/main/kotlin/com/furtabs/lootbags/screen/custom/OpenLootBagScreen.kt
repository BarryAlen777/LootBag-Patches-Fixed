package com.furtabs.lootbags.screen.custom

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Inventory
import com.furtabs.lootbags.LootBags
import com.furtabs.lootbags.util.setShaderTexture
import kotlin.math.max

private val LOOTBAG_GUI_TEXTURE: ResourceLocation =
    ResourceLocation(LootBags.MOD_ID, "textures/gui/lootbag_gui.png")

/** Matches the painted area in `lootbag_gui.png` (below this we extend with a flat panel). */
private const val TEXTURE_BG_HEIGHT: Int = 166

/** Vanilla-style light gray used under inventory rows when extending past the PNG. */
private const val PANEL_FILL_COLOR: Int = 0xFFC6C6C6.toInt()

class OpenLootBagScreen(
    menu: OpenLootBagMenu,
    playerInventory: Inventory,
    title: Component
) : AbstractContainerScreen<OpenLootBagMenu>(menu, playerInventory, title) {
    init {
        imageWidth = 176
        // Bottom padding so slots are not clipped past the blitted texture (avoids magenta gaps).
        imageHeight = max(TEXTURE_BG_HEIGHT, OpenLootBagMenu.PLAYER_HOTBAR_Y + 18 + 12)
    }

    override fun renderLabels(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        // No title / "Inventory" text — GUI texture carries the layout.
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        renderTooltip(guiGraphics, mouseX, mouseY)
    }

    override fun renderBg(guiGraphics: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) {
        val x = leftPos
        val y = topPos
        setShaderTexture(0, LOOTBAG_GUI_TEXTURE)
        // 7-arg blit: no overload ambiguity in Kotlin (see other container screens).
        guiGraphics.blit(LOOTBAG_GUI_TEXTURE, x, y, 0, 0, imageWidth, TEXTURE_BG_HEIGHT)
        if (imageHeight > TEXTURE_BG_HEIGHT) {
            guiGraphics.fill(x, y + TEXTURE_BG_HEIGHT, x + imageWidth, y + imageHeight, PANEL_FILL_COLOR)
        }
    }
}
