package com.furtabs.lootbags.block.custom

import net.minecraft.world.level.block.BaseEntityBlock

/**
 * Base class for the machine blocks that own a block entity.
 *
 * It used to render "stored: N" tooltips from the block-entity data of the dropped item stack.
 * That data no longer exists: the loot tables now use `dropSelf` and the contents are dropped
 * from the block entity directly, so the tooltip was dead code and has been removed.
 */
abstract class LootBagEntityBlock(properties: Properties) : BaseEntityBlock(properties)
