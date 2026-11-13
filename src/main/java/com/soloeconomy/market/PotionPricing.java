package com.soloeconomy.market;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.Potions;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Prices potions the way they are made: by brewing, starting from a bottle of water.
 *
 * <p>Brewing isn't a recipe the catalogue can read, so the brewing stand is asked directly: for
 * every potion already priced and every brewing ingredient, does putting one on the other make
 * something? That covers vanilla's mixes and any a mod adds. One ingredient brews three bottles,
 * so each bottle is its input potion plus a third of the ingredient, plus its share of the blaze
 * powder that fuels the stand.
 *
 * <p>Like any crafted item, a potion is then just the raw materials that went into it: a Potion of
 * Healing is a bottle, some nether wart and a little glistering melon, and selling one moves those
 * prices, not a price of its own.
 */
final class PotionPricing {

    /** One blaze powder fuels twenty brews of up to three bottles each. */
    private static final double FUEL_PER_BOTTLE = 1.0D / 60.0D;
    private static final double BOTTLES_PER_BREW = 3.0D;
    /** A lingering potion tips eight arrows. */
    private static final double ARROWS_PER_POTION = 8.0D;
    private static final int MAX_PASSES = 32;

    private PotionPricing() {
    }

    /**
     * @param items      bundles for ordinary items, the ingredients among them
     * @param primitives base prices, to tell which of two ways to brew a potion is cheaper
     * @return a bundle for every potion, splash potion, lingering potion and tipped arrow that can be brewed
     */
    static Map<Good, MarketCatalog.Bundle> price(@Nullable PotionBrewing brewing,
                                                 Map<Item, MarketCatalog.Bundle> items,
                                                 Map<Item, Double> primitives) {
        Map<Good, MarketCatalog.Bundle> potions = new HashMap<>();
        MarketCatalog.Bundle bottle = items.get(Items.GLASS_BOTTLE);
        if (brewing == null || bottle == null) {
            return potions;
        }
        // Water is free, so a water bottle is just the bottle.
        potions.put(Good.potion(Items.POTION, Potions.WATER), bottle);

        List<Item> reagents = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (items.containsKey(item) && brewing.isIngredient(new ItemStack(item))) {
                reagents.add(item);
            }
        }
        MarketCatalog.Bundle fuel = items.get(Items.BLAZE_POWDER);

        // Keep brewing from everything priced so far until nothing new or cheaper turns up.
        boolean changed = true;
        for (int pass = 0; changed && pass < MAX_PASSES; pass++) {
            changed = false;
            for (Map.Entry<Good, MarketCatalog.Bundle> known : List.copyOf(potions.entrySet())) {
                ItemStack input = known.getKey().stack(1);
                for (Item reagent : reagents) {
                    ItemStack ingredient = new ItemStack(reagent);
                    if (!brewing.hasMix(input, ingredient)) {
                        continue;
                    }
                    Good made = Good.of(brewing.mix(ingredient, input.copy()));
                    if (made == null || made.potion() == null || made.equals(known.getKey())) {
                        continue; // a mod's brew that makes something other than a potion is priced by its own recipes
                    }
                    MarketCatalog.Bundle bundle = brew(known.getValue(), reagent, items, fuel);
                    if (bundle != null && cheaper(bundle, potions.get(made), primitives)) {
                        potions.put(made, bundle);
                        changed = true;
                    }
                }
            }
        }

        MarketCatalog.Bundle arrow = items.get(Items.ARROW);
        if (arrow != null) {
            for (Map.Entry<Good, MarketCatalog.Bundle> lingering : List.copyOf(potions.entrySet())) {
                // Only potions with an effect: water or an awkward potion makes what is just a plain arrow.
                if (lingering.getKey().item() == Items.LINGERING_POTION
                        && !lingering.getKey().potion().value().getEffects().isEmpty()) {
                    Map<Item, Double> contents = new HashMap<>(arrow.contents());
                    add(contents, lingering.getValue(), 1.0D / ARROWS_PER_POTION);
                    potions.put(Good.potion(Items.TIPPED_ARROW, lingering.getKey().potion()), new MarketCatalog.Bundle(
                            Map.copyOf(contents), step(arrow, lingering.getValue())));
                }
            }
        }
        return potions;
    }

    /** One bottle of what brewing {@code reagent} into {@code input} makes, or null if it comes out worthless. */
    @Nullable
    private static MarketCatalog.Bundle brew(MarketCatalog.Bundle input, Item reagent,
                                             Map<Item, MarketCatalog.Bundle> items,
                                             @Nullable MarketCatalog.Bundle fuel) {
        MarketCatalog.Bundle used = items.get(reagent);
        Map<Item, Double> contents = new HashMap<>(input.contents());
        add(contents, used, 1.0D / BOTTLES_PER_BREW);
        // Dragon's breath leaves its bottle behind in the stand.
        ItemStack remainder = new ItemStack(reagent).getCraftingRemainingItem();
        MarketCatalog.Bundle back = remainder.isEmpty() ? null : items.get(remainder.getItem());
        if (back != null) {
            add(contents, back, -remainder.getCount() / BOTTLES_PER_BREW);
        }
        if (fuel != null) {
            add(contents, fuel, FUEL_PER_BOTTLE);
        }
        contents.values().removeIf(quantity -> Math.abs(quantity) < 1.0e-6D);
        return contents.isEmpty() ? null : new MarketCatalog.Bundle(Map.copyOf(contents), step(input, used));
    }

    private static void add(Map<Item, Double> contents, MarketCatalog.Bundle part, double times) {
        part.contents().forEach((primitive, quantity) -> contents.merge(primitive, quantity * times, Double::sum));
    }

    private static int step(MarketCatalog.Bundle a, MarketCatalog.Bundle b) {
        return Math.min(MarketCatalog.MAX_CRAFT_STEPS, Math.max(a.craftSteps(), b.craftSteps()) + 1);
    }

    private static boolean cheaper(MarketCatalog.Bundle candidate, @Nullable MarketCatalog.Bundle current,
                                   Map<Item, Double> primitives) {
        double value = value(candidate, primitives);
        return value > 0.0D && (current == null || value < value(current, primitives) - 1.0e-9D);
    }

    private static double value(MarketCatalog.Bundle bundle, Map<Item, Double> primitives) {
        double total = 0.0D;
        for (Map.Entry<Item, Double> part : bundle.contents().entrySet()) {
            total += part.getValue() * primitives.getOrDefault(part.getKey(), 0.0D);
        }
        return total;
    }
}
