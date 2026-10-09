package com.soloeconomy.client;

import com.soloeconomy.market.BrokerDeals;
import com.soloeconomy.market.EconomyAccount;
import com.soloeconomy.menu.MarketMenu;
import com.soloeconomy.network.Listing;
import com.soloeconomy.network.MarketListingsPayload;
import com.soloeconomy.network.MarketQueryPayload;
import com.soloeconomy.network.QuotePayload;
import com.soloeconomy.network.QuoteRequestPayload;
import com.soloeconomy.network.ServerMarketHandler;
import com.soloeconomy.network.TradePayload;
import com.soloeconomy.network.TransferPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The stall UI, styled after the console edition menus: merchants down the left, the chosen
 * merchant's goods as rows with their sell and buy prices, and one action bar at the bottom.
 *
 * <p>Rows, icons and names are built once when a listing arrives rather than every frame.
 * Prices shown are the server's quotes; clicking sends only an item, a quantity and a direction.
 */
public class MarketScreen extends AbstractContainerScreen<MarketMenu> {

    private static final ResourceLocation PANEL = ResourceLocation.withDefaultNamespace("textures/gui/container/villager.png");

    private static final int WIDTH = 316;
    private static final int HEIGHT = 230;

    private static final int SIDE_X = 6;
    private static final int SIDE_WIDTH = 88;
    private static final int BOX_Y = 24;
    private static final int BOX_HEIGHT = 176;
    private static final int COLOR_DEAL = 0xFFFFD83D;

    private static final int LIST_X = 98;
    private static final int LIST_WIDTH = 212;
    private static final int ROWS_Y = 72;
    private static final int ROW_HEIGHT = 18;
    private static final int ROWS = 7;
    private static final int SELL_RIGHT = 250;
    private static final int BUY_RIGHT = 296;

    /** The inventory panel to the right: 36 slots, four across, main inventory first then hotbar. */
    private static final int INV_X = WIDTH + 4;
    private static final int INV_WIDTH = 90;
    private static final int INV_COLUMNS = 4;
    private static final int SLOT = 18;
    private static final int SLOTS_Y = 27;

    private static final int BAR_Y = 205;
    private static final int CHIP_X = 68;
    private static final int CHIP_PITCH = 21;
    private static final int BUTTON_WIDTH = 77;

    private static final int COLOR_BOX = 0xFF2B2B2B;
    private static final int COLOR_BOX_EDGE = 0xFF141414;
    private static final int COLOR_TITLE = 0xFF404040;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_MUTED = 0xFF9A9A9A;
    private static final int COLOR_SELL = 0xFF55FF55;
    private static final int COLOR_BUY = 0xFFFFAA00;
    private static final int COLOR_ACCENT = 0xFF3DDC84;
    private static final int COLOR_LOCKED = 0xFF626262;

    /** Quantities to trade; -1 asks the server for "as many as possible". */
    private static final int[] QUANTITIES = {1, 8, 64, ServerMarketHandler.COUNT_MAX};
    private static final String BANK = "bank";
    private static final Component LOCKED = Component.translatable("gui.soloeconomy.locked");

    /** A listing with its icon and name resolved once, so drawing a row allocates nothing. */
    private record Row(Listing listing, ItemStack stack, String name) {
    }

    private record Entry(String id, ItemStack icon, String name) {
    }

    private List<Row> rows = List.of();
    private List<Entry> entries = List.of();

    private EditBox searchBox;
    private Button leftButton;
    private Button rightButton;

    private String merchant = "";
    private String search = "";
    private boolean carryOnly;
    private int quantityIndex = 2;
    private int scrollRow;
    private boolean draggingScrollbar;

    @Nullable
    private Row selected;
    /** An item clicked in the inventory panel, selected once its search results arrive. */
    @Nullable
    private Item pendingSelect;
    @Nullable
    private Item quoteRequestedFor;
    private int quoteRequestedQuantity = Integer.MIN_VALUE;

    public MarketScreen(MarketMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = INV_X + INV_WIDTH;
        this.imageHeight = HEIGHT;
    }

