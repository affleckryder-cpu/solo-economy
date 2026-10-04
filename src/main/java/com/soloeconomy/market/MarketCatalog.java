package com.soloeconomy.market;

import com.soloeconomy.SoloEconomy;
import com.soloeconomy.config.EconomyConfig;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SingleItemRecipe;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
    private final List<Merchant> merchants;
    private final Set<Item> listed = new HashSet<>();
    private final List<Item> sortedItems;
    private List<MarketAudit.LoopRisk> loopRisks = List.of();
    private List<CraftPath> recipes = List.of();

    private MarketCatalog(Map<Item, Double> primitivePrices, Map<Item, Bundle> bundles, List<Merchant> merchants) {
        this.primitivePrices = primitivePrices;
        this.bundles = bundles;
        this.merchants = merchants;
        merchants.forEach(merchant -> listed.addAll(merchant.items()));
        List<Item> items = new ArrayList<>(listed);
        items.sort((a, b) -> BuiltInRegistries.ITEM.getKey(a).compareTo(BuiltInRegistries.ITEM.getKey(b)));
        this.sortedItems = Collections.unmodifiableList(items);
    }

    public static MarketCatalog empty() {
        return new MarketCatalog(Map.of(), Map.of(), List.of());
    }

    public static MarketCatalog active() {
        return active;
    }

    public static void setActive(MarketCatalog catalog) {
        active = catalog;
    }

    /** Tradeable means some merchant deals in it. Pricing works for more items than that. */
    public boolean isTradeable(Item item) {
        return listed.contains(item);
    }

    public List<Merchant> merchants() {
        return merchants;
    }

    /** The named merchant, or the first one if the id is unknown. Null only with no merchants at all. */
    @Nullable
    public Merchant merchant(String id) {
        for (Merchant merchant : merchants) {
            if (merchant.id().equals(id)) {
                return merchant;
            }
        }
        return merchants.isEmpty() ? null : merchants.get(0);
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
        // Depth follows the unmultiplied price, so priceMultiplier changes prices but not how fast they move.
        double depth = EconomyConfig.INSTANCE.marketDepth.get()
                * Math.pow(price / EconomyConfig.INSTANCE.priceMultiplier.get(),
                        -EconomyConfig.INSTANCE.depthPriceExponent.get());
        return Math.max(MIN_BASE_STOCK, depth);
    }

    public List<Item> tradeableItems() {
        return sortedItems;
    }

    public int primitiveCount() {
        return primitivePrices.size();
    }

    public int size() {
        return listed.size();
    }

    /** Every recipe the catalogue was built from, for re-auditing under other assumptions. */
    public List<CraftPath> recipes() {
        return recipes;
    }

    /** Recipes found to pay more when sold than their inputs cost. Empty in a healthy catalogue. */
    public List<MarketAudit.LoopRisk> loopRisks() {
        return loopRisks;
    }

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    public static MarketCatalog build(Map<Item, Double> seeds,
                                      Set<Item> blocked,
                                      List<MerchantLoader.Definition> merchantDefinitions,
                                      RecipeManager recipeManager,
                                      RegistryAccess registries) {
        Map<Item, Double> primitives = new HashMap<>();
        for (Map.Entry<Item, Double> seed : seeds.entrySet()) {
            if (!blocked.contains(seed.getKey())) {
                primitives.put(seed.getKey(), Math.max(MIN_PRICE, seed.getValue()));
            }
        }

        List<CraftPath> nodes = collectRecipes(recipeManager, registries, blocked);
        MarketCatalog catalog = assemble(primitives, blocked, merchantDefinitions, nodes);

        // A modded machine can make a hand-priced item for less than its price - Create haunts soul
        // sand into quartz - which is a real money machine in that world. Price those items from
        // their recipes instead, until the audit comes back clean.
        for (int round = 0; round < 8 && EconomyConfig.INSTANCE.modRecipes.get(); round++) {
            List<Item> undercut = catalog.loopRisks.stream().map(MarketAudit.LoopRisk::result)
                    .filter(primitives::containsKey).distinct().toList();
            if (undercut.isEmpty()) {
                break;
            }
            for (Item item : undercut) {
                primitives.remove(item);
                SoloEconomy.LOGGER.info("{} is now priced from a modded recipe that makes it for less than its set price",
                        BuiltInRegistries.ITEM.getKey(item));
            }
            catalog = assemble(primitives, blocked, merchantDefinitions, nodes);
        }

        SoloEconomy.LOGGER.info("Market catalog built: {} priced items, {} traded by {} merchants",
                catalog.bundles.size(), catalog.size(), catalog.merchants().size());
        reportLoops(catalog.loopRisks);
        return catalog;
    }

    private static MarketCatalog assemble(Map<Item, Double> primitives, Set<Item> blocked,
                                          List<MerchantLoader.Definition> merchantDefinitions,
                                          List<CraftPath> nodes) {
        // Static prices exist only to decide which option in a tag-based ingredient slot is the
        // cheapest. They never reach a player.
        Map<Item, Double> estimates = new HashMap<>(primitives);
        Map<Item, CraftPath> paths = new HashMap<>();
        relax(estimates, paths, nodes, primitives.keySet());

        Map<Item, Bundle> bundles = new HashMap<>();
        Set<Item> failed = new HashSet<>();
        for (Item item : estimates.keySet()) {
            expand(item, bundles, failed, new HashSet<>(), primitives, paths, estimates,
                    blocked, 0, new boolean[1]);
        }
        bundles.keySet().removeAll(blocked);

        MarketCatalog catalog = new MarketCatalog(new HashMap<>(primitives), bundles,
                resolveMerchants(merchantDefinitions, bundles));
        catalog.recipes = List.copyOf(nodes);
        catalog.loopRisks = MarketAudit.findLoops(catalog, nodes, EconomyConfig.INSTANCE.minimumSpread());
        return catalog;
    }

    /**
     * Turns merchant definitions into item lists: tags expanded, order kept, duplicates dropped.
     * Only priced items can be traded; an explicit id with no price is reported, since that is
     * almost always a mistake in the datapack.
     */
    private static List<Merchant> resolveMerchants(List<MerchantLoader.Definition> definitions,
                                                   Map<Item, Bundle> bundles) {
        List<Merchant> merchants = new ArrayList<>();
        for (MerchantLoader.Definition definition : definitions) {
            Set<Item> items = new LinkedHashSet<>();
            for (String entry : definition.entries()) {
                boolean tag = entry.startsWith("#");
                ResourceLocation id = ResourceLocation.tryParse(tag ? entry.substring(1) : entry);
                if (id == null) {
                    SoloEconomy.LOGGER.warn("Merchant {}: malformed entry '{}'", definition.id(), entry);
                } else if (tag) {
                    BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, id)).ifPresent(set -> set.forEach(
                            holder -> {
                                if (bundles.containsKey(holder.value())) {
                                    items.add(holder.value());
                                }
                            }));
                } else if (BuiltInRegistries.ITEM.containsKey(id) && bundles.containsKey(BuiltInRegistries.ITEM.get(id))) {
                    items.add(BuiltInRegistries.ITEM.get(id));
                } else {
                    SoloEconomy.LOGGER.warn("Merchant {}: '{}' is unknown or has no price, skipping it",
                            definition.id(), entry);
                }
            }
            merchants.add(new Merchant(definition.id(), definition.icon(), List.copyOf(items)));
        }
        if (EconomyConfig.INSTANCE.openMarket.get()) {
            // The Store deals in everything with a price, modded items included.
            List<Item> everything = new ArrayList<>(bundles.keySet());
            everything.sort(Comparator.comparing(BuiltInRegistries.ITEM::getKey));
            merchants.add(new Merchant("general", Items.CHEST, List.copyOf(everything)));
        }
        return List.copyOf(merchants);
    }

    /**
     * Loud on purpose. A loop here is a way to print emeralds, and the usual cause is a datapack
     * giving a hand price to something that can also be crafted - which is easy to do by accident.
     */
    private static void reportLoops(List<MarketAudit.LoopRisk> risks) {
        if (risks.isEmpty()) {
            SoloEconomy.LOGGER.info("Market audit: no recipe can be crafted and sold at a profit");
            return;
        }
        SoloEconomy.LOGGER.error(
                "Market audit found {} recipe(s) that make money from nothing. Players can buy the inputs, "
                        + "craft, and sell at a profit. Usually this means base_prices.json gives a hand price "
                        + "to an item that can also be crafted - remove that entry and let it be priced from "
                        + "its recipe.", risks.size());
        int shown = 0;
        for (MarketAudit.LoopRisk risk : risks) {
            if (shown++ >= 25) {
                SoloEconomy.LOGGER.error("  ...and {} more", risks.size() - 25);
                break;
            }
            SoloEconomy.LOGGER.error("  {}", risk.describe());
        }
    }

    /**
     * Bellman-Ford style relaxation to get a rough price for everything, and with it the cheapest
     * recipe for each item. Only the recipe choice survives into the live catalogue.
     *
     * <p>Paths are compared on raw material cost alone, with no assembly fee. Selling pays raw
     * value, so the bundle has to be the raw-cheapest way to make an item: pick a recipe that is
     * cheaper only because it has fewer crafting steps, and a deeper recipe with cheaper materials
     * becomes a way to craft the item for less than it sells for.
     */
    private static void relax(Map<Item, Double> estimates,
                              Map<Item, CraftPath> paths,
                              List<CraftPath> nodes,
                              Set<Item> pinned) {
        boolean derive = EconomyConfig.INSTANCE.deriveUnpricedItems.get();
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
                            best = Math.min(best, price - remainderEstimate(choice, estimates));
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
                double price = Math.max(MIN_PRICE, unitCost);
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

    /** Estimated value of the container an ingredient leaves behind in the crafting grid. */
    private static double remainderEstimate(Item ingredient, Map<Item, Double> estimates) {
        ItemStack remainder = new ItemStack(ingredient).getCraftingRemainingItem();
        if (remainder.isEmpty()) {
            return 0.0D;
        }
        return estimates.getOrDefault(remainder.getItem(), 0.0D) * remainder.getCount();
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

        double divisor = path.resultCount(); // always > 0: zero-output recipes are never collected
        // Quantities can be negative. Cake uses three milk buckets and hands the buckets back, but
        // a milk bucket is a hand-priced primitive with no iron inside it, so the returned buckets
        // come out as minus nine raw iron. Dropping that - as this code once did - prices cake as
        // though the buckets were destroyed, and crafting cake from bought milk made 7.60 profit.
        Map<Item, Double> normalised = new HashMap<>();
        double baseValue = 0.0D;
        for (Map.Entry<Item, Double> entry : contents.entrySet()) {
            double qty = entry.getValue() / divisor;
            if (Math.abs(qty) > 1.0e-6D) {
                normalised.put(entry.getKey(), qty);
                baseValue += qty * primitives.getOrDefault(entry.getKey(), 0.0D);
            }
        }
        if (normalised.isEmpty() || baseValue <= 0.0D) {
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
                                                  RegistryAccess registries,
                                                  Set<Item> blocked) {
        List<CraftPath> nodes = new ArrayList<>();

        for (Recipe<?> recipe : recipeManager.getRecipes()) {
            if (!reads(recipe)) {
                continue;
            }
            boolean modded = !(recipe instanceof CraftingRecipe || recipe instanceof AbstractCookingRecipe
                    || recipe instanceof SingleItemRecipe);

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

            double count = modded ? expectedCount(recipe, resultItem, result.getCount()) : result.getCount();
            if (count > 0.0D) {
                nodes.add(new CraftPath(resultItem, count, List.copyOf(choices), modded));
            }
        }

        return nodes;
    }

    /**
     * How many of {@code item} a modded recipe makes on average. Create-style recipes list outputs
     * with a chance (crushing gravel gives flint 25% of the time); counting those as certain would
     * price flint at a quarter of what it costs. Read by method name so Create isn't a dependency.
     */
    private static double expectedCount(Recipe<?> recipe, Item item, int fallback) {
        try {
            double total = 0.0D;
            for (Object output : (List<?>) recipe.getClass().getMethod("getRollableResults").invoke(recipe)) {
                ItemStack stack = (ItemStack) output.getClass().getMethod("getStack").invoke(output);
                if (stack.is(item)) {
                    total += stack.getCount() * ((Number) output.getClass().getMethod("getChance").invoke(output)).doubleValue();
                }
            }
            return total;
        } catch (ReflectiveOperationException | ClassCastException e) {
            return fallback; // not a chance-based recipe
        }
    }

    /**
     * Crafting, smelting and stonecutting always. Any other recipe - Create machines, modded
     * workbenches - only with modRecipes on, since all we can see is its ingredient list and main
     * result: fluids, per-slot counts, chance byproducts and tools that aren't used up are
     * invisible, and the last two can overprice an item beyond what the audit can check.
     */
    static boolean reads(Recipe<?> recipe) {
        if (recipe instanceof CraftingRecipe || recipe instanceof AbstractCookingRecipe
                || recipe instanceof SingleItemRecipe) {
            return true;
        }
        // Not filtered on isSpecial(): Create marks every machine recipe special to keep it out of
        // the recipe book. Truly dynamic recipes are skipped anyway - they list no ingredients.
        return EconomyConfig.INSTANCE.modRecipes.get();
    }

    /**
     * What one unit of an item is worth in raw materials.
     *
     * @param contents   primitive item to quantity needed per unit; a primitive maps to itself at 1.
     *                   A quantity is negative when the recipe returns a container that the
     *                   ingredient's own bundle does not contain, like the buckets from cake.
     * @param craftSteps how deep the crafting chain was, which sets the assembly fee on buying
     */
    public record Bundle(Map<Item, Double> contents, int craftSteps) {
    }

    /** Someone with a reason to trade: an id for its lang keys, an icon, and its goods in display order. */
    public record Merchant(String id, Item icon, List<Item> items) {
    }

    /** One recipe, reduced to what pricing cares about. {@code modded}: read only because of modRecipes. */
    public record CraftPath(Item result, double resultCount, List<List<Item>> ingredients, boolean modded) {
    }
}
