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
import net.minecraft.world.item.Items;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads {@code data/soloeconomy/merchants.json} from every datapack: who trades what.
 *
 * <p>Entries stay as raw strings here because {@code #tags} can only be resolved once tags are
 * bound, which is after reload listeners run. {@link MarketCatalog} resolves them.
 */
public class MerchantLoader extends SimplePreparableReloadListener<List<MerchantLoader.Definition>> {

    public static final ResourceLocation FILE =
            ResourceLocation.fromNamespaceAndPath(SoloEconomy.MOD_ID, "merchants.json");

    private static final Gson GSON = new Gson();

    private static List<Definition> current = List.of();

    public static List<Definition> current() {
        return current;
    }

    @Override
    protected List<Definition> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<String, Definition> merchants = new LinkedHashMap<>();

        for (Resource resource : resourceManager.getResourceStack(FILE)) {
            try (BufferedReader reader = resource.openAsReader()) {
                JsonObject root = GsonHelper.fromJson(GSON, reader, JsonObject.class);
                if (GsonHelper.getAsBoolean(root, "replace", false)) {
                    merchants.clear();
                }
                for (JsonElement element : GsonHelper.getAsJsonArray(root, "merchants")) {
                    JsonObject merchant = element.getAsJsonObject();
                    String id = GsonHelper.getAsString(merchant, "id");
                    List<String> entries = new ArrayList<>();
                    GsonHelper.getAsJsonArray(merchant, "items").forEach(e -> entries.add(e.getAsString()));
                    // A replaced merchant keeps its original place in the menu.
                    merchants.put(id, new Definition(id, icon(GsonHelper.getAsString(merchant, "icon", "")), entries));
                }
            } catch (IOException | RuntimeException e) {
                SoloEconomy.LOGGER.error("Failed to read {} from pack {}", FILE, resource.sourcePackId(), e);
            }
        }
        return List.copyOf(merchants.values());
    }

    @Override
    protected void apply(List<Definition> definitions, ResourceManager resourceManager, ProfilerFiller profiler) {
        current = definitions;
    }

    private static Item icon(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key != null && BuiltInRegistries.ITEM.containsKey(key) ? BuiltInRegistries.ITEM.get(key) : Items.CHEST;
    }

    /** A merchant as written: id, icon, and item ids or {@code #tags} in display order. */
    public record Definition(String id, Item icon, List<String> entries) {
    }
}
