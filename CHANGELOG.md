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
