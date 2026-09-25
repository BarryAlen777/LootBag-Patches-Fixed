package com.furtabs.lootbags.item.custom

import com.furtabs.lootbags.screen.ModMenuTypes
import com.furtabs.lootbags.screen.custom.OpenLootBagMenu
import com.furtabs.lootbags.screen.custom.newLootResultHandlerWithLoot
import com.furtabs.lootbags.util.LootBagType
import com.furtabs.lootbags.util.MAX_LOOT_BAG_ITEM_STACKS
import com.furtabs.lootbags.util.clearStoredOpenLoot
import com.furtabs.lootbags.util.isOpenedBag
import com.furtabs.lootbags.util.readStoredOpenLoot
import com.furtabs.lootbags.util.rollLootBagDisplayedItemCount
import com.furtabs.lootbags.util.writeStoredOpenLoot
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.stats.Stats
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.level.storage.loot.parameters.LootContextParams
import net.minecraftforge.network.NetworkHooks

class LootBagItem(
    val type: LootBagType,
    properties: Properties = Properties().stacksTo(1)
) : Item(properties) {

    override fun use(level: Level, player: Player, usedHand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(usedHand)

        if (!level.isClientSide && level is ServerLevel && player is ServerPlayer) {
            // A bag is rolled exactly once. From then on its contents live in NBT, so we must
            // never roll it again -- otherwise an already-opened bag could be re-opened forever.
            val alreadyOpened = isOpenedBag(stack)
            val loots: List<ItemStack>

            if (alreadyOpened) {
                val stored = readStoredOpenLoot(stack)
                if (stored == null) {
                    // Spent bag: it was opened before and every item has been taken. Destroy it
                    // instead of rerolling, which is what allowed the infinite loot loop.
                    if (!player.abilities.instabuild) {
                        stack.shrink(1)
                    }
                    player.awardStat(Stats.ITEM_USED.get(this))
                    return InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
                }
                loots = stored
            } else {
                // Fresh bag: roll the loot and persist it immediately so a re-open sees the same items.
                val maxStacks = rollLootBagDisplayedItemCount(level.random)
                loots = type.lootGenerator.generateLoot(
                    level,
                    LootParams.Builder(level)
                        .withParameter(LootContextParams.THIS_ENTITY, player)
                        .withParameter(LootContextParams.ORIGIN, player.position())
                        .withParameter(LootContextParams.TOOL, stack),
                    maxStacks = maxStacks
                )
                writeStoredOpenLoot(stack, loots)
            }

            // A roll that came up empty would leave a bag that can never give anything: consume it.
            val visibleLoot = loots.filter { !it.isEmpty }
            if (visibleLoot.isEmpty()) {
                clearStoredOpenLoot(stack)
                if (!player.abilities.instabuild) {
                    stack.shrink(1)
                }
                player.awardStat(Stats.ITEM_USED.get(this))
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
            }

            // Create the handler for the Menu. Whenever a slot changes we mirror the remaining
            // loot into NBT; the moment the bag is emptied we consume it on the spot.
            lateinit var handler: net.minecraftforge.items.ItemStackHandler
            handler = newLootResultHandlerWithLoot(visibleLoot) { h ->
                val remaining = mutableListOf<ItemStack>()
                for (i in 0 until MAX_LOOT_BAG_ITEM_STACKS) {
                    val s = h.getStackInSlot(i)
                    if (!s.isEmpty) remaining.add(s.copy())
                }
                if (remaining.isEmpty()) {
                    // Everything has been taken: the bag is used up right now, so the player
                    // cannot carry it to another slot and open it again.
                    clearStoredOpenLoot(stack)
                    if (!player.abilities.instabuild) {
                        stack.shrink(1)
                    }
                } else {
                    writeStoredOpenLoot(stack, remaining)
                }
            }

            val menuProvider = object : MenuProvider {
                override fun getDisplayName() = stack.hoverName
                override fun createMenu(id: Int, inv: Inventory, p: Player) =
                    OpenLootBagMenu(ModMenuTypes.OPEN_LOOT_BAG.get(), id, inv, handler, usedHand, stack)
            }

            // Open the screen and sync the loot to the client
            NetworkHooks.openScreen(player, menuProvider) { buf ->
                buf.writeByte(usedHand.ordinal)
                // Ensure we write exactly the expected amount of slots to the buffer
                for (i in 0 until MAX_LOOT_BAG_ITEM_STACKS) {
                    buf.writeItem(handler.getStackInSlot(i))
                }
            }
        }

        player.awardStat(Stats.ITEM_USED.get(this))
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
    }
}
