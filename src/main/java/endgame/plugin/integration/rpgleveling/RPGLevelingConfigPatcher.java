package endgame.plugin.integration.rpgleveling;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hypixel.hytale.logger.HytaleLogger;
import org.zuxaw.plugin.api.RPGLevelingAPI;

/**
 * Registers RPG Leveling config default overrides via the Config Defaults Override API (0.3.3+).
 * Only overrides values that match RPG Leveling's bundled defaults — admin-customized values
 * are preserved. This replaces the old file-patching approach that forcibly overwrote configs.
 *
 * Must be called in setup() — RPG Leveling applies overrides in its start() phase.
 */
public class RPGLevelingConfigPatcher {

    private RPGLevelingConfigPatcher() {}

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.RPGLeveling");

    /** Minimum MaxLevel required for our highest instance (Void Realm LevelMax=155) */
    private static final int REQUIRED_MAX_LEVEL = 155;

    /**
     * Register config default overrides with RPG Leveling's API.
     * Safe to call even if RPG Leveling is not installed (catches ClassNotFoundException).
     */
    public static void register() {
        try {
            Class.forName("org.zuxaw.plugin.api.RPGLevelingAPI");
        } catch (ClassNotFoundException e) {
            LOGGER.atFine().log("RPG Leveling not present — skipping config defaults registration");
            return;
        }

        try {
            doRegister();
            LOGGER.atInfo().log("Registered RPG Leveling config defaults (MaxLevel, instances, zone overrides)");
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to register RPG Leveling config defaults: %s", e.getMessage());
        }
    }

    /**
     * Actual registration — isolated so ClassNotFoundException on import doesn't propagate.
     */
    private static void doRegister() {
        RPGLevelingAPI.registerConfigDefaults("EndgameQoL", defaults -> {
            defaults
                // === RPGLevelingConfig.json — raise MaxLevel ===
                .forConfig("RPGLevelingConfig.json")
                    .setDefault("MaxLevel", REQUIRED_MAX_LEVEL)
                    .done()

                // === InstanceLevelConfig.json — add endgame dungeon instances ===
                .forConfig("InstanceLevelConfig.json")
                    .appendToArray("Instances", "Id", buildFrozenDungeon())
                    .appendToArray("Instances", "Id", buildSwampDungeon())
                    .appendToArray("Instances", "Id", buildVoidRealm())
                    .done()

                // === ZoneLevelConfig.json — add overworld mob level overrides ===
                .forConfig("ZoneLevelConfig.json")
                    .appendToArray("EntityOverrides", "EntityId", entityOverride("Endgame_Dragon_Fire", 95))
                    .appendToArray("EntityOverrides", "EntityId", entityOverride("Alpha_Rex", 90))
                    .appendToArray("EntityOverrides", "EntityId", entityOverride("Zombie_Aberrant", 80))
                    .appendToArray("EntityOverrides", "EntityId", entityOverride("Endgame_Shadow_Knight", 100))
                    .appendToArray("EntityOverrides", "EntityId", entityOverride("Endgame_Werewolf", 90))
                    .appendToArray("EntityOverrides", "EntityId", entityOverride("Endgame_Goblin_Duke", 95))
                    .appendToArray("EntityOverrides", "EntityId", entityOverride("Endgame_Necromancer_Void", 90))
                    .appendToArray("EntityOverrides", "EntityId", entityOverride("Endgame_Ghoul", 85))
                    .appendToArray("EntityOverrides", "EntityId", entityOverride("Endgame_Saurian_Warrior", 85))
                    .appendToArray("EntityOverrides", "EntityId", entityOverride("Endgame_Saurian_Hunter", 88))
                    .appendToArray("EntityOverrides", "EntityId", entityOverride("Endgame_Saurian_Rogue", 85))
                    .appendToArray("EntityOverrides", "EntityId", entityOverride("Endgame_Onyxium_Knight", 105))
                    .appendToArray("EntityOverrides", "EntityId", entityOverride("Endgame_Swamp_Crocodile", 125))
                    .appendToArray("EntityOverrides", "EntityId", entityOverride("Crocodile", 0))
                    .appendToArray("EntityOverrides", "EntityId", entityOverride("Deco_Raven_Fly", 0))
                    .appendToArray("EntityOverrides", "EntityId", entityOverride("Deco_Raven_NoFly", 0))
                    .done();
        });
    }

    // === Instance builders ===

    private static JsonObject buildFrozenDungeon() {
        JsonObject inst = new JsonObject();
        inst.addProperty("Id", "Endgame_Frozen_Dungeon");
        inst.addProperty("LevelMin", 80);
        inst.addProperty("LevelMax", 110);
        inst.addProperty("ExactIdMatch", false);
        inst.add("EntityOverrides", buildOverrides(
                entityOverride("Endgame_Dragon_Frost", 105),
                entityOverride("Endgame_Yeti", 100),
                entityOverride("Endgame_Golem_Crystal_Frost", 95),
                entityOverride("Frost_Feran", 95),
                entityOverride("Skeleton_Frost_Knight", 95),
                entityOverride("Skeleton_Frost_Archmage", 95),
                entityOverride("Endgame_Toad_Frost", 90),
                entityOverride("Skeleton_Frost_Ranger", 90),
                entityOverride("Skeleton_Frost_Soldier", 90),
                entityOverride("Skeleton_Frost_Archer", 88),
                entityOverride("Skeleton_Frost_Mage", 88),
                entityOverride("Endgame_Rat_Frost", 85),
                entityOverride("Spirit_Frost", 85)
        ));
        return inst;
    }

    private static JsonObject buildSwampDungeon() {
        JsonObject inst = new JsonObject();
        inst.addProperty("Id", "Endgame_Swamp_Dungeon");
        inst.addProperty("LevelMin", 100);
        inst.addProperty("LevelMax", 135);
        inst.addProperty("ExactIdMatch", false);
        inst.add("EntityOverrides", buildOverrides(
                entityOverride("Endgame_Hedera", 130),
                entityOverride("Endgame_Swamp_Crocodile", 125),
                entityOverride("Endgame_Bramble_Elite", 115),
                entityOverride("Endgame_Grooble", 110),
                entityOverride("Endgame_Fen_Stalker", 108),
                entityOverride("Spirit_Root", 115),
                entityOverride("Crocodile", 0),
                entityOverride("Deco_Raven_Fly", 0),
                entityOverride("Deco_Raven_NoFly", 0)
        ));
        return inst;
    }

    private static JsonObject buildVoidRealm() {
        JsonObject inst = new JsonObject();
        inst.addProperty("Id", "Endgame_Void_Realm");
        inst.addProperty("LevelMin", 110);
        inst.addProperty("LevelMax", 155);
        inst.addProperty("ExactIdMatch", false);
        inst.add("EntityOverrides", buildOverrides(
                entityOverride("Endgame_Golem_Void", 150),
                entityOverride("Golem_Eye_Void", 130)
        ));
        return inst;
    }

    // === Helpers ===

    private static JsonObject entityOverride(String entityId, int level) {
        JsonObject obj = new JsonObject();
        obj.addProperty("EntityId", entityId);
        obj.addProperty("Level", level);
        return obj;
    }

    private static JsonArray buildOverrides(JsonObject... overrides) {
        JsonArray arr = new JsonArray();
        for (JsonObject o : overrides) arr.add(o);
        return arr;
    }
}
