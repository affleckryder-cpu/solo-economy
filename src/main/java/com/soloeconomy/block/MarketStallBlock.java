package com.soloeconomy.block;

import com.soloeconomy.menu.MarketMenu;
import com.soloeconomy.registry.ModProfessions;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

import java.util.List;
import java.util.Optional;

/**
 * The Market Stall. Right-click to open the market; a Broker villager who has claimed this stall
 * as their job site halves the spread, so hiring one is a real upgrade rather than decoration.
 */
public class MarketStallBlock extends HorizontalDirectionalBlock {

    /** How far from the stall a broker can wander and still count as staffing it. */
    private static final double BROKER_SEARCH_RADIUS = 8.0D;

    public MarketStallBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        boolean staffed = hasBrokerAssigned((ServerLevel) level, pos);
        NetworkHooks.openScreen((ServerPlayer) player, new SimpleMenuProvider(
                (windowId, inventory, p) -> new MarketMenu(windowId, inventory, pos, staffed),
                Component.translatable("container.soloeconomy.market")));
        return InteractionResult.CONSUME;
    }

    /**
     * True when a Broker villager has this exact block registered as their job site. Checking the
     * job-site memory rather than just proximity means a broker working a stall next door does not
     * quietly discount this one.
     */
    public static boolean hasBrokerAssigned(ServerLevel level, BlockPos pos) {
        AABB search = new AABB(pos).inflate(BROKER_SEARCH_RADIUS);
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class, search);

        for (Villager villager : villagers) {
            if (villager.getVillagerData().getProfession() != ModProfessions.BROKER.get()) {
                continue;
            }
            Optional<GlobalPos> jobSite = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
            if (jobSite.isPresent()
                    && jobSite.get().dimension() == level.dimension()
                    && jobSite.get().pos().equals(pos)) {
                return true;
            }
        }
        return false;
    }
}
