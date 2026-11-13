package com.soloeconomy.market;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;

import javax.annotation.Nullable;

import java.util.Comparator;
import java.util.Optional;

/**
 * One thing the market trades. Usually just an item, but every potion is the same item with
 * different contents, so a potion is the item plus which potion is inside: a Potion of Healing and
 * a Potion of Swiftness are different goods with different prices.
 *
 * @param potion the potion inside, for potions, splash and lingering potions and tipped arrows;
 *               null for everything else
 */
public record Good(Item item, @Nullable Holder<Potion> potion) {

    public static final StreamCodec<RegistryFriendlyByteBuf, Good> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.registry(Registries.ITEM), Good::item,
            ByteBufCodecs.optional(ByteBufCodecs.holderRegistry(Registries.POTION)), good -> Optional.ofNullable(good.potion()),
            (item, potion) -> new Good(item, potion.orElse(null)));

    /** Item id, then potions in registry order, so each potion's variants sit together. */
    public static final Comparator<Good> ORDER = Comparator
            .comparing((Good good) -> BuiltInRegistries.ITEM.getKey(good.item()))
            .thenComparingInt(good -> good.potion() == null ? -1 : BuiltInRegistries.POTION.getId(good.potion().value()));

    public Good {
        // A potion read off a stack, a packet or the registry must all be the same holder, or two
        // Potions of Healing would not be equal.
        if (potion != null) {
            potion = potion.unwrapKey().<Holder<Potion>>map(BuiltInRegistries.POTION::getHolderOrThrow).orElse(potion);
        }
    }

    public static Good of(Item item) {
        return new Good(item, null);
    }

    public static Good potion(Item item, Holder<Potion> potion) {
        return new Good(item, potion);
    }

    /**
     * The good this stack is, or null if it carries anything that changes what it's worth.
     *
     * <p>That one rule covers enchanted gear, renamed items, damaged tools and - importantly -
     * shulker boxes with things inside, any of which would otherwise be bought or sold at the
     * price of a plain one. The one change allowed is a potion's contents, and only a plain
     * potion: custom effects or colours make it something the market has no price for.
     */
    @Nullable
    public static Good of(ItemStack stack) {
        if (stack.isEmpty() || stack.isDamaged()) {
            return null;
        }
        if (stack.getComponentsPatch().isEmpty()) {
            return of(stack.getItem());
        }
        if (stack.getComponentsPatch().size() != 1 || !holdsPotion(stack.getItem())) {
            return null;
        }
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null || contents.potion().isEmpty() || contents.customColor().isPresent()
                || !contents.customEffects().isEmpty()) {
            return null;
        }
        return new Good(stack.getItem(), contents.potion().get());
    }

    /** Potions, splash and lingering potions, tipped arrows, and any modded item built the same way. */
    public static boolean holdsPotion(Item item) {
        return item.components().has(DataComponents.POTION_CONTENTS);
    }

    public ItemStack stack(int count) {
        ItemStack stack = new ItemStack(item, count);
        if (potion != null) {
            stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));
        }
        return stack;
    }

    public Component name() {
        return stack(1).getHoverName();
    }

    public int maxStackSize() {
        return stack(1).getMaxStackSize();
    }

    /**
     * For saving: {@code minecraft:diamond}, or {@code minecraft:potion[minecraft:healing]} for a
     * potion. A plain item's key is its id, so what older versions saved still matches.
     */
    public String key() {
        String item = BuiltInRegistries.ITEM.getKey(this.item).toString();
        return potion == null ? item : item + "[" + BuiltInRegistries.POTION.getKey(potion.value()) + "]";
    }
}
