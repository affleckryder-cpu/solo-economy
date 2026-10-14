# Solo Economy 0.6.0

**Minecraft 1.21.1 · NeoForge (built on 21.1.209) · Java 21**

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

# Solo Economy 0.5.1

**Minecraft 1.21.1 · NeoForge (built on 21.1.209) · Java 21**

## Move the balance display

The emerald balance in the corner of the screen can be moved, so it stops covering your minimap.

Open `config/soloeconomy-client.toml` (it appears after you run the game once with this version) and set:

- `corner` to `TOP_RIGHT`, `TOP_LEFT`, `BOTTOM_RIGHT`, `BOTTOM_LEFT`, or `HIDDEN` to turn it off
- `offsetX` and `offsetY` to nudge it away from the edges. About 45 for `offsetY` clears the hotbar in a bottom corner.

Changes apply as soon as you save the file. No restart needed, and it works on servers too, since it's your own display setting.

## Big modpacks

Checked that the price book stays fast on a huge recipe set: 40,000 extra recipes, tangled on purpose, are priced in about three seconds. There's now a test that keeps it that way.

---

# Solo Economy 0.5.0

**Minecraft 1.21.1 · NeoForge (built on 21.1.209) · Java 21**

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

# Solo Economy 0.4.0

**Minecraft 1.21.1 · NeoForge (built on 21.1.209) · Java 21**

## Broker deals

A stall with a Broker now has deals that change every in-game day:

- **On offer:** three goods you can buy with no fee, even if you haven't discovered them yet.
- **Wanted:** three goods the Broker takes off you with no fee.

Open a stall that has a Broker and pick **Deals**, in gold at the top of the list, to see today's. Anywhere else in the market, a gold pip marks the price that's fee-free. Stalls without a Broker don't get deals, so hiring one is worth more than the fee cut alone.

Deals are fee-free rather than discounted on purpose: a price below what the market pays would let you buy something and sell it straight back for a profit.

Pack makers: `brokerDeals` in the config sets how many of each there are per day. 0 turns them off.

---

# Solo Economy 0.3.5

**Minecraft 1.21.1 · NeoForge (built on 21.1.209) · Java 21**

## New art

- **The Market Stall is an actual stall now.** A wooden counter under a green-and-cream striped awning, with a ledger, a few coins and an emerald on top, instead of a plain crate.
- **The Broker dresses the part:** a green waistcoat with gold buttons, a belt with a coin pouch, and a green cap.

Stalls you've already placed update on their own.

---

# Solo Economy 0.3.4

**Minecraft 1.21.1 · NeoForge (built on 21.1.209) · Java 21**

Now also available for **Forge 1.20.1**.

## Fixed

- Items you sell or deposit now disappear from the inventory panel straight away. Before, they stayed visible until you closed the market, even though they were already gone.

---

# Solo Economy 0.3.3

**Minecraft 1.21.1 · NeoForge (built on 21.1.209) · Java 21**

## Added

- **Price items from other mods' recipes.** Set `modRecipes = true` in `config/soloeconomy-server.toml` and the market prices items from any mod's recipes, such as Create's machines and modded workbenches, by adding up their ingredients. Tested with Create 6: 430 of its 699 items get a price.
  - Outputs with a chance, like Create's crushing byproducts, are priced by how many you get on average.
  - If a modded machine makes a vanilla item for less than its normal price (Create can turn soul sand into quartz), that item is priced from the machine recipe instead, so it can't be used to make money from nothing.
  - Best effort: fluids aren't counted, and a recipe that asks for several of one item in a single slot may count it once. If a price looks off, fix it with `/soloeconomy price <item> set <emeralds>`.
  - Off by default. Modded items are only bought and sold with `openMarket` on, or when a datapack adds them to a merchant.

---

# Solo Economy 0.3.2

**Minecraft 1.21.1 · NeoForge (built on 21.1.209) · Java 21**

## Balance

