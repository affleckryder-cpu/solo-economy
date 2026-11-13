package com.soloeconomy.market;

import com.soloeconomy.config.EconomyConfig;

import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Stock certificates turn up in loot chests. Using one unlocks a rare good at the market: one of
 * the most valuable things the player hasn't found yet. With nothing left to unlock (or discovery
 * turned off) it is cashed in for emeralds instead.
 */
public final class StockCertificates {

    /** The payout when there is nothing to unlock, in emeralds before priceMultiplier. */
    private static final double CASH_VALUE = 10.0D;

    private StockCertificates() {
    }

    /** @return the good unlocked, or null if the certificate was cashed in */
    @Nullable
    public static Good redeem(Player player) {
        MarketCatalog catalog = MarketCatalog.active();
        List<Good> locked = new ArrayList<>();
        for (Good good : catalog.goods()) {
            if (!Discovery.canBuy(player, good)) {
                locked.add(good);
            }
        }
        if (locked.isEmpty()) {
            EconomyAccount.deposit(player, cashValue());
            return null;
        }
        locked.sort(Comparator.comparingDouble(catalog::baseValue).reversed());
        // Any of the most valuable quarter: always worth having, not always the same thing.
        Good good = locked.get(player.getRandom().nextInt((locked.size() + 3) / 4));
        Discovery.discover(player, good);
        return good;
    }

    public static long cashValue() {
        return Math.round(CASH_VALUE * EconomyConfig.INSTANCE.priceMultiplier.get() * EconomyAccount.CENTS_PER_EMERALD);
    }
}
