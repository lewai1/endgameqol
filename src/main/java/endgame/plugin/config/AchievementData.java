package endgame.plugin.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player achievement progress and completion state.
 */
public class AchievementData {

    // === Player Achievement State ===
    public static final BuilderCodec<PlayerAchievementState> PLAYER_STATE_CODEC = BuilderCodec
            .builder(PlayerAchievementState.class, PlayerAchievementState::new)
            .append(new KeyedCodec<Map<String, Boolean>>("Completed",
                    new MapCodec<>(Codec.BOOLEAN, ConcurrentHashMap::new, false)),
                    (s, v) -> { if (v != null) s.completed.putAll(v); },
                    s -> s.completed).add()
            .append(new KeyedCodec<Map<String, Integer>>("Progress",
                    new MapCodec<>(Codec.INTEGER, ConcurrentHashMap::new, false)),
                    (s, v) -> { if (v != null) s.progress.putAll(v); },
                    s -> s.progress).add()
            .append(new KeyedCodec<Map<String, Long>>("CompletedAt",
                    new MapCodec<>(Codec.LONG, ConcurrentHashMap::new, false)),
                    (s, v) -> { if (v != null) s.completedAt.putAll(v); },
                    s -> s.completedAt).add()
            .append(new KeyedCodec<Map<String, Boolean>>("Claimed",
                    new MapCodec<>(Codec.BOOLEAN, ConcurrentHashMap::new, false)),
                    (s, v) -> { if (v != null) s.claimed.putAll(v); },
                    s -> s.claimed).add()
            .build();

    // === Root ===
    @Nonnull
    public static final BuilderCodec<AchievementData> CODEC = BuilderCodec
            .builder(AchievementData.class, AchievementData::new)
            .append(new KeyedCodec<Map<String, PlayerAchievementState>>("Players",
                    new MapCodec<>(PLAYER_STATE_CODEC, ConcurrentHashMap::new, false)),
                    (ad, v) -> { if (v != null) ad.players.putAll(v); },
                    ad -> new ConcurrentHashMap<>(ad.players)).add()
            .build();

    private final Map<String, PlayerAchievementState> players = new ConcurrentHashMap<>();

    public PlayerAchievementState getPlayerState(String uuid) {
        return players.get(uuid);
    }

    public void setPlayerState(String uuid, PlayerAchievementState state) {
        players.put(uuid, state);
    }

    public PlayerAchievementState getOrCreatePlayerState(String uuid) {
        return players.computeIfAbsent(uuid, k -> new PlayerAchievementState());
    }

    // === Inner Class ===

    public static class PlayerAchievementState {
        final Map<String, Boolean> completed = new ConcurrentHashMap<>();
        final Map<String, Integer> progress = new ConcurrentHashMap<>();
        final Map<String, Long> completedAt = new ConcurrentHashMap<>();
        final Map<String, Boolean> claimed = new ConcurrentHashMap<>();

        public PlayerAchievementState() {}

        public PlayerAchievementState(PlayerAchievementState other) {
            this.completed.putAll(other.completed);
            this.progress.putAll(other.progress);
            this.completedAt.putAll(other.completedAt);
            this.claimed.putAll(other.claimed);
        }

        public boolean isCompleted(String achievementId) {
            return Boolean.TRUE.equals(completed.get(achievementId));
        }

        public int getProgress(String achievementId) {
            return progress.getOrDefault(achievementId, 0);
        }

        public void setProgress(String achievementId, int value) {
            progress.put(achievementId, value);
        }

        public void markCompleted(String achievementId) {
            completed.put(achievementId, true);
            completedAt.put(achievementId, System.currentTimeMillis());
        }

        public int getCompletedCount() {
            return (int) completed.values().stream().filter(v -> v).count();
        }

        public boolean isClaimed(String achievementId) {
            return Boolean.TRUE.equals(claimed.get(achievementId));
        }

        public void markClaimed(String achievementId) {
            claimed.put(achievementId, true);
        }

        public Map<String, Boolean> getCompleted() { return completed; }
        public Map<String, Integer> getProgressMap() { return progress; }
        public Map<String, Long> getCompletedAt() { return completedAt; }
        public Map<String, Boolean> getClaimed() { return claimed; }
    }
}