Bulk selling now hits diminishing returns. Before, a farm dumping 1,000 logs a day earned over 8,000 emeralds a month, enough for an elytra every day. Now it earns about a quarter of that, and after the first week income settles at a few hundred emeralds a week. Selling a stack now and then pays almost the same as before.

- Markets recover more slowly: about halfway back to normal in a week, down from a day.
- Prices react more strongly to big sales, and can fall further: to 5% of normal, down from 20%.
- Selling a stack of diamonds now moves their price about 30%, up from 15%.

## Upgrading

Your config updates itself on first start. Settings still at the old defaults (`recoveryPerDay`, `elasticity`, `minPriceMultiplier`, `marketDepth`) move to the new ones. Anything you changed yourself is left alone.

---

# Solo Economy 0.3.1

**Minecraft 1.21.1 · NeoForge (built on 21.1.209) · Java 21**

## Added

- **Open market option.** Set `openMarket = true` in `config/soloeconomy-server.toml` to add a **Store** merchant that buys and sells every item with a price, 944 in vanilla. Modded items are included when they're crafted, smelted or stonecut from priced materials, or given a price with `/soloeconomy price`. It's off by default, and discovery still applies unless you turn that off too.

---

# Solo Economy 0.3.0

**Minecraft 1.21.1 · NeoForge (built on 21.1.209) · Java 21**

## Added

- **Inventory panel.** Your inventory now sits beside the market. Hover an item to see what it sells for. Click it to find it across every merchant and select it, or shift-click to sell all of it. Items no merchant buys are greyed out.
- **Discover items to buy them.** The market only sells you what you've found yourself. Carry an item to a stall, or sell it there, and you can buy it from then on. Until then its row is greyed out and its buy price shows **Locked**. Selling is never locked. Turn this off with `requireDiscovery = false`.
- **Price multiplier.** `priceMultiplier` in `config/soloeconomy-server.toml` scales every default price. Set it to 3 and everything costs three times the emeralds, so emeralds from villagers and mining go a third as far. How fast prices move is unaffected.
- **Change prices in game.** No datapack needed:
  - `/soloeconomy price <item>` shows an item's price and where it comes from.
  - `/soloeconomy price <item> set <emeralds>` gives it a new base price. Crafted items made from it follow automatically.
  - `/soloeconomy price <item> reset` puts it back.
  - Setting or resetting a price also resets that item's supply, so it trades at the new price right away.
  - Changes apply immediately and are saved to `priceOverrides` in `config/soloeconomy-server.toml`, which you can also edit by hand.
  - Setting prices needs cheats or operator permission. If a new price makes crafting-for-profit possible, the command warns you.
  - Prices set this way are used exactly as written, ignoring the multiplier.

## Upgrading

Discovery starts empty, so after updating you'll need to bring items to a stall before you can buy them again. Set `requireDiscovery = false` to keep the old behaviour.

---

# Solo Economy 0.2.0

**Minecraft 1.21.1 · NeoForge (built on 21.1.209) · Java 21**

## Merchants

The market no longer buys and sells everything. It's now **eleven merchants**, each trading the goods their work needs: Farmer, Butcher, Fisherman, Woodcutter, Mason, Smith, Collector, Cleric, Tailor, Librarian and Fletcher.

- **259 tradeable items**, down from 944. If no merchant has a reason to want something (dirt, netherrack, leaves), it can't be traded.
- Some goods are wanted by more than one merchant. String sells to the Tailor, Fisherman and Fletcher.
- Modpacks can change who trades what with `data/soloeconomy/merchants.json`, by item id or `#tag`.

## New market screen

- Rebuilt in the style of the console edition menus.
- **Merchants down the left**, with the **Bank** at the bottom for deposits and withdrawals.
- Every row shows its **sell price and buy price** side by side.
- The **Sell** and **Buy** buttons show the total for your chosen quantity before you click. Hover one for the count and exact amount.
- **Search** looks across every merchant. **Only what I carry** filters the list to your inventory.
- **Arrow keys** move through the list.
- Removed the sort button. Each merchant lists its goods in a set order.

