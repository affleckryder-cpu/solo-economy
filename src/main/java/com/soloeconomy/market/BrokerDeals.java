package com.soloeconomy.market;

import com.soloeconomy.config.EconomyConfig;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * A stall with a Broker gets a few fee-free goods each day: some "on offer" (no fee to buy) and
 * some "wanted" (no fee when you sell).
 *
 * <p>Fee-free is as far as a deal can go. A real discount would put the buy price under the sell
 * price, and then buying a thing and selling it straight back prints emeralds. At zero spread a
 * round trip still loses, because each unit is priced after its own effect on stock.
 *
 * <p>Nothing is saved: the day's deals are picked from the world seed and the day number.
 */
public final class BrokerDeals {

    public static final String MERCHANT_ID = "deals";
    public static final byte NONE = 0;
    public static final byte ON_OFFER = 1;
    public static final byte WANTED = 2;

    private static final long TICKS_PER_DAY = 24000L;

    private static MarketCatalog pickedFor;
    private static long pickedDay = Long.MIN_VALUE;
    private static int pickedCount = -1;
    private static List<Item> onOffer = List.of();
    private static List<Item> wanted = List.of();

    private BrokerDeals() {
    }

    private static void refresh(MinecraftServer server) {
        long day = server.overworld().getGameTime() / TICKS_PER_DAY;
        MarketCatalog catalog = MarketCatalog.active();
        int count = EconomyConfig.INSTANCE.brokerDeals.get();
        if (catalog == pickedFor && day == pickedDay && count == pickedCount) {
            return;
        }
        pickedFor = catalog;
        pickedDay = day;
        pickedCount = count;

        List<Item> pool = new ArrayList<>(catalog.tradeableItems());
        Collections.shuffle(pool, new Random(server.overworld().getSeed() * 31L + day));
        count = Math.min(count, pool.size() / 2);
        onOffer = List.copyOf(pool.subList(0, count));
        wanted = List.copyOf(pool.subList(count, 2 * count));
    }

    /** Today's deals, goods on offer first. The same item is never both. */
    public static List<Item> items(MinecraftServer server) {
        refresh(server);
        List<Item> all = new ArrayList<>(onOffer);
        all.addAll(wanted);
        return all;
    }

    public static byte dealFor(MinecraftServer server, Item item, boolean staffed) {
        if (!staffed) {
            return NONE;
        }
        refresh(server);
        return onOffer.contains(item) ? ON_OFFER : wanted.contains(item) ? WANTED : NONE;
    }

    /** The half-spread for one side of a trade at a stall: nothing on a deal, the usual fee otherwise. */
    public static double spread(MinecraftServer server, Item item, boolean buying, boolean staffed) {
        byte deal = dealFor(server, item, staffed);
        if ((buying && deal == ON_OFFER) || (!buying && deal == WANTED)) {
            return 0.0D;
        }
        return EconomyConfig.INSTANCE.effectiveSpread(staffed);
    }
}
