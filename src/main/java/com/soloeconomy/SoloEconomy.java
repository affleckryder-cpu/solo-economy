package com.soloeconomy;

import com.soloeconomy.config.EconomyConfig;
import com.soloeconomy.network.ModNetwork;
import com.soloeconomy.registry.ModBlocks;
import com.soloeconomy.registry.ModCreativeTabs;
import com.soloeconomy.registry.ModMenus;
import com.soloeconomy.registry.ModPoiTypes;
import com.soloeconomy.registry.ModProfessions;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(SoloEconomy.MOD_ID)
public class SoloEconomy {

    public static final String MOD_ID = "soloeconomy";
    public static final Logger LOGGER = LoggerFactory.getLogger("Solo Economy");

    public SoloEconomy() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.register(modBus);
        ModMenus.register(modBus);
        ModPoiTypes.register(modBus);
        ModProfessions.register(modBus);
        ModCreativeTabs.register(modBus);

        ModNetwork.register();

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, EconomyConfig.SPEC);
    }
}
