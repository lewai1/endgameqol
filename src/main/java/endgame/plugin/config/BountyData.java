package endgame.plugin.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Persistent bounty state per player, stored via withConfig("BountyData", CODEC).
 */
public class BountyData {

    // === Active Bounty ===
    public static final BuilderCodec<ActiveBounty> ACTIVE_BOUNTY_CODEC = BuilderCodec
            .builder(ActiveBounty.class, ActiveBounty::new)
            .append(new KeyedCodec<String>("TemplateId", Codec.STRING),
                    (b, v) -> b.templateId = v != null ? v : "", b -> b.templateId).add()
            .append(new KeyedCodec<Integer>("Progress", Codec.INTEGER),
                    (b, v) -> b.progress = v != null ? v : 0, b -> b.progress).add()
            .append(new KeyedCodec<Integer>("Target", Codec.INTEGER),
                    (b, v) -> b.target = v != null ? v : 1, b -> b.target).add()
            .append(new KeyedCodec<Boolean>("Completed", Codec.BOOLEAN),
                    (b, v) -> b.completed = v != null ? v : false, b -> b.completed).add()
            .append(new KeyedCodec<Boolean>("Claimed", Codec.BOOLEAN),
                    (b, v) -> b.claimed = v != null ? v : false, b -> b.claimed).add()
            .append(new KeyedCodec<String>("BonusType", Codec.STRING),
                    (b, v) -> b.bonusType = v != null ? v : "", b -> b.bonusType).add()
            .append(new KeyedCodec<Boolean>("BonusCompleted", Codec.BOOLEAN),
                    (b, v) -> b.bonusCompleted = v != null ? v : false, b -> b.bonusCompleted).add()
            .append(new KeyedCodec<String>("Metadata", Codec.STRING),
                    (b, v) -> b.metadata = v != null ? v : "", b -> b.metadata).add()
            .build();

    // === Player Bounty State ===
    public static final BuilderCodec<PlayerBountyState> PLAYER_STATE_CODEC = BuilderCodec
            .builder(PlayerBountyState.class, PlayerBountyState::new)
            .append(new KeyedCodec<Long>("LastRefreshTimestamp", Codec.LONG),
                    (s, v) -> s.lastRefreshTimestamp = v != null ? v : 0L, s -> s.lastRefreshTimestamp).add()
            .append(new KeyedCodec<Map<String, ActiveBounty>>("Bounties",
                    new MapCodec<>(ACTIVE_BOUNTY_CODEC, ConcurrentHashMap::new, false)),
                    (s, v) -> {
                        if (v != null) {
                            // Restore in key order (0, 1, 2)
                            v.entrySet().stream()
                                    .sorted(Map.Entry.comparingByKey())
                                    .forEach(e -> s.bounties.add(e.getValue()));
                        }
                    },
                    s -> {
                        Map<String, ActiveBounty> map = new ConcurrentHashMap<>();
                        for (int i = 0; i < s.bounties.size(); i++) {
                            map.put(String.valueOf(i), s.bounties.get(i));
                        }
                        return map;
                    }).add()
            .append(new KeyedCodec<Boolean>("StreakClaimed", Codec.BOOLEAN),
                    (s, v) -> s.streakClaimed = v != null ? v : false, s -> s.streakClaimed).add()
            .append(new KeyedCodec<Integer>("TotalBountiesCompleted", Codec.INTEGER),
                    (s, v) -> s.totalBountiesCompleted = v != null ? v : 0, s -> s.totalBountiesCompleted).add()
            .append(new KeyedCodec<Integer>("Reputation", Codec.INTEGER),
                    (s, v) -> s.reputation = v != null ? v : 0, s -> s.reputation).add()
            .append(new KeyedCodec<Long>("LastWeeklyRefreshTimestamp", Codec.LONG),
                    (s, v) -> s.lastWeeklyRefreshTimestamp = v != null ? v : 0L, s -> s.lastWeeklyRefreshTimestamp).add()
            .append(new KeyedCodec<Map<String, ActiveBounty>>("WeeklyBounty",
                    new MapCodec<>(ACTIVE_BOUNTY_CODEC, ConcurrentHashMap::new, false)),
                    (s, v) -> {
                        if (v != null && !v.isEmpty()) {
                            s.weeklyBounty = v.values().iterator().next();
                        }
                    },
                    s -> {
                        Map<String, ActiveBounty> map = new ConcurrentHashMap<>();
                        if (s.weeklyBounty != null) {
                            map.put("0", s.weeklyBounty);
                        }
                        return map;
                    }).add()
            .build();

    // === Root ===
    @Nonnull
    public static final BuilderCodec<BountyData> CODEC = BuilderCodec
            .builder(BountyData.class, BountyData::new)
            .append(new KeyedCodec<Map<String, PlayerBountyState>>("Players",
                    new MapCodec<>(PLAYER_STATE_CODEC, ConcurrentHashMap::new, false)),
                    (bd, v) -> { if (v != null) bd.players.putAll(v); },
                    bd -> new ConcurrentHashMap<>(bd.players)).add()
            .build();

    private final Map<String, PlayerBountyState> players = new ConcurrentHashMap<>();

    public Map<String, PlayerBountyState> getPlayers() {
        return players;
    }

    public PlayerBountyState getPlayerState(String uuid) {
        return players.get(uuid);
    }

