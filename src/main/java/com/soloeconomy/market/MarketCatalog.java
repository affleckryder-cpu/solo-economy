package com.soloeconomy.market;

import com.soloeconomy.SoloEconomy;
import com.soloeconomy.config.EconomyConfig;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SingleItemRecipe;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The price book for a running server.
 *
 * <p>Only <b>primitives</b> - the things you dig, farm, kill or loot for, seeded by hand in
 * {@code base_prices.json} - have a price of their own. Every other item is stored as a
 * {@link Bundle}: the sack of primitives you would have to gather to make one, worked out by
 * expanding its cheapest recipe all the way down.
 *
 * <p>That is the whole trick. A diamond block is not an item with its own price that happens to
 * be near nine diamonds; it <em>is</em> nine diamonds as far as the market is concerned, and it
 * cannot drift away from them however hard the market is pushed. Giving every item its own
 * independent stock and its own price floor is what let a floored diamond block be bought for 53
 * and uncrafted into 252 emeralds of diamonds.
 *
 * <p>Prices follow from the bundle:
 * <pre>
 *   raw(x)  = sum over the bundle of quantity * current primitive price
 *   buy(x)  = raw(x) * craftMarkup^steps * (1 + spread)   - materials plus an assembly fee
 *   sell(x) = raw(x) * (1 - spread)                       - materials only
 * </pre>
 *
 * <p>Every round trip therefore loses the spread, no matter which direction you go or how many
 * crafting steps you route through, and no configuration can open a loop.
 */
public final class MarketCatalog {

    /** Prices below this would make the stock curve explode, so everything is floored here. */
    public static final double MIN_PRICE = 0.01D;
    /** No market is thinner than this, or a handful of trades would swing it end to end. */
    private static final double MIN_BASE_STOCK = 16.0D;
    /** Tag-based ingredients like #logs can list hundreds of items; only scan this many. */
    private static final int MAX_INGREDIENT_CHOICES = 64;
    private static final int MAX_RELAXATION_PASSES = 32;
    /** Guards against pathological recipe chains while expanding a bundle. */
    private static final int MAX_EXPANSION_DEPTH = 16;
    /** Assembly fees stop compounding here, so a deep chain cannot balloon into absurdity. */
    private static final int MAX_CRAFT_STEPS = 6;

    private static MarketCatalog active = empty();

    private final Map<Item, Double> primitivePrices;
    private final Map<Item, Bundle> bundles;
    private final List<Item> sortedItems;

    private MarketCatalog(Map<Item, Double> primitivePrices, Map<Item, Bundle> bundles) {
        this.primitivePrices = primitivePrices;
        this.bundles = bundles;
        List<Item> items = new ArrayList<>(bundles.keySet());
        items.sort((a, b) -> BuiltInRegistries.ITEM.getKey(a).compareTo(BuiltInRegistries.ITEM.getKey(b)));
        this.sortedItems = Collections.unmodifiableList(items);
    }

    public static MarketCatalog empty() {
        return new MarketCatalog(Map.of(), Map.of());
    }

    public static MarketCatalog active() {
        return active;
    }

    public static void setActive(MarketCatalog catalog) {
        active = catalog;
    }

    /** Tradeable means we know what it is made of, which for a primitive is itself. */
    public boolean isTradeable(Item item) {
        return bundles.containsKey(item);
    }

    public boolean isPrimitive(Item item) {
        return primitivePrices.containsKey(item);
    }

    /** What one unit of this item is made of. Null if the market will not handle it. */
    @Nullable
    public Bundle bundle(Item item) {
        return bundles.get(item);
    }

    /** Equilibrium price of a primitive, in emeralds. Zero for anything else. */
    public double primitivePrice(Item item) {
        return primitivePrices.getOrDefault(item, 0.0D);
    }

    /**
     * Equilibrium stock of a primitive, in item units.
     *
     * <p>Cheap goods get deeper markets than expensive ones, but only sub-linearly. Scaling depth
     * as a flat emerald value (depth / price) sounds reasonable and is not: it hands cobblestone a
     * 40,000-unit market that a player cannot shift in a hundred hours, so the economy looks
     * frozen.
     */
    public double baseStock(Item item) {
        double price = primitivePrice(item);
        if (price <= 0.0D) {
            return MIN_BASE_STOCK;
        }
        double depth = EconomyConfig.INSTANCE.marketDepth.get()
                * Math.pow(price, -EconomyConfig.INSTANCE.depthPriceExponent.get());
        return Math.max(MIN_BASE_STOCK, depth);
    }

    public List<Item> tradeableItems() {
        return sortedItems;
    }

    public int primitiveCount() {
        return primitivePrices.size();
    }

