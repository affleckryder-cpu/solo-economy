package com.soloeconomy.menu;

import com.soloeconomy.config.EconomyConfig;
import com.soloeconomy.registry.ModBlocks;
import com.soloeconomy.registry.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Menu for the Market Stall.
 *
 * <p>The shop side is not made of slots - the catalogue is far too large for that, so listings and
 * trades travel as payloads instead. This menu exists to carry the player's inventory (you sell
 * out of it and buy into it), to scope trade packets to an open stall, and to remember whether the
 * stall this session is attached to has a broker working it.
 */
public class MarketMenu extends AbstractContainerMenu {

    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 174;
    public static final int HOTBAR_Y = 232;

    private static final int MAIN_SLOT_COUNT = 27;
    private static final int TOTAL_SLOT_COUNT = 36;

    private final ContainerLevelAccess access;
    private final BlockPos stallPos;
    private final boolean staffed;

    /** Client-side constructor: the client is told the spread by the server, not by the block. */
    public MarketMenu(int windowId, Inventory inventory) {
        this(windowId, inventory, BlockPos.ZERO, false);
    }

    public MarketMenu(int windowId, Inventory inventory, BlockPos stallPos, boolean staffed) {
        super(ModMenus.MARKET.get(), windowId);
        this.stallPos = stallPos;
        this.staffed = staffed;
        this.access = inventory.player.level().isClientSide()
                ? ContainerLevelAccess.NULL
                : ContainerLevelAccess.create(inventory.player.level(), stallPos);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, 9 + row * 9 + col,
                        INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INVENTORY_X + col * 18, HOTBAR_Y));
        }
    }

    public BlockPos stallPos() {
        return stallPos;
    }

    public boolean isStaffed() {
        return staffed;
    }

    /** Half-spread this stall trades at. Authoritative on the server. */
    public double spread() {
        return EconomyConfig.INSTANCE.effectiveSpread(staffed);
    }

    @Override
    public boolean stillValid(Player player) {
        if (access == ContainerLevelAccess.NULL) {
            return true;
        }
        return AbstractContainerMenu.stillValid(access, player, ModBlocks.MARKET_STALL.get());
    }

    /** Only player inventory slots exist here, so shift-click just swaps between the two halves. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index < MAIN_SLOT_COUNT) {
            if (!moveItemStackTo(stack, MAIN_SLOT_COUNT, TOTAL_SLOT_COUNT, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, MAIN_SLOT_COUNT, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }
}
