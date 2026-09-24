package com.soloeconomy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.soloeconomy.SoloEconomy;
import com.soloeconomy.config.EconomyConfig;
import com.soloeconomy.event.ServerEvents;
import com.soloeconomy.market.MarketAudit;
import com.soloeconomy.market.MarketCatalog;
import com.soloeconomy.market.MarketData;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code /soloeconomy price <item> [set <emeralds> | reset]}: price changes without writing a
 * datapack. Changes live in the server config's {@code priceOverrides} list.
 */
public final class PriceCommand {

    private PriceCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(Commands.literal(SoloEconomy.MOD_ID)
                .then(Commands.literal("price")
                        .then(Commands.argument("item", ItemArgument.item(context))
                                .executes(PriceCommand::show)
                                .then(Commands.literal("set")
                                        .requires(source -> source.hasPermission(2))
                                        .then(Commands.argument("emeralds", DoubleArgumentType.doubleArg(0.001D, 1_000_000D))
                                                .executes(PriceCommand::set)))
                                .then(Commands.literal("reset")
                                        .requires(source -> source.hasPermission(2))
                                        .executes(PriceCommand::reset)))));
    }

    /** The config list as items and prices. Malformed or unknown entries are skipped. */
    public static Map<Item, Double> overrides() {
        Map<Item, Double> prices = new HashMap<>();
        for (String entry : EconomyConfig.INSTANCE.priceOverrides.get()) {
            int split = entry.lastIndexOf('=');
            ResourceLocation id = ResourceLocation.tryParse(entry.substring(0, split).trim());
            try {
                double price = Double.parseDouble(entry.substring(split + 1).trim());
                if (id != null && BuiltInRegistries.ITEM.containsKey(id) && price > 0.0D && Double.isFinite(price)) {
                    prices.put(BuiltInRegistries.ITEM.get(id), price);
                    continue;
                }
            } catch (NumberFormatException ignored) {
                // reported below
            }
            SoloEconomy.LOGGER.warn("Ignoring price override '{}': expected item_id=emeralds", entry);
        }
        return prices;
    }

    private static int show(CommandContext<CommandSourceStack> ctx) {
        Item item = ItemArgument.getItem(ctx, "item").getItem();
        MarketCatalog catalog = MarketCatalog.active();
        Component name = item.getDescription();
        CommandSourceStack source = ctx.getSource();

        if (overrides().containsKey(item)) {
            source.sendSuccess(() -> Component.translatable("command.soloeconomy.price.override", name,
                    format(overrides().get(item))), false);
        } else if (catalog.isPrimitive(item)) {
            source.sendSuccess(() -> Component.translatable("command.soloeconomy.price.base", name,
                    format(catalog.primitivePrice(item))), false);
        } else if (catalog.bundle(item) != null) {
            source.sendSuccess(() -> Component.translatable("command.soloeconomy.price.derived", name), false);
        } else {
            source.sendSuccess(() -> Component.translatable("command.soloeconomy.price.none", name), false);
            return 0;
        }

        printNow(source, item);
        if (!catalog.isTradeable(item)) {
            source.sendSuccess(() -> Component.translatable("command.soloeconomy.price.unlisted"), false);
        }
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> ctx) {
        Item item = ItemArgument.getItem(ctx, "item").getItem();
        double price = DoubleArgumentType.getDouble(ctx, "emeralds");
        boolean wasDerived = !MarketCatalog.active().isPrimitive(item) && MarketCatalog.active().bundle(item) != null;

        save(item, BuiltInRegistries.ITEM.getKey(item) + "=" + price);
        ServerEvents.rebuildCatalog(ctx.getSource().getServer());
        // Old trading history would drag the new price straight back down (or up); start it fresh.
        MarketData.get(ctx.getSource().getServer()).resetStock(item);

        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.translatable("command.soloeconomy.price.set", item.getDescription(),
                format(price)), true);
        if (wasDerived) {
            source.sendSuccess(() -> Component.translatable("command.soloeconomy.price.detached"), false);
        }
        printNow(source, item);
        warnAboutLoops(source);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) {
        Item item = ItemArgument.getItem(ctx, "item").getItem();
        CommandSourceStack source = ctx.getSource();
        if (!overrides().containsKey(item)) {
            source.sendFailure(Component.translatable("command.soloeconomy.price.not_set", item.getDescription()));
            return 0;
        }
        save(item, null);
        ServerEvents.rebuildCatalog(source.getServer());
        MarketData.get(source.getServer()).resetStock(item);
        source.sendSuccess(() -> Component.translatable("command.soloeconomy.price.reset", item.getDescription()), true);
        printNow(source, item);
        warnAboutLoops(source);
        return 1;
    }

    /** Replaces this item's entry in the config list, or drops it when {@code entry} is null. */
    private static void save(Item item, String entry) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
        List<String> list = new ArrayList<>();
        for (String existing : EconomyConfig.INSTANCE.priceOverrides.get()) {
            // Parsed, so a hand-written "diamond=20" matches minecraft:diamond too.
            if (!key.equals(ResourceLocation.tryParse(existing.substring(0, existing.lastIndexOf('=')).trim()))) {
                list.add(existing);
            }
        }
        if (entry != null) {
            list.add(entry);
        }
        EconomyConfig.INSTANCE.priceOverrides.set(list);
        EconomyConfig.INSTANCE.priceOverrides.save();
    }

    /** What the market pays and charges for one right now: spread and supply included. */
    private static void printNow(CommandSourceStack source, Item item) {
        MarketData data = MarketData.get(source.getServer());
        long time = source.getLevel().getGameTime();
        double spread = EconomyConfig.INSTANCE.effectiveSpread(false);
        source.sendSuccess(() -> Component.translatable("command.soloeconomy.price.now",
                format(data.spotSellPrice(item, time, spread)), format(data.spotBuyPrice(item, time, spread))), false);
    }

    /** The rebuild already ran the audit, and a clean market has no loops, so any found are news. */
    private static void warnAboutLoops(CommandSourceStack source) {
        List<MarketAudit.LoopRisk> risks = MarketCatalog.active().loopRisks();
        for (MarketAudit.LoopRisk risk : risks.subList(0, Math.min(3, risks.size()))) {
            source.sendFailure(Component.translatable("command.soloeconomy.price.loop", risk.describe()));
        }
    }

    private static String format(double price) {
        return price >= 10.0D ? String.format("%.2f", price) : String.format("%.3f", price);
    }
}