    public void setPlayerState(String uuid, PlayerBountyState state) {
        players.put(uuid, state);
    }

    // === Inner classes ===

    public static class ActiveBounty {
        String templateId = "";
        int progress = 0;
        int target = 1;
        boolean completed = false;
        boolean claimed = false;
        String bonusType = "";
        boolean bonusCompleted = false;
        // Tracking metadata (e.g., comma-separated boss types for KILL_UNIQUE_BOSSES)
        volatile String metadata = "";

        public ActiveBounty() {}

        public ActiveBounty(ActiveBounty other) {
            this.templateId = other.templateId;
            this.progress = other.progress;
            this.target = other.target;
            this.completed = other.completed;
            this.claimed = other.claimed;
            this.bonusType = other.bonusType;
            this.bonusCompleted = other.bonusCompleted;
            this.metadata = other.metadata;
        }

        public ActiveBounty(String templateId, int target) {
            this.templateId = templateId;
            this.target = target;
        }

        public String getTemplateId() { return templateId; }
        public int getProgress() { return progress; }
        public int getTarget() { return target; }
        public boolean isCompleted() { return completed; }
        public boolean isClaimed() { return claimed; }
        public String getBonusType() { return bonusType; }
        public void setBonusType(String bonusType) { this.bonusType = bonusType != null ? bonusType : ""; }
        public boolean isBonusCompleted() { return bonusCompleted; }
        public void setBonusCompleted(boolean bonusCompleted) { this.bonusCompleted = bonusCompleted; }

        public void incrementProgress(int amount) {
            this.progress = Math.min(this.progress + amount, this.target);
            if (this.progress >= this.target) {
                this.completed = true;
            }
        }

        public void setClaimed(boolean claimed) { this.claimed = claimed; }
        public String getMetadata() { return metadata; }
        public void setMetadata(String metadata) { this.metadata = metadata != null ? metadata : ""; }
    }

    public static class PlayerBountyState {
        long lastRefreshTimestamp = 0;
        final List<ActiveBounty> bounties = new CopyOnWriteArrayList<>();
        boolean streakClaimed = false;
        int totalBountiesCompleted = 0;
        int reputation = 0;
        long lastWeeklyRefreshTimestamp = 0;
        ActiveBounty weeklyBounty = null;

        public PlayerBountyState() {}

        public PlayerBountyState(PlayerBountyState other) {
            this.lastRefreshTimestamp = other.lastRefreshTimestamp;
            for (ActiveBounty b : other.bounties) {
                this.bounties.add(new ActiveBounty(b));
            }
            this.streakClaimed = other.streakClaimed;
            this.totalBountiesCompleted = other.totalBountiesCompleted;
            this.reputation = other.reputation;
            this.lastWeeklyRefreshTimestamp = other.lastWeeklyRefreshTimestamp;
            this.weeklyBounty = other.weeklyBounty != null ? new ActiveBounty(other.weeklyBounty) : null;
        }

        public long getLastRefreshTimestamp() { return lastRefreshTimestamp; }
        public void setLastRefreshTimestamp(long ts) { this.lastRefreshTimestamp = ts; }
        public List<ActiveBounty> getBounties() { return bounties; }
        public boolean isStreakClaimed() { return streakClaimed; }
        public void setStreakClaimed(boolean claimed) { this.streakClaimed = claimed; }

        public int getTotalBountiesCompleted() { return totalBountiesCompleted; }
        public void incrementTotalCompleted() { this.totalBountiesCompleted++; }

        public int getReputation() { return reputation; }
        public void addReputation(int amount) { this.reputation += amount; }

        public long getLastWeeklyRefreshTimestamp() { return lastWeeklyRefreshTimestamp; }
        public void setLastWeeklyRefreshTimestamp(long ts) { this.lastWeeklyRefreshTimestamp = ts; }
        public ActiveBounty getWeeklyBounty() { return weeklyBounty; }
        public void setWeeklyBounty(ActiveBounty bounty) { this.weeklyBounty = bounty; }

        public boolean allCompleted() {
            return bounties.stream().allMatch(ActiveBounty::isCompleted);
        }

        public boolean allClaimed() {
            return bounties.stream().allMatch(ActiveBounty::isClaimed);
        }

        /**
         * Get reputation rank based on accumulated reputation points.
         * 0-499: Novice, 500-1499: Veteran, 1500-2999: Elite, 3000+: Legend
         */
        public String getReputationRank() {
            if (reputation >= 3000) return "Legend";
            if (reputation >= 1500) return "Elite";
            if (reputation >= 500) return "Veteran";
            return "Novice";
        }

        /**
         * Get the reputation threshold for the next rank.
         */
        public int getNextRankThreshold() {
            if (reputation >= 3000) return 3000; // already max
            if (reputation >= 1500) return 3000;
            if (reputation >= 500) return 1500;
            return 500;
        }

        /**
         * Get reputation reward multiplier (bonus percentage on drops).
         * Novice: 1.0x, Veteran: 1.1x, Elite: 1.2x, Legend: 1.3x
         */
        public float getReputationRewardMultiplier() {
            if (reputation >= 3000) return 1.3f;
            if (reputation >= 1500) return 1.2f;
            if (reputation >= 500) return 1.1f;
            return 1.0f;
        }
    }
}
