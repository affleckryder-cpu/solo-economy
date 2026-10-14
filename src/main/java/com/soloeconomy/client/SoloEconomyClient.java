package com.soloeconomy.client;

import com.soloeconomy.SoloEconomy;
import com.soloeconomy.registry.ModMenus;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = SoloEconomy.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class SoloEconomyClient {

    private SoloEconomyClient() {
    }

    /** Mods list -> Solo Economy -> Config opens the balance display settings. */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        ModList.get().getModContainerById(SoloEconomy.MOD_ID).ifPresent(container -> container.registerExtensionPoint(
                IConfigScreenFactory.class, (mod, parent) -> new BalanceSettingsScreen(parent)));
    }

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.MARKET.get(), MarketScreen::new);
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR,
                ResourceLocation.fromNamespaceAndPath(SoloEconomy.MOD_ID, "balance"),
                BalanceHud::render);
    }
}
