package com.soloeconomy.client;

import com.soloeconomy.market.EconomyAccount;
import com.soloeconomy.menu.MarketMenu;
import com.soloeconomy.network.Listing;
import com.soloeconomy.network.MarketQueryPayload;
import com.soloeconomy.network.QuotePayload;
import com.soloeconomy.network.QuoteRequestPayload;
import com.soloeconomy.network.ServerMarketHandler;
import com.soloeconomy.network.TradePayload;
import com.soloeconomy.network.TransferPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The stall UI: a searchable, scrollable catalogue with a sell column and a buy column.
 *
 * <p>Prices shown here are quotes the server sent. Nothing on this screen is authoritative -
 * clicking sends only an item, a quantity and a direction, and the server re-prices the whole
 * trade before it happens.
 */
public class MarketScreen extends AbstractContainerScreen<MarketMenu> {

    private static final int VISIBLE_ROWS = 5;
    private static final int ROW_HEIGHT = 18;
    private static final int LIST_X = 8;
    private static final int LIST_Y = 58;
    private static final int LIST_WIDTH = 232;
    private static final int LIST_HEIGHT = VISIBLE_ROWS * ROW_HEIGHT;
    private static final int SCROLLBAR_X = LIST_X + LIST_WIDTH + 2;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int MIN_THUMB_HEIGHT = 14;
    private static final int SELL_COLUMN_X = 140;
    private static final int BUY_COLUMN_X = 192;
    private static final int COLUMN_WIDTH = 48;
    private static final int PREVIEW_Y = 152;
    private static final int CONTROLS_Y = 36;

    private static final int COLOR_PANEL = 0xFF1C1C20;
    private static final int COLOR_BORDER = 0xFF000000;
    private static final int COLOR_HEADER = 0xFF2A2A31;
    private static final int COLOR_ROW = 0xFF232329;
    private static final int COLOR_ROW_ALT = 0xFF26262D;
    private static final int COLOR_SLOT = 0xFF373737;
    private static final int COLOR_TRACK = 0xFF141418;
    private static final int COLOR_THUMB = 0xFF4C4C57;
    private static final int COLOR_THUMB_ACTIVE = 0xFF6E6E7C;
    private static final int COLOR_TEXT = 0xFFE6E6E6;
    private static final int COLOR_MUTED = 0xFF9A9AA2;
    private static final int COLOR_SELL = 0xFF7FD07F;
    private static final int COLOR_BUY = 0xFFE8B860;
    private static final int COLOR_DENIED = 0xFFD07070;
    private static final int COLOR_HOVER = 0x40FFFFFF;

    /** Quantities the buttons cycle through; -1 asks the server for "as many as possible". */
    private static final int[] QUANTITIES = {1, 8, 64, ServerMarketHandler.COUNT_MAX};

    private EditBox searchBox;
    private final List<Button> quantityButtons = new ArrayList<>();

    private String search = "";
    private int sort;
    private boolean inventoryOnly;
    private int quantityIndex = 2;

    private int scrollOffset;
    private boolean draggingScrollbar;

    /** The row the pointer is over, tracked so we only ask the server for a quote once per row. */
    @Nullable
    private Item hoveredItem;
    @Nullable
    private Item quoteRequestedFor;
    private int quoteRequestedQuantity = Integer.MIN_VALUE;

