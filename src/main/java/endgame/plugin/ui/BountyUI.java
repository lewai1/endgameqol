package endgame.plugin.ui;

import au.ellie.hyui.builders.PageBuilder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.modules.item.ItemModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;
import endgame.plugin.config.BountyData.ActiveBounty;
import endgame.plugin.config.BountyData.PlayerBountyState;
import endgame.plugin.managers.BountyManager;
import endgame.plugin.managers.BountyTemplate;
import endgame.plugin.utils.I18n;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HyUI page for the Bounty Board (/bounty command).
 * Shows 3 bounty cards with progress bars, difficulty badges, and claim buttons.
 */
public class BountyUI {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.BountyUI");

    // Difficulty styling (EASY=0, MEDIUM=1, HARD=2, WEEKLY=3)
    private static final String[] DIFF_BADGE_COLORS = {"#2d5a2d", "#5a5a2d", "#5a2d2d", "#3a2d5a"};
    private static final String[] DIFF_TEXT_COLORS = {"#55ff55", "#ffff55", "#ff5555", "#bb88ff"};
    private static final String[] DIFF_NAMES = {"EASY", "MEDIUM", "HARD", "WEEKLY"};
    private static final String[] DIFF_CARD_BG = {"#141f14", "#1f1f14", "#1f1414", "#1a141f"};
    private static final String[] DIFF_BAR_BG = {"#0a150a", "#15150a", "#150a0a", "#0f0a15"};

    // B2: Reputation rank colors
    private static final Map<String, String> RANK_COLORS = Map.of(
        "Novice", "#888888",
        "Veteran", "#55ff55",
        "Elite", "#ffff55",
        "Legend", "#ff8800"
    );

