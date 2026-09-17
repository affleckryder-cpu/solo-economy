package com.soloeconomy;

import com.soloeconomy.config.EconomyConfig;
import com.soloeconomy.network.ModNetwork;
import com.soloeconomy.registry.ModAttachments;
import com.soloeconomy.registry.ModBlocks;
import com.soloeconomy.registry.ModCreativeTabs;
import com.soloeconomy.registry.ModMenus;
import com.soloeconomy.registry.ModPoiTypes;
import com.soloeconomy.registry.ModProfessions;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(SoloEconomy.MOD_ID)
public class SoloEconomy {

    public static final String MOD_ID = "soloeconomy";
    public static final Logger LOGGER = LoggerFactory.getLogger("Solo Economy");

    public SoloEconomy(IEventBus modBus, ModContainer container) {
        ModBlocks.register(modBus);
        ModMenus.register(modBus);
        ModPoiTypes.register(modBus);
        ModProfessions.register(modBus);
        ModAttachments.register(modBus);
        ModCreativeTabs.register(modBus);

        modBus.addListener(ModNetwork::register);

        container.registerConfig(ModConfig.Type.SERVER, EconomyConfig.SPEC);
    }
}
