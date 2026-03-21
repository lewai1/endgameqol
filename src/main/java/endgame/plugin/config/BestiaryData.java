package endgame.plugin.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-player bestiary data: tracks NPC kills and discoveries.
 */
public class BestiaryData {

    // === NPC Entry ===
    public static final BuilderCodec<NPCEntry> NPC_ENTRY_CODEC = BuilderCodec
            .builder(NPCEntry.class, NPCEntry::new)
            .append(new KeyedCodec<Integer>("KillCount", Codec.INTEGER),
                    (e, v) -> e.killCount.set(v != null ? v : 0), e -> e.killCount.get()).add()
            .append(new KeyedCodec<Long>("FirstKillTimestamp", Codec.LONG),
                    (e, v) -> e.firstKillTimestamp = v != null ? v : 0L, e -> e.firstKillTimestamp).add()
            .append(new KeyedCodec<Boolean>("Discovered", Codec.BOOLEAN),
                    (e, v) -> e.discovered = v != null ? v : false, e -> e.discovered).add()
            .append(new KeyedCodec<Integer>("ClaimedMilestone", Codec.INTEGER),
                    (e, v) -> e.claimedMilestone.set(v != null ? v : 0), e -> e.claimedMilestone.get()).add()
            .build();

    // === Player Bestiary State ===
    public static final BuilderCodec<PlayerBestiaryState> PLAYER_STATE_CODEC = BuilderCodec
            .builder(PlayerBestiaryState.class, PlayerBestiaryState::new)
            .append(new KeyedCodec<Map<String, NPCEntry>>("Entries",
                    new MapCodec<>(NPC_ENTRY_CODEC, ConcurrentHashMap::new, false)),
                    (s, v) -> { if (v != null) s.entries.putAll(v); },
                    s -> s.entries).add()
            .append(new KeyedCodec<Integer>("ClaimedDiscoveryMilestone", Codec.INTEGER),
                    (s, v) -> s.claimedDiscoveryMilestone = v != null ? v : 0,
                    s -> s.claimedDiscoveryMilestone).add()
            .build();

    // === Root ===
    @Nonnull
    public static final BuilderCodec<BestiaryData> CODEC = BuilderCodec
            .builder(BestiaryData.class, BestiaryData::new)
            .append(new KeyedCodec<Map<String, PlayerBestiaryState>>("Players",
                    new MapCodec<>(PLAYER_STATE_CODEC, ConcurrentHashMap::new, false)),
                    (bd, v) -> { if (v != null) bd.players.putAll(v); },
                    bd -> new ConcurrentHashMap<>(bd.players)).add()
            .build();

    private final Map<String, PlayerBestiaryState> players = new ConcurrentHashMap<>();

    public PlayerBestiaryState getPlayerState(String uuid) {
        return players.get(uuid);
    }

    public void setPlayerState(String uuid, PlayerBestiaryState state) {
        players.put(uuid, state);
    }

    public PlayerBestiaryState getOrCreatePlayerState(String uuid) {
        return players.computeIfAbsent(uuid, k -> new PlayerBestiaryState());
    }

    // === Inner Classes ===

    public static class NPCEntry {
        final AtomicInteger killCount = new AtomicInteger(0);
        volatile long firstKillTimestamp = 0;
        volatile boolean discovered = false;
        final AtomicInteger claimedMilestone = new AtomicInteger(0);

        public NPCEntry() {}

        public NPCEntry(NPCEntry other) {
            this.killCount.set(other.killCount.get());
            this.firstKillTimestamp = other.firstKillTimestamp;
            this.discovered = other.discovered;
            this.claimedMilestone.set(other.claimedMilestone.get());
        }

        public int getKillCount() { return killCount.get(); }
        public long getFirstKillTimestamp() { return firstKillTimestamp; }
        public boolean isDiscovered() { return discovered; }
        public int getClaimedMilestone() { return claimedMilestone.get(); }
        public void setClaimedMilestone(int threshold) { this.claimedMilestone.set(threshold); }

        public void recordKill() {
            this.killCount.incrementAndGet();
            if (this.firstKillTimestamp == 0) {
                this.firstKillTimestamp = System.currentTimeMillis();
            }
            this.discovered = true;
        }

        public void markDiscovered() {
            this.discovered = true;
        }
    }

    public static class PlayerBestiaryState {
        final Map<String, NPCEntry> entries = new ConcurrentHashMap<>();
        volatile int claimedDiscoveryMilestone = 0;

        public PlayerBestiaryState() {}

        public PlayerBestiaryState(PlayerBestiaryState other) {
            for (var entry : other.entries.entrySet()) {
                this.entries.put(entry.getKey(), new NPCEntry(entry.getValue()));
            }
            this.claimedDiscoveryMilestone = other.claimedDiscoveryMilestone;
        }

        public int getClaimedDiscoveryMilestone() { return claimedDiscoveryMilestone; }
        public void setClaimedDiscoveryMilestone(int threshold) { this.claimedDiscoveryMilestone = threshold; }

        public Map<String, NPCEntry> getEntries() { return entries; }

        public NPCEntry getOrCreate(String npcTypeId) {
            return entries.computeIfAbsent(npcTypeId, k -> new NPCEntry());
        }

        public int getTotalKills() {
            return entries.values().stream().mapToInt(NPCEntry::getKillCount).sum();
        }

        public int getDiscoveredCount() {
            return (int) entries.values().stream().filter(NPCEntry::isDiscovered).count();
        }
    }
}
