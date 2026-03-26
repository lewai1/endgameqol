package endgame.plugin.rifts;

import com.hypixel.hytale.logger.HytaleLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.Properties;

public class RiftConfig {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    static final Path CONFIG_DIR = Path.of("mods", "HyRifts");
    static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.cfg");

    // --- Portal spawning ---
    volatile int rollIntervalSeconds = 300;
    volatile double rollChance = 0.35;
    volatile int maxActivePortals = 3;
    volatile int minPortalsPerRoll = 1;
    volatile int maxPortalsPerRoll = 2;
    volatile int portalLifetimeSeconds = 600;

    // --- Placement ---
    volatile int minDistanceFromPlayers = 80;
    volatile int maxDistanceFromPlayers = 500;
    volatile int maxPlacementAttempts = 20;

    // --- Grief mode: "enabled" (spawn anywhere), "disabled" (avoid all builds), "claimsProtection" (avoid claimed chunks) ---
    volatile GriefMode griefMode = GriefMode.CLAIMS_PROTECTION;

    // --- Protected block keywords (grief=disabled). Block IDs containing any of these are treated as player-placed. ---
    volatile Set<String> griefProtectedBlocks = new HashSet<>(Arrays.asList(
            "crafting", "furnace", "chest", "barrel", "bench", "anvil",
            "sign", "door", "fence", "lantern", "torch", "bed",
            "plank", "brick", "slab", "stair", "wool", "glass",
            "carpet", "banner"
    ));

    // --- Difficulty (Endless Leveling mob levels) ---
    volatile double mobLevelRadius = 50.0;

    // --- Announcements ---
    volatile boolean announcePortals = true;
    volatile boolean announceDespawn = true;

    // --- Rift Compass item ---
    volatile String riftCompassItemId = "Rift_Compass";
    volatile double riftCompassRange = 750.0;

    // --- Per-rank settings ---
    final Map<RiftRank, RiftRankSettings> rankSettings = new EnumMap<>(RiftRank.class);

    public RiftConfig() {
        for (RiftRank rank : RiftRank.values()) {
            rankSettings.put(rank, new RiftRankSettings(rank));
        }
    }

    RiftRankSettings getRankSettings(RiftRank rank) {
        return rankSettings.get(rank);
    }

    public void load() {
        Properties props = new Properties();
        if (Files.exists(CONFIG_FILE)) {
            try (var reader = Files.newBufferedReader(CONFIG_FILE)) {
                props.load(reader);
            } catch (IOException e) {
                ((HytaleLogger.Api) LOGGER.atWarning()).log("Failed to read config: %s", e.getMessage());
            }
        } else {
            try {
                Files.createDirectories(CONFIG_DIR);
                Files.writeString(CONFIG_FILE, DEFAULT_CONFIG);
                ((HytaleLogger.Api) LOGGER.atInfo()).log("Created default config at %s", CONFIG_FILE);
            } catch (IOException e) {
                ((HytaleLogger.Api) LOGGER.atWarning()).log("Failed to create default config: %s", e.getMessage());
            }
        }

        rollIntervalSeconds = getInt(props, "roll_interval_seconds", rollIntervalSeconds);
        rollChance = getDouble(props, "roll_chance", rollChance);
        maxActivePortals = getInt(props, "max_active_portals", maxActivePortals);
        minPortalsPerRoll = getInt(props, "min_portals_per_roll", minPortalsPerRoll);
        maxPortalsPerRoll = getInt(props, "max_portals_per_roll", maxPortalsPerRoll);
        portalLifetimeSeconds = getInt(props, "portal_lifetime_seconds", portalLifetimeSeconds);

        minDistanceFromPlayers = getInt(props, "min_distance_from_players", minDistanceFromPlayers);
        maxDistanceFromPlayers = getInt(props, "max_distance_from_players", maxDistanceFromPlayers);
        maxPlacementAttempts = getInt(props, "max_placement_attempts", maxPlacementAttempts);

        String griefStr = props.getProperty("grief", "claimsProtection").trim();
        griefMode = switch (griefStr.toLowerCase()) {
            case "enabled" -> GriefMode.ENABLED;
            case "disabled" -> GriefMode.DISABLED;
            default -> GriefMode.CLAIMS_PROTECTION;
        };

        String protectedStr = props.getProperty("grief_protected_blocks");
        if (protectedStr != null && !protectedStr.isBlank()) {
            Set<String> parsed = new HashSet<>();
            for (String token : protectedStr.split(",")) {
                String trimmed = token.trim().toLowerCase();
                if (!trimmed.isEmpty()) parsed.add(trimmed);
            }
            if (!parsed.isEmpty()) griefProtectedBlocks = parsed;
        }

        mobLevelRadius = getDouble(props, "mob_level_radius", mobLevelRadius);
        announcePortals = getBool(props, "announce_portals", announcePortals);
        announceDespawn = getBool(props, "announce_despawn", announceDespawn);

        riftCompassItemId = props.getProperty("rift_compass_item_id", riftCompassItemId).trim();
        riftCompassRange = getDouble(props, "rift_compass_range", riftCompassRange);

        // Per-rank settings: rank.E.min_level, rank.E.max_level, rank.E.weight, rank.E.tiered
        for (RiftRank rank : RiftRank.values()) {
            String prefix = "rank." + rank.name() + ".";
            RiftRankSettings rs = rankSettings.get(rank);
            rs.minLevel = getInt(props, prefix + "min_level", rank.defaultMinLevel);
            rs.maxLevel = getInt(props, prefix + "max_level", rank.defaultMaxLevel);
            rs.weight = getInt(props, prefix + "weight", rank.defaultWeight);
            rs.tiered = getBool(props, prefix + "tiered", rank.defaultTiered);
        }
    }

