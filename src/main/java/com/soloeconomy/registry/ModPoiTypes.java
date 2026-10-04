package com.soloeconomy.registry;

import com.google.common.collect.ImmutableSet;
import com.soloeconomy.SoloEconomy;

import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The Market Stall is a point of interest so that villagers path to it and claim it as a job site,
 * exactly like a lectern or a smithing table. {@code maxTickets = 1} means one broker per stall.
 */
public final class ModPoiTypes {

    public static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(ForgeRegistries.POI_TYPES, SoloEconomy.MOD_ID);

    public static final RegistryObject<PoiType> MARKET_STALL = POI_TYPES.register("market_stall",
            () -> new PoiType(
                    ImmutableSet.copyOf(ModBlocks.MARKET_STALL.get().getStateDefinition().getPossibleStates()),
                    1,
                    1));

    private ModPoiTypes() {
    }

    public static void register(IEventBus modBus) {
        POI_TYPES.register(modBus);
    }
}