    public int size() {
        return bundles.size();
    }

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    public static MarketCatalog build(Map<Item, Double> seeds,
                                      Set<Item> blocked,
                                      RecipeManager recipeManager,
                                      HolderLookup.Provider registries) {
        Map<Item, Double> primitives = new HashMap<>();
        for (Map.Entry<Item, Double> seed : seeds.entrySet()) {
            if (!blocked.contains(seed.getKey())) {
                primitives.put(seed.getKey(), Math.max(MIN_PRICE, seed.getValue()));
            }
        }

        // Static prices exist only to decide which option in a tag-based ingredient slot is the
        // cheapest. They never reach a player.
        Map<Item, Double> estimates = new HashMap<>(primitives);
        Map<Item, CraftPath> paths = new HashMap<>();
        List<CraftPath> nodes = collectRecipes(recipeManager, registries, blocked);
        relax(estimates, paths, nodes, primitives.keySet());

        Map<Item, Bundle> bundles = new HashMap<>();
        Set<Item> failed = new HashSet<>();
        for (Item item : estimates.keySet()) {
            expand(item, bundles, failed, new HashSet<>(), primitives, paths, estimates,
                    blocked, 0, new boolean[1]);
        }

        for (Item item : blocked) {
            bundles.remove(item);
            primitives.remove(item);
        }

        SoloEconomy.LOGGER.info(
                "Market catalog built: {} primitives priced by hand, {} items priced as bundles of them, "
                        + "{} tradeable in total",
                primitives.size(), bundles.size() - primitives.size(), bundles.size());

        return new MarketCatalog(primitives, bundles);
    }

    /**
     * Bellman-Ford style relaxation to get a rough price for everything, and with it the cheapest
     * recipe for each item. Only the recipe choice survives into the live catalogue.
     */
    private static void relax(Map<Item, Double> estimates,
                              Map<Item, CraftPath> paths,
                              List<CraftPath> nodes,
                              Set<Item> pinned) {
        boolean derive = EconomyConfig.INSTANCE.deriveUnpricedItems.get();
        double markup = EconomyConfig.INSTANCE.craftMarkup.get();
        Map<Item, Double> bestPathCost = new HashMap<>();

        for (int pass = 0; pass < MAX_RELAXATION_PASSES; pass++) {
            boolean changed = false;

            for (CraftPath node : nodes) {
                double cost = 0.0D;
                boolean complete = true;

                for (List<Item> choices : node.ingredients()) {
                    double best = Double.MAX_VALUE;
                    for (Item choice : choices) {
                        Double price = estimates.get(choice);
                        if (price != null) {
                            best = Math.min(best, price);
                        }
                    }
                    if (best == Double.MAX_VALUE) {
                        complete = false;
                        break;
                    }
                    cost += best;
                }
                if (!complete) {
                    continue;
                }

                double unitCost = cost / node.resultCount();
                Double bestSoFar = bestPathCost.get(node.result());
                if (bestSoFar == null || unitCost < bestSoFar - 1.0e-9D) {
                    bestPathCost.put(node.result(), unitCost);
                    paths.put(node.result(), node);
                    changed = true;
                }

                if (!derive || pinned.contains(node.result())) {
                    continue;
                }
                double price = Math.max(MIN_PRICE, unitCost * markup);
                Double current = estimates.get(node.result());
                if (current == null || price < current - 1.0e-9D) {
                    estimates.put(node.result(), price);
                    changed = true;
                }
            }

            if (!changed) {
                break;
            }
        }
    }