    @Override
    protected void init() {
        super.init();

        searchBox = new EditBox(font, leftPos + 200, topPos + 9, 104, 10, Component.translatable("gui.soloeconomy.search"));
        searchBox.setBordered(false);
        searchBox.setMaxLength(48);
        searchBox.setHint(Component.translatable("gui.soloeconomy.search_hint").withStyle(ChatFormatting.DARK_GRAY));
        searchBox.setValue(search);
        searchBox.setResponder(value -> {
            search = value;
            scrollRow = 0;
            selected = null;
            requestRefresh();
        });
        addRenderableWidget(searchBox);

        // Two action buttons: Sell and Buy for a merchant, Deposit and Withdraw at the bank.
        leftButton = addRenderableWidget(Button.builder(Component.empty(), b -> act(false))
                .bounds(leftPos + WIDTH - 8 - 2 * BUTTON_WIDTH - 2, topPos + BAR_Y, BUTTON_WIDTH, 18).build());
        rightButton = addRenderableWidget(Button.builder(Component.empty(), b -> act(true))
                .bounds(leftPos + WIDTH - 6 - BUTTON_WIDTH, topPos + BAR_Y, BUTTON_WIDTH, 18).build());

        rebuildRows();
        requestRefresh();
    }

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    private boolean atBank() {
        return BANK.equals(merchant) && search.isEmpty();
    }

    public void requestRefresh() {
        if (!atBank()) {
            PacketDistributor.sendToServer(new MarketQueryPayload(search, carryOnly, merchant));
        }
    }

    public void onListingsChanged() {
        rebuildRows();
        quoteRequestedFor = null;
    }

    private void rebuildRows() {
        List<Entry> newEntries = new ArrayList<>();
        for (MarketListingsPayload.Merchant m : ClientMarketState.merchants()) {
            Entry entry = new Entry(m.id(), new ItemStack(m.icon()), sidebarName(merchantName(m.id())));
            // Today's deals lead the list; the server sends them last so the default merchant stays first.
            newEntries.add(BrokerDeals.MERCHANT_ID.equals(m.id()) ? 0 : newEntries.size(), entry);
        }
        newEntries.add(new Entry(BANK, new ItemStack(Items.EMERALD), sidebarName(Component.translatable("gui.soloeconomy.bank"))));
        entries = newEntries;
        // Until the first listing arrives there are no merchants; landing on the bank then would never query.
        if (merchant.isEmpty() && !ClientMarketState.merchants().isEmpty()) {
            merchant = ClientMarketState.merchants().get(0).id();
        }

        Item keep = pendingSelect != null ? pendingSelect : selected == null ? null : selected.listing().item();
        List<Row> newRows = new ArrayList<>();
        selected = null;
        for (Listing listing : ClientMarketState.listings()) {
            ItemStack stack = new ItemStack(listing.item());
            Row row = new Row(listing, stack, stack.getHoverName().getString());
            newRows.add(row);
            if (listing.item() == keep) {
                selected = row;
            }
        }
        rows = newRows;
        scrollRow = Mth.clamp(scrollRow, 0, maxScroll());
        if (selected != null && pendingSelect != null) {
            int index = rows.indexOf(selected);
            scrollRow = Mth.clamp(scrollRow, index - ROWS + 1, index);
        }
        pendingSelect = null;
    }

    private void selectMerchant(String id) {
        if (id.equals(merchant) && search.isEmpty()) {
            return;
        }
        merchant = id;
        rows = List.of(); // don't flash the previous merchant's goods while the new ones arrive
        searchBox.setValue(""); // its responder clears the selection and re-queries
        setFocused(null); // picking a merchant ends the search, so give the hint back
    }

    private int maxScroll() {
        return Math.max(0, rows.size() - ROWS);
    }

    private int quantity() {
        return QUANTITIES[quantityIndex];
    }

    private void act(boolean right) {
        if (atBank()) {
            int amount = quantity() == ServerMarketHandler.COUNT_MAX ? -1 : quantity();
            PacketDistributor.sendToServer(new TransferPayload(amount, !right));
        } else if (selected != null) {
            PacketDistributor.sendToServer(new TradePayload(selected.listing().item(), quantity(), right));
        }
    }

    private void ensureQuote(Item item) {
        if (item == quoteRequestedFor && quantity() == quoteRequestedQuantity) {
            return;
        }
        quoteRequestedFor = item;
        quoteRequestedQuantity = quantity();
        PacketDistributor.sendToServer(new QuoteRequestPayload(item, quantity()));
    }