    public MarketScreen(MarketMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 256;
        this.imageHeight = 256;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 164;

        int left = leftPos;
        int top = topPos;

        searchBox = new EditBox(font, left + 8, top + 18, 132, 14, Component.translatable("gui.soloeconomy.search"));
        searchBox.setMaxLength(48);
        searchBox.setHint(Component.translatable("gui.soloeconomy.search_hint"));
        searchBox.setValue(search);
        searchBox.setResponder(value -> {
            search = value;
            scrollOffset = 0;
            requestRefresh();
        });
        addRenderableWidget(searchBox);

        addRenderableWidget(Button.builder(sortLabel(), button -> {
            sort = (sort + 1) % 3;
            button.setMessage(sortLabel());
            scrollOffset = 0;
            requestRefresh();
        }).bounds(left + 144, top + 18, 48, 14).build());

        addRenderableWidget(Button.builder(bagLabel(), button -> {
            inventoryOnly = !inventoryOnly;
            button.setMessage(bagLabel());
            scrollOffset = 0;
            requestRefresh();
        }).bounds(left + 196, top + 18, 52, 14).build());

        quantityButtons.clear();
        for (int i = 0; i < QUANTITIES.length; i++) {
            final int index = i;
            Button button = Button.builder(quantityLabel(i), b -> {
                quantityIndex = index;
                refreshQuantityLabels();
                // The preview is per-quantity, so it has to be asked for again.
                ClientMarketState.clearQuote();
                quoteRequestedFor = null;
            }).bounds(left + 28 + i * 24, top + CONTROLS_Y, 22, 12).build();
            quantityButtons.add(button);
            addRenderableWidget(button);
        }
        refreshQuantityLabels();

        // Deposit and withdraw both honour the quantity selector, so "All" banks everything you
        // are carrying or cashes out the whole account.
        addRenderableWidget(Button.builder(Component.translatable("gui.soloeconomy.deposit"),
                        b -> PacketDistributor.sendToServer(new TransferPayload(transferAmount(), true)))
                .tooltip(Tooltip.create(Component.translatable("gui.soloeconomy.deposit_tip")))
                .bounds(left + 128, top + CONTROLS_Y, 58, 12).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.soloeconomy.withdraw"),
                        b -> PacketDistributor.sendToServer(new TransferPayload(transferAmount(), false)))
                .tooltip(Tooltip.create(Component.translatable("gui.soloeconomy.withdraw_tip")))
                .bounds(left + 190, top + CONTROLS_Y, 58, 12).build());