    public static void open(EndgameQoL plugin, PlayerRef playerRef, Store<EntityStore> store, UUID playerUuid) {
        BountyManager manager = plugin.getBountyManager();
        if (manager == null) return;

        PlayerBountyState state = manager.getPlayerBounties(playerUuid);
        if (state == null) return;

        List<ActiveBounty> bounties = state.getBounties();

        // Refresh timer
        long remainingMs = manager.getTimeUntilRefresh(playerUuid);
        long hours = remainingMs / 3600000;
        long minutes = (remainingMs % 3600000) / 60000;
        String refreshText = String.format("%dh %dm", hours, minutes);

        // Count completed
        int completedCount = 0;
        for (ActiveBounty b : bounties) {
            if (b.isCompleted()) completedCount++;
        }

        // B2: Reputation
        String rank = state.getReputationRank();
        String rankColor = RANK_COLORS.getOrDefault(rank, "#888888");
        int totalCompleted = state.getTotalBountiesCompleted();

        // B1: Weekly bounty
        ActiveBounty weeklyBounty = state.getWeeklyBounty();
        boolean weeklyEnabled = plugin.getConfig().get().isBountyWeeklyEnabled();

        // Weekly refresh timer
        long weeklyRemainingMs = manager.getWeeklyTimeUntilRefresh(playerUuid);
        long weeklyDays = weeklyRemainingMs / 86400000L;
        long weeklyHours = (weeklyRemainingMs % 86400000L) / 3600000L;

        StringBuilder sb = new StringBuilder();
        sb.append("""
            <style>
                .bounty-container { anchor-width: 560; }

                /* Header */
                .bounty-header {
                    layout-mode: top;
                    anchor-width: 100%;
                    anchor-height: 65;
                    padding: 8;
                }
                .bounty-title {
                    font-size: 18;
                    font-weight: bold;
                    color: #FFD700;
                    anchor-height: 26;
                    text-align: center;
                    anchor-width: 100%;
                }
                .bounty-subtitle-row {
                    layout-mode: left;
                    anchor-height: 20;
                    anchor-width: 100%;
                    horizontal-align: center;
                }
                .bounty-refresh-label {
                    font-size: 10;
                    color: #666666;
                    anchor-height: 20;
                    vertical-align: center;
                    text-align: center;
                    anchor-width: 140;
                }
                .bounty-refresh-time {
                    font-size: 10;
                    font-weight: bold;
                    color: #888888;
                    anchor-height: 20;
                    vertical-align: center;
                    anchor-width: 60;
                }
                .bounty-progress-summary {
                    font-size: 10;
                    color: #666666;
                    anchor-height: 20;
                    vertical-align: center;
                    text-align: center;
                    anchor-width: 140;
                    padding-left: 20;
                }

                /* Divider */
                .bounty-divider {
                    anchor-width: 100%;
                    anchor-height: 1;
                    background-color: #2a2a1a;
                    margin-bottom: 6;
                }

                /* Card */
                .bounty-card {
                    layout-mode: top;
                    padding: 10;
                    margin-bottom: 6;
                }
                .card-top-row {
                    layout-mode: left;
                    anchor-height: 22;
                    anchor-width: 100%;
                    vertical-align: center;
                }
                .diff-badge {
                    font-size: 9;
                    font-weight: bold;
                    anchor-width: 55;
                    anchor-height: 18;
                    text-align: center;
                    vertical-align: center;
                    padding-top: 1;
                }
                .card-desc {
                    font-size: 13;
                    color: #dddddd;
                    anchor-height: 22;
                    vertical-align: center;
                    flex-weight: 1;
                    padding-left: 10;
                }

                /* Progress bar */
                .progress-row {
                    layout-mode: left;
                    anchor-height: 22;
                    anchor-width: 100%;
                    margin-top: 6;
                    vertical-align: center;
                }
                .progress-bar-bg {
                    anchor-width: 380;
                    anchor-height: 12;
                    vertical-align: center;
                }
                .progress-bar-fill {
                    anchor-height: 12;
                    anchor-left: 0;
                    anchor-top: 0;
                }
                .progress-text {
                    font-size: 11;
                    color: #999999;
                    anchor-height: 22;
                    vertical-align: center;
                    anchor-width: 80;
                    padding-left: 10;
                }

                /* Status line */
                .status-row {
                    layout-mode: left;
                    anchor-height: 20;
                    anchor-width: 100%;
                    margin-top: 4;
                    vertical-align: center;
                }
                .status-text {
                    font-size: 11;
                    font-weight: bold;
                    anchor-height: 20;
                    vertical-align: center;
                    flex-weight: 1;
                }

                /* Claim button */
                @ClaimBtnBg { background-color: #2d5a2d; }
                @ClaimBtnHoverBg { background-color: #3a7a3a; }
                @ClaimBtnPressedBg { background-color: #1e4a1e; }
                @ClaimBtnLabel { font-size: 11; color: #ffffff; font-weight: bold; horizontal-align: center; vertical-align: center; }
                @ClaimBtnHoverLabel { font-size: 11; color: #ffff88; font-weight: bold; horizontal-align: center; vertical-align: center; }

                /* Streak card */
                .streak-card {
                    layout-mode: top;
                    padding: 10;
                    margin-top: 4;
                }
                .streak-header-row {
                    layout-mode: left;
                    anchor-height: 22;
                    anchor-width: 100%;
                    vertical-align: center;
                    horizontal-align: center;
                }
                .streak-icon {
                    font-size: 14;
                    font-weight: bold;
                    color: #FFD700;
                    anchor-width: 24;
                    anchor-height: 22;
                    vertical-align: center;
                    text-align: center;
                }
                .streak-text {
                    font-size: 13;
                    font-weight: bold;
                    color: #FFD700;
                    anchor-height: 22;
                    vertical-align: center;
                    text-align: center;
                    flex-weight: 1;
                }
                .streak-pips-row {
                    layout-mode: left;
                    anchor-height: 16;
                    anchor-width: 100%;
                    horizontal-align: center;
                    margin-top: 4;
                }
                .streak-pip {
                    anchor-width: 12;
                    anchor-height: 12;
                    margin-left: 8;
                    margin-right: 8;
                }
            </style>
            <div class="page-overlay" style="layout-mode: center; vertical-align: middle; horizontal-align: center;">
                <div class="decorated-container bounty-container" data-hyui-title="Bounty Board" style="anchor-height: 620;">
                    <div class="container-contents" style="layout-mode: top; padding: 10;">
                        <div class="bounty-header">
                            <p class="bounty-title">DAILY BOUNTIES</p>
                            <div class="bounty-subtitle-row">
                                <p class="bounty-refresh-label">New bounties in:</p>
            """);
        sb.append(String.format("<p class=\"bounty-refresh-time\">%s</p>", refreshText));
        sb.append(String.format(
            "<p class=\"bounty-progress-summary\">%d / %d completed</p>",
            completedCount, bounties.size()));

        // Phase 2: Reputation rank badge
        sb.append(String.format(
            "<p style=\"font-size: 9; font-weight: bold; color: %s; anchor-height: 20; vertical-align: center; anchor-width: 120; text-align: center;\">%s (%d)</p>",
            rankColor, rank.toUpperCase(), totalCompleted));
        sb.append("""
                            </div>
                        </div>
            """);

        // Phase 2: Reputation progress bar
        int reputation = state.getReputation();
        int nextThreshold = state.getNextRankThreshold();
        float repProgress = nextThreshold > 0 ? Math.min(1f, (float) reputation / nextThreshold) : 1f;
        int repBarWidth = Math.round(520 * repProgress);
        if (repBarWidth < 1 && reputation > 0) repBarWidth = 1;
        sb.append(String.format("""
                        <div style="layout-mode: left; anchor-height: 18; anchor-width: 100%%; padding-left: 10; padding-right: 10; margin-bottom: 4;">
                            <p style="font-size: 9; color: %s; anchor-width: 80; anchor-height: 18; vertical-align: center; font-weight: bold;">%s</p>
                            <div style="anchor-width: 380; anchor-height: 10; background-color: #1a1a2a; vertical-align: center;">
                                <div style="anchor-width: %d; anchor-height: 10; background-color: %s; anchor-left: 0; anchor-top: 0;"></div>
                            </div>
                            <p style="font-size: 9; color: #666666; anchor-width: 60; anchor-height: 18; vertical-align: center; text-align: center;">%d/%d</p>
                        </div>
            """,
            rankColor, rank.toUpperCase(),
            Math.round(380 * repProgress), rankColor,
            reputation, nextThreshold));

        sb.append("""
                        <div class="bounty-divider" style="background-color: #FFD700; anchor-height: 2; margin-bottom: 8;"></div>
            """);

        // Bounty cards
        for (int i = 0; i < bounties.size(); i++) {
            ActiveBounty bounty = bounties.get(i);
            BountyTemplate template = findTemplate(bounty.getTemplateId());
            String desc = template != null ? template.getDescription() : "Unknown bounty";
            int diffIdx = Math.min(i, 2);
            String diffBadgeBg = DIFF_BADGE_COLORS[diffIdx];
            String diffTextColor = DIFF_TEXT_COLORS[diffIdx];
            String diffName = DIFF_NAMES[diffIdx];
            String cardBg = DIFF_CARD_BG[diffIdx];
            String barBg = DIFF_BAR_BG[diffIdx];

            float progress = bounty.getTarget() > 0 ?
                    Math.min(1f, (float) bounty.getProgress() / bounty.getTarget()) : 0f;
            int progressWidth = Math.round(380 * progress);
            if (progressWidth < 1 && bounty.getProgress() > 0) progressWidth = 1;

            String barColor;
            if (bounty.isClaimed()) {
                barColor = "#336633";
            } else if (bounty.isCompleted()) {
                barColor = "#55ff55";
            } else {
                barColor = diffTextColor;
            }

            String statusText;
            String statusColor;
            if (bounty.isClaimed()) {
                statusText = "CLAIMED";
                statusColor = "#336633";
            } else if (bounty.isCompleted()) {
                statusText = "COMPLETE";
                statusColor = "#55ff55";
            } else {
                statusText = "";
                statusColor = "#888888";
            }

            String progressStr = bounty.getProgress() + " / " + bounty.getTarget();

            sb.append(String.format("""
                <div class="bounty-card" style="background-color: %s;">
                    <div class="card-top-row">
                        <p class="diff-badge" style="background-color: %s; color: %s;">%s</p>
                        <p class="card-desc">%s</p>
                    </div>
                    <div class="progress-row">
                        <div class="progress-bar-bg" style="background-color: %s;">
                            <div class="progress-bar-fill" style="anchor-width: %d; background-color: %s;"></div>
                        </div>
                        <p class="progress-text">%s</p>
                    </div>
                """,
                cardBg,
                diffBadgeBg, diffTextColor, diffName,
                escapeHtml(desc),
                barBg,
                progressWidth, barColor,
                progressStr));

            // Phase 2: Reward preview (XP + reputation points)
            if (template != null && (template.getXpReward() > 0 || template.getReputationReward() > 0)) {
                StringBuilder rewardSb = new StringBuilder();
                if (template.getXpReward() > 0) {
                    rewardSb.append(template.getXpReward()).append(" XP");
                }
                if (template.getReputationReward() > 0) {
                    if (rewardSb.length() > 0) rewardSb.append(" + ");
                    rewardSb.append(template.getReputationReward()).append(" Rep");
                }
                sb.append(String.format(
                    "<p style=\"font-size: 9; color: #8888cc; anchor-height: 14; margin-top: 2;\">Reward: %s + Items</p>",
                    rewardSb.toString()));
            }

            // B3: Bonus objective line
            String bonusTypeId = bounty.getBonusType();
            if (bonusTypeId != null && !bonusTypeId.isEmpty()) {
                BountyTemplate.BonusType bonusType = BountyTemplate.BonusType.fromId(bonusTypeId);
                if (bonusType != BountyTemplate.BonusType.NONE) {
                    String bonusColor = bounty.isBonusCompleted() ? "#55ff55" : "#666666";
                    String bonusPrefix = bounty.isBonusCompleted() ? "BONUS COMPLETE" : "BONUS";
                    sb.append(String.format(
                        "<p style=\"font-size: 9; color: %s; anchor-height: 14; margin-top: 2;\">%s: %s (+50%% reward)</p>",
                        bonusColor, bonusPrefix, escapeHtml(bonusType.getDescription())));
                }
            }

            // Status + claim button row
            if (bounty.isCompleted() && !bounty.isClaimed()) {
                sb.append(String.format("""
                    <div class="status-row">
                        <p class="status-text" style="color: %s;">%s</p>
                        <button id="claim_%d" class="custom-textbutton"
                            data-hyui-default-bg="@ClaimBtnBg"
                            data-hyui-hovered-bg="@ClaimBtnHoverBg"
                            data-hyui-pressed-bg="@ClaimBtnPressedBg"
                            data-hyui-default-label-style="@ClaimBtnLabel"
                            data-hyui-hovered-label-style="@ClaimBtnHoverLabel"
                            style="anchor-height: 24; anchor-width: 110;">Claim</button>
                    </div>
                    """, statusColor, statusText, i));
            } else if (!statusText.isEmpty()) {
                sb.append(String.format("""
                    <div class="status-row">
                        <p class="status-text" style="color: %s;">%s</p>
                    </div>
                    """, statusColor, statusText));
            }

            sb.append("</div>");
        }

        // Streak bonus section
        boolean allCompleted = state.allCompleted();
        boolean streakClaimed = state.isStreakClaimed();
        if (plugin.getConfig().get().isBountyStreakEnabled()) {
            String streakBg = streakClaimed ? "#142014" :
                              allCompleted ? "#1f1f0a" : "#141418";

            sb.append(String.format("<div class=\"streak-card\" style=\"background-color: %s;\">", streakBg));

            // Streak pips (3 circles showing bounty completion)
            sb.append("<div class=\"streak-pips-row\">");
            for (int i = 0; i < bounties.size(); i++) {
                ActiveBounty b = bounties.get(i);
                String pipColor = b.isCompleted() ? "#55ff55" : "#333333";
                sb.append(String.format(
                    "<div class=\"streak-pip\" style=\"background-color: %s;\"></div>", pipColor));
            }
            sb.append("</div>");

            sb.append("<div class=\"streak-header-row\">");
            if (streakClaimed) {
                sb.append("<p class=\"streak-text\" style=\"color: #55ff55;\">Streak Bonus Claimed!</p>");
            } else if (allCompleted) {
                sb.append("<p class=\"streak-text\">All bounties complete! Claim your streak bonus</p>");
            } else {
                sb.append(String.format(
                    "<p class=\"streak-text\" style=\"color: #666666;\">Complete all %d bounties for a streak bonus</p>",
                    bounties.size()));
            }
            sb.append("</div>");

            if (allCompleted && !streakClaimed) {
                sb.append("""
                    <button id="claim_streak" class="custom-textbutton"
                        data-hyui-default-bg="@ClaimBtnBg"
                        data-hyui-hovered-bg="@ClaimBtnHoverBg"
                        data-hyui-pressed-bg="@ClaimBtnPressedBg"
                        data-hyui-default-label-style="@ClaimBtnLabel"
                        data-hyui-hovered-label-style="@ClaimBtnHoverLabel"
                        style="anchor-height: 28; anchor-width: 140; margin-top: 4; horizontal-align: center;">Claim Streak</button>
                    """);
            }

            sb.append("</div>");
        }

        // B1: Weekly bounty card
        if (weeklyEnabled && weeklyBounty != null) {
            sb.append("<div class=\"bounty-divider\" style=\"background-color: #bb88ff; anchor-height: 2; margin-top: 8; margin-bottom: 8;\"></div>");

            BountyTemplate weeklyTemplate = findTemplate(weeklyBounty.getTemplateId());
            String weeklyDesc = weeklyTemplate != null ? weeklyTemplate.getDescription() : "Unknown weekly";
            int wDiffIdx = 3; // WEEKLY index
            String wBadgeBg = DIFF_BADGE_COLORS[wDiffIdx];
            String wTextColor = DIFF_TEXT_COLORS[wDiffIdx];
            String wCardBg = DIFF_CARD_BG[wDiffIdx];
            String wBarBg = DIFF_BAR_BG[wDiffIdx];

            float wProgress = weeklyBounty.getTarget() > 0 ?
                    Math.min(1f, (float) weeklyBounty.getProgress() / weeklyBounty.getTarget()) : 0f;
            int wProgressWidth = Math.round(380 * wProgress);
            if (wProgressWidth < 1 && weeklyBounty.getProgress() > 0) wProgressWidth = 1;

            String wBarColor;
            if (weeklyBounty.isClaimed()) {
                wBarColor = "#333366";
            } else if (weeklyBounty.isCompleted()) {
                wBarColor = "#bb88ff";
            } else {
                wBarColor = wTextColor;
            }

            String wStatusText;
            String wStatusColor;
            if (weeklyBounty.isClaimed()) {
                wStatusText = "CLAIMED";
                wStatusColor = "#333366";
            } else if (weeklyBounty.isCompleted()) {
                wStatusText = "COMPLETE";
                wStatusColor = "#bb88ff";
            } else {
                wStatusText = String.format("Resets in %dd %dh", weeklyDays, weeklyHours);
                wStatusColor = "#666666";
            }

            sb.append(String.format("""
                <div class="bounty-card" style="background-color: %s;">
                    <div class="card-top-row">
                        <p class="diff-badge" style="background-color: %s; color: %s;">%s</p>
                        <p class="card-desc">%s</p>
                    </div>
                    <div class="progress-row">
                        <div class="progress-bar-bg" style="background-color: %s;">
                            <div class="progress-bar-fill" style="anchor-width: %d; background-color: %s;"></div>
                        </div>
                        <p class="progress-text">%s</p>
                    </div>
                """,
                wCardBg,
                wBadgeBg, wTextColor, DIFF_NAMES[wDiffIdx],
                escapeHtml(weeklyDesc),
                wBarBg,
                wProgressWidth, wBarColor,
                weeklyBounty.getProgress() + " / " + weeklyBounty.getTarget()));

            if (weeklyBounty.isCompleted() && !weeklyBounty.isClaimed()) {
                sb.append(String.format("""
                    <div class="status-row">
                        <p class="status-text" style="color: %s;">%s</p>
                        <button id="claim_3" class="custom-textbutton"
                            data-hyui-default-bg="@ClaimBtnBg"
                            data-hyui-hovered-bg="@ClaimBtnHoverBg"
                            data-hyui-pressed-bg="@ClaimBtnPressedBg"
                            data-hyui-default-label-style="@ClaimBtnLabel"
                            data-hyui-hovered-label-style="@ClaimBtnHoverLabel"
                            style="anchor-height: 24; anchor-width: 110;">Claim</button>
                    </div>
                    """, wStatusColor, wStatusText));
            } else {
                sb.append(String.format("""
                    <div class="status-row">
                        <p class="status-text" style="color: %s;">%s</p>
                    </div>
                    """, wStatusColor, wStatusText));
            }

            sb.append("</div>");
        }

        sb.append("""
                    </div>
                </div>
            </div>
            """);

        var builder = PageBuilder.pageForPlayer(playerRef)
                .fromHtml(sb.toString());

        // Claim listeners (only for bounties that have a claim button)
        for (int i = 0; i < bounties.size(); i++) {
            ActiveBounty b = bounties.get(i);
            if (b.isCompleted() && !b.isClaimed()) {
                final int idx = i;
                builder.addEventListener("claim_" + i, CustomUIEventBindingType.Activating, (data, ctx) -> {
                    String dropTable = manager.claimBounty(playerUuid, idx);
                    if (dropTable != null) {
                        giveDropTableRewards(playerRef, store, dropTable);
                        playerRef.sendMessage(Message.raw("[Bounty] " + I18n.getForPlayer(playerRef, "bounty.reward_claimed")).color("#55ff55"));
                        open(plugin, playerRef, store, playerUuid);
                    }
                });
            }
        }

        // B1: Weekly claim listener (only if button exists)
        if (weeklyEnabled && weeklyBounty != null && weeklyBounty.isCompleted() && !weeklyBounty.isClaimed()) {
            builder.addEventListener("claim_3", CustomUIEventBindingType.Activating, (data, ctx) -> {
                String dropTable = manager.claimBounty(playerUuid, 3);
                if (dropTable != null) {
                    giveDropTableRewards(playerRef, store, dropTable);
                    playerRef.sendMessage(Message.raw("[Bounty] " + I18n.getForPlayer(playerRef, "bounty.weekly_claimed")).color("#bb88ff"));
                    open(plugin, playerRef, store, playerUuid);
                }
            });
        }

        // Streak claim listener (only if button exists)
        if (allCompleted && !streakClaimed && plugin.getConfig().get().isBountyStreakEnabled()) {
            builder.addEventListener("claim_streak", CustomUIEventBindingType.Activating, (data, ctx) -> {
                if (manager.claimStreak(playerUuid)) {
                    giveDropTableRewards(playerRef, store, "Endgame_Drop_Bounty_Streak");
                    playerRef.sendMessage(Message.raw("[Bounty] " + I18n.getForPlayer(playerRef, "bounty.streak_claimed")).color("#FFD700"));
                    open(plugin, playerRef, store, playerUuid);
                }
            });
        }

        builder.open(store);
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
            LOGGER.atWarning().log("[BountyUI] Error giving rewards: %s", e.getMessage());
        }
    }

    private static BountyTemplate findTemplate(String id) {
        for (BountyTemplate t : BountyTemplate.getEasyPool()) {
            if (t.getId().equals(id)) return t;
        }
        for (BountyTemplate t : BountyTemplate.getMediumPool()) {
            if (t.getId().equals(id)) return t;
        }
        for (BountyTemplate t : BountyTemplate.getHardPool()) {
            if (t.getId().equals(id)) return t;
        }
        for (BountyTemplate t : BountyTemplate.getWeeklyPool()) {
            if (t.getId().equals(id)) return t;
        }
        return null;
    }

    private static String escapeHtml(String text) {
        return endgame.plugin.utils.HtmlUtil.escape(text);
    }
}
