# Solo Economy 0.4.1 for Forge 1.20.1

**Minecraft 1.20.1 · Forge 47 or newer · Java 17**

## Netherite gear

Netherite tools and armour can be traded now. The **Smith** deals in all nine pieces, plus the Netherite Upgrade template you need to make them.

A netherite sword is priced as what goes into it: the diamond sword, a netherite ingot, and the template the smithing table uses up. Like everything else, you have to find one yourself before you can buy it.

## Fixes

- **Sugar could be mispriced with Create installed.** With `modRecipes` on, the market could decide the cheapest way to make sugar was from honey bottles, price it at three times what sugar cane costs, and let you craft cane into sugar for a profit. The price search behind this is fixed, and it's fixed for every item that hands back a bottle or a bucket, not only sugar.
- **Recipes that use fluids are now left out** when reading other mods' recipes. Fluids have no price, so a Create recipe that fills a bottle with honey used to price a honey bottle like an empty bottle. With `openMarket` on as well, that could leave sugar and glass bottles with no price at all.
- With `modRecipes` on, an item a modded machine makes cheaply is now repriced even when the profit would only show up with no fee, so a Broker deal can't open a gap.

## Create

Tested with Create 6.0.8 on both Minecraft versions now, with `modRecipes` and `openMarket` on: every priced item trades and no recipe can be crafted and sold at a profit.

---

# Solo Economy 0.4.0 for Forge 1.20.1

**Minecraft 1.20.1 · Forge 47 or newer · Java 17**

## Broker deals

A stall with a Broker now has deals that change every in-game day:

- **On offer:** three goods you can buy with no fee, even if you haven't discovered them yet.
- **Wanted:** three goods the Broker takes off you with no fee.

Open a stall that has a Broker and pick **Deals**, in gold at the top of the list, to see today's. Anywhere else in the market, a gold pip marks the price that's fee-free. Stalls without a Broker don't get deals, so hiring one is worth more than the fee cut alone.

Deals are fee-free rather than discounted on purpose: a price below what the market pays would let you buy something and sell it straight back for a profit.

Pack makers: `brokerDeals` in the config sets how many of each there are per day. 0 turns them off.

---

# Solo Economy 0.3.5 for Forge 1.20.1

**Minecraft 1.20.1 · Forge 47 or newer · Java 17**

## New art

- **The Market Stall is an actual stall now.** A wooden counter under a green-and-cream striped awning, with a ledger, a few coins and an emerald on top, instead of a plain crate.
- **The Broker dresses the part:** a green waistcoat with gold buttons, a belt with a coin pouch, and a green cap.

Stalls you've already placed update on their own.

---

# Solo Economy 0.3.4 for Forge 1.20.1

**Minecraft 1.20.1 · Forge 47 or newer · Java 17**

First release for Forge 1.20.1. It has everything the NeoForge 1.21.1 version has: the eleven merchants, the market screen with its inventory panel, the emerald account, discovery, the Broker villager, `/soloeconomy price`, and the `openMarket`, `modRecipes` and `priceMultiplier` options.

## Different from the 1.21.1 version

- **The config file is per world:** `saves/<world>/serverconfig/soloeconomy-server.toml`. To use the same settings for every new world, put a copy in the `defaultconfigs` folder.
- Items that only exist in 1.21 (breeze rods, wind charges, the heavy core) aren't traded.

## Fixed

- Items you sell or deposit now disappear from the inventory panel straight away. Before, they stayed visible until you closed the market, even though they were already gone.

## Known limitations

- `modRecipes` was tested with Create on 1.21.1, not on 1.20.1. It reads recipes the same way, so it should work, but let me know if a price looks wrong.
