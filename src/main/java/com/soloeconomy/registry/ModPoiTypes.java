package com.soloeconomy.registry;

import com.google.common.collect.ImmutableSet;
import com.soloeconomy.SoloEconomy;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Market Stall is a point of interest so that villagers path to it and claim it as a job site,
 * exactly like a lectern or a smithing table. {@code maxTickets = 1} means one broker per stall.
 */
public final class ModPoiTypes {

    public static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, SoloEconomy.MOD_ID);

    public static final DeferredHolder<PoiType, PoiType> MARKET_STALL = POI_TYPES.register("market_stall",
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