## Changed

- The market screen loads only the merchant you're looking at, so it opens faster.

## Upgrading

Balances and market prices carry over. Items you were holding that no merchant trades can no longer be sold.

---

# Solo Economy 0.1.1

**Minecraft 1.21.1 · NeoForge (built on 21.1.209) · Java 21**

## Fixed

- **Prices under one emerald are now charged properly.** Balances are tracked to two decimal places, so a block worth 0.05 costs 0.05 instead of rounding up to a whole emerald. Selling cheap items no longer pays nothing.
- Trade totals and messages show decimals where they have them.

## Changed

- Your balance shows whole emeralds, matching what Withdraw pays out. Hover it in the market screen to see the exact amount.

Existing balances carry over automatically the first time you log in.

---

# Solo Economy 0.1.0

**Minecraft 1.21.1 · NeoForge (built on 21.1.209) · Java 21**

First release. A market economy for single-player worlds: sell what you gather, buy what you need, at prices that respond to what you've been doing.

## Features

### Market Stall
- New block, crafted from 5 planks, a chest and an emerald.
- Right-click to open the market. Works on its own; no villager required.

### Broker villager
- A new villager profession. Place a Market Stall near an unemployed villager and they'll take the job.
- A Broker working a stall cuts the trading fees at that stall by 25% (spread drops from 20% to 15%).

### Emerald account
- Emeralds are the currency, held as a balance instead of in your inventory, so a big sale doesn't bury you in stacks.
- **Deposit** and **Withdraw** move emeralds between your inventory and your account, always 1:1.
- Your balance is shown in the top-right corner of the screen.

### Market screen
- Search, sort (A–Z, Cheapest, Priciest), and an **In bag** filter to show only what you're carrying.
- Scrolling list with a draggable scrollbar.
- Quantity buttons: 1, 8, 64, or **All** (everything you're carrying when selling, as many as you can afford when buying).
- **Cost preview**: hover any row to see the exact total for your chosen quantity before you click.
- Tooltips show whether an item's materials are oversupplied or in demand right now.

### Dynamic prices
- **944 tradeable items.** 225 raw materials (the things you mine, farm, kill or loot) have hand-set prices; everything else is priced from what it's made of.
- Selling floods the market and lowers the price; buying drains it and raises it. Markets recover halfway back to normal each in-game day.
- A crafted item is priced as its ingredients, so selling a diamond block moves the diamond price exactly as selling nine diamonds would.
- Selling pays for materials. Buying something pre-made adds a 12% assembly fee per crafting step.
- Every buy-craft-sell route loses money, so income comes from gathering rather than arbitrage.

### For modpack makers
- All tuning lives in `config/soloeconomy-server.toml`: fees, price sensitivity, market depth, recovery speed, Broker discount and starting balance.
- Override or extend prices with a datapack at `data/soloeconomy/base_prices.json`. Packs stack, and changes apply on `/reload`.
- Every time prices load, the mod checks all recipes and logs an error naming any that can be crafted and sold at a profit, so a pricing mistake is caught right away.

## Not tradeable
- Emeralds and emerald blocks (use Deposit and Withdraw instead)
- Damaged, enchanted or renamed items, and containers with items inside
- Water buckets, and creative-only blocks like bedrock and spawners

## Known limitations
- **Placeholder art.** The stall and Broker textures are temporary.
- **Some items can't be traded:** netherite tools and armour (smithing recipes aren't supported yet), potions, spawn eggs, music discs, pottery sherds and smithing templates.
- **Modded items** are mostly untradeable unless their raw materials are given prices by a datapack.
- **The balance display** is fixed to the top-right corner and may overlap a minimap.
- **Built for single-player.** On a server, all players share one market.
- English only.