    private int countHeld(Item item) {
        int total = 0;
        for (ItemStack stack : minecraft.player.getInventory().items) {
            if (stack.is(item) && ServerMarketHandler.isTradeableStack(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Nullable
    private Row rowAt(double mx, double my) {
        if (!inside(mx, my, leftPos + LIST_X + 2, topPos + ROWS_Y, LIST_WIDTH - 10, ROWS * ROW_HEIGHT)) {
            return null;
        }
        int index = scrollRow + (int) (my - topPos - ROWS_Y) / ROW_HEIGHT;
        return index < rows.size() ? rows.get(index) : null;
    }

    // The sidebar is three groups: today's deals on top (when a Broker works the stall), the
    // merchants, and the bank pinned to the bottom. A divider sits between each.
    private static final int DIVIDER = 5;

    private boolean hasDeals() {
        return !entries.isEmpty() && BrokerDeals.MERCHANT_ID.equals(entries.get(0).id());
    }

    /** As tall as fits, so a long merchant list (open market, datapacks) still shows the bank. */
    private int entryHeight() {
        int room = BOX_HEIGHT - 4 - DIVIDER - (hasDeals() ? DIVIDER : 0);
        return Mth.clamp(room / Math.max(1, entries.size()), 9, 13);
    }

    /** Top of entry {@code i}, relative to the screen. The bank is always last and sits at the bottom. */
    private int entryY(int i) {
        if (i == entries.size() - 1) {
            return topPos + BOX_Y + BOX_HEIGHT - 2 - entryHeight();
        }
        return topPos + BOX_Y + 2 + i * entryHeight() + (hasDeals() && i > 0 ? DIVIDER : 0);
    }

    private int entryAt(double mx, double my) {
        for (int i = 0; i < entries.size(); i++) {
            if (inside(mx, my, leftPos + SIDE_X + 2, entryY(i), SIDE_WIDTH - 4, entryHeight())) {
                return i;
            }
        }
        return -1;
    }

    private int chipAt(double mx, double my) {
        if (!inside(mx, my, leftPos + CHIP_X, topPos + BAR_Y + 1, QUANTITIES.length * CHIP_PITCH, 16)) {
            return -1;
        }
        int index = (int) (mx - leftPos - CHIP_X) / CHIP_PITCH;
        return (mx - leftPos - CHIP_X) % CHIP_PITCH < CHIP_PITCH - 2 ? index : -1;
    }

    private boolean overToggle(double mx, double my) {
        return !atBank() && inside(mx, my, leftPos + LIST_X + 4, topPos + BOX_Y + 36, 12 + font.width(Component.translatable("gui.soloeconomy.carry_only")), 10);
    }

    /** Inventory slot under the pointer, as an index into Inventory.items, or -1. */
    private int slotAt(double mx, double my) {
        int x0 = leftPos + INV_X + 9;
        int y0 = topPos + SLOTS_Y;
        if (!inside(mx, my, x0, y0, INV_COLUMNS * SLOT, 9 * SLOT)) {
            return -1;
        }
        int shown = (int) (my - y0) / SLOT * INV_COLUMNS + (int) (mx - x0) / SLOT;
        return shown < 27 ? shown + 9 : shown - 27; // main inventory first, hotbar last, as in vanilla
    }

    /** Selling from the inventory panel needs a clean, unenchanted stack that some merchant buys. */
    @Nullable
    private static Listing sellable(ItemStack stack) {
        return ServerMarketHandler.isTradeableStack(stack) ? ClientMarketState.carried(stack.getItem()) : null;
    }

    private boolean overScrollbar(double mx, double my) {
        return inside(mx, my, leftPos + LIST_X + LIST_WIDTH - 8, topPos + ROWS_Y, 6, ROWS * ROW_HEIGHT);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int slot = slotAt(mx, my);
        if (slot >= 0) {
            ItemStack stack = minecraft.player.getInventory().items.get(slot);
            if (sellable(stack) != null) {
                if (hasShiftDown()) {
                    PacketDistributor.sendToServer(new TradePayload(stack.getItem(), ServerMarketHandler.COUNT_MAX, false));
                } else {
                    // Search for it: that lists every merchant who deals in it, and selects it there.
                    pendingSelect = stack.getItem();
                    searchBox.setValue(stack.getHoverName().getString());
                    setFocused(null);
                }
            }
            return true;
        }
        int entry = entryAt(mx, my);
        if (entry >= 0 && entry < entries.size()) {
            selectMerchant(entries.get(entry).id());
            return true;
        }
        int chip = chipAt(mx, my);
        if (chip >= 0) {
            quantityIndex = chip;
            ClientMarketState.clearQuote(); // totals are per quantity; don't show the old ones
            return true;
        }
        if (overToggle(mx, my)) {
            carryOnly = !carryOnly;
            scrollRow = 0;
            requestRefresh();
            return true;
        }
        if (overScrollbar(mx, my)) {
            draggingScrollbar = true;
            dragScrollbarTo(my);
            return true;
        }
        Row row = rowAt(mx, my);
        if (row != null) {
            selected = row;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) {
        if (draggingScrollbar) {
            dragScrollbarTo(my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        draggingScrollbar = false;
        return super.mouseReleased(mx, my, button);
    }

    private void dragScrollbarTo(double my) {
        double fraction = (my - topPos - ROWS_Y) / (ROWS * ROW_HEIGHT);
        scrollRow = Mth.clamp((int) Math.round(fraction * maxScroll()), 0, maxScroll());
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (inside(mx, my, leftPos + LIST_X, topPos + BOX_Y, LIST_WIDTH, BOX_HEIGHT)) {
            scrollRow = Mth.clamp(scrollRow + (scrollY > 0 ? -1 : 1), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Let the search box swallow typing, or "e" would slam the screen shut mid-word.
        if (searchBox.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
            return searchBox.keyPressed(keyCode, scanCode, modifiers) || searchBox.canConsumeInput();
        }
        // Console-style: arrow keys walk the list.
        if ((keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) && !rows.isEmpty()) {
            int index = selected == null ? -1 : rows.indexOf(selected);
            index = Mth.clamp(index + (keyCode == GLFW.GLFW_KEY_UP ? -1 : 1), 0, rows.size() - 1);
            selected = rows.get(index);
            scrollRow = Mth.clamp(scrollRow, index - ROWS + 1, index);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (searchBox.isFocused()) {
            return searchBox.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateButtons();
        super.render(graphics, mouseX, mouseY, partialTick);
        renderHoverTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        drawPanel(g, leftPos, topPos, WIDTH, HEIGHT);
        drawBox(g, leftPos + 196, topPos + 6, 112, 14);
        drawBox(g, leftPos + SIDE_X, topPos + BOX_Y, SIDE_WIDTH, BOX_HEIGHT);
        drawBox(g, leftPos + LIST_X, topPos + BOX_Y, LIST_WIDTH, BOX_HEIGHT);

        renderSidebar(g, mouseX, mouseY);
        if (atBank()) {
            renderBank(g);
        } else {
            renderListing(g, mouseX, mouseY);
        }
        renderBar(g, mouseX, mouseY);
        renderInventory(g, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 10, 9, COLOR_TITLE, false);
        Component fee = Component.translatable("gui.soloeconomy.fee", String.format("%.1f", ClientMarketState.spread() * 200.0F));
        g.drawString(font, fee, 190 - font.width(fee), 9, COLOR_TITLE, false);
    }

    private void renderSidebar(GuiGraphics g, int mouseX, int mouseY) {
        int hovered = entryAt(mouseX, mouseY);
        int h = entryHeight();
        int x = leftPos + SIDE_X + 2;
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            int y = entryY(i);
            boolean deals = i == 0 && hasDeals();
            boolean chosen = entry.id().equals(merchant) && search.isEmpty();
            if (chosen) {
                g.fill(x, y, x + SIDE_WIDTH - 4, y + h, 0x30FFFFFF);
                g.fill(x, y, x + 2, y + h, deals ? COLOR_DEAL : COLOR_ACCENT);
            } else if (i == hovered) {
                g.fill(x, y, x + SIDE_WIDTH - 4, y + h, 0x14FFFFFF);
            } else if (deals) {
                g.fill(x, y, x + SIDE_WIDTH - 4, y + h, 0x28FFD83D); // a gold wash, so it's hard to miss
            }
            g.pose().pushPose();
            g.pose().translate(x + 5, y + (h - 12) / 2.0F, 0);
            g.pose().scale(0.75F, 0.75F, 1.0F);
            g.renderItem(entry.icon(), 0, 0);
            g.pose().popPose();
            g.drawString(font, entry.name(), x + 20, y + (h - 8) / 2, deals ? COLOR_DEAL : chosen ? COLOR_TEXT : 0xFFC8C8C8, true);
        }
        if (hasDeals()) {
            g.fill(x + 2, entryY(1) - 3, x + SIDE_WIDTH - 6, entryY(1) - 2, 0xFF444444);
        }
        if (!entries.isEmpty()) {
            int bankY = entryY(entries.size() - 1);
            g.fill(x + 2, bankY - 3, x + SIDE_WIDTH - 6, bankY - 2, 0xFF444444);
        }
    }

    private void renderListing(GuiGraphics g, int mouseX, int mouseY) {
        int x = leftPos + LIST_X;
        int y = topPos + BOX_Y;

        Component heading = search.isEmpty() ? merchantName(merchant) : Component.translatable("gui.soloeconomy.results");
        Component blurb = search.isEmpty() ? merchantBlurb(merchant) : Component.translatable("gui.soloeconomy.results.desc");
        g.drawString(font, heading, x + 6, y + 5, COLOR_TEXT, true);
        List<FormattedCharSequence> lines = font.split(blurb, LIST_WIDTH - 12);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            g.drawString(font, lines.get(i), x + 6, y + 16 + i * 9, COLOR_MUTED, false);
        }

        // "Only what I carry" toggle, sharing the column-header line
        int tx = x + 6;
        g.fill(tx, y + 37, tx + 8, y + 45, 0xFF555555);
        g.fill(tx + 1, y + 38, tx + 7, y + 44, carryOnly ? COLOR_ACCENT : COLOR_BOX);
        g.drawString(font, Component.translatable("gui.soloeconomy.carry_only"), tx + 11, y + 38,
                overToggle(mouseX, mouseY) ? COLOR_TEXT : COLOR_MUTED, false);

        g.drawString(font, Component.translatable("gui.soloeconomy.unit_sell"),
                leftPos + SELL_RIGHT - font.width(Component.translatable("gui.soloeconomy.unit_sell")), y + 38, COLOR_MUTED, false);
        g.drawString(font, Component.translatable("gui.soloeconomy.unit_buy"),
                leftPos + BUY_RIGHT - font.width(Component.translatable("gui.soloeconomy.unit_buy")), y + 38, COLOR_MUTED, false);
        g.fill(x + 4, y + 47, x + LIST_WIDTH - 4, y + 48, 0xFF444444);

        if (rows.isEmpty()) {
            Component empty = Component.translatable(carryOnly ? "gui.soloeconomy.empty_carry" : "gui.soloeconomy.empty");
            g.drawString(font, empty, x + (LIST_WIDTH - font.width(empty)) / 2, topPos + ROWS_Y + 50, COLOR_MUTED, false);
            return;
        }

        Row hovered = rowAt(mouseX, mouseY);
        for (int i = 0; i < ROWS && scrollRow + i < rows.size(); i++) {
            Row row = rows.get(scrollRow + i);
            int rx = x + 2;
            int ry = topPos + ROWS_Y + i * ROW_HEIGHT;
            int rw = LIST_WIDTH - 12;
            if (row == selected) {
                g.fill(rx, ry, rx + rw, ry + ROW_HEIGHT, 0x30FFFFFF);
                drawFrame(g, rx, ry, rw, ROW_HEIGHT, 0xFFFFFFFF);
            } else if (row == hovered) {
                g.fill(rx, ry, rx + rw, ry + ROW_HEIGHT, 0x18FFFFFF);
            } else if ((scrollRow + i) % 2 == 1) {
                g.fill(rx, ry, rx + rw, ry + ROW_HEIGHT, 0x0AFFFFFF);
            }
            boolean locked = row.listing().locked();
            g.renderItem(row.stack(), rx + 3, ry + 1);
            if (locked) {
                dimItem(g, rx + 3, ry + 1);
            }
            String sell = formatPrice(row.listing().sellPrice());
            String name = font.plainSubstrByWidth(row.name(), SELL_RIGHT - font.width(sell) - 8 - (rx + 22 - leftPos));
            g.drawString(font, name, rx + 22, ry + 5, locked ? COLOR_LOCKED : COLOR_TEXT, !locked);
            g.drawString(font, sell, leftPos + SELL_RIGHT - font.width(sell), ry + 5, locked ? COLOR_LOCKED : COLOR_SELL, !locked);
            String buy = row.listing().locked() ? LOCKED.getString() : formatPrice(row.listing().buyPrice());
            g.drawString(font, buy, leftPos + BUY_RIGHT - font.width(buy), ry + 5, locked ? COLOR_LOCKED : COLOR_BUY, !locked);
            // A gold pip beside whichever price is fee-free today.
            byte deal = row.listing().deal();
            if (deal != BrokerDeals.NONE) {
                int px = leftPos + (deal == BrokerDeals.WANTED ? SELL_RIGHT - font.width(sell) : BUY_RIGHT - font.width(buy)) - 6;
                g.fill(px, ry + 7, px + 3, ry + 10, 0xFF3F2A00);
                g.fill(px, ry + 6, px + 3, ry + 9, 0xFFFFD83D);
            }
        }

        int max = maxScroll();
        if (max > 0) {
            int trackX = x + LIST_WIDTH - 7;
            int trackH = ROWS * ROW_HEIGHT;
            int thumbH = Math.max(12, trackH * ROWS / rows.size());
            int thumbY = topPos + ROWS_Y + (trackH - thumbH) * scrollRow / max;
            g.fill(trackX, topPos + ROWS_Y, trackX + 3, topPos + ROWS_Y + trackH, 0x30000000);
            g.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, 0xFFB0B0B0);
        }
    }

    private void renderBank(GuiGraphics g) {
        int x = leftPos + LIST_X;
        int y = topPos + BOX_Y;
        g.drawString(font, Component.translatable("gui.soloeconomy.bank"), x + 6, y + 5, COLOR_TEXT, true);
        List<FormattedCharSequence> lines = font.split(Component.translatable("gui.soloeconomy.bank.desc"), LIST_WIDTH - 12);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), x + 6, y + 16 + i * 9, COLOR_MUTED, false);
        }

        String balance = EconomyAccount.format(ClientMarketState.balance());
        g.pose().pushPose();
        g.pose().translate(x + LIST_WIDTH / 2.0F, y + 80, 0);
        g.pose().scale(2.0F, 2.0F, 1.0F);
        g.drawString(font, balance, -font.width(balance) / 2, 0, COLOR_SELL, true);
        g.pose().popPose();
        Component unit = Component.translatable("gui.soloeconomy.bank.unit");
        g.drawString(font, unit, x + (LIST_WIDTH - font.width(unit)) / 2, y + 102, COLOR_MUTED, false);

        Component carried = Component.translatable("gui.soloeconomy.bank.carrying", countHeld(Items.EMERALD));
        g.drawString(font, carried, x + (LIST_WIDTH - font.width(carried)) / 2, y + 140, COLOR_MUTED, false);
    }

    private void renderBar(GuiGraphics g, int mouseX, int mouseY) {
        int y = topPos + BAR_Y;
        g.renderItem(new ItemStack(Items.EMERALD), leftPos + 8, y + 1);
        g.drawString(font, wholeBalance(), leftPos + 26, y + 5, COLOR_TITLE, false);

        int hoveredChip = chipAt(mouseX, mouseY);
        for (int i = 0; i < QUANTITIES.length; i++) {
            int cx = leftPos + CHIP_X + i * CHIP_PITCH;
            int cw = CHIP_PITCH - 2;
            boolean chosen = i == quantityIndex;
            drawBox(g, cx, y + 1, cw, 16);
            if (chosen) {
                drawFrame(g, cx, y + 1, cw, 16, 0xFFFFFFFF);
            }
            String label = QUANTITIES[i] == ServerMarketHandler.COUNT_MAX
                    ? Component.translatable("gui.soloeconomy.quantity_all").getString()
                    : String.valueOf(QUANTITIES[i]);
            int color = chosen ? COLOR_TEXT : i == hoveredChip ? 0xFFD8D8D8 : COLOR_MUTED;
            g.drawString(font, label, cx + (cw - font.width(label)) / 2 + 1, y + 5, color, chosen);
        }
    }

    /** The action buttons carry the exact total for the chosen quantity, once the server has priced it. */
    private void updateButtons() {
        if (atBank()) {
            setButton(leftButton, Component.translatable("gui.soloeconomy.deposit"), true);
            setButton(rightButton, Component.translatable("gui.soloeconomy.withdraw"), true);
            return;
        }
        if (selected == null) {
            setButton(leftButton, Component.translatable("gui.soloeconomy.unit_sell"), false);
            setButton(rightButton, Component.translatable("gui.soloeconomy.unit_buy"), false);
            return;
        }
        ensureQuote(selected.listing().item());
        QuotePayload quote = ClientMarketState.quoteFor(selected.listing().item());
        if (quote == null) {
            setButton(leftButton, Component.literal("..."), false);
            setButton(rightButton, Component.literal("..."), false);
            return;
        }
        // Labels carry only the total to fit; hovering gives the count and exact amount.
        setButton(leftButton, quote.sellCount() <= 0
                ? Component.translatable("gui.soloeconomy.action_none")
                : Component.translatable("gui.soloeconomy.action_sell", shortTotal(quote.sellTotal()))
                        .withStyle(ChatFormatting.GREEN), quote.sellCount() > 0);
        setButton(rightButton, selected.listing().locked() ? LOCKED
                : quote.buyCount() <= 0 ? Component.translatable("gui.soloeconomy.action_broke")
                : Component.translatable("gui.soloeconomy.action_buy", shortTotal(quote.buyTotal()))
                        .withStyle(ChatFormatting.GOLD), quote.buyCount() > 0);
    }

    private static void setButton(Button button, Component message, boolean active) {
        button.setMessage(message);
        button.active = active;
    }

    private void renderInventory(GuiGraphics g, int mouseX, int mouseY) {
        int x = leftPos + INV_X;
        drawPanel(g, x, topPos, INV_WIDTH, HEIGHT);
        g.drawString(font, Component.translatable("gui.soloeconomy.inventory"), x + 8, topPos + 9, COLOR_TITLE, false);
        drawBox(g, x + 6, topPos + BOX_Y, INV_COLUMNS * SLOT + 6, 9 * SLOT + 6);

        int hovered = slotAt(mouseX, mouseY);
        Item chosen = selected == null ? null : selected.listing().item();
        List<ItemStack> items = minecraft.player.getInventory().items;
        for (int shown = 0; shown < 36; shown++) {
            int slot = shown < 27 ? shown + 9 : shown - 27;
            int sx = x + 9 + shown % INV_COLUMNS * SLOT;
            int sy = topPos + SLOTS_Y + shown / INV_COLUMNS * SLOT;
            ItemStack stack = items.get(slot);
            g.fill(sx + 1, sy + 1, sx + SLOT - 1, sy + SLOT - 1, slot == hovered ? 0xFF3A3A3A : 0xFF1F1F1F);
            if (stack.isEmpty()) {
                continue;
            }
            g.renderItem(stack, sx + 1, sy + 1);
            g.renderItemDecorations(font, stack, sx + 1, sy + 1);
            if (sellable(stack) == null) {
                dimItem(g, sx + 1, sy + 1);
            } else if (stack.is(chosen)) {
                drawFrame(g, sx, sy, SLOT, SLOT, 0xFFFFFFFF);
            }
        }

        List<FormattedCharSequence> hint = font.split(Component.translatable("gui.soloeconomy.inventory_hint"), INV_WIDTH - 14);
        for (int i = 0; i < Math.min(3, hint.size()); i++) {
            g.drawString(font, hint.get(i), x + 7, topPos + 198 + i * 9, COLOR_TITLE, false);
        }
    }

    /** Greys out an item icon; drawn above it, since items render in front of plain fills. */
    private static void dimItem(GuiGraphics g, int x, int y) {
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 300.0F);
        g.fill(x, y, x + 16, y + 16, 0xB02B2B2B);
        g.pose().popPose();
    }

    private void renderHoverTooltip(GuiGraphics g, int mouseX, int mouseY) {
        int slot = slotAt(mouseX, mouseY);
        if (slot >= 0) {
            ItemStack stack = minecraft.player.getInventory().items.get(slot);
            if (!stack.isEmpty()) {
                Listing listing = sellable(stack);
                g.renderComponentTooltip(font, List.of(stack.getHoverName(), listing == null
                        ? Component.translatable("gui.soloeconomy.inventory_unwanted").withStyle(ChatFormatting.GRAY)
                        : Component.translatable("gui.soloeconomy.inventory_price", formatPrice(listing.sellPrice()))
                                .withStyle(ChatFormatting.GREEN)), mouseX, mouseY);
            }
            return;
        }
        // Only over the icon: a tooltip from anywhere else on the row would cover the prices.
        Row row = atBank() || mouseX >= leftPos + LIST_X + 23 ? null : rowAt(mouseX, mouseY);
        if (row != null) {
            List<Component> lines = new ArrayList<>(List.of(
                    row.stack().getHoverName(),
                    supplyLine(row.listing().stockRatio()),
                    Component.translatable("gui.soloeconomy.carrying", countHeld(row.listing().item()))
                            .withStyle(ChatFormatting.GRAY)));
            if (row.listing().locked()) {
                lines.add(Component.translatable("gui.soloeconomy.locked_tip").withStyle(ChatFormatting.GRAY));
            }
            if (row.listing().deal() != BrokerDeals.NONE) {
                lines.add(Component.translatable(row.listing().deal() == BrokerDeals.WANTED
                        ? "gui.soloeconomy.deal_wanted" : "gui.soloeconomy.deal_offer").withStyle(ChatFormatting.GOLD));
            }
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
            return;
        }
        if (inside(mouseX, mouseY, leftPos + 8, topPos + BAR_Y, 18 + font.width(wholeBalance()), 18)) {
            g.renderTooltip(font, Component.translatable("gui.soloeconomy.balance",
                    EconomyAccount.format(ClientMarketState.balance())), mouseX, mouseY);
            return;
        }
        QuotePayload quote = selected == null || atBank() ? null : ClientMarketState.quoteFor(selected.listing().item());
        if (quote != null && leftButton.isHovered() && quote.sellCount() > 0) {
            g.renderTooltip(font, Component.translatable("gui.soloeconomy.action_sell_tip", quote.sellCount(),
                    EconomyAccount.format(quote.sellTotal())), mouseX, mouseY);
            return;
        }
        if (selected != null && !atBank() && selected.listing().locked() && rightButton.isHovered()) {
            g.renderTooltip(font, Component.translatable("gui.soloeconomy.locked_tip"), mouseX, mouseY);
            return;
        }
        if (quote != null && rightButton.isHovered() && quote.buyCount() > 0) {
            g.renderTooltip(font, Component.translatable("gui.soloeconomy.action_buy_tip", quote.buyCount(),
                    EconomyAccount.format(quote.buyTotal())), mouseX, mouseY);
            return;
        }
        if (chipAt(mouseX, mouseY) >= 0 && atBank()) {
            g.renderTooltip(font, Component.translatable("gui.soloeconomy.quantity_bank_tip"), mouseX, mouseY);
        }
    }

    // ------------------------------------------------------------------
    // Drawing helpers
    // ------------------------------------------------------------------

    /** Vanilla container panel of any size, nine-sliced from the villager screen's 4px frame. */
    private static void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        int b = 4;
        g.blit(PANEL, x, y, b, b, 0, 0, b, b, 512, 256);
        g.blit(PANEL, x + w - b, y, b, b, 272, 0, b, b, 512, 256);
        g.blit(PANEL, x, y + h - b, b, b, 0, 162, b, b, 512, 256);
        g.blit(PANEL, x + w - b, y + h - b, b, b, 272, 162, b, b, 512, 256);
        g.blit(PANEL, x + b, y, w - 2 * b, b, 150, 0, 1, b, 512, 256);
        g.blit(PANEL, x + b, y + h - b, w - 2 * b, b, 150, 162, 1, b, 512, 256);
        g.blit(PANEL, x, y + b, b, h - 2 * b, 0, 8, b, 1, 512, 256);
        g.blit(PANEL, x + w - b, y + b, b, h - 2 * b, 272, 8, b, 1, 512, 256);
        g.blit(PANEL, x + b, y + b, w - 2 * b, h - 2 * b, 150, 8, 1, 1, 512, 256);
    }

    /** Dark rounded box, the console-menu content area. */
    private static void drawBox(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 1, y, x + w - 1, y + h, COLOR_BOX_EDGE);
        g.fill(x, y + 1, x + w, y + h - 1, COLOR_BOX_EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, COLOR_BOX);
    }

    private static void drawFrame(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    /** Clipped to the sidebar, so a long name from a datapack can't run past the box. */
    private String sidebarName(Component name) {
        return font.plainSubstrByWidth(name.getString(), SIDE_WIDTH - 26);
    }

    private static Component merchantName(String id) {
        return Component.translatableWithFallback("merchant.soloeconomy." + id, id);
    }

    private static Component merchantBlurb(String id) {
        return Component.translatableWithFallback("merchant.soloeconomy." + id + ".desc", "");
    }

    /** Whole emeralds with thousands separators; the exact amount is a hover away. */
    private static String wholeBalance() {
        return String.format("%,d", ClientMarketState.balance() / EconomyAccount.CENTS_PER_EMERALD);
    }

    /** Button-sized total: cents until it reaches four figures. */
    private static String shortTotal(long cents) {
        return cents >= 1000 * EconomyAccount.CENTS_PER_EMERALD
                ? String.format("%,d", cents / EconomyAccount.CENTS_PER_EMERALD)
                : EconomyAccount.format(cents);
    }

    /** Cheap goods need decimals, expensive ones don't. */
    private static String formatPrice(float price) {
        if (price >= 100.0F) {
            return String.format("%,.0f", price);
        }
        if (price >= 10.0F) {
            return String.format("%.1f", price);
        }
        if (price >= 1.0F) {
            return String.format("%.2f", price);
        }
        return String.format("%.3f", price);
    }

    private static Component supplyLine(float stockRatio) {
        if (stockRatio > 1.25F) {
            return Component.translatable("gui.soloeconomy.supply_glut").withStyle(ChatFormatting.RED);
        }
        if (stockRatio < 0.8F) {
            return Component.translatable("gui.soloeconomy.supply_short").withStyle(ChatFormatting.GREEN);
        }
        return Component.translatable("gui.soloeconomy.supply_normal").withStyle(ChatFormatting.GRAY);
    }
}
