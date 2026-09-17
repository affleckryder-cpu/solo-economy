package com.soloeconomy.registry;

import com.google.common.collect.ImmutableSet;
import com.soloeconomy.SoloEconomy;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Broker: a villager profession whose job site is the Market Stall.
 *
 * <p>Brokers deliberately have no vanilla trade offers. The stall itself is the interface, so the
 * villager is there to make the stall feel staffed and to gate it behind the usual find-a-villager,
 * place-a-job-block loop rather than to haggle over emeralds.
 */
public final class ModProfessions {

    public static final DeferredRegister<VillagerProfession> PROFESSIONS =
            DeferredRegister.create(Registries.VILLAGER_PROFESSION, SoloEconomy.MOD_ID);

    public static final DeferredHolder<VillagerProfession, VillagerProfession> BROKER =
            PROFESSIONS.register("broker", () -> new VillagerProfession(
                    "broker",
                    holder -> holder.value() == ModPoiTypes.MARKET_STALL.get(),
                    holder -> holder.value() == ModPoiTypes.MARKET_STALL.get(),
                    ImmutableSet.of(),
                    ImmutableSet.of(),
                    SoundEvents.VILLAGER_WORK_CARTOGRAPHER));

    private ModProfessions() {
    }

    public static void register(IEventBus modBus) {
        PROFESSIONS.register(modBus);
    }
}
