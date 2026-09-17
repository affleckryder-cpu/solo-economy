package com.soloeconomy.registry;

import com.soloeconomy.SoloEconomy;
import com.soloeconomy.block.MarketStallBlock;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SoloEconomy.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SoloEconomy.MOD_ID);

    public static final DeferredBlock<Block> MARKET_STALL = BLOCKS.register("market_stall",
            () -> new MarketStallBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)
                    .ignitedByLava()));

    public static final DeferredItem<BlockItem> MARKET_STALL_ITEM =
            ITEMS.registerSimpleBlockItem("market_stall", MARKET_STALL, new Item.Properties());

    private ModBlocks() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }
}
