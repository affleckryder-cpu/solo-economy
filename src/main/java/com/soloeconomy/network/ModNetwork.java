package com.soloeconomy.network;

import com.soloeconomy.SoloEconomy;
import com.soloeconomy.client.ClientMarketHandler;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Packet registration. Every handler runs on the main thread.
 *
 * <p>Client-bound handlers go through {@link DistExecutor} so the client-only handler class is
 * never linked on a dedicated server.
 */
public final class ModNetwork {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(SoloEconomy.MOD_ID, "main"),
            () -> PROTOCOL_VERSION, PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals);

    private static int nextId;

    private ModNetwork() {
    }

    public static void register() {
        toServer(MarketQueryPayload.class, MarketQueryPayload::encode, MarketQueryPayload::decode,
                ServerMarketHandler::handleQuery);
        toServer(QuoteRequestPayload.class, QuoteRequestPayload::encode, QuoteRequestPayload::decode,
                ServerMarketHandler::handleQuoteRequest);
        toServer(TradePayload.class, TradePayload::encode, TradePayload::decode,
                ServerMarketHandler::handleTrade);
        toServer(TransferPayload.class, TransferPayload::encode, TransferPayload::decode,
                ServerMarketHandler::handleTransfer);

        toClient(MarketListingsPayload.class, MarketListingsPayload::encode, MarketListingsPayload::decode,
                payload -> ClientMarketHandler.handleListings(payload));
        toClient(QuotePayload.class, QuotePayload::encode, QuotePayload::decode,
                payload -> ClientMarketHandler.handleQuote(payload));
        toClient(BalanceSyncPayload.class, BalanceSyncPayload::encode, BalanceSyncPayload::decode,
                payload -> ClientMarketHandler.handleBalance(payload));
    }

    public static void sendTo(ServerPlayer player, Object payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }

    private static <T> void toServer(Class<T> type, BiConsumer<T, FriendlyByteBuf> encoder,
                                     Function<FriendlyByteBuf, T> decoder, BiConsumer<T, ServerPlayer> handler) {
        CHANNEL.registerMessage(nextId++, type, encoder, decoder, (payload, context) -> {
            context.get().enqueueWork(() -> {
                ServerPlayer sender = context.get().getSender();
                if (sender != null) {
                    handler.accept(payload, sender);
                }
            });
            context.get().setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    private static <T> void toClient(Class<T> type, BiConsumer<T, FriendlyByteBuf> encoder,
                                     Function<FriendlyByteBuf, T> decoder, Consumer<T> handler) {
        CHANNEL.registerMessage(nextId++, type, encoder, decoder, (payload, context) -> {
            context.get().enqueueWork(() ->
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handler.accept(payload)));
            context.get().setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
}
