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