    private static int getInt(Properties props, String key, int def) {
        String val = props.getProperty(key);
        if (val == null || val.isBlank()) return def;
        try { return Integer.parseInt(val.trim()); }
        catch (NumberFormatException e) { return def; }
    }

    private static double getDouble(Properties props, String key, double def) {
        String val = props.getProperty(key);
        if (val == null || val.isBlank()) return def;
        try { return Double.parseDouble(val.trim()); }
        catch (NumberFormatException e) { return def; }
    }

    private static boolean getBool(Properties props, String key, boolean def) {
        String val = props.getProperty(key);
        if (val == null || val.isBlank()) return def;
        return Boolean.parseBoolean(val.trim());
    }

    enum GriefMode {
        ENABLED,
        DISABLED,
        CLAIMS_PROTECTION
    }

    private static final String DEFAULT_CONFIG = """
            # HyRifts - Configuration
            # Solo Leveling-style random dungeon portals.
            # Use /rift reload to apply changes at runtime.

            # ===== Portal Spawning =====

            # Seconds between each dice roll to potentially spawn a portal.
            roll_interval_seconds=300

            # Chance (0.0 - 1.0) per roll that a portal actually spawns.
            roll_chance=0.35

            # Maximum number of portals that can be active at the same time.
            max_active_portals=3

            # How many portals spawn per successful roll (random between min and max).
            min_portals_per_roll=1
            max_portals_per_roll=2

            # How long (seconds) a portal stays open before despawning. 0 = infinite.
            portal_lifetime_seconds=600

            # ===== Placement =====

            # Minimum distance (blocks) from any online player to spawn a portal.
            min_distance_from_players=80

            # Maximum distance (blocks) from any online player to spawn a portal.
            max_distance_from_players=500

            # Maximum attempts to find a valid location before giving up on this roll.
            max_placement_attempts=20

            # ===== Grief Mode =====
            # Controls whether portals can overwrite blocks when spawning.
            #   enabled          - portals can spawn anywhere, replacing blocks
            #   disabled         - portals will NOT spawn on/near player-placed structures
            #   claimsProtection - portals can spawn anywhere EXCEPT inside claimed chunks
            #                      (requires SimpleClaims mod)
            grief=claimsProtection

            # Block keywords for grief=disabled mode. Portals won't spawn near blocks whose ID
            # contains any of these keywords (case-insensitive). Comma-separated.
            # Add custom block IDs your server uses (e.g. custom chests, storage, etc.)
            grief_protected_blocks=crafting,furnace,chest,barrel,bench,anvil,sign,door,fence,lantern,torch,bed,plank,brick,slab,stair,wool,glass,carpet,banner

            # ===== Difficulty (Endless Leveling) =====

            # Radius (blocks) around the portal where the EL mob level override applies.
            mob_level_radius=50.0

            # ===== Rift Compass =====

            # Item ID for the rift compass (glows stronger near portals).
            rift_compass_item_id=Rift_Compass

            # Maximum range (blocks) at which the compass detects a portal.
            rift_compass_range=750.0

            # ===== Announcements =====

            # Broadcast when a portal opens. Message: "A rift in reality has opened"
            announce_portals=true

            # Announce when a portal despawns / closes.
            announce_despawn=true

            # ===== Rank Configuration =====
            # Each rank has: min_level, max_level, weight (spawn chance), tiered (EL scaling)
            # Weight is relative — higher weight = more common. Defaults sum to 100.
            # Tiered means the dungeon difficulty escalates as you go deeper (future feature).

            rank.E.min_level=1
            rank.E.max_level=10
            rank.E.weight=30
            rank.E.tiered=false

            rank.D.min_level=10
            rank.D.max_level=25
            rank.D.weight=25
            rank.D.tiered=false

            rank.C.min_level=25
            rank.C.max_level=50
            rank.C.weight=20
            rank.C.tiered=false

            rank.B.min_level=50
            rank.B.max_level=80
            rank.B.weight=13
            rank.B.tiered=true

            rank.A.min_level=80
            rank.A.max_level=120
            rank.A.weight=8
            rank.A.tiered=true

            rank.S.min_level=120
            rank.S.max_level=200
            rank.S.weight=4
            rank.S.tiered=true
            """;
}
