package endgame.wavearena;

/**
 * Functional interface for external mods (e.g., EndlessLeveling) to define
 * mob levels dynamically per arena and wave. Replaces the static MobLevel
 * JSON field when set.
 */
@FunctionalInterface
public interface MobLevelProvider {
    int getMobLevel(String arenaId, int waveIndex, String npcType);
}
