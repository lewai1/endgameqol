package endgame.plugin.ui;

import au.ellie.hyui.builders.PageBuilder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.modules.item.ItemModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;
import endgame.plugin.config.AchievementData.PlayerAchievementState;
import endgame.plugin.config.BestiaryData.NPCEntry;
import endgame.plugin.config.BestiaryData.PlayerBestiaryState;
import endgame.plugin.managers.AchievementManager;
import endgame.plugin.managers.AchievementTemplate;
import endgame.plugin.utils.BestiaryRegistry;
import endgame.plugin.utils.BestiaryRegistry.Category;
import endgame.plugin.utils.BestiaryRegistry.KillMilestone;
import endgame.plugin.utils.BestiaryRegistry.MobInfo;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * HyUI page for the combined Bestiary + Achievements UI.
 * Two tabs: Bestiary (NPC kills/discoveries with detailed cards) and Achievements (milestones).
 * Accessible via /eg bestiary.
 */
public class BestiaryUI {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.BestiaryUI");

    private static final String[] ACH_CATEGORY_COLORS = {
        "#ff5555", "#ff8800", "#FFD700", "#55ff55", "#55aaff", "#bb88ff"
    };

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    /** Static image path prefix — resolves to Common/UI/Custom/Bestiary/ in asset pack */
    private static final String IMG_PATH_PREFIX = "Bestiary/";

