# Solo Economy

A Minecraft mod for single-player worlds. Place a **Market Stall**, hire a **Broker**, and buy or
sell almost anything at prices that move with supply and demand.

- **Minecraft** 1.21.1
- **Loader** NeoForge 21.1.209
- **Currency** emeralds, held as an account balance shown on the HUD

---

## The idea

Multiplayer servers get an economy for free: other players. Solo worlds don't, so trading is
whatever the villager RNG hands you. This mod gives a solo world a real market — one that will buy
anything you dig up and sell you anything you need, at a price that responds to what you have been
doing to it.

## How it plays

1. Craft a **Market Stall** (planks, a chest and an emerald).
2. Right-click it to open the market. Search, sort, and click the **Sell** or **Buy** column on any
   row. The quantity buttons (1 / 8 / 64 / All) decide how much a click trades.
3. Your balance sits in the top-right of the screen. **Deposit** turns held emeralds into balance,
   **Withdraw** turns balance back into emeralds. Always 1:1.
4. Park an unemployed villager next to the stall. They will claim it as a job site and become a
   **Broker**, which cuts the house's cut on every trade at that stall.

## How prices work

Each **primitive** has a base price and a stock level. Its price is a pure function of stock:

```
price = basePrice * (baseStock / stock) ^ elasticity
```

Selling floods the market and drives that item's price down. Buying drains it and drives the price
up. Between trades, stock drifts back toward equilibrium — half the distance per in-game day by
default — so a market you wrecked on Monday is worth revisiting by Wednesday.

Bulk trades walk the curve one unit at a time, so selling 64 at once pays exactly what selling 64
one-at-a-time would. There is never a reason to click 64 times.

Each unit is priced *after* its own effect on stock — a sale into the glut it just made, a purchase
into the shortage it just caused. Pricing before the move instead let a sale land one step up the
curve from the purchase before it, and in a thin enough market that step was bigger than the spread:
buy one, sell it back, keep the difference, forever.

`baseStock` is `marketDepth * basePrice^-depthPriceExponent`, so cheap goods get deeper markets than
expensive ones, but only sub-linearly. A stack of diamonds moves the diamond price about 15%; a
stack of dirt barely registers, but a double chest of it moves dirt about 27%.

Scaling depth as a flat emerald value instead (`depth / price`, the first thing I tried) hands
cobblestone a 40,000-unit market needing half a million sales to shift. The economy looks frozen.

### Only primitives have a price

"Sell anything, buy anything" is trivially exploitable if every item has its own independent price,
because the prices drift apart. So most items don't have one.

`base_prices.json` seeds 234 **primitives** — things you dig, farm, kill or loot for. They are the
only items with a price and a stock level of their own. Every other item is stored as a **bundle**:
the sack of primitives you would have to gather to make one, worked out by expanding its cheapest
recipe all the way down. A diamond block is not an item priced near nine diamonds; as far as the
market is concerned it *is* nine diamonds.

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

That holds for any item priced through its own recipe. It does **not** automatically hold for an item
that is hand-priced *and* craftable, because then it has two independent prices — see the market
audit below. `craftMarkup` is pure flavour — it decides what you pay for the convenience of not crafting something
yourself, and cannot open a loop at any value.

**This replaced an earlier design** where every item had its own price derived from its recipe at
startup, its own stock, and its own price floor. That looked correct and wasn't: dump enough
diamond blocks to hit the 0.2x floor while buying diamonds pushed those up, and you could buy a
block for 53 emeralds and uncraft it into 252 emeralds of diamonds. Independent stock per item is
the bug; bundles are the fix.

The same reasoning covers other classic loops:

- **Uncrafting** (block → 9 ingots) loses money for the same reason, in reverse.
- **Recipes that return a container** (cake and its three milk buckets) subtract the returned
  buckets. Because a milk bucket is hand-priced and has no iron in its own bundle, cake's bundle
  holds *minus* nine raw iron. An earlier version dropped negative quantities, which priced cake as
  though the buckets were destroyed — crafting cake from bought milk made 7.60 emeralds profit.
- **Emeralds themselves** are untradeable — you deposit and withdraw them instead. Otherwise
  crafting emerald blocks and selling them would be a loop straight out of the currency.
- **Water buckets** are untradeable, because water is free and buckets are not.

Money is supposed to come from *gathering*: mines, farms, mob grinders. Not from arbitrage.

### The market audit

Every time the catalogue is built — server start and every `/reload` — every crafting, smelting and
stonecutting recipe is checked: at base prices and the narrowest spread in the game, does buying the
inputs, crafting, and selling the result make money? Any that do are logged as errors naming the
recipe and the profit. The usual cause is a datapack hand-pricing something that can also be
crafted. A clean start logs:

```
Market audit: no recipe can be crafted and sold at a profit
```

The audit is deliberately about **base** prices. When a farm floods one market, crafting your own
output into a different item can pay better than selling it raw — that is choosing a better outlet
for goods you gathered, bounded by what the farm produces, not money from nothing.

