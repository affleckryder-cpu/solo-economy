# CurseForge page: Solo Economy

Everything to fill in, in the order CurseForge asks for it.

---

## 1. Create project

| Field | Value |
| --- | --- |
| **Game** | Minecraft |
| **Class** | Mods |
| **Project name** | `Solo Economy` |
| **Logo** | `curseforge/logo.png` (400×400 PNG) |
| **Summary** | see below |
| **Main category** | Adventure and RPG |
| **Additional categories** | Utility & QoL |
| **Allow comments** | On. The description asks for bug reports in the comments. |
| **Experimental** | Off |
| **Description** | Switch the editor from WYSIWYG to **Markdown**, then paste `curseforge/description.md` |
| **License** | MIT, which matches what the jar declares. **Your call:** pick another here and tell me, and I'll update the jar to match. |

### Summary

> Sell what you gather and buy what you need, at a market whose prices move with supply and demand.

If you want something shorter:

> A single-player market where prices rise and fall with what you buy and sell.

### Why these categories

- **Adventure and RPG:** it adds a progression loop (gather, sell, buy) and a villager profession.
- **Utility & QoL:** turning a pile of loot into what you actually need.
- **Not Server Utility**, even though other economy mods use it. This mod is built for single-player, and that tag would attract server owners looking for a player-to-player economy.

### Rules the page already meets

- **Name:** no version number, game name, or words like "mod".
- **Logo:** original art rendered from the mod's own textures, not a solid colour, no vanilla textures.
- **Summary:** one line, says what it does, doesn't copy the description.
- **Description:** explains what the mod adds and how to use it. There are no external download links, and nothing promotional.

---

## 2. After creating: settings

| Setting | Recommendation |
| --- | --- |
| **Source code link** | `https://github.com/affleckryder-cpu/solo-economy` |
| **Issues link** | `https://github.com/affleckryder-cpu/solo-economy/issues` |
| **Wiki link** | Leave empty. |
| **Allow modpack distribution** | On. Pack makers are an audience the description speaks to. |

---

## 3. Upload the file

| Field | Value |
| --- | --- |
| **File** | `build/libs/soloeconomy-0.1.0.jar` |
| **Display name** | `Solo Economy 0.1.0` |
| **Release type** | Beta |
| **Game version** | 1.21.1 |
| **Mod loader** | NeoForge |
| **Java version** | Java 21 |
| **Changelog** | Markdown; paste `CHANGELOG.md` |
| **Related projects** | None |

---

## 4. Screenshots

CurseForge lets each gallery image have a **title** and a **description**. It recommends in-game screenshots with the mod running.

**Setup for every shot**
- 1920×1080 window, GUI scale 3, so the 256px market screen is sharp and not tiny.
- Clear weather and daytime: `/weather clear`, `/time set 1000`.
- Press **F1** to hide the HUD in scenery shots. Leave the HUD on for shot 6, which shows the balance.
- Hide chat, and don't show the F3 debug screen.
- **F2** saves a screenshot. In the dev client they land in `run/screenshots`.
- Put some realistic money in the account first, a few hundred emeralds, so the balance doesn't read 0.

The textures are placeholders. If you're replacing the art soon, take these afterwards.

---

### Shot 1: Market Stall and Broker
**Set up:** Put a stall in a village square with an unemployed villager beside it, and wait for them to take the job (they get the green apron). Frame the villager and stall together at eye level, HUD hidden.

**Title:** Market Stall and Broker

**Description:** Place a Market Stall near an unemployed villager and they become a Broker. A Broker working a stall cuts that stall's trading fees by 25%.

---

### Shot 2: The market ⭐ *use as the featured image*
**Set up:** Open the stall, sort by **Priciest**, and don't hover anything. That way the line under the list shows the item count and spread, and the header shows your balance.

**Title:** The market

**Description:** Search, sort, and filter to what you're carrying. Every item shows its current sell and buy price.

---

### Shot 3: Cost preview
**Set up:** Set the quantity to **64**, then hover a row worth something, such as iron ingots or diamonds. The line under the list shows the sell and buy totals. Make sure the pointer isn't covering the preview line.

**Title:** See the total before you trade

**Description:** Hover any row to see exactly what your chosen quantity will pay or cost. Set the quantity to All to see how many you can afford.

---

### Shot 4: Supply and demand
**Set up:** Sell a large amount of one thing, for example several stacks of diamonds with quantity **All**. Then hover that row so the tooltip reads *Materials oversupplied*, next to the lowered sell price.

**Title:** Supply and demand

**Description:** Sell a lot of something and its price drops. Buy it up and the price climbs. Markets recover halfway back to normal every in-game day.

---

### Shot 5: Crafted items track their ingredients
**Set up:** Search `diamond` so diamonds, diamond blocks, pickaxes and so on appear together, with no hover.

**Title:** Crafted items are priced from their ingredients

**Description:** A diamond block sells for what its nine diamonds are worth. Buying something pre-made adds a small fee per crafting step, so crafting it yourself always saves.

---

### Shot 6: Emerald account
**Set up:** Out in the world with the **HUD on**, so the balance shows in the top-right corner. Stand near the stall and hold emeralds in your hand.

**Title:** Emerald account

**Description:** Emeralds are the currency, kept as a balance shown in the corner of your screen. Deposit and withdraw them at any stall, 1:1.

---

### Shot 7: Sell your haul
**Set up:** Fill your inventory with a mixed mining or farming haul. Open the stall with **In bag** on and the quantity set to **All**.

**Title:** Sell your haul

**Description:** Switch on In bag to list only what you're carrying, set the quantity to All, and sell a whole trip's worth in a few clicks.

---

## 5. Before you hit publish

- [ ] **License chosen.** If it isn't MIT, tell me so the jar matches.
- [ ] **Died once in a test world** and confirmed your balance survived. The page doesn't promise this, but players will assume it.
- [ ] **Screenshots taken** with the mod's current art, or after replacing it.
- [ ] **File uploaded as Beta.** CurseForge reviews both the project and the file before they go public.

---

## 6. Roadmap image

`curseforge/roadmap.png` (1920×1080) is for the gallery. Its source is `curseforge/roadmap.html`: edit the three lists there, then re-render from the `curseforge` folder:

```bash
"C:/Program Files/Google/Chrome/Application/chrome.exe" --headless=new --hide-scrollbars --window-size=1920,1080 --virtual-time-budget=8000 --screenshot="roadmap.png" "roadmap.html"
```

**Title:** Roadmap

**Description:** What's released, what's coming next, and ideas for later. Plans can change.
