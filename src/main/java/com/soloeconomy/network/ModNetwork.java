package com.soloeconomy.network;

import com.soloeconomy.client.ClientMarketHandler;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Payload registration.
 *
 * <p>Client-bound handlers are dispatched through private forwarding methods rather than direct
 * method references to {@code ClientMarketHandler}. That keeps the client-only class from being
 * linked when the dedicated server registers its payloads.
 */
public final class ModNetwork {

    private static final String PROTOCOL_VERSION = "1";

    private ModNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        registrar.playToServer(MarketQueryPayload.TYPE, MarketQueryPayload.STREAM_CODEC,
                ServerMarketHandler::handleQuery);
        registrar.playToServer(QuoteRequestPayload.TYPE, QuoteRequestPayload.STREAM_CODEC,
                ServerMarketHandler::handleQuoteRequest);
        registrar.playToServer(TradePayload.TYPE, TradePayload.STREAM_CODEC,
                ServerMarketHandler::handleTrade);
        registrar.playToServer(TransferPayload.TYPE, TransferPayload.STREAM_CODEC,
                ServerMarketHandler::handleTransfer);

        registrar.playToClient(MarketListingsPayload.TYPE, MarketListingsPayload.STREAM_CODEC,
                ModNetwork::forwardListings);
        registrar.playToClient(QuotePayload.TYPE, QuotePayload.STREAM_CODEC,
                ModNetwork::forwardQuote);
        registrar.playToClient(BalanceSyncPayload.TYPE, BalanceSyncPayload.STREAM_CODEC,
                ModNetwork::forwardBalance);
    }

    private static void forwardListings(MarketListingsPayload payload, IPayloadContext context) {
        ClientMarketHandler.handleListings(payload, context);
    }

    private static void forwardQuote(QuotePayload payload, IPayloadContext context) {
        ClientMarketHandler.handleQuote(payload, context);
    }

    private static void forwardBalance(BalanceSyncPayload payload, IPayloadContext context) {
        ClientMarketHandler.handleBalance(payload, context);
    }
}
