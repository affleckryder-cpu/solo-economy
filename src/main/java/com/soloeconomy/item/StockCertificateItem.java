package com.soloeconomy.item;

import com.soloeconomy.market.EconomyAccount;
import com.soloeconomy.market.StockCertificates;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Found in loot; used, it unlocks a rare good at the market. See {@link StockCertificates}. */
public class StockCertificateItem extends Item {

    public StockCertificateItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            Item unlocked = StockCertificates.redeem(serverPlayer);
            serverPlayer.displayClientMessage(unlocked != null
                    ? Component.translatable("item.soloeconomy.stock_certificate.unlocked", unlocked.getDescription())
                            .withStyle(ChatFormatting.GREEN)
                    : Component.translatable("item.soloeconomy.stock_certificate.cashed",
                            EconomyAccount.format(StockCertificates.cashValue())).withStyle(ChatFormatting.GOLD), false);
            level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.soloeconomy.stock_certificate.desc").withStyle(ChatFormatting.GRAY));
    }
}