### What the market won't touch

Items carrying custom data components — enchanted, renamed, damaged, or a shulker box with things
inside — are never tradeable. One rule, and it closes a whole category of "sell the shulker box for
the price of an empty one" problems. Plus an explicit block list for creative-only and duplication-
prone blocks (bedrock, spawners, budding amethyst, and friends).

## Configuration

`config/soloeconomy-server.toml`, all under `[market]`:

| Key | Default | What it does |
| --- | --- | --- |
| `spread` | 0.10 | Half-spread. Buy costs `price*(1+spread)`, sell pays `price*(1-spread)`. |
| `craftMarkup` | 1.12 | Assembly fee per crafting step when buying pre-made goods. Flavour only. |
| `elasticity` | 0.6 | How hard a primitive's price reacts to its stock. 0 = fixed prices. |
| `minPriceMultiplier` | 0.20 | Price floor, as a multiple of base. Stops a market being killed for good. |
| `maxPriceMultiplier` | 5.0 | Price ceiling. |
| `marketDepth` | 1024 | Equilibrium stock for a primitive worth one emerald. The main responsiveness lever. |
| `depthPriceExponent` | 0.5 | How much deeper cheap markets are: `baseStock = marketDepth * price^-exp`. |
| `recoveryPerDay` | 0.5 | Fraction of the gap to equilibrium that heals per in-game day. |
| `deriveUnpricedItems` | true | Turn off to restrict the market to seeded primitives only. |
| `startingBalance` | 0 | Emeralds granted on first login. |
| `brokerSpreadMultiplier` | 0.75 | Spread multiplier at a stall with a Broker working it. |

### Retuning prices

Drop a datapack containing `data/soloeconomy/base_prices.json`. Packs stack in load order, so you
can retune a single price without restating the book, or set `"replace": true` to start fresh.
Prices reload with `/reload` and the whole catalogue is rebuilt from the current recipe set.

**Only seed primitives.** Seeding both sides of a recipe (both `raw_iron` and `iron_ingot`, both
`cobblestone` and `stone`) makes the second one a primitive too, with its own price and its own
stock — so the pair can drift apart and reopen exactly the arbitrage that bundles exist to close.
Seed the input only and let the output be a bundle of it.

## Tests

```bash
JAVA_HOME="/c/Program Files/Java/jdk-20" ./gradlew runGameTestServer
```

Runs five GameTests headlessly against the real recipe set, and exits non-zero on failure:

| test | guards against |
| --- | --- |
| no recipe is profitable at any spread | hand-priced craftables (lead +3.55), cake's dropped bucket refund (+7.60) |
| buying then selling back always loses | per-item round trips, every tradeable item, 1 and 64 units |
| thin-market round trips still lose | pricing units before their own stock move, with the price cap lifted |
| crafted items track their materials | per-item stock that let a diamond block drift from nine diamonds |
| market stock survives save and recovers | persistence and the recovery rate |

Each test was confirmed to fail when its bug was put back.

## Building

The Java 21 toolchain NeoForge needs is auto-provisioned by Gradle, but Gradle itself has to start
on a JDK it supports — 8.14 tops out at Java 24. Your `JAVA_HOME` currently points at JDK 25, which
Gradle will refuse, so point it at the JDK 20 install for build commands:

```bash
JAVA_HOME="/c/Program Files/Java/jdk-20" ./gradlew build
```

The jar lands in `build/libs/`. To launch a dev client:

```bash
JAVA_HOME="/c/Program Files/Java/jdk-20" ./gradlew runClient
```

`./gradlew runServer` starts a headless dev server, which is what the market catalogue numbers
above were measured on.

## Layout

| Path | What's in it |
| --- | --- |
| `market/MarketCatalog.java` | Primitive prices, and expanding every other item into a bundle of them |
| `market/MarketData.java` | Live primitive stock, the price curve, quoting and settlement |
| `market/BasePriceLoader.java` | Datapack-merged seed prices |
| `network/ServerMarketHandler.java` | All trade validation and execution — the security boundary |
| `client/MarketScreen.java` | The stall UI |
| `client/BalanceHud.java` | The balance readout |
| `data/soloeconomy/base_prices.json` | The seed price book |

## Verified so far

A dev server boots clean with no errors and reports:

```
Loaded 234 seed prices and 39 untradeable entries
Market catalog built: 234 primitives priced by hand, 710 items priced as bundles of them,
944 tradeable in total
```

The ~350 registry items with no price are things no recipe produces and the seed file does not
list: spawn eggs, music discs, pottery sherds, smithing templates, potions (brewing is not part of
the recipe manager), and technical blocks. They are simply untradeable.

Confirmed in game: the stall places and opens, and villagers take the Broker job.

## Known gaps

- The Broker villager overlay texture is a flat generated placeholder. It reads as a green apron in
  game but it is not real pixel art.
- The HUD balance sits in the top-right corner and is not yet repositionable.
- No trade history or price graph. The tooltip only says whether an item is currently oversupplied,
  short, or steady.
