/*
 * Copyright (c) 2019-2022 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

package org.geysermc.geyser.translator.protocol.bedrock;

import org.geysermc.geyser.inventory.GeyserItemStack;
import org.geysermc.geyser.item.type.BlockItem;
import org.geysermc.geyser.item.type.BoatItem;
import org.geysermc.geyser.item.type.FishingRodItem;
import org.geysermc.geyser.item.type.Item;
import org.geysermc.geyser.item.type.SpawnEggItem;
import org.geysermc.geyser.level.block.type.BlockState;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.cache.tags.BlockTag;
import org.geysermc.geyser.session.cache.tags.ItemTag;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;

/**
 * Java's use key tries the main hand, and the off hand only if that hand passes.
 * A door or chest answers the main hand, so the off hand is not asked.
 * Sneaking with an item in either hand skips the block and lets the item act.
 */
public final class JavaHandUse {

    private JavaHandUse() {
    }

    /**
     * True when the main hand would finish the click, so the off hand must stay unused.
     */
    static boolean mainHandConsumes(GeyserSession session, BlockState block) {
        GeyserItemStack main = session.getPlayerInventory().getItemInHand();
        GeyserItemStack off = session.getPlayerInventory().getOffhand();
        boolean holding = !main.isEmpty() || !off.isEmpty();
        if (block != null && !(session.isSneaking() && holding)) {
            int id = block.javaId();
            if (BlockRegistries.INTERACTIVE.get().get(id)) {
                // useItemOn runs before the empty-hand action. A sword passes, so a disc,
                // book, dye, or bone meal in the off hand still gets the block.
                if (!itemConsumes(session, main, block) && itemConsumes(session, off, block)
                        && heldItemBlock(block)) {
                    return false;
                }
                return true;
            }
            GameMode mode = session.getGameMode();
            if ((mode == GameMode.SURVIVAL || mode == GameMode.CREATIVE)
                    && BlockRegistries.INTERACTIVE_MAY_BUILD.get().get(id)) {
                return true;
            }
        }
        return itemConsumes(session, main, block);
    }

    /**
     * True when the block itself finishes the click, the way a door or chest does.
     * Food, a bow, and a shield do not get their air use in that case.
     */
    static boolean blockAnswersEmptyHand(GeyserSession session, BlockState block) {
        if (block == null) {
            return false;
        }
        GeyserItemStack main = session.getPlayerInventory().getItemInHand();
        GeyserItemStack off = session.getPlayerInventory().getOffhand();
        if (session.isSneaking() && (!main.isEmpty() || !off.isEmpty())) {
            return false;
        }
        int id = block.javaId();
        if (BlockRegistries.INTERACTIVE.get().get(id)) {
            if (!itemConsumes(session, main, block) && itemConsumes(session, off, block) && heldItemBlock(block)) {
                return false;
            }
            return true;
        }
        GameMode mode = session.getGameMode();
        return (mode == GameMode.SURVIVAL || mode == GameMode.CREATIVE)
                && BlockRegistries.INTERACTIVE_MAY_BUILD.get().get(id);
    }

    /**
     * The one hand Java would actually use for this click.
     */
    static Hand handToUse(GeyserSession session, BlockState block) {
        if (mainHandConsumes(session, block)) {
            return Hand.MAIN_HAND;
        }
        GeyserItemStack off = session.getPlayerInventory().getOffhand();
        if (itemConsumes(session, off, block)) {
            return Hand.OFF_HAND;
        }
        return Hand.MAIN_HAND;
    }

    /**
     * True when this stack would spend a right-click on this block, or in the air when block is null.
     * A sword on stone returns false, which is what lets the off hand place.
     * An axe returns true only on a log, copper, or a waxed block, matching Item.useOn.
     */
    static boolean itemConsumes(GeyserSession session, GeyserItemStack stack, BlockState block) {
        if (stack.isEmpty()) {
            return false;
        }
        if (block == null) {
            return usesInAir(stack);
        }
        Item item = stack.asItem();
        if (item instanceof BlockItem || item instanceof BoatItem || item instanceof SpawnEggItem) {
            return true;
        }
        String name = item.javaKey().asString();
        String blockName = block.block().javaIdentifier().asString();
        if (stack.is(session, ItemTag.AXES)) {
            return block.block().is(session, BlockTag.LOGS)
                    || blockName.contains("copper")
                    || blockName.contains("waxed");
        }
        if (stack.is(session, ItemTag.HOES)) {
            return blockName.equals("minecraft:grass_block")
                    || blockName.equals("minecraft:dirt")
                    || blockName.equals("minecraft:coarse_dirt")
                    || blockName.equals("minecraft:rooted_dirt");
        }
        if (stack.is(session, ItemTag.SHOVELS)) {
            return block.block().is(session, BlockTag.TURNS_INTO_DIRT_PATH)
                    || block.block().is(session, BlockTag.CAMPFIRES);
        }
        if (name.equals("minecraft:shears")) {
            return shearTarget(blockName);
        }
        if (name.equals("minecraft:bone_meal")) {
            return boneMealTarget(blockName);
        }
        if (name.equals("minecraft:brush")) {
            return blockName.contains("suspicious_");
        }
        if (name.contains("music_disc")) {
            return blockName.equals("minecraft:jukebox");
        }
        if (name.equals("minecraft:book") || name.equals("minecraft:writable_book")
                || name.equals("minecraft:written_book") || name.equals("minecraft:enchanted_book")) {
            return blockName.equals("minecraft:lectern") || blockName.equals("minecraft:chiseled_bookshelf");
        }
        if (name.equals("minecraft:glowstone")) {
            return blockName.equals("minecraft:respawn_anchor");
        }
        if (name.equals("minecraft:glass_bottle")) {
            return blockName.contains("cauldron") || blockName.contains("water");
        }
        if (name.equals("minecraft:honeycomb") || name.contains("ink_sac") || stack.is(session, ItemTag.DYES)) {
            return block.block().is(session, BlockTag.ALL_SIGNS)
                    || blockName.contains("copper")
                    || blockName.contains("sign");
        }
        if (name.contains("bucket") || name.equals("minecraft:flint_and_steel")
                || name.equals("minecraft:fire_charge") || name.equals("minecraft:ender_eye")
                || name.equals("minecraft:end_crystal") || name.equals("minecraft:armor_stand")
                || name.endsWith("_minecart") || name.equals("minecraft:minecart")) {
            return true;
        }
        return usesInAir(stack);
    }

