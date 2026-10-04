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