    /**
     * Expand one item into the primitives it is ultimately made of.
     *
     * <p>Per ingredient slot the cheapest option that can itself be expanded wins. Anything the
     * recipe hands back (empty buckets) is subtracted, so a cake is not priced as though its three
     * milk buckets were destroyed.
     *
     * @param cycleHit set when this subtree bailed out on a cycle. Such a failure depends on which
     *                 item we started from, so it must not be cached: B may be perfectly
     *                 expandable on its own even though A -> B -> A went round in a circle.
     * @return the bundle, or null if this item cannot be reduced to primitives
     */
    @Nullable
    private static Bundle expand(Item item,
                                 Map<Item, Bundle> memo,
                                 Set<Item> failed,
                                 Set<Item> visiting,
                                 Map<Item, Double> primitives,
                                 Map<Item, CraftPath> paths,
                                 Map<Item, Double> estimates,
                                 Set<Item> blocked,
                                 int depth,
                                 boolean[] cycleHit) {
        if (blocked.contains(item) || failed.contains(item)) {
            return null;
        }
        Bundle cached = memo.get(item);
        if (cached != null) {
            return cached;
        }
        if (primitives.containsKey(item)) {
            Bundle self = new Bundle(Map.of(item, 1.0D), 0);
            memo.put(item, self);
            return self;
        }
        // A cycle (ingot -> block -> ingot) or a runaway chain means no honest answer exists.
        if (depth >= MAX_EXPANSION_DEPTH || visiting.contains(item)) {
            cycleHit[0] = true;
            return null;
        }

        CraftPath path = paths.get(item);
        if (path == null) {
            failed.add(item);
            return null;
        }

        visiting.add(item);
        Map<Item, Double> contents = new HashMap<>();
        int steps = 0;
        boolean ok = true;
        boolean[] subCycle = new boolean[1];

        for (List<Item> choices : path.ingredients()) {
            List<Item> ordered = new ArrayList<>(choices);
            ordered.sort(Comparator.comparingDouble(
                    c -> estimates.getOrDefault(c, Double.MAX_VALUE)));

            Bundle chosen = null;
            Item chosenItem = null;
            for (Item choice : ordered) {
                if (!estimates.containsKey(choice)) {
                    continue;
                }
                Bundle sub = expand(choice, memo, failed, visiting, primitives, paths,
                        estimates, blocked, depth + 1, subCycle);
                if (sub != null) {
                    chosen = sub;
                    chosenItem = choice;
                    break;
                }
            }
            if (chosen == null) {
                ok = false;
                break;
            }

            chosen.contents().forEach((prim, qty) -> contents.merge(prim, qty, Double::sum));
            steps = Math.max(steps, chosen.craftSteps());

            ItemStack remainder = new ItemStack(chosenItem).getCraftingRemainingItem();
            if (!remainder.isEmpty()) {
                Bundle back = expand(remainder.getItem(), memo, failed, visiting, primitives,
                        paths, estimates, blocked, depth + 1, subCycle);
                if (back != null) {
                    double count = remainder.getCount();
                    back.contents().forEach((prim, qty) -> contents.merge(prim, -qty * count, Double::sum));
                }
            }
        }

        visiting.remove(item);
        cycleHit[0] |= subCycle[0];
        if (!ok) {
            if (!subCycle[0]) {
                failed.add(item);
            }
            return null;
        }

        double divisor = Math.max(1, path.resultCount());
        Map<Item, Double> normalised = new HashMap<>();
        for (Map.Entry<Item, Double> entry : contents.entrySet()) {
            double qty = entry.getValue() / divisor;
            if (qty > 1.0e-6D) {
                normalised.put(entry.getKey(), qty);
            }
        }
        if (normalised.isEmpty()) {
            if (!subCycle[0]) {
                failed.add(item);
            }
            return null;
        }

        Bundle bundle = new Bundle(Map.copyOf(normalised), Math.min(MAX_CRAFT_STEPS, steps + 1));
        memo.put(item, bundle);
        return bundle;
    }

    private static List<CraftPath> collectRecipes(RecipeManager recipeManager,
                                                  HolderLookup.Provider registries,
                                                  Set<Item> blocked) {
        List<CraftPath> nodes = new ArrayList<>();

        for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
            Recipe<?> recipe = holder.value();
            if (!(recipe instanceof CraftingRecipe)
                    && !(recipe instanceof AbstractCookingRecipe)
                    && !(recipe instanceof SingleItemRecipe)) {
                continue;
            }

            ItemStack result;
            try {
                result = recipe.getResultItem(registries);
            } catch (Exception e) {
                continue; // dynamic recipes can throw without a real crafting container
            }
            if (result == null || result.isEmpty() || result.getCount() <= 0) {
                continue;
            }

            Item resultItem = result.getItem();
            if (resultItem == Items.AIR || blocked.contains(resultItem)) {
                continue;
            }

            List<Ingredient> ingredients = recipe.getIngredients();
            if (ingredients.isEmpty()) {
                continue; // map cloning, firework crafting and friends have no fixed inputs
            }

            List<List<Item>> choices = new ArrayList<>(ingredients.size());
            boolean usable = true;
            for (Ingredient ingredient : ingredients) {
                if (ingredient.isEmpty()) {
                    continue; // empty grid slot
                }
                ItemStack[] options = ingredient.getItems();
                if (options.length == 0) {
                    usable = false;
                    break;
                }
                List<Item> items = new ArrayList<>(Math.min(options.length, MAX_INGREDIENT_CHOICES));
                for (int i = 0; i < options.length && i < MAX_INGREDIENT_CHOICES; i++) {
                    items.add(options[i].getItem());
                }
                choices.add(List.copyOf(items));
            }

            if (!usable || choices.isEmpty()) {
                continue;
            }

            nodes.add(new CraftPath(resultItem, result.getCount(), List.copyOf(choices)));
        }

        return nodes;
    }

    /**
     * What one unit of an item is worth in raw materials.
     *
     * @param contents   primitive item to quantity needed per unit; a primitive maps to itself at 1
     * @param craftSteps how deep the crafting chain was, which sets the assembly fee on buying
     */
    public record Bundle(Map<Item, Double> contents, int craftSteps) {
    }

    /** One recipe, reduced to what pricing cares about. */
    public record CraftPath(Item result, int resultCount, List<List<Item>> ingredients) {
    }
}
