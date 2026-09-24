package com.soloeconomy.registry;

import com.mojang.serialization.Codec;
import com.soloeconomy.SoloEconomy;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The player's emerald account. Kept as an attachment rather than as inventory emeralds so a
 * cross-continent diamond sale does not hand you fifteen stacks of currency.
 */
public final class ModAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, SoloEconomy.MOD_ID);

    /** The account, in hundredths of an emerald. Money is never stored as a fraction. */
    public static final Supplier<AttachmentType<Long>> BALANCE_CENTS = ATTACHMENT_TYPES.register("balance_cents",
            () -> AttachmentType.builder(() -> 0L)
                    .serialize(Codec.LONG)
                    .copyOnDeath()
                    .build());

    /**
     * Balances from 0.1.0, in whole emeralds. Only read once, to convert an existing account;
     * see ServerEvents. Remove after a release or two, once nobody is upgrading from 0.1.0.
     */
    public static final Supplier<AttachmentType<Long>> LEGACY_BALANCE = ATTACHMENT_TYPES.register("balance",
            () -> AttachmentType.builder(() -> 0L)
                    .serialize(Codec.LONG)
                    .copyOnDeath()
                    .build());

    /**
     * Items this player has unlocked for buying; see Discovery. Stored as ids, not items, so a
     * removed mod's items don't make the whole set fail to load.
     */
    public static final Supplier<AttachmentType<Set<ResourceLocation>>> DISCOVERED = ATTACHMENT_TYPES.register("discovered",
            () -> AttachmentType.<Set<ResourceLocation>>builder(() -> new HashSet<>())
                    .serialize(ResourceLocation.CODEC.listOf().<Set<ResourceLocation>>xmap(HashSet::new, List::copyOf))
                    .copyOnDeath()
                    .build());

    private ModAttachments() {
    }

    public static void register(IEventBus modBus) {
        ATTACHMENT_TYPES.register(modBus);
    }
}
