package endgame.plugin.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;
import endgame.plugin.components.PlayerEndgameComponent;
import endgame.plugin.config.PetAbility;
import endgame.plugin.config.PetAbilityRegistry;
import endgame.plugin.config.PetData;
import endgame.plugin.config.PetTier;
import endgame.plugin.config.PetTierRequirements;
import endgame.plugin.config.PetTierRequirements.ItemRequirement;
import endgame.plugin.managers.PetManager;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;

/**
 * Native .ui page for the Pet system with tier progression.
 * Shows 4 pet cards in a 2x2 grid with tier badge, ability list, and Feed button.
 */
public class NativePetPage extends InteractiveCustomUIPage<NativePetPage.PetEventData> {

    private static final String PAGE_FILE = "Pages/EndgamePetPage.ui";

    private static final String[][] PET_INFO = {
            // petId, displayName, bossName, archetype, iconFile
            {"Endgame_Pet_Dragon_Frost", "Dragon Frost", "Dragon Frost",
                    "Mobility / Ice Control", "Dragon_Frost@2x.png"},
            {"Endgame_Pet_Dragon_Fire", "Dragon Fire", "Dragon Fire",
                    "DPS / Destruction", "Dragon_Fire@2x.png"},
            {"Endgame_Pet_Golem_Void", "Golem Void", "Golem Void",
                    "Tank / Protection", "Golem_Void@2x.png"},
            {"Endgame_Pet_Hedera", "Hedera", "Hedera",
                    "Support / Poison", "Hedera@2x.png"}
    };

    private static final String[] ACCENT_COLORS = {"#5bceff", "#ff6600", "#9040ff", "#33cc33"};
    private static final String[] ACCENT_DIM = {"#1a2a33", "#331a00", "#1a0a33", "#0a330a"};

    private final EndgameQoL plugin;
    private final PlayerRef playerRef;
    private final UUID playerUuid;