        requestRefresh();
    }

    // ------------------------------------------------------------------
    // Server round trips
    // ------------------------------------------------------------------

    public void requestRefresh() {
        PacketDistributor.sendToServer(new MarketQueryPayload(search, sort, inventoryOnly));
    }

    /** Keeps the scroll position sane when the match set shrinks under us. */
    public void onListingsChanged() {
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll());
        quoteRequestedFor = null;
    }

    private int maxScroll() {
        return Math.max(0, ClientMarketState.listings().size() - VISIBLE_ROWS);
    }

    private void scrollBy(int rows) {
        scrollOffset = Mth.clamp(scrollOffset + rows, 0, maxScroll());
    }

    private int selectedQuantity() {
        return QUANTITIES[quantityIndex];
    }

    /** Emeralds to move on a deposit or withdrawal; -1 is the server's "as much as possible". */
    private int transferAmount() {
        int quantity = selectedQuantity();
        return quantity == ServerMarketHandler.COUNT_MAX ? -1 : quantity;
    }

    /** Ask for exact totals when the pointer lands on a new row, and not once per frame. */
    private void ensureQuote(Item item) {
        if (item == quoteRequestedFor && selectedQuantity() == quoteRequestedQuantity) {
            return;
        }
        quoteRequestedFor = item;
        quoteRequestedQuantity = selectedQuantity();
        PacketDistributor.sendToServer(new QuoteRequestPayload(item, selectedQuantity()));
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    @Nullable
    private Listing listingAtRow(int row) {
        List<Listing> listings = ClientMarketState.listings();
        int index = scrollOffset + row;
        return index >= 0 && index < listings.size() ? listings.get(index) : null;
    }

    private int rowAt(double mouseY) {
        int relative = (int) (mouseY - (topPos + LIST_Y));
        if (relative < 0 || relative >= LIST_HEIGHT) {
            return -1;
        }
        return relative / ROW_HEIGHT;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isOverScrollbar(mouseX, mouseY)) {
            draggingScrollbar = true;
            dragScrollbarTo(mouseY);
            return true;
        }

        int row = rowAt(mouseY);
        if (row >= 0) {
            Listing listing = listingAtRow(row);
            if (listing != null) {
                if (inColumn(mouseX, SELL_COLUMN_X)) {
                    PacketDistributor.sendToServer(
                            new TradePayload(listing.item(), selectedQuantity(), false));
                    return true;
                }
                if (inColumn(mouseX, BUY_COLUMN_X)) {
                    PacketDistributor.sendToServer(
                            new TradePayload(listing.item(), selectedQuantity(), true));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar) {
            dragScrollbarTo(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean isOverScrollbar(double mouseX, double mouseY) {
        double x = mouseX - leftPos;
        double y = mouseY - topPos;
        return x >= SCROLLBAR_X && x < SCROLLBAR_X + SCROLLBAR_WIDTH
                && y >= LIST_Y && y < LIST_Y + LIST_HEIGHT;
    }

    /** Maps a pointer position on the track to a scroll row, centring the thumb on the cursor. */
    private void dragScrollbarTo(double mouseY) {
        int max = maxScroll();
        if (max <= 0) {
            scrollOffset = 0;
            return;
        }
        int thumbHeight = thumbHeight();
        double travel = LIST_HEIGHT - thumbHeight;
        double local = mouseY - (topPos + LIST_Y) - thumbHeight / 2.0D;
        double fraction = travel <= 0.0D ? 0.0D : local / travel;
        scrollOffset = Mth.clamp((int) Math.round(fraction * max), 0, max);
    }

    private int thumbHeight() {
        int total = ClientMarketState.listings().size();
        if (total <= VISIBLE_ROWS) {
            return LIST_HEIGHT;
        }
        return Math.max(MIN_THUMB_HEIGHT, LIST_HEIGHT * VISIBLE_ROWS / total);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double y = mouseY - topPos;
        if (y >= LIST_Y && y < LIST_Y + LIST_HEIGHT) {
            scrollBy(scrollY > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Let the search box swallow typing, or "e" would slam the screen shut mid-word.
        if (searchBox != null && searchBox.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
            return searchBox.keyPressed(keyCode, scanCode, modifiers) || searchBox.canConsumeInput();
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (searchBox != null && searchBox.isFocused()) {
            return searchBox.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    private boolean inColumn(double mouseX, int columnX) {
        double x = mouseX - leftPos;
        return x >= columnX && x < columnX + COLUMN_WIDTH;
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = leftPos;
        int top = topPos;

        graphics.fill(left - 1, top - 1, left + imageWidth + 1, top + imageHeight + 1, COLOR_BORDER);
        graphics.fill(left, top, left + imageWidth, top + imageHeight, COLOR_PANEL);

        graphics.drawString(font, Component.translatable("gui.soloeconomy.quantity"),
                left + LIST_X, top + CONTROLS_Y + 2, COLOR_MUTED, false);

        graphics.fill(left + LIST_X, top + LIST_Y - 10, left + LIST_X + LIST_WIDTH, top + LIST_Y - 1, COLOR_HEADER);
        graphics.drawString(font, Component.translatable("gui.soloeconomy.column_item"),
                left + LIST_X + 3, top + LIST_Y - 8, COLOR_MUTED, false);
        drawCentered(graphics, Component.translatable("gui.soloeconomy.column_sell"),
                left + SELL_COLUMN_X + COLUMN_WIDTH / 2, top + LIST_Y - 8, COLOR_MUTED);
        drawCentered(graphics, Component.translatable("gui.soloeconomy.column_buy"),
                left + BUY_COLUMN_X + COLUMN_WIDTH / 2, top + LIST_Y - 8, COLOR_MUTED);

        renderRows(graphics, mouseX, mouseY);
        renderScrollbar(graphics, mouseX, mouseY);
        renderPreview(graphics);
        renderSlotBackgrounds(graphics);
    }

    private void renderRows(GuiGraphics graphics, int mouseX, int mouseY) {
        hoveredItem = null;

        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int y = topPos + LIST_Y + row * ROW_HEIGHT;
            graphics.fill(leftPos + LIST_X, y, leftPos + LIST_X + LIST_WIDTH, y + ROW_HEIGHT - 1,
                    (row & 1) == 0 ? COLOR_ROW : COLOR_ROW_ALT);

            Listing listing = listingAtRow(row);
            if (listing == null) {
                continue;
            }
            ItemStack stack = new ItemStack(listing.item());

            graphics.renderItem(stack, leftPos + LIST_X + 3, y + 1);

            String name = stack.getHoverName().getString();
            int maxWidth = SELL_COLUMN_X - (LIST_X + 24) - 4;
            graphics.drawString(font, font.plainSubstrByWidth(name, maxWidth),
                    leftPos + LIST_X + 24, y + 5, COLOR_TEXT, false);

            drawPriceCell(graphics, SELL_COLUMN_X, y, listing.sellPrice(), COLOR_SELL, mouseX, mouseY);
            drawPriceCell(graphics, BUY_COLUMN_X, y, listing.buyPrice(), COLOR_BUY, mouseX, mouseY);

            boolean overRow = mouseX >= leftPos + LIST_X && mouseX < leftPos + LIST_X + LIST_WIDTH
                    && mouseY >= y && mouseY < y + ROW_HEIGHT - 1;
            if (overRow) {
                hoveredItem = listing.item();
            }
        }

        if (hoveredItem != null) {
            ensureQuote(hoveredItem);
        }
    }

    private void drawPriceCell(GuiGraphics graphics, int columnX, int y, float price,
                               int color, int mouseX, int mouseY) {
        int x0 = leftPos + columnX;
        int x1 = x0 + COLUMN_WIDTH;
        boolean hovered = mouseX >= x0 && mouseX < x1 && mouseY >= y && mouseY < y + ROW_HEIGHT - 1;
        if (hovered) {
            graphics.fill(x0, y, x1, y + ROW_HEIGHT - 1, COLOR_HOVER);
        }
        drawCentered(graphics, Component.literal(formatPrice(price)),
                x0 + COLUMN_WIDTH / 2, y + 5, color);
    }

    private void renderScrollbar(GuiGraphics graphics, int mouseX, int mouseY) {
        int x0 = leftPos + SCROLLBAR_X;
        int x1 = x0 + SCROLLBAR_WIDTH;
        int y0 = topPos + LIST_Y;
        graphics.fill(x0, y0, x1, y0 + LIST_HEIGHT, COLOR_TRACK);

        int max = maxScroll();
        if (max <= 0) {
            return; // everything fits; no thumb to drag
        }

        int thumbHeight = thumbHeight();
        int travel = LIST_HEIGHT - thumbHeight;
        int thumbY = y0 + (int) Math.round((double) travel * scrollOffset / max);
        boolean active = draggingScrollbar || isOverScrollbar(mouseX, mouseY);
        graphics.fill(x0, thumbY, x1, thumbY + thumbHeight, active ? COLOR_THUMB_ACTIVE : COLOR_THUMB);
    }

    /**
     * The cost preview. Shows what the selected quantity would actually cost or pay, using totals
     * the server worked out by walking the price curve - not the unit price multiplied out, which
     * would be wrong for anything big enough to move the market.
     */
    private void renderPreview(GuiGraphics graphics) {
        int x = leftPos + LIST_X;
        int y = topPos + PREVIEW_Y;

        if (hoveredItem == null) {
            Component idle = ClientMarketState.truncated()
                    ? Component.translatable("gui.soloeconomy.truncated",
                            ClientMarketState.listings().size())
                    : Component.translatable("gui.soloeconomy.preview_idle",
                            ClientMarketState.listings().size(),
                            String.format("%.1f", ClientMarketState.spread() * 200.0F));
            graphics.drawString(font, idle, x, y, COLOR_MUTED, false);
            return;
        }

        QuotePayload quote = ClientMarketState.quoteFor(hoveredItem);
        if (quote == null) {
            graphics.drawString(font, Component.translatable("gui.soloeconomy.preview_pending"),
                    x, y, COLOR_MUTED, false);
            return;
        }

        Component sell = quote.sellCount() <= 0
                ? Component.translatable("gui.soloeconomy.preview_none")
                : Component.translatable("gui.soloeconomy.preview_sell",
                        quote.sellCount(), EconomyAccount.format(quote.sellTotal()));
        graphics.drawString(font, sell, x, y, quote.sellCount() <= 0 ? COLOR_MUTED : COLOR_SELL, false);

        Component buy = quote.buyCount() <= 0
                ? Component.translatable("gui.soloeconomy.preview_broke")
                : Component.translatable("gui.soloeconomy.preview_buy",
                        quote.buyCount(), EconomyAccount.format(quote.buyTotal()));
        String buyText = buy.getString();
        graphics.drawString(font, buyText, leftPos + imageWidth - 8 - font.width(buyText), y,
                quote.buyCount() <= 0 ? COLOR_DENIED : COLOR_BUY, false);
    }

    private void renderSlotBackgrounds(GuiGraphics graphics) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlot(graphics, MarketMenu.INVENTORY_X + col * 18, MarketMenu.INVENTORY_Y + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlot(graphics, MarketMenu.INVENTORY_X + col * 18, MarketMenu.HOTBAR_Y);
        }
    }

    private void drawSlot(GuiGraphics graphics, int x, int y) {
        int px = leftPos + x - 1;
        int py = topPos + y - 1;
        graphics.fill(px, py, px + 18, py + 18, COLOR_BORDER);
        graphics.fill(px + 1, py + 1, px + 17, py + 17, COLOR_SLOT);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, COLOR_TEXT, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, COLOR_MUTED, false);

        String balance = wholeBalanceLabel();
        graphics.drawString(font, balance, imageWidth - 8 - font.width(balance), 6, COLOR_SELL, false);
    }

    /** Whole emeralds only; the exact amount is one hover away. Floored, matching what Withdraw gives. */
    private String wholeBalanceLabel() {
        return Component.translatable("gui.soloeconomy.balance",
                ClientMarketState.balance() / EconomyAccount.CENTS_PER_EMERALD).getString();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderRowTooltip(graphics, mouseX, mouseY);
        renderBalanceTooltip(graphics, mouseX, mouseY);
        renderTooltip(graphics, mouseX, mouseY);
    }

    private void renderBalanceTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int right = leftPos + imageWidth - 8;
        int left = right - font.width(wholeBalanceLabel());
        if (mouseX >= left && mouseX < right && mouseY >= topPos + 5 && mouseY < topPos + 15) {
            graphics.renderTooltip(font, Component.translatable("gui.soloeconomy.balance",
                    EconomyAccount.format(ClientMarketState.balance())), mouseX, mouseY);
        }
    }

    private void renderRowTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int row = rowAt(mouseY);
        if (row < 0 || mouseX < leftPos + LIST_X || mouseX >= leftPos + LIST_X + LIST_WIDTH) {
            return;
        }
        Listing listing = listingAtRow(row);
        if (listing == null) {
            return;
        }

        List<Component> lines = new ArrayList<>();
        lines.add(new ItemStack(listing.item()).getHoverName());
        lines.add(supplyLine(listing.stockRatio()));
        lines.add(Component.translatable("gui.soloeconomy.tooltip_hint", quantityLabel(quantityIndex)));
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    /**
     * Turns the raw stock ratio into the only thing a player actually needs from it: whether this
     * is a good moment to sell or to buy.
     */
    private static Component supplyLine(float stockRatio) {
        if (stockRatio > 1.25F) {
            return Component.translatable("gui.soloeconomy.supply_glut");
        }
        if (stockRatio < 0.8F) {
            return Component.translatable("gui.soloeconomy.supply_short");
        }
        return Component.translatable("gui.soloeconomy.supply_normal");
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------

    private void drawCentered(GuiGraphics graphics, Component text, int centerX, int y, int color) {
        String value = text.getString();
        graphics.drawString(font, value, centerX - font.width(value) / 2, y, color, false);
    }

    /**
     * Cheap goods need decimals; expensive ones do not. Sub-emerald items get three, because at
     * two a whole stack of dirt can move the price without the display ever changing, which makes
     * a working market look broken.
     */
    private static String formatPrice(float price) {
        if (price >= 100.0F) {
            return String.format("%.0f", price);
        }
        if (price >= 10.0F) {
            return String.format("%.1f", price);
        }
        if (price >= 1.0F) {
            return String.format("%.2f", price);
        }
        return String.format("%.3f", price);
    }

    private Component sortLabel() {
        return switch (sort) {
            case 1 -> Component.translatable("gui.soloeconomy.sort_cheap");
            case 2 -> Component.translatable("gui.soloeconomy.sort_rich");
            default -> Component.translatable("gui.soloeconomy.sort_name");
        };
    }

    private Component bagLabel() {
        return inventoryOnly
                ? Component.translatable("gui.soloeconomy.filter_bag_on")
                : Component.translatable("gui.soloeconomy.filter_bag_off");
    }

    private static Component quantityLabel(int index) {
        int value = QUANTITIES[index];
        return value == ServerMarketHandler.COUNT_MAX
                ? Component.translatable("gui.soloeconomy.quantity_all")
                : Component.literal(String.valueOf(value));
    }

    /** Marks the active quantity with brackets, since plain Buttons have no toggled state. */
    private void refreshQuantityLabels() {
        for (int i = 0; i < quantityButtons.size(); i++) {
            Component label = quantityLabel(i);
            quantityButtons.get(i).setMessage(i == quantityIndex
                    ? Component.literal("[").append(label).append("]")
                    : label);
        }
    }
}
