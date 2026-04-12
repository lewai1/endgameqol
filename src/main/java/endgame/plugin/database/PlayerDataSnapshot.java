package endgame.plugin.database;

import javax.annotation.Nullable;

/**
 * DTO for player data that syncs to the database.
 * Contains serialized JSON blobs for all per-player persistent data.
 */
public record PlayerDataSnapshot(
        String uuid,
        String username,
        @Nullable String bountyStateJson,
        @Nullable String achievementJson,
        @Nullable String bestiaryJson,
        @Nullable String accessoryPouchJson,
        int comboPersonalBest
) {
    public PlayerDataSnapshot {
        if (uuid == null || uuid.isBlank()) throw new IllegalArgumentException("uuid required");
        if (username == null || username.isBlank()) throw new IllegalArgumentException("username required");
    }
}
