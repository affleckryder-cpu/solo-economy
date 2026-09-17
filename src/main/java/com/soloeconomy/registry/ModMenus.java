package com.soloeconomy.registry;

import com.soloeconomy.SoloEconomy;
import com.soloeconomy.menu.MarketMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, SoloEconomy.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<MarketMenu>> MARKET =
            MENUS.register("market", () -> IMenuTypeExtension.create(
                    (windowId, inventory, buffer) -> new MarketMenu(windowId, inventory)));

    private ModMenus() {
    }

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
