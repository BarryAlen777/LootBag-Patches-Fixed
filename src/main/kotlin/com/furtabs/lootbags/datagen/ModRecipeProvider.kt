package com.furtabs.lootbags.datagen

import net.minecraft.data.PackOutput
import net.minecraft.data.recipes.RecipeCategory
import net.minecraft.data.recipes.RecipeProvider
import net.minecraft.data.recipes.ShapedRecipeBuilder
import net.minecraft.data.recipes.ShapelessRecipeBuilder
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import com.furtabs.lootbags.LootBags
import com.furtabs.lootbags.block.ModBlocks
import com.furtabs.lootbags.util.LootBagType
import java.util.function.Consumer
import net.minecraft.data.recipes.FinishedRecipe
import net.minecraft.resources.ResourceLocation

class ModRecipeProvider(
    output: PackOutput
) : RecipeProvider(output) {

    override fun buildRecipes(out: Consumer<FinishedRecipe>) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.LOOT_RECYCLER.get())
            .pattern(" A ")
            .pattern("CBC")
            .pattern("DDD")
            .define('A', Items.DIAMOND)
            .define('B', Blocks.GRINDSTONE)
            .define('C', Items.COPPER_INGOT)
            .define('D', Blocks.DEEPSLATE)
            .unlockedBy("has_grindstone", has(Blocks.GRINDSTONE))
            .save(out)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.BAG_OPENER.get())
            .pattern("ABA")
            .pattern("CDC")
            .pattern("BBB")
            .define('A', Items.COPPER_INGOT)
            .define('B', Blocks.DEEPSLATE)
            .define('C', Items.DIAMOND)
            .define('D', Blocks.BLAST_FURNACE)
            .unlockedBy("has_blast_furnace", has(Blocks.BLAST_FURNACE))
            .save(out)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.BAG_STORAGE.get())
            .pattern("ABA")
            .pattern("CDC")
            .pattern("EEE")
            .define('A', Items.COPPER_INGOT)
            .define('B', Items.CALIBRATED_SCULK_SENSOR)
            .define('C', Items.NETHERITE_INGOT)
            .define('D', Blocks.ENDER_CHEST)
            .define('E', Blocks.DEEPSLATE)
            .unlockedBy("has_ender_chest", has(Blocks.ENDER_CHEST))
            .save(out)

        for (type in LootBagType.entries) {
            for (otherType in LootBagType.entries) {
                if (otherType.rarity >= type.rarity) {
                    continue
                }
                if (type.creativeOnly || otherType.creativeOnly) {
                    continue
                }
                val amount = type.amountFactorEquivalentTo(otherType).toInt()
                if (amount <= 4) {
                    ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, type.asItem())
                        .requires(otherType.asItem(), amount)
                        .unlockedBy("has_${otherType.itemId}", has(otherType.asItem()))
                        .save(
                            out,
                            ResourceLocation(
                                LootBags.MOD_ID,
                                "${type.itemId}_from_${otherType.itemId}"
                            )
                        )
                }
            }
        }
    }
}