    // CSS shared by both tabs
    private static final String SHARED_CSS = """
        <style>
            .bestiary-container { anchor-width: 920; }
            .page-title { font-size: 18; font-weight: bold; anchor-height: 26; text-align: center; anchor-width: 100%; }
            .page-stats { font-size: 11; color: #888888; anchor-height: 18; text-align: center; anchor-width: 100%; }
            .tab-row { layout-mode: left; anchor-height: 30; anchor-width: 310; horizontal-align: center; margin-bottom: 6; }
            @TabBg { background-color: #2a2a3a; }
            @TabHoverBg { background-color: #3a3a4a; }
            @TabActiveBg { background-color: #3a5a3a; }
            @TabLabel { font-size: 11; color: #cccccc; font-weight: bold; horizontal-align: center; vertical-align: center; }
            @TabHoverLabel { font-size: 11; color: #ffffff; font-weight: bold; horizontal-align: center; vertical-align: center; }
            @TabPressedBg { background-color: #4a6a4a; }
            @TabPressedLabel { font-size: 11; color: #ffffff; font-weight: bold; horizontal-align: center; vertical-align: center; }
            .divider { anchor-width: 100%; anchor-height: 1; background-color: #2a2a3a; }
            .filter-row { layout-mode: left; anchor-height: 28; anchor-width: 100%; horizontal-align: center; margin-bottom: 6; }
            @FilterBg { background-color: #1e1e2e; }
            @FilterHoverBg { background-color: #2e2e3e; }
            @FilterActiveBg { background-color: #3a3a5a; }
            @FilterLabel { font-size: 10; color: #999999; font-weight: bold; horizontal-align: center; vertical-align: center; }
            @FilterHoverLabel { font-size: 10; color: #ffffff; font-weight: bold; horizontal-align: center; vertical-align: center; }
            @FilterActiveLabel { font-size: 10; color: #ffffff; font-weight: bold; horizontal-align: center; vertical-align: center; }
            @FilterPressedBg { background-color: #4a4a6a; }
            @FilterPressedLabel { font-size: 10; color: #ffffff; font-weight: bold; horizontal-align: center; vertical-align: center; }
            .mob-card { layout-mode: left; margin-bottom: 4; padding: 6; }
            .mob-portrait { anchor-width: 64; anchor-height: 64; margin-right: 8; }
            .mob-portrait-placeholder { anchor-width: 64; anchor-height: 64; margin-right: 8; background-color: #151520; }
            .mob-info { layout-mode: top; flex-weight: 1; }
            .card-row1 { layout-mode: left; anchor-height: 20; anchor-width: 100%; }
            .card-row2 { layout-mode: left; anchor-height: 16; anchor-width: 100%; }
            .card-row3 { anchor-height: 16; anchor-width: 100%; }
            .card-row4 { anchor-height: 14; anchor-width: 100%; }
            .card-row5 { anchor-height: 14; anchor-width: 100%; }
            .cat-badge { font-size: 9; font-weight: bold; anchor-width: 70; anchor-height: 16; text-align: center; vertical-align: center; }
            .mob-name { font-size: 13; font-weight: bold; anchor-height: 20; vertical-align: center; flex-weight: 1; padding-left: 6; }
            .mob-hp { font-size: 12; color: #ff5555; anchor-height: 20; vertical-align: center; anchor-width: 90; text-align: center; }
            .mob-dmg { font-size: 11; color: #ff8800; anchor-height: 16; vertical-align: center; flex-weight: 1; }
            .mob-loc { font-size: 11; color: #5588bb; anchor-height: 16; vertical-align: center; anchor-width: 150; text-align: center; }
            .mob-desc { font-size: 11; color: #888888; anchor-height: 16; anchor-width: 100%; }
            .mob-drops { font-size: 11; color: #FFD700; anchor-height: 14; anchor-width: 100%; }
            .mob-kills { font-size: 10; color: #666666; anchor-height: 14; anchor-width: 100%; }
            .ach-card { layout-mode: top; padding: 8; margin-bottom: 4; }
            .ach-header { layout-mode: left; anchor-height: 22; anchor-width: 100%; }
            .ach-name { font-size: 13; font-weight: bold; anchor-height: 22; vertical-align: center; flex-weight: 1; }
            .ach-cat { font-size: 9; font-weight: bold; anchor-width: 70; anchor-height: 16; text-align: center; vertical-align: center; }
            .ach-desc { font-size: 11; color: #999999; anchor-height: 16; anchor-width: 100%; }
            .ach-progress-row { layout-mode: left; anchor-height: 16; anchor-width: 100%; margin-top: 4; }
            .ach-bar-bg { anchor-width: 580; anchor-height: 8; vertical-align: center; }
            .ach-bar-fill { anchor-height: 8; anchor-left: 0; anchor-top: 0; }
            .ach-progress-text { font-size: 10; color: #888888; anchor-height: 16; vertical-align: center; anchor-width: 80; padding-left: 8; }
            .ach-reward-row { layout-mode: left; anchor-height: 24; anchor-width: 100%; margin-top: 2; }
            .ach-reward-text { font-size: 10; color: #FFD700; anchor-height: 24; vertical-align: center; flex-weight: 1; }
            @ClaimBg { background-color: #2d5a2d; }
            @ClaimHoverBg { background-color: #3d7a3d; }
            @ClaimPressedBg { background-color: #4d8a4d; }
            @ClaimLabel { font-size: 10; color: #55ff55; font-weight: bold; horizontal-align: center; vertical-align: center; }
            @ClaimHoverLabel { font-size: 10; color: #ffffff; font-weight: bold; horizontal-align: center; vertical-align: center; }
            @ClaimPressedLabel { font-size: 10; color: #ffffff; font-weight: bold; horizontal-align: center; vertical-align: center; }
            .milestone-row { layout-mode: left; anchor-height: 22; anchor-width: 100%; margin-top: 2; }
            .milestone-badge { anchor-width: 44; anchor-height: 20; margin-right: 4; text-align: center; vertical-align: center; }
            .milestone-label { font-size: 9; font-weight: bold; horizontal-align: center; vertical-align: center; }
            .discovery-row { layout-mode: left; anchor-height: 28; anchor-width: 100%; horizontal-align: center; margin-bottom: 4; }
            .discovery-badge { anchor-width: 70; anchor-height: 24; margin-left: 4; margin-right: 4; text-align: center; vertical-align: center; }
            .discovery-label { font-size: 10; font-weight: bold; horizontal-align: center; vertical-align: center; }
        </style>
        """;