    /**
     * Food, bows, shields, and thrown items spend the click with no block.
     * Name tags, leads, shears, and bone meal do not: those pass in the air so the other hand can run.
     */
    static boolean usesInAir(GeyserItemStack stack) {
        if (stack.isEmpty() || stack.asItem() instanceof BlockItem) {
            return false;
        }
        if (stack.getComponent(DataComponentTypes.CONSUMABLE) != null
                || stack.getComponent(DataComponentTypes.FOOD) != null
                || stack.getComponent(DataComponentTypes.BLOCKS_ATTACKS) != null
                || stack.getComponent(DataComponentTypes.EQUIPPABLE) != null) {
            return true;
        }
        Item item = stack.asItem();
        if (item instanceof FishingRodItem) {
            return true;
        }
        String name = item.javaKey().asString();
        return name.equals("minecraft:bow")
                || name.equals("minecraft:crossbow")
                || name.equals("minecraft:trident")
                || name.equals("minecraft:shield")
                || name.equals("minecraft:spyglass")
                || name.equals("minecraft:goat_horn")
                || name.equals("minecraft:ender_pearl")
                || name.equals("minecraft:snowball")
                || name.equals("minecraft:egg")
                || name.equals("minecraft:wind_charge")
                || name.equals("minecraft:experience_bottle")
                || name.equals("minecraft:ender_eye")
                || name.equals("minecraft:carrot_on_a_stick")
                || name.equals("minecraft:warped_fungus_on_a_stick")
                || name.equals("minecraft:bundle")
                || name.endsWith("_bundle")
                || name.endsWith("_spear");
    }

    /**
     * Items whose living-entity use succeeds: breed, name, leash, dye, saddle, milk.
     * A sword returns false so the off hand can feed.
     */
    public static boolean entityItemConsumes(GeyserSession session, GeyserItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.getComponent(DataComponentTypes.CONSUMABLE) != null
                || stack.getComponent(DataComponentTypes.FOOD) != null
                || stack.asItem() instanceof SpawnEggItem) {
            return true;
        }
        String name = stack.asItem().javaKey().asString();
        return name.equals("minecraft:name_tag")
                || name.equals("minecraft:lead")
                || name.equals("minecraft:shears")
                || name.equals("minecraft:brush")
                || name.equals("minecraft:saddle")
                || name.contains("bucket")
                || name.contains("_armor")
                || name.equals("minecraft:chest")
                || stack.is(session, ItemTag.DYES);
    }

    /** Blocks whose useItemOn looks at the held item before the empty-hand click. */
    private static boolean heldItemBlock(BlockState block) {
        String name = block.block().javaIdentifier().asString();
        return name.equals("minecraft:jukebox")
                || name.equals("minecraft:lectern")
                || name.equals("minecraft:chiseled_bookshelf")
                || name.equals("minecraft:composter")
                || name.equals("minecraft:respawn_anchor")
                || name.equals("minecraft:cake")
                || name.contains("sign")
                || name.contains("campfire");
    }

    /** Pumpkins, hives, and the vines shears actually cut. Stone lets the other hand run. */
    private static boolean shearTarget(String blockName) {
        return blockName.equals("minecraft:pumpkin")
                || blockName.equals("minecraft:carved_pumpkin")
                || blockName.contains("beehive")
                || blockName.contains("bee_nest")
                || blockName.contains("cave_vines")
                || blockName.contains("weeping_vines")
                || blockName.contains("twisting_vines")
                || blockName.contains("kelp");
    }

    /** Growable blocks. Bone meal on stone passes, so an off-hand torch can still place. */
    private static boolean boneMealTarget(String blockName) {
        return blockName.contains("sapling")
                || blockName.contains("grass")
                || blockName.contains("moss")
                || blockName.contains("flower")
                || blockName.contains("nylium")
                || blockName.contains("fungus")
                || blockName.contains("azalea")
                || blockName.contains("propagule")
                || blockName.contains("crop")
                || blockName.contains("stem")
                || blockName.contains("wheat")
                || blockName.contains("carrot")
                || blockName.contains("potato")
                || blockName.contains("beetroot")
                || blockName.contains("torchflower")
                || blockName.contains("pitcher")
                || blockName.contains("nether_wart")
                || blockName.contains("cocoa")
                || blockName.contains("berry")
                || blockName.contains("dripleaf")
                || blockName.contains("seagrass")
                || blockName.contains("kelp")
                || blockName.contains("bamboo")
                || blockName.contains("cactus")
                || blockName.contains("sugar_cane")
                || blockName.contains("rooted_dirt")
                || blockName.contains("petals");
    }
}