    public NativePetPage(@Nonnull PlayerRef playerRef, @Nonnull EndgameQoL plugin, @Nonnull UUID playerUuid) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PetEventData.CODEC);
        this.plugin = plugin;
        this.playerRef = playerRef;
        this.playerUuid = playerUuid;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        cmd.append(PAGE_FILE);

        PlayerEndgameComponent comp = plugin.getPlayerComponent(playerUuid);
        PetData petData = comp != null ? comp.getPetData() : new PetData();
        String activePetId = petData.getActivePetId();

        // Get player inventory for feed cost display
        CombinedItemContainer inventory = InventoryComponent.getCombined(store, ref, InventoryComponent.HOTBAR_FIRST);

        int unlocked = 0;
        for (int i = 0; i < PET_INFO.length; i++) {
            String petId = PET_INFO[i][0];
            String name = PET_INFO[i][1];
            String boss = PET_INFO[i][2];
            String archetype = PET_INFO[i][3];
            String icon = PET_INFO[i][4];
            boolean isUnlocked = petData.isUnlocked(petId);
            boolean isActive = petId.equals(activePetId);
            String prefix = "#Pet" + i;

            if (isUnlocked) {
                unlocked++;
                PetTier tier = petData.getPetTier(petId);
                PetTier nextTier = tier.next();
                boolean isMaxTier = nextTier == null;

                // Name + subtitle — use pet accent color when active
                cmd.set(prefix + "Name.Text", name);
                cmd.set(prefix + "Name.Style.TextColor", isActive ? ACCENT_COLORS[i] : "#e8f0ff");
                String subText = archetype + "  |  " + String.format("%.1fx", tier.getDamageMultiplier()) + " dmg";
                if (isActive) subText += "  |  ACTIVE";
                cmd.set(prefix + "Sub.Text", subText);
                cmd.set(prefix + "Sub.Style.TextColor", isActive ? "#3a8a5a" : "#4a6a80");

                // Tier badge
                cmd.set(prefix + "TierBadge.Text", tier.getLabel());
                cmd.set(prefix + "TierBadge.Style.TextColor", tier.getColor());

                // Portrait
                cmd.set(prefix + "Portrait.AssetPath", "UI/Custom/Bestiary/" + icon);
                cmd.set(prefix + "Portrait.Visible", true);
                cmd.set(prefix + "PortraitBg.Background.Color", "#040a12");
                cmd.set(prefix + "Strip.Background.Color", isActive ? "#44dd88" : ACCENT_COLORS[i]);

                // Abilities list — 6 abilities in 2-column layout, ASCII only
                List<PetAbility> abilities = PetAbilityRegistry.getAbilities(petId);
                for (int a = 0; a < 6; a++) {
                    String abId = prefix + "Ab" + a;
                    if (a < abilities.size()) {
                        PetAbility ability = abilities.get(a);
                        boolean abUnlocked = ability.requiredTier().ordinal() <= tier.ordinal();
                        String tl = ability.requiredTier().getLabel();
                        String text = "[" + tl + "] " + ability.name();
                        cmd.set(abId + ".Text", text);
                        cmd.set(abId + ".Style.TextColor", abUnlocked ? "#c0d8e8" : "#2a3a4a");
                    } else {
                        cmd.set(abId + ".Text", "");
                    }
                }

                // Next tier section
                if (!isMaxTier) {
                    cmd.set(prefix + "NextTier.Text", "NEXT: Tier " + nextTier.getLabel()
                            + " (" + String.format("%.1fx", nextTier.getDamageMultiplier()) + " dmg)");
                    cmd.set(prefix + "NextTier.Style.TextColor", nextTier.getColor());

                    // Feed button
                    cmd.set(prefix + "Feed.Visible", true);
                    cmd.set(prefix + "Feed.Text", "Feed > " + nextTier.getLabel());
                    events.addEventBinding(CustomUIEventBindingType.Activating,
                            prefix + "Feed", EventData.of("Action", "feed:" + petId), false);

                    // Cost line
                    List<ItemRequirement> reqs = PetTierRequirements.getRequirements(petId, nextTier);
                    StringBuilder costText = new StringBuilder();
                    boolean canAfford = true;
                    for (int r = 0; r < reqs.size(); r++) {
                        ItemRequirement req = reqs.get(r);
                        int has = inventory != null
                                ? inventory.countItemStacks(is -> req.itemId().equals(is.getItemId()))
                                : 0;
                        boolean enough = has >= req.quantity();
                        if (!enough) canAfford = false;
                        if (r > 0) costText.append(" + ");
                        costText.append(req.quantity()).append("x ")
                                .append(PetTierRequirements.formatItemName(req.itemId()));
                        if (!enough) costText.append(" [").append(has).append("]");
                    }
                    cmd.set(prefix + "FeedCost.Text", costText.toString());
                    cmd.set(prefix + "FeedCost.Style.TextColor", canAfford ? "#55aa55" : "#aa5555");
                } else {
                    cmd.set(prefix + "NextTier.Text", "MAX TIER - " + String.format("%.1fx", tier.getDamageMultiplier()) + " dmg");
                    cmd.set(prefix + "NextTier.Style.TextColor", "#ffaa00");
                    cmd.set(prefix + "Feed.Visible", false);
                    cmd.set(prefix + "FeedCost.Text", "");
                }

                // Summon/Dismiss button
                cmd.set(prefix + "Action.Visible", true);
                if (isActive) {
                    cmd.set(prefix + "Action.Text", "Dismiss");
                    events.addEventBinding(CustomUIEventBindingType.Activating,
                            prefix + "Action", EventData.of("Action", "despawn"), false);
                } else {
                    cmd.set(prefix + "Action.Text", "Summon");
                    events.addEventBinding(CustomUIEventBindingType.Activating,
                            prefix + "Action", EventData.of("Action", "spawn:" + petId), false);
                }

            } else {
                // Locked pet
                cmd.set(prefix + "Name.Text", "???");
                cmd.set(prefix + "Name.Style.TextColor", "#1e2a3a");
                cmd.set(prefix + "Sub.Text", "Defeat " + boss + " to unlock");
                cmd.set(prefix + "Sub.Style.TextColor", "#1a2838");
                cmd.set(prefix + "TierBadge.Text", "--");
                cmd.set(prefix + "TierBadge.Style.TextColor", "#1a2838");
                cmd.set(prefix + "Portrait.Visible", false);
                cmd.set(prefix + "PortraitBg.Background.Color", "#030810");
                cmd.set(prefix + "Strip.Background.Color", "#0a1520");
                cmd.set(prefix + "Action.Visible", false);
                cmd.set(prefix + "Feed.Visible", false);
                cmd.set(prefix + "NextTier.Text", "");
                cmd.set(prefix + "FeedCost.Text", "");
                for (int a = 0; a < 6; a++) {
                    cmd.set(prefix + "Ab" + a + ".Text", "");
                }
            }
        }

        cmd.set("#UnlockCount.Text", unlocked + "/4 Unlocked");
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull PetEventData data) {
        if (data.action == null) return;

        plugin.getLogger().atFine().log("[PetPage] handleDataEvent: action=%s", data.action);

        PetManager petManager = plugin.getSystemRegistry() != null
                ? plugin.getSystemRegistry().getPetManager() : null;
        if (petManager == null) return;

        World world = store.getExternalData().getWorld();

        if (data.action.equals("despawn")) {
            petManager.despawnPet(playerUuid);
            closePage(ref, store);

        } else if (data.action.startsWith("spawn:")) {
            String petId = data.action.substring(6);
            PlayerEndgameComponent comp = plugin.getPlayerComponent(playerUuid);
            if (comp != null && comp.getPetData().isUnlocked(petId)) {
                TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
                if (tc != null && world != null) {
                    Vector3d pos = new Vector3d(
                            tc.getPosition().x + 2, tc.getPosition().y + 1, tc.getPosition().z + 2);
                    world.execute(() -> petManager.spawnPet(store, playerUuid, petId, pos));
                    closePage(ref, store);
                }
            }

        } else if (data.action.startsWith("feed:")) {
            String petId = data.action.substring(5);
            handleFeed(ref, store, petId, petManager, world);

        } else if (data.action.equals("mount")) {
            if (world != null) {
                world.execute(() -> {
                    if (petManager.isMounted(playerUuid)) {
                        petManager.dismountPet(playerUuid);
                        notifyPlayer("#ffaa00", "Dismounted.");
                    } else {
                        boolean ok = petManager.mountPet(store, ref, playerUuid, playerRef);
                        if (ok) notifyPlayer("#44dd77", "Mounted!");
                    }
                });
                sendRefreshUpdate(ref, store);
            }
        }
    }

    private void handleFeed(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                            @Nonnull String petId, @Nonnull PetManager petManager, World world) {
        PlayerEndgameComponent comp = plugin.getPlayerComponent(playerUuid);
        if (comp == null) return;

        PetData petData = comp.getPetData();
        if (!petData.isUnlocked(petId)) return;

        PetTier currentTier = petData.getPetTier(petId);
        PetTier nextTier = currentTier.next();
        if (nextTier == null) {
            notifyPlayer("#ffaa00", "Already at max tier!");
            return;
        }

        List<ItemRequirement> reqs = PetTierRequirements.getRequirements(petId, nextTier);
        if (reqs.isEmpty()) return;

        CombinedItemContainer container = InventoryComponent.getCombined(store, ref, InventoryComponent.HOTBAR_FIRST);
        if (container == null) return;

        // Check all requirements first
        for (ItemRequirement req : reqs) {
            int has = container.countItemStacks(is -> req.itemId().equals(is.getItemId()));
            if (has < req.quantity()) {
                notifyPlayer("#cc4444", "Not enough " + PetTierRequirements.formatItemName(req.itemId())
                        + " (" + has + "/" + req.quantity() + ")");
                return;
            }
        }

        // Consume items
        for (ItemRequirement req : reqs) {
            container.removeItemStack(new ItemStack(req.itemId(), req.quantity()));
        }

        // Apply tier-up
        petData.setPetTier(petId, nextTier);

        plugin.getLogger().atInfo().log("[PetTier] %s tiered up %s: %s -> %s",
                playerUuid, petId, currentTier.getLabel(), nextTier.getLabel());

        // If pet is active, despawn and respawn with new tier stats
        if (petId.equals(petData.getActivePetId())) {
            petManager.despawnPet(playerUuid);
            TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
            if (tc != null && world != null) {
                Vector3d pos = new Vector3d(
                        tc.getPosition().x + 2, tc.getPosition().y + 1, tc.getPosition().z + 2);
                world.execute(() -> petManager.spawnPet(store, playerUuid, petId, pos));
            }
        }

        notifyPlayer(nextTier.getColor(), "Pet " + getPetDisplayName(petId)
                + " upgraded to Tier " + nextTier.getLabel() + "!");

        // Rebuild the page to show updated tier (don't close — player may feed again)
        sendRefreshUpdate(ref, store);
    }

    private void sendRefreshUpdate(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            player.getPageManager().openCustomPage(ref, store, new NativePetPage(playerRef, plugin, playerUuid));
        }
    }

    private void closePage(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            player.getPageManager().setPage(ref, store, com.hypixel.hytale.protocol.packets.interface_.Page.None);
        }
    }

    private void notifyPlayer(@Nonnull String color, @Nonnull String message) {
        playerRef.sendMessage(com.hypixel.hytale.server.core.Message.raw("[Pets] " + message).color(color));
    }

    private String getPetDisplayName(@Nonnull String petId) {
        for (String[] info : PET_INFO) {
            if (info[0].equals(petId)) return info[1];
        }
        return petId;
    }

    public static void open(@Nonnull EndgameQoL plugin, @Nonnull PlayerRef playerRef,
                             @Nonnull Store<EntityStore> store, @Nonnull UUID uuid) {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) return;
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        if (plugin.getPlayerComponent(uuid) == null) {
            playerRef.sendMessage(com.hypixel.hytale.server.core.Message.raw("[EndgameQoL] Loading pet data, please try again.").color("#ffaa00"));
            return;
        }
        player.getPageManager().openCustomPage(ref, store, new NativePetPage(playerRef, plugin, uuid));
    }

    public static class PetEventData {
        public static final BuilderCodec<PetEventData> CODEC = BuilderCodec
                .builder(PetEventData.class, PetEventData::new)
                .append(new KeyedCodec<String>("Action", Codec.STRING),
                        (d, v) -> d.action = v, d -> d.action).add()
                .build();
        String action;
    }
}
