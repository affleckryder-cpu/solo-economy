package com.soloeconomy.market;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.TippedArrowItem;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;

import javax.annotation.Nullable;

import java.util.Comparator;

/**
 * One thing the market trades. Usually just an item, but every potion is the same item with
 * different contents, so a potion is the item plus which potion is inside: a Potion of Healing and
 * a Potion of Swiftness are different goods with different prices.
 *
 * @param potion the potion inside, for potions, splash and lingering potions and tipped arrows;
 *               null for everything else
 */
public record Good(Item item, @Nullable Potion potion) {

    /** Item id, then potions in registry order, so each potion's variants sit together. */
    public static final Comparator<Good> ORDER = Comparator
            .comparing((Good good) -> BuiltInRegistries.ITEM.getKey(good.item()))
            .thenComparingInt(good -> good.potion() == null ? -1 : BuiltInRegistries.POTION.getId(good.potion()));

    public static Good of(Item item) {
        return new Good(item, null);
    }

    public static Good potion(Item item, Potion potion) {
        return new Good(item, potion);
    }

    /**
     * The good this stack is, or null if it carries anything that changes what it's worth.
     *
     * <p>That one rule covers enchanted gear, renamed items, damaged tools and - importantly -
     * shulker boxes with things inside, any of which would otherwise be bought or sold at the
     * price of a plain one. A tool repaired back to full keeps a bare "Damage: 0" tag, which
     * doesn't count. The one other tag allowed is a potion's, and only a plain potion: custom
     * effects or colours make it something the market has no price for.
     */
    @Nullable
    public static Good of(ItemStack stack) {
        if (stack.isEmpty() || stack.isDamaged()) {
            return null;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || tag.isEmpty() || (tag.size() == 1 && tag.contains(ItemStack.TAG_DAMAGE))) {
            return of(stack.getItem());
        }
        if (tag.size() != 1 || !tag.contains(PotionUtils.TAG_POTION, Tag.TAG_STRING) || !holdsPotion(stack.getItem())) {
            return null;
        }
        Potion potion = PotionUtils.getPotion(tag);
        return potion == Potions.EMPTY ? null : new Good(stack.getItem(), potion);
    }

    /** Potions, splash and lingering potions, and tipped arrows. */
    public static boolean holdsPotion(Item item) {
        return item instanceof PotionItem || item instanceof TippedArrowItem;
    }

    public ItemStack stack(int count) {
        ItemStack stack = new ItemStack(item, count);
        if (potion != null) {
            PotionUtils.setPotion(stack, potion);
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
     * potion. A plain item's key is its id, which is what older versions saved, so they still match.
     */
    public String key() {
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        return potion == null ? id : id + "[" + BuiltInRegistries.POTION.getKey(potion) + "]";
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeId(BuiltInRegistries.ITEM, item);
        buf.writeBoolean(potion != null);
        if (potion != null) {
            buf.writeId(BuiltInRegistries.POTION, potion);
        }
    }

    public static Good decode(FriendlyByteBuf buf) {
        Item item = buf.readById(BuiltInRegistries.ITEM);
        return new Good(item, buf.readBoolean() ? buf.readById(BuiltInRegistries.POTION) : null);
    }
}
