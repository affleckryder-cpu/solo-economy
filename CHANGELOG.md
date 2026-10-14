# Solo Economy 0.6.1 for Forge 1.20.1

**Minecraft 1.20.1 · Forge 47 or newer · Java 17**

## Russian

Solo Economy now speaks Russian. Pick Русский under Options → Language and the whole mod follows: the market, the bank, every merchant, the commands, stock certificates and the settings page.

A few names were shortened to fit the market screen. If any wording reads oddly, leave a comment with the line and a better one.

---

# Solo Economy 0.6.0 for Forge 1.20.1

**Minecraft 1.20.1 · Forge 47 or newer · Java 17**

## Stock certificates

A new item, found in loot chests: dungeons, mineshafts, temples, shipwreck and buried treasure, bastions, strongholds, mansions, End cities, ancient cities, outposts and ruined portals. About one chest in four has one.

**Use it** (right-click) and it unlocks a rare good at the market: one of the most valuable things you haven't found yet, picked at random from the top quarter. A trident, a heart of the sea, an elytra, that kind of thing. You can buy it from then on, same as if you'd found it yourself.

Exploring now pays off at the market, not just in what's in the chest.

Once there's nothing left for a certificate to unlock (or if `requireDiscovery` is off), it's cashed in for 10 emeralds instead, scaled by `priceMultiplier`. So a certificate is never a dud.

They stack to 16 and show up in the Solo Economy creative tab.

## Balance display settings in game

The balance display can now be moved from inside the game: **Mods → Solo Economy → Config**. Pick a corner (or Hidden) and how far from the edges it sits, and you'll see it move as you change it. Changes save when you press Done.

The settings still live in `config/soloeconomy-client.toml` if you'd rather edit the file.

---

# Solo Economy 0.5.1 for Forge 1.20.1

**Minecraft 1.20.1 · Forge 47 or newer · Java 17**

## Move the balance display

The emerald balance in the corner of the screen can be moved, so it stops covering your minimap.

Open `config/soloeconomy-client.toml` (it appears after you run the game once with this version) and set:

- `corner` to `TOP_RIGHT`, `TOP_LEFT`, `BOTTOM_RIGHT`, `BOTTOM_LEFT`, or `HIDDEN` to turn it off
- `offsetX` and `offsetY` to nudge it away from the edges. About 45 for `offsetY` clears the hotbar in a bottom corner.

Changes apply as soon as you save the file. No restart needed, and it works on servers too, since it's your own display setting.

## Big modpacks

Checked that the price book stays fast on a huge recipe set: 40,000 extra recipes, tangled on purpose, are priced in about three seconds. There's now a test that keeps it that way.

---

# Solo Economy 0.5.0 for Forge 1.20.1

**Minecraft 1.20.1 · Forge 47 or newer · Java 17**

## Fixed: worlds stuck at 100% in modpacks

In a modpack, a world could sit at 100% on the loading screen and never open, most often with `modRecipes = true`.

The cause was recipes that make each other in a circle: ingots to dust and back, ore doubling, items that convert between mods. Working out prices, the mod tried every possible route round those circles, and with enough of them that never finishes. It now settles prices from the raw materials upward and gives up cleanly on a circle it can't resolve, so the same step takes well under a second.

Items caught in a circle with no way in from a priced material stay unpriced, and you can set those with `/soloeconomy price <item> set <emeralds>`.

If a world still hangs or loads slowly, the log now says how long the price book took. Look for the line starting `Market catalog built in` and send it along with your mod list.

## Netherite gear

Netherite tools and armour can be traded now. The **Smith** deals in all nine pieces, plus the Netherite Upgrade template you need to make them.

A netherite sword is priced as what goes into it: the diamond sword, a netherite ingot, and the template the smithing table uses up. Like everything else, you have to find one yourself before you can buy it.

## Better Create support

Playing with Create? Turn on `modRecipes` and `openMarket` in the config and most of Create is now priced for you: **about 575 of its 700 items**, up from about 440.

- **Zinc and brass have prices.** Raw zinc and Create's stones (asurine, crimsite, limestone, ochrum, veridium) come with default prices, and everything made from them follows: zinc ingots, brass, casings, cogwheels and so on.
- **Recipes that use fluids are priced properly.** A fluid is counted as part of a bucket of it, so a recipe that takes 250 mB of honey costs a quarter of a honey bucket's worth of honey. Water is free. Fluids a machine makes, like chocolate, are priced from what went into them. Before, fluids were treated as free, which priced a honey bottle like an empty bottle.
- Recipes that use a fluid with no bucket (potions) are skipped.

What still has no price: things with no recipe to read (weathered copper, creative items, experience nuggets) and items built by sequenced assembly, like the precision mechanism. Set those with `/soloeconomy price <item> set <emeralds>`.

One thing to know: Create can make some vanilla items cheaply (lava from cobblestone, for one). With `modRecipes` on, those items are priced from the machine, so lava is cheap in a Create world. That's on purpose, or the machine would be a money printer.

Tested with Create 6.0.8 on both Minecraft versions, with `modRecipes` and `openMarket` on: no recipe can be crafted and sold at a profit.

## Fixes

- **Sugar could be mispriced with Create installed.** With `modRecipes` on, the market could decide the cheapest way to make sugar was from honey bottles, price it at three times what sugar cane costs, and let you craft cane into sugar for a profit. The price search behind this is fixed, and it's fixed for every item that hands back a bottle or a bucket, not only sugar.
- With `modRecipes` on, an item a modded machine makes cheaply is now repriced even when the profit would only show up with no fee, so a Broker deal can't open a gap.

Nothing changes for vanilla prices if you don't use `modRecipes`.

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
