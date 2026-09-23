package com.soloeconomy.menu;

import com.soloeconomy.config.EconomyConfig;
import com.soloeconomy.registry.ModBlocks;
import com.soloeconomy.registry.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

/**
 * Menu for the Market Stall.
 *
 * <p>It has no slots. The catalogue travels as payloads, and the player's own goods are browsed
 * through the "Your items" tab rather than an inventory grid. The menu exists to scope trade
 * packets to an open stall and to remember whether a broker is working it.
 */
public class MarketMenu extends AbstractContainerMenu {

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

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY; // no slots to move between
    }
}
