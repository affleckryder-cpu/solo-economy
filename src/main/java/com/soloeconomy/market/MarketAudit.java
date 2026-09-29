package com.soloeconomy.market;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Checks every recipe the server knows for a money loop: buying the inputs, crafting, and selling
 * the result for more than the inputs cost.
 *
 * <p>Bundles make a loop impossible for any item priced through its own cheapest recipe, but two
 * things fall outside that guarantee, and both have shipped broken before:
 * <ul>
 *   <li>a hand-priced primitive that can also be crafted from other hand-priced items has two
 *       independent prices, which can disagree (four string and a slime ball made two leads worth
 *       3.55 emeralds more than they cost);</li>
 *   <li>a recipe cheaper in raw materials than the one an item's bundle was built from.</li>
 * </ul>
 *
 * <p>Evaluated at base prices and at the narrowest spread in the game. Buying an input is costed
 * as buying its primitives and crafting it yourself, since that is always the cheapest way to get
 * it; anything the recipe hands back (empty buckets) is credited at its sell price.
 */
public final class MarketAudit {

    private static final double EPSILON = 1.0e-9D;

    private MarketAudit() {
    }

    public static List<LoopRisk> findLoops(MarketCatalog catalog,
                                           List<MarketCatalog.CraftPath> recipes,
                                           double spread) {
        List<LoopRisk> risks = new ArrayList<>();

        for (MarketCatalog.CraftPath recipe : recipes) {
            if (!catalog.isTradeable(recipe.result())) {
                continue; // no merchant buys the result, so there is nothing to sell into
            }
            MarketCatalog.Bundle output = catalog.bundle(recipe.result());

            double revenue = rawValue(catalog, output) * recipe.resultCount() * (1.0D - spread);
            double cost = 0.0D;
            List<Item> inputs = new ArrayList<>(recipe.ingredients().size());
            boolean buyable = true;

            for (List<Item> choices : recipe.ingredients()) {
                double cheapest = Double.MAX_VALUE;
                Item cheapestItem = null;
                for (Item choice : choices) {
                    if (!catalog.isTradeable(choice)) {
                        continue; // no merchant sells it, so it can't be bought
                    }
                    double net = rawValue(catalog, catalog.bundle(choice)) * (1.0D + spread) - refund(catalog, choice, spread);
                    if (net < cheapest) {
                        cheapest = net;
                        cheapestItem = choice;
                    }
                }
                if (cheapestItem == null) {
                    buyable = false; // an input cannot be bought, so the loop cannot be started
                    break;
                }
                cost += cheapest;
                inputs.add(cheapestItem);
            }

            if (buyable && revenue > cost + EPSILON) {
                risks.add(new LoopRisk(recipe.result(), recipe.resultCount(), List.copyOf(inputs), revenue, cost));
            }
        }

        risks.sort(Comparator.comparingDouble(LoopRisk::profit).reversed());
        return risks;
    }

    private static double rawValue(MarketCatalog catalog, MarketCatalog.Bundle bundle) {
        double total = 0.0D;
        for (Map.Entry<Item, Double> part : bundle.contents().entrySet()) {
            total += part.getValue() * catalog.primitivePrice(part.getKey());
        }
        return Math.max(0.0D, total); // matches what the market actually pays
    }

    private static double refund(MarketCatalog catalog, Item ingredient, double spread) {
        ItemStack remainder = new ItemStack(ingredient).getCraftingRemainingItem();
        if (remainder.isEmpty()) {
            return 0.0D;
        }
        if (!catalog.isTradeable(remainder.getItem())) {
            return 0.0D; // nobody will buy the returned container back
        }
        return rawValue(catalog, catalog.bundle(remainder.getItem())) * remainder.getCount() * (1.0D - spread);
    }

    /** A recipe that pays more to sell than its inputs cost to buy. */
    public record LoopRisk(Item result, double resultCount, List<Item> inputs, double revenue, double cost) {

        public double profit() {
            return revenue - cost;
        }

        public String describe() {
            StringBuilder from = new StringBuilder();
            for (Item input : inputs) {
                if (!from.isEmpty()) {
                    from.append(" + ");
                }
                from.append(BuiltInRegistries.ITEM.getKey(input));
            }
            String count = resultCount % 1 == 0 ? String.valueOf((long) resultCount) : String.format("%.2f", resultCount);
            return String.format("%sx %s from %s: costs %.3f, sells for %.3f (profit %.3f)",
                    count, BuiltInRegistries.ITEM.getKey(result), from, cost, revenue, profit());
        }
    }
}
