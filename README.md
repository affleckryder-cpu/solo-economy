<p align="center">
  <img src="https://raw.githubusercontent.com/affleckryder-cpu/solo-economy/main/curseforge/logo.png" alt="Solo Economy" width="160">
</p>

# Solo Economy

A real market for single-player Minecraft worlds. Place a **Market Stall**, hire a **Broker**, and
sell what you gather or buy what you need, at prices that move with supply and demand.

![A Broker villager standing at a Market Stall under a cherry tree](https://raw.githubusercontent.com/affleckryder-cpu/solo-economy/main/curseforge/gallery/01-market-stall-and-broker.jpg)

| Minecraft | Loader | Branch | Java |
| --- | --- | --- | --- |
| 1.21.1 | NeoForge 21.1+ | [`main`](https://github.com/affleckryder-cpu/solo-economy/tree/main) | 21 |
| 1.20.1 | Forge 47+ | [`forge-1.20.1`](https://github.com/affleckryder-cpu/solo-economy/tree/forge-1.20.1) | 17 |

Both versions have the same features. Downloads are on CurseForge; what's planned is in the
[roadmap](https://github.com/affleckryder-cpu/solo-economy/blob/main/curseforge/roadmap.png), and what changed in each release is in the
[changelog](CHANGELOG.md).

---

## The idea

Multiplayer servers get an economy for free: other players. Solo worlds don't, so trading is
whatever the villager RNG hands you. This mod gives a solo world a market that responds to what you
have been doing to it. Money is supposed to come from *gathering* (mines, farms, mob grinders), not
from gaming the prices.

## How it plays

1. Craft a **Market Stall**: five planks, a chest and an emerald.
2. Right-click it. The market is **eleven merchants** down the left, each trading what their work
   needs: the Farmer buys crops, the Smith buys ore and sells tools, the Collector wants rare finds.
   Something no merchant has a use for (dirt, netherrack) can't be traded.
3. Pick an item and a quantity (1 / 8 / 64 / All), then **Sell** or **Buy**. Every row shows both
   prices, and the buttons show the total before you click.
4. Your inventory sits beside the market. Click an item there to find who buys it, or shift-click to
   sell all of it.
5. Emeralds are the currency, kept as an account balance. **Deposit** and **Withdraw** at the Bank,
   always 1:1. The balance survives death.
6. Put the stall near an unemployed villager. They take it as a job site and become a **Broker**,
   which cuts the fees at that stall by a quarter.

**Discovery.** You can only buy what you've found yourself. Carry an item to a stall, or sell it
there, and it unlocks for buying from then on. Until then its row is greyed out and marked Locked.
Selling is never locked.

## How prices work

Each **primitive** has a base price and a stock level. Its price is a pure function of stock:

```
price = basePrice * (baseStock / stock) ^ elasticity
```

Selling floods the market and drives that item's price down. Buying drains it and drives the price
up. Between trades, stock drifts back toward equilibrium, a tenth of the distance per in-game day by
default, so a market takes about a week to get halfway back. Selling a stack now and then pays well;
dumping a farm's output every day drives that price down and keeps it there.

Bulk trades walk the curve one unit at a time, so selling 64 at once pays exactly what selling 64
one-at-a-time would. There is never a reason to click 64 times.

Each unit is priced *after* its own effect on stock: a sale into the glut it just made, a purchase
into the shortage it just caused. Pricing before the move instead let a sale land one step up the
curve from the purchase before it, and in a thin enough market that step was bigger than the spread:
buy one, sell it back, keep the difference, forever.

`baseStock` is `marketDepth * basePrice^-depthPriceExponent`, so cheap goods get deeper markets than
expensive ones, but only sub-linearly. Selling a stack of diamonds moves the diamond price about
30%; a stack of cobblestone barely registers.

### Only primitives have a price

"Sell anything, buy anything" is trivially exploitable if every item has its own independent price,
because the prices drift apart. So most items don't have one.

`base_prices.json` seeds about 225 **primitives**: things you dig, farm, kill or loot for. They are
the only items with a price and a stock level of their own. Every other item is stored as a
**bundle**: the sack of primitives you would have to gather to make one, worked out by expanding its
cheapest recipe all the way down. A diamond block is not an item priced near nine diamonds; as far
as the market is concerned it *is* nine diamonds.

```
raw(x)  = sum over the bundle of quantity * current primitive price
buy(x)  = raw(x) * craftMarkup^steps * (1 + spread)    materials plus an assembly fee
sell(x) = raw(x) * (1 - spread)                        materials only
```

Selling a diamond block adds nine diamonds to the diamond market, exactly as selling nine diamonds
would. The two can never disagree about what a diamond is worth, however hard either is pushed.

Every round trip loses the spread, whichever direction you go and however many crafting steps you
route through:

| route | cost | return |
| --- | --- | --- |
| buy materials → craft → sell | `raw * (1+spread)` | `raw * (1-spread)` |
| buy item → uncraft → sell parts | `raw * markup^steps * (1+spread)` | `raw * (1-spread)` |
| buy item → sell item | `raw * markup^steps * (1+spread)` | `raw * (1-spread)` |

That holds for any item priced through its own recipe. It does **not** automatically hold for an
item that is hand-priced *and* craftable, because then it has two independent prices; that is what
the market audit below is for. `craftMarkup` is pure flavour: it decides what you pay for not
crafting something yourself, and cannot open a loop at any value.

**This replaced an earlier design** where every item had its own price derived from its recipe at
startup, its own stock, and its own price floor. That looked correct and wasn't: dump enough diamond
blocks to hit the floor while buying diamonds pushed those up, and you could buy a block for 53
emeralds and uncraft it into 252 emeralds of diamonds. Independent stock per item is the bug;
bundles are the fix.

The same reasoning covers other classic loops:

- **Uncrafting** (block → 9 ingots) loses money for the same reason, in reverse.
- **Recipes that return a container** (cake and its three milk buckets) subtract the returned
  buckets. An earlier version dropped that, which priced cake as though the buckets were destroyed:
  crafting cake from bought milk made 7.60 emeralds profit.
- **Emeralds themselves** are untradeable; you deposit and withdraw them instead. Otherwise crafting
  emerald blocks and selling them would be a loop straight out of the currency.
- **Water buckets** are untradeable, because water is free and buckets are not.

### The market audit

Every time the catalogue is built (server start and every `/reload`), every recipe the market reads
is checked: at base prices and the narrowest spread in the game, does buying the inputs, crafting,
and selling the result make money? Any that do are logged as errors naming the recipe and the
profit. A clean start logs:

```
Market audit: no recipe can be crafted and sold at a profit
```

The audit is deliberately about **base** prices. When a farm floods one market, crafting your own
output into a different item can pay better than selling it raw. That is choosing a better outlet
for goods you gathered, bounded by what the farm produces, not money from nothing.

### What the market won't touch

Items carrying custom data (enchanted, renamed, damaged, or a shulker box with things inside) are
never tradeable. One rule, and it closes a whole category of "sell the full shulker box for the
price of an empty one" problems. Plus an explicit block list for creative-only and
duplication-prone blocks (bedrock, spawners, budding amethyst and friends).

## For modpack makers

### Configuration

`config/soloeconomy-server.toml` on 1.21.1. On Forge 1.20.1 the file is per world, at
`saves/<world>/serverconfig/soloeconomy-server.toml`; put a copy in `defaultconfigs` to use it for
new worlds. Everything is under `[market]`:

| Key | Default | What it does |
| --- | --- | --- |
| `spread` | 0.10 | Half-spread. Buy costs `price*(1+spread)`, sell pays `price*(1-spread)`. |
| `craftMarkup` | 1.12 | Assembly fee per crafting step when buying pre-made goods. Flavour only. |
| `elasticity` | 1.0 | How hard a primitive's price reacts to its stock. 0 = fixed prices. |
| `minPriceMultiplier` | 0.05 | Price floor, as a multiple of base. |
| `maxPriceMultiplier` | 5.0 | Price ceiling. |
| `marketDepth` | 768 | Equilibrium stock for a primitive worth one emerald. The main responsiveness lever. |
| `depthPriceExponent` | 0.5 | How much deeper cheap markets are: `baseStock = marketDepth * price^-exp`. |
| `recoveryPerDay` | 0.1 | Fraction of the gap to equilibrium that heals per in-game day. |
| `brokerSpreadMultiplier` | 0.75 | Spread multiplier at a stall with a Broker working it. |
| `startingBalance` | 0 | Emeralds granted on first login. |
| `priceMultiplier` | 1.0 | Scales every default price. Raise it to make emeralds go less far. |
| `requireDiscovery` | true | Players can only buy items they have carried to or sold at a stall. |
| `openMarket` | false | Adds a Store merchant that trades every item with a price. |
| `modRecipes` | false | Also price items from other mods' recipes. See below. |
| `priceOverrides` | [] | Per-item prices set with `/soloeconomy price`. |
| `deriveUnpricedItems` | true | Turn off to restrict the market to seeded primitives only. |

### Changing prices

In game, with cheats or operator permission:

```
/soloeconomy price <item>                  where its price comes from, and what it trades at now
/soloeconomy price <item> set <emeralds>   give it a new base price
/soloeconomy price <item> reset            back to the default
```

Crafted items follow their ingredients automatically, and the command warns you if a new price makes
any recipe profitable to craft and sell.

For bulk changes, a datapack with `data/soloeconomy/base_prices.json`. Packs stack in load order, so
you can retune a single price without restating the book, or set `"replace": true` to start fresh.
**Only seed primitives.** Seeding both sides of a recipe (both `raw_iron` and `iron_ingot`) gives
the second one its own price and stock, so the pair can drift apart and reopen exactly the arbitrage
that bundles exist to close.

### Changing who trades what

`data/soloeconomy/merchants.json` lists each merchant's goods by item id or `#tag`. A datapack can
replace a merchant, add new ones, or start from nothing. Names and descriptions come from the lang
keys `merchant.soloeconomy.<id>` and `merchant.soloeconomy.<id>.desc`.

### Other mods' items

Items crafted, smelted or stonecut from priced materials get a price automatically. With
`modRecipes = true` the market also reads any other recipe type that lists its ingredients, such as
Create's machines and modded workbenches:

- Outputs with a chance (Create's crushing byproducts) are priced by how many you get on average.
- When a modded machine makes a hand-priced item for less than its price (Create haunts soul sand
  into quartz), that item is priced from the machine instead, so it can't be farmed for money.
- It is best effort. Only a recipe's ingredient list and results are visible, so fluids aren't
  counted and a slot that needs several of one item may count once.

Modded items are only bought and sold with `openMarket` on, or when a datapack lists them under a
merchant. Tested against Create 6 on 1.21.1: 430 of its 699 items get a price, with a clean audit.

## Building

Gradle provisions the right Java toolchain for each branch by itself, but Gradle 8.14 has to *start*
on a JDK it supports (Java 24 or older):

```bash
./gradlew build              # the jar lands in build/libs/
./gradlew runClient          # a dev client
./gradlew runGameTestServer  # the test suite, headless
```

On 1.21.1, `-PwithCreate` adds Create to the dev runs without shipping it.

## Tests

`runGameTestServer` runs twelve GameTests against the real recipe set and exits non-zero on failure:

| test | guards against |
| --- | --- |
| no recipe is profitable at any spread | hand-priced craftables (lead made 3.55), cake's dropped bucket refund (7.60) |
| buying then selling back always loses | per-item round trips, every tradeable item, 1 and 64 units |
| thin-market round trips still lose | pricing units before their own stock move |
| crafted items track their materials | per-item stock that let a diamond block drift from nine diamonds |
| cheap items cost fractions of an emerald | whole-emerald balances that charged 1 for a 0.05 item |
| market stock survives save and recovers | persistence and the recovery rate |
| price overrides win and crafted items follow | `/soloeconomy price` being ignored, or not reaching crafted goods |
| price multiplier scales prices, not depth | raising prices quietly making markets swing harder |
| carrying an item unlocks buying it | discovery unlocking too much, or nothing |
| open market trades everything safely | a loop appearing once every priced item is tradeable |
| daily dumping hits diminishing returns | a 1000-logs-a-day farm earning 8,200 emeralds a month |
| mod recipes price machine outputs | modded recipes read when off, or chance outputs counted as certain |

## Layout

| Path | What's in it |
| --- | --- |
| `market/MarketCatalog.java` | Primitive prices, expanding every other item into a bundle, merchants |
| `market/MarketData.java` | Live primitive stock, the price curve, quoting and settlement |
| `market/MarketAudit.java` | The check that no recipe can be crafted and sold at a profit |
| `market/Discovery.java`, `EconomyAccount.java` | What a player has unlocked, and their balance |
| `network/ServerMarketHandler.java` | All trade validation and execution: the security boundary |
| `client/MarketScreen.java` | The stall UI |
| `command/PriceCommand.java` | `/soloeconomy price` |
| `data/soloeconomy/base_prices.json` | The seed price book |
| `data/soloeconomy/merchants.json` | Who trades what |

## Known limitations

- Netherite tools and armour aren't tradeable yet: smithing recipes don't list their ingredients the
  way crafting does. Potions, spawn eggs, music discs and pottery sherds have no price either.
- The balance display sits in the top-right corner and can't be moved yet.
- Built for single-player. On a server, every player shares one market.
- English only.

## License

[MIT](LICENSE)
