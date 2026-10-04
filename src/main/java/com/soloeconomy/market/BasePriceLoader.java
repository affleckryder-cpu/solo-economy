package com.soloeconomy.market;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.soloeconomy.SoloEconomy;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads {@code data/soloeconomy/base_prices.json} out of every loaded datapack and merges them.
 *
 * <p>Only primitives belong in that file - anything with a recipe gets priced by
 * {@link MarketCatalog} instead. Datapacks stack in load order, so a pack can retune a single
 * price without restating the whole book, or set {@code "replace": true} to start from scratch.
 */
public class BasePriceLoader extends SimplePreparableReloadListener<BasePriceLoader.Parsed> {

    public static final ResourceLocation FILE =
            new ResourceLocation(SoloEconomy.MOD_ID, "base_prices.json");

    private static final Gson GSON = new Gson();

    private static Parsed current = new Parsed(Map.of(), Set.of());

    public static Parsed current() {
        return current;
    }

    @Override
    protected Parsed prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<Item, Double> prices = new LinkedHashMap<>();
        Set<Item> untradeable = new LinkedHashSet<>();

        List<Resource> stack;
        try {
            stack = resourceManager.getResourceStack(FILE);
        } catch (Exception e) {
            SoloEconomy.LOGGER.error("Could not list {}", FILE, e);
            return new Parsed(Map.of(), Set.of());
        }

        for (Resource resource : stack) {
            try (BufferedReader reader = resource.openAsReader()) {
                JsonObject root = GsonHelper.fromJson(GSON, reader, JsonObject.class);
                if (root == null) {
                    continue;
                }

                if (GsonHelper.getAsBoolean(root, "replace", false)) {
                    prices.clear();
                    untradeable.clear();
                }

                if (root.has("prices")) {
                    JsonObject priceObject = GsonHelper.getAsJsonObject(root, "prices");
                    for (Map.Entry<String, JsonElement> entry : priceObject.entrySet()) {
                        Item item = lookup(entry.getKey());
                        if (item == null) {
                            continue;
                        }
                        double value = entry.getValue().getAsDouble();
                        if (value <= 0.0D || !Double.isFinite(value)) {
                            SoloEconomy.LOGGER.warn("Ignoring non-positive base price for {}", entry.getKey());
                            continue;
                        }
                        prices.put(item, value);
                    }
                }

                if (root.has("untradeable")) {
                    for (JsonElement element : GsonHelper.getAsJsonArray(root, "untradeable")) {
                        Item item = lookup(element.getAsString());
                        if (item != null) {
                            untradeable.add(item);
                        }
                    }
                }
            } catch (IOException | RuntimeException e) {
                SoloEconomy.LOGGER.error("Failed to read {} from pack {}", FILE, resource.sourcePackId(), e);
            }
        }

        return new Parsed(new HashMap<>(prices), new HashSet<>(untradeable));
    }

    @Override
    protected void apply(Parsed parsed, ResourceManager resourceManager, ProfilerFiller profiler) {
        current = parsed;
        SoloEconomy.LOGGER.info("Loaded {} seed prices and {} untradeable entries",
                parsed.prices().size(), parsed.untradeable().size());
    }

    /** Resolves an item id, quietly skipping ids from mods that are not installed. */
    private static Item lookup(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) {
            SoloEconomy.LOGGER.warn("Malformed item id in base_prices.json: {}", id);
            return null;
        }
        if (!BuiltInRegistries.ITEM.containsKey(key)) {
            return null;
        }
        return BuiltInRegistries.ITEM.get(key);
    }

    public record Parsed(Map<Item, Double> prices, Set<Item> untradeable) {
    }
}