    public static void open(EndgameQoL plugin, PlayerRef playerRef, Store<EntityStore> store,
                            UUID playerUuid, String tab) {
        open(plugin, playerRef, store, playerUuid, tab, null);
    }

    public static void open(EndgameQoL plugin, PlayerRef playerRef, Store<EntityStore> store,
                            UUID playerUuid, String tab, Category filter) {
        AchievementManager manager = plugin.getAchievementManager();
        if (manager == null) return;

        if ("achievements".equals(tab)) {
            openAchievements(plugin, playerRef, store, playerUuid, manager);
        } else {
            openBestiary(plugin, playerRef, store, playerUuid, manager, filter);
        }
    }

    // ======================== BESTIARY TAB ========================

    private static void openBestiary(EndgameQoL plugin, PlayerRef playerRef, Store<EntityStore> store,
                                     UUID playerUuid, AchievementManager manager, Category filter) {
        PlayerBestiaryState state = manager.getBestiaryState(playerUuid);

        int discovered = 0;
        int totalKills = 0;
        for (MobInfo mob : BestiaryRegistry.getAll()) {
            NPCEntry entry = state.getEntries().get(mob.npcTypeId());
            if (entry != null) {
                if (entry.isDiscovered()) discovered++;
                totalKills += entry.getKillCount();
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(SHARED_CSS);

        sb.append("""
            <div class="page-overlay" style="layout-mode: center; vertical-align: middle; horizontal-align: center;">
                <div class="decorated-container bestiary-container" data-hyui-title="Bestiary" style="anchor-height: 700;">
                    <div class="container-contents" style="layout-mode: top; padding: 10;">
                        <p class="page-title" style="color: #ccb588;">BESTIARY</p>
            """);

        sb.append(String.format(
            "<p class=\"page-stats\">%d / %d Discovered  |  %d Total Kills</p>",
            discovered, BestiaryRegistry.getTotalCount(), totalKills));

        // Main tab buttons
        appendTabRow(sb, true);

        // Discovery milestone row
        int claimedDiscovery = state.getClaimedDiscoveryMilestone();
        List<Integer> claimableDiscoveryIndices = new ArrayList<>();
        sb.append("<div class=\"discovery-row\">");
        sb.append("<p style=\"font-size: 10; color: #888888; anchor-height: 24; vertical-align: center; anchor-width: 80;\">Discover:</p>");
        for (int i = 0; i < BestiaryRegistry.DISCOVERY_MILESTONES.length; i++) {
            int mt = BestiaryRegistry.DISCOVERY_MILESTONES[i];
            int effective = mt == -1 ? BestiaryRegistry.getTotalCount() : mt;
            String label = mt == -1 ? "ALL" : String.valueOf(mt);
            boolean reached = discovered >= effective;
            boolean claimed = claimedDiscovery >= effective;
            appendDiscoveryBadge(sb, "disc_" + i, label, reached, claimed);
            if (reached && !claimed) claimableDiscoveryIndices.add(i);
        }
        sb.append("</div>");

        sb.append("<div class=\"divider\" style=\"background-color: #ccb588; anchor-height: 2; margin-bottom: 4;\"></div>");

        // Category filter row — 7 × 120px + margins = 868px, fits in 920px container
        sb.append("<div class=\"filter-row\">");
        appendFilterButton(sb, "filter_all", "ALL", filter == null, 120);
        appendFilterButton(sb, "filter_boss", "BOSS", filter == Category.BOSS, 120);
        appendFilterButton(sb, "filter_elite", "ELITE", filter == Category.ELITE, 120);
        appendFilterButton(sb, "filter_elemental", "ELEMENTAL", filter == Category.ELEMENTAL, 120);
        appendFilterButton(sb, "filter_creature", "CREATURE", filter == Category.CREATURE, 120);
        appendFilterButton(sb, "filter_endgame", "ENDGAME", filter == Category.ENDGAME, 120);
        appendFilterButton(sb, "filter_special", "SPECIAL", filter == Category.SPECIAL, 120);
        sb.append("</div>");

        // Scrollable card area
        sb.append("<div style=\"layout-mode: top-scrolling; flex-weight: 1;\">");

        // Build list of mobs matching filter
        Collection<MobInfo> mobs = (filter != null)
                ? BestiaryRegistry.getByCategory(filter)
                : BestiaryRegistry.getAll();

        int mobIndex = 0;
        for (MobInfo mob : mobs) {
            NPCEntry entry = state.getEntries().get(mob.npcTypeId());
            boolean isDiscovered = entry != null && entry.isDiscovered();
            boolean canShowImage = isDiscovered && mob.iconFile() != null;
            appendMobCard(sb, mob, entry, isDiscovered, canShowImage, mobIndex);
            mobIndex++;
        }

        if (!mobs.iterator().hasNext()) {
            sb.append("""
                <p style="font-size: 12; color: #666666; anchor-height: 40; text-align: center; anchor-width: 100%; vertical-align: center;">
                    No entries in this category.
                </p>
                """);
        }

        sb.append("""
                    </div>
                </div>
            </div>
        </div>
        """);

        var builder = PageBuilder.pageForPlayer(playerRef).fromHtml(sb.toString());

        // Tab switch
        builder.addEventListener("tab_achievements", CustomUIEventBindingType.Activating, (data, ctx) -> {
            openAchievements(plugin, playerRef, store, playerUuid, manager);
        });

        // Filter buttons
        builder.addEventListener("filter_all", CustomUIEventBindingType.Activating, (data, ctx) -> {
            openBestiary(plugin, playerRef, store, playerUuid, manager, null);
        });
        for (Category cat : Category.values()) {
            final Category c = cat;
            builder.addEventListener("filter_" + cat.name().toLowerCase(), CustomUIEventBindingType.Activating, (data, ctx) -> {
                openBestiary(plugin, playerRef, store, playerUuid, manager, c);
            });
        }

        // Discovery milestone claim listeners (only for claimable badges)
        for (int i : claimableDiscoveryIndices) {
            final int mt = BestiaryRegistry.DISCOVERY_MILESTONES[i];
            builder.addEventListener("disc_" + i, CustomUIEventBindingType.Activating, (data, ctx) -> {
                String dropTable = manager.claimDiscoveryMilestone(playerUuid, mt);
                if (dropTable != null) {
                    giveDropTableRewards(playerRef, store, dropTable);
                    openBestiary(plugin, playerRef, store, playerUuid, manager, filter);
                }
            });
        }

        // Kill milestone claim listeners (only for claimable badges)
        int idx = 0;
        for (MobInfo mob : mobs) {
            NPCEntry entry = state.getEntries().get(mob.npcTypeId());
            if (entry != null && entry.isDiscovered()) {
                List<KillMilestone> milestones = BestiaryRegistry.getKillMilestones(mob.category());
                int kills = entry.getKillCount();
                int claimedMs = entry.getClaimedMilestone();
                for (int mi = 0; mi < milestones.size(); mi++) {
                    final KillMilestone km = milestones.get(mi);
                    boolean reached = kills >= km.threshold();
                    boolean isClaimed = claimedMs >= km.threshold();
                    if (reached && !isClaimed) {
                        final String npcId = mob.npcTypeId();
                        builder.addEventListener("km_" + idx + "_" + mi, CustomUIEventBindingType.Activating, (data, ctx) -> {
                            String dropTable = manager.claimBestiaryMilestone(playerUuid, npcId, km.threshold());
                            if (dropTable != null) {
                                giveDropTableRewards(playerRef, store, dropTable);
                                openBestiary(plugin, playerRef, store, playerUuid, manager, filter);
                            }
                        });
                    }
                }
            }
            idx++;
        }

        builder.open(store);
    }

    private static void appendFilterButton(StringBuilder sb, String id, String label, boolean active, int width) {
        String bg = active ? "@FilterActiveBg" : "@FilterBg";
        String labelStyle = active ? "@FilterActiveLabel" : "@FilterLabel";
        sb.append(String.format("""
            <button id="%s" class="custom-textbutton"
                data-hyui-default-bg="%s"
                data-hyui-hovered-bg="@FilterHoverBg"
                data-hyui-pressed-bg="@FilterPressedBg"
                data-hyui-default-label-style="%s"
                data-hyui-hovered-label-style="@FilterHoverLabel"
                data-hyui-pressed-label-style="@FilterPressedLabel"
                style="anchor-width: %d; anchor-height: 24; margin-left: 2; margin-right: 2;">%s</button>
            """, id, bg, labelStyle, width, label));
    }

    private static void appendMobCard(StringBuilder sb, MobInfo mob, NPCEntry entry,
                                       boolean discovered, boolean showImage, int mobIndex) {
        String cardBg = discovered ? "#1a1a2a" : "#0a0a15";
        String catColor = mob.category().getColor();

        sb.append(String.format("<div class=\"mob-card\" style=\"background-color: %s;\">", cardBg));

        // Portrait (left side)
        if (showImage) {
            sb.append(String.format(
                "<img src=\"%s%s\" style=\"anchor-width: 64; anchor-height: 64; margin-right: 8;\" />",
                IMG_PATH_PREFIX, mob.iconFile()));
        } else {
            // Placeholder box
            sb.append("<div class=\"mob-portrait-placeholder\"></div>");
        }

        // Info section (right side)
        sb.append("<div class=\"mob-info\">");

        // Row 1: Category badge + Name + HP
        sb.append("<div class=\"card-row1\">");
        sb.append(String.format(
            "<p class=\"cat-badge\" style=\"background-color: %s22; color: %s;\">%s</p>",
            catColor, catColor, mob.category().getLabel().toUpperCase()));

        if (discovered) {
            sb.append(String.format("<p class=\"mob-name\" style=\"color: #dddddd;\">%s</p>",
                esc(mob.displayName())));
            if (mob.health() > 0) {
                sb.append(String.format("<p class=\"mob-hp\">HP: %d</p>", mob.health()));
            } else {
                sb.append("<p class=\"mob-hp\" style=\"color: #55ff55;\">NPC</p>");
            }
        } else {
            sb.append("<p class=\"mob-name\" style=\"color: #555555;\">???</p>");
            sb.append("<p class=\"mob-hp\" style=\"color: #333333;\">HP: ???</p>");
        }
        sb.append("</div>");

        // Row 2: Damage info + Location
        sb.append("<div class=\"card-row2\">");
        if (discovered) {
            sb.append(String.format("<p class=\"mob-dmg\">%s</p>", esc(mob.damageInfo())));
        } else {
            sb.append("<p class=\"mob-dmg\" style=\"color: #333333;\">???</p>");
        }
        sb.append(String.format("<p class=\"mob-loc\">%s</p>", esc(mob.location())));
        sb.append("</div>");

        // Row 3: Description / lore
        sb.append("<div class=\"card-row3\">");
        if (discovered) {
            sb.append(String.format("<p class=\"mob-desc\">%s</p>", esc(mob.description())));
        } else {
            sb.append("<p class=\"mob-desc\" style=\"color: #444444;\">Kill this creature to discover it.</p>");
        }
        sb.append("</div>");

        // Row 4: Drops (only if discovered and has drops)
        if (discovered && mob.notableDrops().length > 0) {
            sb.append("<div class=\"card-row4\">");
            sb.append(String.format("<p class=\"mob-drops\">Drops: %s</p>",
                esc(String.join(", ", mob.notableDrops()))));
            sb.append("</div>");
        }

        // Row 5: Kill stats (only if discovered and has kills)
        if (discovered && entry != null && entry.getKillCount() > 0) {
            sb.append("<div class=\"card-row5\">");
            String killText = String.format("Kills: %d", entry.getKillCount());
            if (entry.getFirstKillTimestamp() > 0) {
                String date = DATE_FMT.format(Instant.ofEpochMilli(entry.getFirstKillTimestamp()));
                killText += "  |  First kill: " + date;
            }
            sb.append(String.format("<p class=\"mob-kills\">%s</p>", esc(killText)));
            sb.append("</div>");
        }

        // Row 6: Kill milestones (only if discovered)
        if (discovered && entry != null) {
            List<KillMilestone> milestones = BestiaryRegistry.getKillMilestones(mob.category());
            if (!milestones.isEmpty()) {
                int kills = entry.getKillCount();
                int claimed = entry.getClaimedMilestone();
                sb.append("<div class=\"milestone-row\">");
                sb.append("<p style=\"font-size: 9; color: #666666; anchor-height: 20; vertical-align: center; anchor-width: 60;\">Milestones:</p>");
                for (int i = 0; i < milestones.size(); i++) {
                    KillMilestone km = milestones.get(i);
                    boolean reached = kills >= km.threshold();
                    boolean isClaimed = claimed >= km.threshold();
                    String badgeId = "km_" + mobIndex + "_" + i;
                    appendMilestoneBadge(sb, badgeId, String.valueOf(km.threshold()), reached, isClaimed);
                }
                sb.append("</div>");
            }
        }

        sb.append("</div>"); // close mob-info
        sb.append("</div>"); // close mob-card
    }

    // ======================== ACHIEVEMENTS TAB ========================

    private static void openAchievements(EndgameQoL plugin, PlayerRef playerRef, Store<EntityStore> store,
                                         UUID playerUuid, AchievementManager manager) {
        PlayerAchievementState state = manager.getAchievementState(playerUuid);
        List<AchievementTemplate> allAchievements = AchievementTemplate.getAll();

        int completed = state.getCompletedCount();
        int total = allAchievements.size();

        StringBuilder sb = new StringBuilder();
        sb.append(SHARED_CSS);

        sb.append("""
            <div class="page-overlay" style="layout-mode: center; vertical-align: middle; horizontal-align: center;">
                <div class="decorated-container bestiary-container" data-hyui-title="Achievements" style="anchor-height: 700;">
                    <div class="container-contents" style="layout-mode: top; padding: 10;">
                        <p class="page-title" style="color: #FFD700;">ACHIEVEMENTS</p>
            """);

        sb.append(String.format("<p class=\"page-stats\">%d / %d completed</p>", completed, total));

        // Main tab buttons
        appendTabRow(sb, false);

        sb.append("<div class=\"divider\" style=\"background-color: #FFD700; anchor-height: 2; margin-bottom: 6;\"></div>");
        sb.append("<div style=\"layout-mode: top-scrolling; flex-weight: 1;\">");

        List<String> claimableIds = new ArrayList<>();

        for (AchievementTemplate.Category category : AchievementTemplate.Category.values()) {
            List<AchievementTemplate> categoryAchievements = AchievementTemplate.getByCategory(category);
            if (categoryAchievements.isEmpty()) continue;

            String catColor = ACH_CATEGORY_COLORS[category.ordinal()];

            for (AchievementTemplate ach : categoryAchievements) {
                boolean isCompleted = state.isCompleted(ach.getId());
                boolean isClaimed = state.isClaimed(ach.getId());
                int progress = state.getProgress(ach.getId());

                String cardBg = isCompleted ? "#142014" : "#1a1a2a";
                String nameColor = isCompleted ? "#55ff55" : "#dddddd";
                String checkmark = isCompleted ? (isClaimed ? " [DONE]" : " [DONE]") : "";

                float pct = ach.getTarget() > 0 ? Math.min(1f, (float) progress / ach.getTarget()) : 0f;
                int barWidth = Math.round(580 * pct);
                if (barWidth < 1 && progress > 0) barWidth = 1;
                String barColor = isCompleted ? "#55ff55" : catColor;

                sb.append(String.format("""
                    <div class="ach-card" style="background-color: %s;">
                        <div class="ach-header">
                            <p class="ach-name" style="color: %s;">%s%s</p>
                            <p class="ach-cat" style="background-color: %s22; color: %s;">%s</p>
                        </div>
                        <p class="ach-desc">%s</p>
                        <div class="ach-progress-row">
                            <div class="ach-bar-bg" style="background-color: #0a0a15;">
                                <div class="ach-bar-fill" style="anchor-width: %d; background-color: %s;"></div>
                            </div>
                            <p class="ach-progress-text">%d / %d</p>
                        </div>
                    """,
                    cardBg,
                    nameColor, esc(ach.getName()), checkmark,
                    catColor, catColor, category.name(),
                    esc(ach.getDescription()),
                    barWidth, barColor,
                    Math.min(progress, ach.getTarget()), ach.getTarget()));

                // Reward row
                sb.append("<div class=\"ach-reward-row\">");
                String rewardText = ach.getRewardDropTable() != null ? "Reward: Item Drop" : "Reward: XP only";
                if (ach.getXpReward() > 0) rewardText += " + " + ach.getXpReward() + " XP";
                sb.append(String.format("<p class=\"ach-reward-text\">%s</p>", esc(rewardText)));

                if (isCompleted && !isClaimed && ach.getRewardDropTable() != null) {
                    // Claimable button
                    String btnId = "claim_ach_" + ach.getId();
                    claimableIds.add(ach.getId());
                    sb.append(String.format("""
                        <button id="%s" class="custom-textbutton"
                            data-hyui-default-bg="@ClaimBg"
                            data-hyui-hovered-bg="@ClaimHoverBg"
                            data-hyui-pressed-bg="@ClaimPressedBg"
                            data-hyui-default-label-style="@ClaimLabel"
                            data-hyui-hovered-label-style="@ClaimHoverLabel"
                            data-hyui-pressed-label-style="@ClaimPressedLabel"
                            style="anchor-width: 80; anchor-height: 22;">Claim</button>
                        """, btnId));
                } else if (isCompleted && isClaimed && ach.getRewardDropTable() != null) {
                    sb.append("<p style=\"font-size: 10; color: #55ff55; anchor-width: 80; anchor-height: 22; text-align: center; vertical-align: center;\">Claimed</p>");
                }
                sb.append("</div>");

                sb.append("</div>"); // close ach-card
            }
        }

        sb.append("""
                    </div>
                </div>
            </div>
        </div>
        """);

        var builder = PageBuilder.pageForPlayer(playerRef).fromHtml(sb.toString());

        builder.addEventListener("tab_bestiary", CustomUIEventBindingType.Activating, (data, ctx) -> {
            openBestiary(plugin, playerRef, store, playerUuid, manager, null);
        });

        // Achievement claim listeners
        for (String achId : claimableIds) {
            builder.addEventListener("claim_ach_" + achId, CustomUIEventBindingType.Activating, (data, ctx) -> {
                String dropTable = manager.claimAchievement(playerUuid, achId);
                if (dropTable != null) {
                    giveDropTableRewards(playerRef, store, dropTable);
                    openAchievements(plugin, playerRef, store, playerUuid, manager);
                }
            });
        }

        builder.open(store);
    }

    // ======================== SHARED HELPERS ========================

    private static void appendTabRow(StringBuilder sb, boolean bestiaryActive) {
        String bestiaryBg = bestiaryActive ? "@TabActiveBg" : "@TabBg";
        String achieveBg = bestiaryActive ? "@TabBg" : "@TabActiveBg";

        sb.append(String.format("""
            <div class="tab-row">
                <button id="tab_bestiary" class="custom-textbutton"
                    data-hyui-default-bg="%s"
                    data-hyui-hovered-bg="@TabHoverBg"
                    data-hyui-pressed-bg="@TabPressedBg"
                    data-hyui-default-label-style="@TabLabel"
                    data-hyui-hovered-label-style="@TabHoverLabel"
                    data-hyui-pressed-label-style="@TabPressedLabel"
                    style="anchor-width: 150; anchor-height: 26;">Bestiary</button>
                <button id="tab_achievements" class="custom-textbutton"
                    data-hyui-default-bg="%s"
                    data-hyui-hovered-bg="@TabHoverBg"
                    data-hyui-pressed-bg="@TabPressedBg"
                    data-hyui-default-label-style="@TabLabel"
                    data-hyui-hovered-label-style="@TabHoverLabel"
                    data-hyui-pressed-label-style="@TabPressedLabel"
                    style="anchor-width: 150; anchor-height: 26; margin-left: 8;">Achievements</button>
            </div>
            """, bestiaryBg, achieveBg));
    }

    private static void appendMilestoneBadge(StringBuilder sb, String id, String label,
                                                boolean reached, boolean claimed) {
        if (claimed) {
            // Green = already claimed
            sb.append(String.format(
                "<div class=\"milestone-badge\" style=\"background-color: #1a3a1a;\">" +
                "<p class=\"milestone-label\" style=\"color: #55ff55;\">%s</p></div>", label));
        } else if (reached) {
            // Gold button = claimable
            sb.append(String.format("""
                <button id="%s" class="custom-textbutton milestone-badge"
                    data-hyui-default-bg="@ClaimBg"
                    data-hyui-hovered-bg="@ClaimHoverBg"
                    data-hyui-pressed-bg="@ClaimPressedBg"
                    data-hyui-default-label-style="@ClaimLabel"
                    data-hyui-hovered-label-style="@ClaimHoverLabel"
                    data-hyui-pressed-label-style="@ClaimPressedLabel"
                    style="anchor-width: 44; anchor-height: 20;">%s</button>
                """, id, label));
        } else {
            // Gray = locked
            sb.append(String.format(
                "<div class=\"milestone-badge\" style=\"background-color: #1a1a1a;\">" +
                "<p class=\"milestone-label\" style=\"color: #444444;\">%s</p></div>", label));
        }
    }

    private static void appendDiscoveryBadge(StringBuilder sb, String id, String label,
                                              boolean reached, boolean claimed) {
        if (claimed) {
            sb.append(String.format(
                "<div class=\"discovery-badge\" style=\"background-color: #1a3a1a;\">" +
                "<p class=\"discovery-label\" style=\"color: #55ff55;\">%s</p></div>", label));
        } else if (reached) {
            sb.append(String.format("""
                <button id="%s" class="custom-textbutton discovery-badge"
                    data-hyui-default-bg="@ClaimBg"
                    data-hyui-hovered-bg="@ClaimHoverBg"
                    data-hyui-pressed-bg="@ClaimPressedBg"
                    data-hyui-default-label-style="@ClaimLabel"
                    data-hyui-hovered-label-style="@ClaimHoverLabel"
                    data-hyui-pressed-label-style="@ClaimPressedLabel"
                    style="anchor-width: 70; anchor-height: 24;">%s</button>
                """, id, label));
        } else {
            sb.append(String.format(
                "<div class=\"discovery-badge\" style=\"background-color: #1a1a1a;\">" +
                "<p class=\"discovery-label\" style=\"color: #444444;\">%s</p></div>", label));
        }
    }

    private static void giveDropTableRewards(PlayerRef playerRef, Store<EntityStore> store, String dropTable) {
        try {
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) return;

            Store<EntityStore> playerStore = ref.getStore();
            Player player = playerStore.getComponent(ref, Player.getComponentType());
            if (player == null) return;

            List<ItemStack> rewards = ItemModule.get().getRandomItemDrops(dropTable);
            if (rewards == null || rewards.isEmpty()) return;

            for (ItemStack item : rewards) {
                ItemStackTransaction transaction = player.giveItem(item, ref, playerStore);
                ItemStack remainder = transaction.getRemainder();
                if (remainder != null && !remainder.isEmpty()) {
                    ItemUtils.dropItem(ref, remainder, playerStore);
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("[BestiaryUI] Error giving rewards: %s", e.getMessage());
        }
    }

    private static String esc(String text) {
        return endgame.plugin.utils.HtmlUtil.escape(text);
    }
}
