package endgame.plugin.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Persistent leaderboard for The Gauntlet, stored via withConfig("GauntletLeaderboard", CODEC).
 * Keeps top 100 entries sorted by wave count descending.
 */
public class GauntletLeaderboard {

    public static final BuilderCodec<LeaderboardEntry> ENTRY_CODEC = BuilderCodec
            .builder(LeaderboardEntry.class, LeaderboardEntry::new)
            .append(new KeyedCodec<String>("PlayerName", Codec.STRING),
                    (e, v) -> e.playerName = v != null ? v : "Unknown", e -> e.playerName).add()
            .append(new KeyedCodec<String>("PlayerUuid", Codec.STRING),
                    (e, v) -> e.playerUuid = v != null ? v : "", e -> e.playerUuid).add()
            .append(new KeyedCodec<Integer>("Wave", Codec.INTEGER),
                    (e, v) -> e.wave = v != null ? v : 0, e -> e.wave).add()
            .append(new KeyedCodec<Long>("Timestamp", Codec.LONG),
                    (e, v) -> e.timestamp = v != null ? v : 0L, e -> e.timestamp).add()
            .build();

    @Nonnull
    public static final BuilderCodec<GauntletLeaderboard> CODEC = BuilderCodec
            .builder(GauntletLeaderboard.class, GauntletLeaderboard::new)
            .append(new KeyedCodec<Map<String, LeaderboardEntry>>("Entries",
                    new MapCodec<>(ENTRY_CODEC, HashMap::new, false)),
                    (lb, v) -> {
                        if (v != null) lb.entries.addAll(v.values());
                        lb.entries.sort(Comparator.comparingInt(LeaderboardEntry::getWave).reversed()
                                .thenComparingLong(LeaderboardEntry::getTimestamp));
                    },
                    lb -> {
                        Map<String, LeaderboardEntry> map = new HashMap<>();
                        for (int i = 0; i < lb.entries.size(); i++) {
                            map.put(String.valueOf(i), lb.entries.get(i));
                        }
                        return map;
                    }).add()
            .build();

    private final List<LeaderboardEntry> entries = new ArrayList<>();

    private static final int MAX_ENTRIES = 100;

    public static class LeaderboardEntry {
        String playerName = "Unknown";
        String playerUuid = "";
        int wave = 0;
        long timestamp = 0;

        public LeaderboardEntry() {}

        public LeaderboardEntry(String playerName, String playerUuid, int wave, long timestamp) {
            this.playerName = playerName;
            this.playerUuid = playerUuid;
            this.wave = wave;
            this.timestamp = timestamp;
        }

        public String getPlayerName() { return playerName; }
        public String getPlayerUuid() { return playerUuid; }
        public int getWave() { return wave; }
        public long getTimestamp() { return timestamp; }
    }

    /**
     * Submit a new score. Returns true if it made the leaderboard.
     */
    public synchronized boolean submitScore(String playerName, String playerUuid, int wave) {
        LeaderboardEntry entry = new LeaderboardEntry(playerName, playerUuid, wave, System.currentTimeMillis());
        entries.add(entry);
        entries.sort(Comparator.comparingInt(LeaderboardEntry::getWave).reversed()
                .thenComparingLong(LeaderboardEntry::getTimestamp));
        while (entries.size() > MAX_ENTRIES) {
            entries.removeLast();
        }
        return entries.contains(entry);
    }

    /**
     * Get top N entries.
     */
    public synchronized List<LeaderboardEntry> getTop(int n) {
        return new ArrayList<>(entries.subList(0, Math.min(n, entries.size())));
    }

    public synchronized List<LeaderboardEntry> getTopEntries(int n) {
        return getTop(n);
    }

    public synchronized int getEntryCount() {
        return entries.size();
    }

    /**
     * Clear all leaderboard entries. Used by admin commands.
     */
    public synchronized void clearAll() {
        entries.clear();
    }
}
