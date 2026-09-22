# Solo Economy

**A real market for single-player worlds.** Sell what you mine, farm and loot. Buy what you need. Prices react to what you've been doing, so dumping a chest of diamonds tanks the diamond price and buying up iron makes it climb.

Multiplayer servers get an economy for free: other players. Solo worlds don't, and trading is whatever villager offers you happen to roll. Solo Economy gives you a market that will buy almost anything you bring it and sell you almost anything you're missing.

---

## Getting started

1. **Craft a Market Stall** from 5 planks, a chest and an emerald.
2. **Right-click it** to open the market.
3. **Deposit** your emeralds, then click the **Sell** or **Buy** price on any row.
4. **Hire a Broker (optional).** Place the stall near an unemployed villager. They'll take the job, and trading at that stall gets 25% cheaper.

---

## Features

### 🏪 Market Stall
A block that opens the market. It works on its own, and a villager can take it as a job site.

### 🧑‍💼 Broker villager
A new villager profession. A Broker working a stall cuts that stall's trading fees by a quarter, so hiring one is a real upgrade rather than decoration.

### 💎 Emerald account
Emeralds are the currency, kept as a balance rather than in your inventory, so a big sale never buries you in stacks. **Deposit** and **Withdraw** at any stall, always 1:1. Your balance is shown in the corner of your screen.

### 🔎 The market screen
- **Search**, **sort** (A–Z, cheapest, priciest), and an **In bag** filter that lists only what you're carrying
- **Scrolling list** with a draggable scrollbar
- **Quantity buttons:** 1, 8, 64, or **All**. All sells everything you're carrying, or buys as many as you can afford
- **Cost preview:** hover any row to see the exact total before you click
- **Supply tooltips** tell you whether an item is oversupplied or in demand right now

### 📈 Prices that move
- **944 tradeable items** in vanilla
- **Selling lowers the price, buying raises it.** A stack of diamonds moves the diamond price about 15%. Cheap goods like dirt have deep markets and barely move, but a double chest of dirt still will.
- **Markets recover** halfway back to normal every in-game day, so a market you flooded on Monday is worth revisiting by Wednesday.
- **Bulk trades are priced unit by unit**, so selling 64 at once is never worse than selling them one at a time. There's no reason to click 64 times.

### 🧱 Crafted items are priced from their ingredients
About 225 raw materials have set prices. Everything else is priced from the ingredients of its cheapest recipe:

- **A diamond block is worth its nine diamonds.** Selling one moves the diamond price exactly as selling nine diamonds would.
- **Selling pays for the materials.** Buying something pre-made adds a small fee per crafting step, so crafting it yourself always saves.
- **Crafting isn't a money machine.** Buying ingredients to craft and sell loses a little at normal prices, so your income comes from gathering, not from gaming the prices.

---

## For modpack makers

**Configuration** lives in `config/soloeconomy-server.toml`:

| Setting | Default | What it does |
| --- | --- | --- |
| `spread` | 0.10 | The market's cut on every trade |
| `craftMarkup` | 1.12 | Fee per crafting step when buying pre-made goods |
| `elasticity` | 0.6 | How strongly prices react to supply |
| `marketDepth` | 1024 | How much trading it takes to move a price |
| `recoveryPerDay` | 0.5 | How fast markets return to normal |
| `minPriceMultiplier` / `maxPriceMultiplier` | 0.2 / 5.0 | Price floor and ceiling |
| `brokerSpreadMultiplier` | 0.75 | Fee reduction when a Broker works the stall |
| `startingBalance` | 0 | Emeralds a new player starts with |

**Custom prices:** add a datapack with `data/soloeconomy/base_prices.json` to change or add prices. Packs stack, and changes apply on `/reload`. Only set prices for raw materials; crafted items are priced from their ingredients automatically.

**Pricing check:** every time prices load, the mod checks every recipe. If a pricing change makes any item profitable to craft and sell, it logs an error naming the recipe.

---

## What can't be traded

- **Emeralds and emerald blocks.** Use Deposit and Withdraw instead.
- **Damaged, enchanted or renamed items**, and containers with items inside
- **Water buckets**, and creative-only blocks such as bedrock and spawners

---

## Known limitations

This is an early release.

- **Placeholder art.** The stall and Broker textures are temporary.
- **Not yet tradeable:** netherite tools and armour (smithing recipes aren't supported yet), potions, spawn eggs, music discs, pottery sherds and smithing templates.
- **Modded items** are mostly untradeable unless a datapack gives their raw materials prices.
- **The balance display** sits in the top-right corner and may overlap a minimap.
- **Built for single-player.** On a server, all players share one market.
- **English only.**

---

## Requirements

- Minecraft **1.21.1**
- **NeoForge** (built on 21.1.209)
- Required on **both** client and server

---

Found a bug or a price that looks wrong? Leave a comment. If you've found a way to make money from nothing, I especially want to hear about it.
