package com.soloeconomy.market;

import com.soloeconomy.market.MarketCatalog.Slot;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads the fluids a Create-style recipe takes and gives, by method name so Create isn't a
 * dependency. A fluid is priced through its bucket: 250 mB of honey is a quarter of a honey
 * bucket, and the empty bucket comes back the way it does from a cake. Water is free.
 */
final class FluidReader {

    static final Parts NONE = new Parts(List.of(), List.of());
    private static final double BUCKET_MB = 1000.0D;

    private FluidReader() {
    }

    /** Both in buckets. An output has exactly one choice: the bucket of the fluid made. */
    record Parts(List<Slot> inputs, List<Slot> outputs) {
    }

    /** Null when the recipe takes a fluid that can't be priced, like a potion, which has no bucket. */
    @Nullable
    static Parts read(Recipe<?> recipe) {
        Collection<?> in = list(recipe, "getFluidIngredients");
        Collection<?> out = list(recipe, "getFluidResults");
        if (in.isEmpty() && out.isEmpty()) {
            return NONE;
        }

        List<Slot> inputs = new ArrayList<>();
        for (Object ingredient : in) {
            List<FluidStack> options = new ArrayList<>();
            int amount = options(ingredient, options);
            if (amount <= 0 || options.isEmpty()) {
                return null;
            }
            if (options.stream().anyMatch(FluidReader::isWater)) {
                continue;
            }
            Set<Item> buckets = new LinkedHashSet<>();
            for (FluidStack option : options) {
                if (option.getFluid().getBucket() != Items.AIR) {
                    buckets.add(option.getFluid().getBucket());
                }
            }
            if (buckets.isEmpty()) {
                return null;
            }
            inputs.add(new Slot(List.copyOf(buckets), amount / BUCKET_MB));
        }

        List<Slot> outputs = new ArrayList<>();
        for (Object made : out) {
            if (made instanceof FluidStack stack && stack.getAmount() > 0 && !isWater(stack)
                    && stack.getFluid().getBucket() != Items.AIR) {
                outputs.add(new Slot(List.of(stack.getFluid().getBucket()), stack.getAmount() / BUCKET_MB));
            }
        }
        return new Parts(inputs, outputs);
    }

    private static boolean isWater(FluidStack stack) {
        return stack.getFluid().isSame(Fluids.WATER);
    }

    /** Fills {@code into} with the fluids that fit this ingredient and returns the mB needed, 0 if unreadable. */
    private static int options(Object ingredient, List<FluidStack> into) {
        if (!(ingredient instanceof SizedFluidIngredient sized)) {
            return 0;
        }
        into.addAll(List.of(sized.getFluids()));
        return sized.amount();
    }

    private static Collection<?> list(Recipe<?> recipe, String getter) {
        try {
            return (Collection<?>) recipe.getClass().getMethod(getter).invoke(recipe);
        } catch (ReflectiveOperationException | ClassCastException e) {
            return List.of(); // not a recipe type with fluids
        }
    }
}
