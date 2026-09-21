package com.furtabs.lootbags.network

import com.furtabs.lootbags.LootBags
import com.furtabs.lootbags.network.custom.ServerboundSelectLootBagTypePacket
import com.furtabs.lootbags.screen.custom.BagStorageMenu
import com.furtabs.lootbags.util.LootBagType
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.network.NetworkRegistry
import net.minecraftforge.network.simple.SimpleChannel

object ModNetworks {
    private const val PROTOCOL_VERSION = "1"

    @JvmField
    val CHANNEL: SimpleChannel = NetworkRegistry.newSimpleChannel(
        ResourceLocation(LootBags.MOD_ID, "main"),
        { PROTOCOL_VERSION },
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    )

    fun register() {
        var id = 0
        CHANNEL.messageBuilder(ServerboundSelectLootBagTypePacket::class.java, id++)
            .encoder { msg, buf -> buf.writeVarInt(msg.lootBagTypeOrdinal) }
            .decoder { buf -> ServerboundSelectLootBagTypePacket(buf.readVarInt()) }
            .consumerMainThread { msg, ctx ->
                val player = ctx.get().sender ?: return@consumerMainThread
                val menu = player.containerMenu
                if (menu is BagStorageMenu) {
                    val idx = msg.lootBagTypeOrdinal % LootBagType.entries.size
                    menu.targetBagType = LootBagType.entries[idx]
                }
                ctx.get().setPacketHandled(true)
            }
            .add()
    }
}
