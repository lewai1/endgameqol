package endgame.plugin.ui;

import com.hypixel.hytale.builtin.adventure.shop.barter.BarterItemStack;
import com.hypixel.hytale.builtin.adventure.shop.barter.BarterShopAsset;
import com.hypixel.hytale.builtin.adventure.shop.barter.BarterShopState;
import com.hypixel.hytale.builtin.adventure.shop.barter.BarterTrade;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.time.Instant;

/**
 * Custom trade UI for EndgameQoL merchants (Korvyn, Morghul, Vorthak).
 * Reads trades from vanilla BarterShopAsset and displays them in a custom .ui layout.
 * Handles trade execution with stock tracking via BarterShopState.
 */
public class TradeUI extends InteractiveCustomUIPage<TradeUI.TradeEventData> {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.TradeUI");
    private static final String PAGE_FILE = "Pages/EndgameTradePage.ui";
    private static final String ROW_FILE = "Pages/EndgameTradeRow.ui";

    private final String shopId;
    private final String merchantName;

    public TradeUI(@Nonnull PlayerRef playerRef, @Nonnull String shopId, @Nonnull String merchantName) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, TradeEventData.CODEC);
        this.shopId = shopId;
        this.merchantName = merchantName;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        cmd.append(PAGE_FILE);
        cmd.set("#MerchantName.Text", merchantName);

        BarterShopAsset shopAsset = (BarterShopAsset) BarterShopAsset.getAssetMap().getAsset(shopId);
        if (shopAsset == null) {
            cmd.set("#CurrencyInfo.Text", "Shop not found");
            return;
        }

        Instant gameTime = getGameTime(store);
        BarterTrade[] trades = BarterShopState.get().getResolvedTrades(shopAsset, gameTime);
        int[] stock = BarterShopState.get().getStockArray(shopAsset, gameTime);

        if (trades == null || trades.length == 0) {
            cmd.set("#CurrencyInfo.Text", "No trades available");
            return;
        }

        // Detect single-currency vs multi-currency shop
        CombinedItemContainer playerInv = InventoryComponent.getCombined(store, ref, InventoryComponent.HOTBAR_FIRST);
        String commonCurrencyId = null;
        boolean singleCurrency = true;
        int affordableCount = 0;

        for (BarterTrade t : trades) {
            if (t.getInput() != null && t.getInput().length > 0) {
                String inputId = t.getInput()[0].getItemId();
                if (commonCurrencyId == null) {
                    commonCurrencyId = inputId;
                } else if (!commonCurrencyId.equals(inputId)) {
                    singleCurrency = false;
                }
            }
        }

        // Header info
        if (singleCurrency && commonCurrencyId != null && playerInv != null) {
            final String cid = commonCurrencyId;
            int count = playerInv.countItemStacks(is -> cid.equals(is.getItemId()));
            cmd.set("#CurrencyInfo.Text", count + "x " + formatItemId(commonCurrencyId));
        } else {
            for (int i = 0; i < trades.length; i++) {
                int tradeStock = (stock != null && i < stock.length) ? stock[i] : 0;
                if (tradeStock > 0 && isAffordable(trades[i], playerInv)) affordableCount++;
            }
            cmd.set("#CurrencyInfo.Text", affordableCount + "/" + trades.length + " affordable");
        }

        // Build trade rows
        for (int i = 0; i < trades.length; i++) {
            BarterTrade trade = trades[i];
            if (trade.getOutput() == null) continue;

            String rowSel = "#TradeList[" + i + "]";
            cmd.append("#TradeList", ROW_FILE);

            String outputId = trade.getOutput().getItemId();
            int outputQty = trade.getOutput().getQuantity();
            int tradeStock = (stock != null && i < stock.length) ? stock[i] : 0;
            boolean canAfford = tradeStock > 0 && isAffordable(trade, playerInv);
            boolean outOfStock = tradeStock <= 0;

            // Output item
            cmd.set(rowSel + " #OutputSlot.ItemId", outputId);
            cmd.set(rowSel + " #ItemName.Text", formatItemId(outputId));
            cmd.set(rowSel + " #ItemQty.Text", outputQty > 1 ? "x" + outputQty : "");

            // Grey out if out of stock or can't afford
            if (outOfStock) {
                cmd.set(rowSel + " #ItemName.Style.TextColor", "#555555");
                cmd.set(rowSel + ".Disabled", true);
            } else if (!canAfford) {
                cmd.set(rowSel + " #ItemName.Style.TextColor", "#888888");
            }

            // Stock display
            if (outOfStock) {
                cmd.set(rowSel + " #Stock.Text", "SOLD");
                cmd.set(rowSel + " #Stock.Style.TextColor", "#cc4444");
                cmd.set(rowSel + " #Stock.Style.FontSize", 10);
            } else {
                cmd.set(rowSel + " #Stock.Text", String.valueOf(tradeStock));
                cmd.set(rowSel + " #Stock.Style.TextColor", "#96a9be");
            }

            // Cost — per-trade affordability
            if (trade.getInput() != null && trade.getInput().length > 0) {
                BarterItemStack cost = trade.getInput()[0];
                cmd.set(rowSel + " #CostSlot.ItemId", cost.getItemId());
                cmd.set(rowSel + " #CostAmount.Text", String.valueOf(cost.getQuantity()));
                cmd.set(rowSel + " #CostAmount.Style.TextColor",
                        outOfStock ? "#555555" : (canAfford ? "#4ade80" : "#cc4444"));
            }

            // Event binding (only for in-stock items)
            if (!outOfStock) {
                events.addEventBinding(CustomUIEventBindingType.Activating,
                        rowSel,
                        com.hypixel.hytale.server.core.ui.builder.EventData.of("TradeIndex", String.valueOf(i)),
                        false);
            }
        }

        playSound("SFX_Merchant_Open");
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                 @Nonnull TradeEventData data) {
        int tradeIndex = data.tradeIndex;
        if (tradeIndex < 0) return;

        BarterShopAsset shopAsset = (BarterShopAsset) BarterShopAsset.getAssetMap().getAsset(shopId);
        if (shopAsset == null) return;

        Instant gameTime = getGameTime(store);
        BarterTrade[] trades = BarterShopState.get().getResolvedTrades(shopAsset, gameTime);
        int[] stock = BarterShopState.get().getStockArray(shopAsset, gameTime);

        if (trades == null || tradeIndex >= trades.length) return;
        BarterTrade trade = trades[tradeIndex];
        if (trade.getOutput() == null || trade.getInput() == null) return;

        int currentStock = (stock != null && tradeIndex < stock.length) ? stock[tradeIndex] : 0;
        if (currentStock <= 0) {
            notifyPlayer("#cc4444", "Out of stock!");
            playSound("SFX_Unbreakable_Block");
            return;
        }

        CombinedItemContainer container = InventoryComponent.getCombined(store, ref, InventoryComponent.HOTBAR_FIRST);
        if (container == null) return;

        for (BarterItemStack input : trade.getInput()) {
            String inputId = input.getItemId();
            int need = input.getQuantity();
            int has = container.countItemStacks(is -> inputId.equals(is.getItemId()));
            if (has < need) {
                notifyPlayer("#cc4444", "Not enough " + formatItemId(inputId) + " (" + has + "/" + need + ")");
                playSound("SFX_Unbreakable_Block");
                return;
            }
        }

        for (BarterItemStack input : trade.getInput()) {
            container.removeItemStack(new ItemStack(input.getItemId(), input.getQuantity()));
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        ItemStack outputStack = new ItemStack(trade.getOutput().getItemId(), trade.getOutput().getQuantity());
        ItemStackTransaction tx = player.giveItem(outputStack, ref, store);
        ItemStack remainder = tx.getRemainder();
        if (remainder != null && !remainder.isEmpty()) {
            ItemUtils.dropItem(ref, remainder, store);
        }

        BarterShopState.get().executeTrade(shopAsset, tradeIndex, 1, gameTime);

        String itemName = formatItemId(trade.getOutput().getItemId());
        int qty = trade.getOutput().getQuantity();
        notifyPlayer("#4ade80", "Purchased " + (qty > 1 ? qty + "x " : "") + itemName);
        playSound("SFX_MemoryMote");

        rebuild();
    }

    // === Helpers ===

    private static boolean isAffordable(BarterTrade trade, CombinedItemContainer playerInv) {
        if (playerInv == null || trade.getInput() == null) return false;
        for (BarterItemStack input : trade.getInput()) {
            String inputId = input.getItemId();
            int need = input.getQuantity();
            int has = playerInv.countItemStacks(is -> inputId.equals(is.getItemId()));
            if (has < need) return false;
        }
        return true;
    }

    private void notifyPlayer(String color, String text) {
        try {
            UICommandBuilder cmd = new UICommandBuilder();
            cmd.set("#NotifBar.Visible", true);
            cmd.set("#NotifText.Text", text);
            cmd.set("#NotifText.Style.TextColor", color);
            sendUpdate(cmd, false);
        } catch (Exception e) {
            try { playerRef.sendMessage(Message.raw("[Trade] " + text).color(color)); }
            catch (Exception ignored) {}
        }
    }

    private void playSound(String sfxId) {
        try {
            int idx = SoundEvent.getAssetMap().getIndex(sfxId);
            if (idx != 0) SoundUtil.playSoundEvent2dToPlayer(playerRef, idx, SoundCategory.UI);
        } catch (Exception ignored) {}
    }

    private Instant getGameTime(Store<EntityStore> store) {
        try {
            var res = store.getResource(com.hypixel.hytale.server.core.modules.time.TimeResource.getResourceType());
            return res != null ? res.getNow() : Instant.now();
        } catch (Exception e) { return Instant.now(); }
    }

    /** Simple item ID formatter for notification messages. */
    private static String formatItemId(String itemId) {
        if (itemId == null || itemId.isEmpty()) return "item";
        String name = itemId;
        for (String prefix : new String[]{"Endgame_", "Armor_", "Tool_", "Weapon_", "Potion_", "Ingredient_", "Big_"}) {
            if (name.startsWith(prefix)) { name = name.substring(prefix.length()); break; }
        }
        return name.replace("_", " ");
    }


    public static void open(PlayerRef playerRef, Store<EntityStore> store,
                             String shopId, String merchantName) {
        try {
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) return;
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) return;
            player.getPageManager().openCustomPage(ref, store, new TradeUI(playerRef, shopId, merchantName));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[TradeUI] Failed to open");
        }
    }

    public static class TradeEventData {
        public static final BuilderCodec<TradeEventData> CODEC = BuilderCodec
                .builder(TradeEventData.class, TradeEventData::new)
                .append(new KeyedCodec<>("TradeIndex", Codec.STRING),
                        (d, v) -> {
                            try { d.tradeIndex = v != null && !v.isEmpty() ? Integer.parseInt(v) : -1; }
                            catch (NumberFormatException e) { d.tradeIndex = -1; }
                        },
                        d -> String.valueOf(d.tradeIndex))
                .add()
                .build();
        int tradeIndex = -1;
    }
}
