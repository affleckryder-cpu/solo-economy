package com.soloeconomy.registry;

import com.soloeconomy.SoloEconomy;
import com.soloeconomy.menu.MarketMenu;

import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, SoloEconomy.MOD_ID);

    public static final RegistryObject<MenuType<MarketMenu>> MARKET =
            MENUS.register("market", () -> IForgeMenuType.create(
                    (windowId, inventory, buffer) -> new MarketMenu(windowId, inventory)));

    private ModMenus() {
    }

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
