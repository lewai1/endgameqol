package endgame.plugin.integration.rpgleveling;

import com.hypixel.hytale.logger.HytaleLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Auto-patches RPG Leveling's config files to support endgame instances.
 * Operates purely on JSON files — no org.zuxaw imports required.
 *
 * Patches three files:
 * 1. RPGLevelingConfig.json — raises MaxLevel if below our required minimum (155)
 * 2. InstanceLevelConfig.json — adds missing endgame instance definitions
 * 3. ZoneLevelConfig.json — adds root-level EntityOverrides for endgame overworld mobs
 */
public class RPGLevelingConfigPatcher {

    private RPGLevelingConfigPatcher() {}

    /** Minimum MaxLevel required for our highest instance (Golem Void LevelMax=155) */
    private static final int REQUIRED_MAX_LEVEL = 155;

    private static final String[][] INSTANCES = {
        // { Id, LevelMin, LevelMax, EntityOverrides JSON fragment }
        {
            "Endgame_Frozen_Dungeon", "80", "110",
            """
                        {
                            "EntityId": "Endgame_Dragon_Frost",
                            "Level": 105
                        },
                        {
                            "EntityId": "Endgame_Yeti",
                            "Level": 100
                        },
                        {
                            "EntityId": "Endgame_Golem_Crystal_Frost",
                            "Level": 95
                        },
                        {
                            "EntityId": "Frost_Feran",
                            "Level": 95
                        },
                        {
                            "EntityId": "Skeleton_Frost_Knight",
                            "Level": 95
                        },
                        {
                            "EntityId": "Skeleton_Frost_Archmage",
                            "Level": 95
                        },
                        {
                            "EntityId": "Endgame_Toad_Frost",
                            "Level": 90
                        },
                        {
                            "EntityId": "Skeleton_Frost_Ranger",
                            "Level": 90
                        },
                        {
                            "EntityId": "Skeleton_Frost_Soldier",
                            "Level": 90
                        },
                        {
                            "EntityId": "Skeleton_Frost_Archer",
                            "Level": 88
                        },
                        {
                            "EntityId": "Skeleton_Frost_Mage",
                            "Level": 88
                        },
                        {
                            "EntityId": "Endgame_Rat_Frost",
                            "Level": 85
                        },
                        {
                            "EntityId": "Spirit_Frost",
                            "Level": 85
                        }"""
        },
        {
            "Endgame_Swamp_Dungeon", "100", "135",
            """
                        {
                            "EntityId": "Endgame_Hedera",
                            "Level": 130
                        },
                        {
                            "EntityId": "Endgame_Swamp_Crocodile",
                            "Level": 125
                        },
                        {
                            "EntityId": "Endgame_Bramble_Elite",
                            "Level": 115
                        },
                        {
                            "EntityId": "Endgame_Grooble",
                            "Level": 110
                        },
                        {
                            "EntityId": "Endgame_Fen_Stalker",
                            "Level": 108
                        },
                        {
                            "EntityId": "Spirit_Root",
                            "Level": 115
                        },
                        {
                            "EntityId": "Crocodile",
                            "Level": 0
                        },
                        {
                            "EntityId": "Deco_Raven_Fly",
                            "Level": 0
                        },
                        {
                            "EntityId": "Deco_Raven_NoFly",
                            "Level": 0
                        }"""
        },
        {
            "Endgame_Golem_Void", "110", "155",
            """
                        {
                            "EntityId": "Endgame_Golem_Void",
                            "Level": 150
                        },
                        {
                            "EntityId": "Golem_Eye_Void",
                            "Level": 130
                        }"""
        }
    };

    /** Overworld mob overrides added to root-level EntityOverrides in ZoneLevelConfig.json */
    private static final String[][] ZONE_OVERRIDES = {
        { "Endgame_Dragon_Fire", "95" },
        { "Alpha_Rex", "90" },
        { "Zombie_Aberrant", "80" },
        { "Endgame_Shadow_Knight", "100" },
        { "Endgame_Werewolf", "90" },
        { "Endgame_Goblin_Duke", "95" },
        { "Endgame_Necromancer_Void", "90" },
        { "Endgame_Ghoul", "85" },
        { "Endgame_Saurian_Warrior", "85" },
        { "Endgame_Saurian_Hunter", "88" },
        { "Endgame_Saurian_Rogue", "85" },
        { "Endgame_Onyxium_Knight", "105" },
        { "Endgame_Swamp_Crocodile", "125" },
        // Passive/decorative — no XP
        { "Crocodile", "0" },
        { "Deco_Raven_Fly", "0" },
        { "Deco_Raven_NoFly", "0" }
    };

    /**
     * Patch InstanceLevelConfig.json with endgame instance entries.
     *
     * @param pluginDataDir the plugin's data directory (e.g., Saves/save/mods/Config_Endgame&QoL/)
     * @param logger        the plugin logger
     * @return number of instances added (0 = nothing changed)
     */
    public static int patch(Path pluginDataDir, HytaleLogger logger) {
        Path modsDir = pluginDataDir.getParent();
        if (modsDir == null) {
            logger.atFine().log("[RPGLeveling] Cannot resolve mods directory from %s", pluginDataDir);
            return 0;
        }

        Path rpgDir = modsDir.resolve("Zuxaw_RPGLeveling");
        if (!Files.isDirectory(rpgDir)) {
            logger.atFine().log("[RPGLeveling] RPG Leveling config directory not found: %s", rpgDir);
            return 0;
        }

        // Step 1: Ensure MaxLevel in RPGLevelingConfig.json is high enough for our instances
        patchMaxLevel(rpgDir, logger);

        // Step 2: Patch ZoneLevelConfig.json with overworld mob overrides
        patchZoneLevels(rpgDir, logger);

        // Step 3: Patch InstanceLevelConfig.json with endgame instances
        Path instanceConfig = rpgDir.resolve("InstanceLevelConfig.json");
        if (!Files.isRegularFile(instanceConfig)) {
            logger.atFine().log("[RPGLeveling] InstanceLevelConfig.json not found (RPG Leveling may not have generated it yet)");
            return 0;
        }

        String content;
        try {
            content = Files.readString(instanceConfig);
        } catch (IOException e) {
            logger.atWarning().log("[RPGLeveling] Failed to read InstanceLevelConfig.json: %s", e.getMessage());
            return 0;
        }

        if (content.isBlank()) {
            logger.atWarning().log("[RPGLeveling] InstanceLevelConfig.json is empty — skipping");
            return 0;
        }

        int added = 0;
        String modified = content;

        // Hytale names instance worlds "instance-<Name>-<UUID>" so exact matching
        // always fails — contains matching (ExactIdMatch: false) is required.
        modified = fixExactIdMatch(modified, logger);

        for (String[] instance : INSTANCES) {
            String id = instance[0];
            String levelMin = instance[1];
            String levelMax = instance[2];
            String entityOverrides = instance[3];

            // Check if this instance already exists (simple text search for the Id)
            if (modified.contains("\"" + id + "\"")) {
                logger.atFine().log("[RPGLeveling] Instance '%s' already configured — skipping", id);
                continue;
            }

            // Build the JSON block for this instance
            // ExactIdMatch MUST be false: Hytale names instance worlds as
            // "instance-<TemplateName>-<UUID>" so exact matching always fails.
            // With false, RPG Leveling uses contains matching which works.
            String block = String.format("""
                    {
                        "Id": "%s",
                        "LevelMin": %s,
                        "LevelMax": %s,
                        "ExactIdMatch": false,
                        "EntityOverrides": [
                %s
                        ]
                    }""", id, levelMin, levelMax, entityOverrides);

            // Find the last ] in the Instances array and insert before it
            int insertPos = findInstancesArrayEnd(modified);
            if (insertPos < 0) {
                logger.atWarning().log("[RPGLeveling] Could not find Instances array closing bracket — skipping patch");
                return added;
            }

            // Determine if we need a comma before our new entry
            // Look backwards from insertPos for content (skip whitespace)
            boolean needsComma = needsLeadingComma(modified, insertPos);

            String insertion = (needsComma ? "," : "") + "\n        " + block;
            modified = modified.substring(0, insertPos) + insertion + "\n    " + modified.substring(insertPos);
            added++;
            logger.atInfo().log("[RPGLeveling] Added instance '%s' (levels %s-%s) to InstanceLevelConfig", id, levelMin, levelMax);
        }

        boolean fileChanged = added > 0 || !modified.equals(content);
        if (fileChanged) {
            try {
                Files.writeString(instanceConfig, modified);
                logger.atInfo().log("[RPGLeveling] Patched InstanceLevelConfig.json — added %d endgame instance(s). Restart RPG Leveling to apply.", added);
            } catch (IOException e) {
                logger.atWarning().log("[RPGLeveling] Failed to write InstanceLevelConfig.json: %s", e.getMessage());
                return 0;
            }
        }

        return added;
    }

    /**
     * Fix ExactIdMatch for our endgame instance entries.
     * RPG Leveling defaults ExactIdMatch to true, but Hytale instance worlds are named
     * "instance-TemplateName-UUID", so exact matching never works. We need contains matching.
     * Only modifies entries that belong to our endgame instances.
     */
    private static String fixExactIdMatch(String content, HytaleLogger logger) {
        String result = content;
        for (String[] instance : INSTANCES) {
            String id = instance[0];
            // Only patch entries that contain our instance Id AND have ExactIdMatch: true
            // Find the block for this instance by locating its Id
            int idPos = result.indexOf("\"" + id + "\"");
            if (idPos < 0) continue;

            // Find the enclosing object boundaries (search backwards for { and forwards for matching })
            int blockStart = result.lastIndexOf('{', idPos);
            if (blockStart < 0) continue;

            // Find the matching closing brace
            int blockEnd = findMatchingBrace(result, blockStart);
            if (blockEnd < 0) continue;

            String block = result.substring(blockStart, blockEnd + 1);

            // Check if this block has "ExactIdMatch": true
            if (block.contains("\"ExactIdMatch\": true") || block.contains("\"ExactIdMatch\":true")) {
                String fixed = block.replace("\"ExactIdMatch\": true", "\"ExactIdMatch\": false")
                                    .replace("\"ExactIdMatch\":true", "\"ExactIdMatch\": false");
                result = result.substring(0, blockStart) + fixed + result.substring(blockEnd + 1);
                logger.atInfo().log("[RPGLeveling] Fixed ExactIdMatch for '%s' (true → false)", id);
            }
        }
        return result;
    }

    /**
     * Find the matching closing brace for an opening brace.
     */
    private static int findMatchingBrace(String content, int openBrace) {
        int depth = 1;
        for (int i = openBrace + 1; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            } else if (c == '"') {
                i = skipString(content, i);
            }
        }
        return -1;
    }

    /**
     * Patch ZoneLevelConfig.json to add EntityOverrides for endgame overworld mobs.
     * EntityOverrides is at the ROOT level of the JSON (sibling of "Zones"), not per-zone.
     * Only adds overrides that don't already exist.
     */
    private static void patchZoneLevels(Path rpgDir, HytaleLogger logger) {
        Path zoneConfig = rpgDir.resolve("ZoneLevelConfig.json");
        if (!Files.isRegularFile(zoneConfig)) {
            logger.atFine().log("[RPGLeveling] ZoneLevelConfig.json not found — skipping zone patch");
            return;
        }

        String content;
        try {
            content = Files.readString(zoneConfig);
        } catch (IOException e) {
            logger.atWarning().log("[RPGLeveling] Failed to read ZoneLevelConfig.json: %s", e.getMessage());
            return;
        }

        // EntityOverrides is at the root level of the JSON, sibling of "Zones"
        int overridesKeyIdx = content.indexOf("\"EntityOverrides\"");
        if (overridesKeyIdx < 0) {
            logger.atFine().log("[RPGLeveling] No EntityOverrides key in ZoneLevelConfig.json — skipping");
            return;
        }

        int openBracket = content.indexOf('[', overridesKeyIdx);
        if (openBracket < 0) return;

        int closeBracket = findMatchingBracket(content, openBracket);
        if (closeBracket < 0) return;

        int added = 0;
        String modified = content;
        int offset = 0;

        for (String[] override : ZONE_OVERRIDES) {
            String entityId = override[0];
            String level = override[1];

            if (modified.contains("\"" + entityId + "\"")) {
                logger.atFine().log("[RPGLeveling] Override for '%s' already exists — skipping", entityId);
                continue;
            }

            String entry = String.format("""
                {
                  "EntityId": "%s",
                  "Level": %s
                }""", entityId, level);

            int insertPos = closeBracket + offset;
            boolean needsComma = needsLeadingComma(modified, insertPos);
            String insertion = (needsComma ? "," : "") + "\n    " + entry;
            modified = modified.substring(0, insertPos) + insertion + "\n  " + modified.substring(insertPos);
            offset += insertion.length() + "\n  ".length();
            added++;
            logger.atInfo().log("[RPGLeveling] Added EntityOverride for '%s' (Level %s)", entityId, level);
        }

        if (added > 0) {
            try {
                Files.writeString(zoneConfig, modified);
                logger.atInfo().log("[RPGLeveling] Patched ZoneLevelConfig.json — added %d override(s). Restart RPG Leveling to apply.", added);
            } catch (IOException e) {
                logger.atWarning().log("[RPGLeveling] Failed to write ZoneLevelConfig.json: %s", e.getMessage());
            }
        }
    }

    /**
     * Find the matching closing bracket for an opening bracket.
     */
    private static int findMatchingBracket(String content, int openBracket) {
        int depth = 1;
        for (int i = openBracket + 1; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) return i;
            } else if (c == '"') {
                i = skipString(content, i);
            }
        }
        return -1;
    }

    /**
     * Patch MaxLevel in RPGLevelingConfig.json if it's below our required minimum.
     * Only raises it, never lowers it.
     */
    private static void patchMaxLevel(Path rpgDir, HytaleLogger logger) {
        Path mainConfig = rpgDir.resolve("RPGLevelingConfig.json");
        if (!Files.isRegularFile(mainConfig)) {
            logger.atFine().log("[RPGLeveling] RPGLevelingConfig.json not found — skipping MaxLevel patch");
            return;
        }

        String content;
        try {
            content = Files.readString(mainConfig);
        } catch (IOException e) {
            logger.atWarning().log("[RPGLeveling] Failed to read RPGLevelingConfig.json: %s", e.getMessage());
            return;
        }

        // Find "MaxLevel" : <number> pattern
        int keyIdx = content.indexOf("\"MaxLevel\"");
        if (keyIdx < 0) {
            logger.atFine().log("[RPGLeveling] No MaxLevel field found in RPGLevelingConfig.json — skipping");
            return;
        }

        // Find the colon after the key
        int colonIdx = content.indexOf(':', keyIdx + 10);
        if (colonIdx < 0) return;

        // Find the start and end of the number value
        int numStart = -1;
        int numEnd = -1;
        for (int i = colonIdx + 1; i < content.length(); i++) {
            char c = content.charAt(i);
            if (Character.isDigit(c) && numStart < 0) {
                numStart = i;
            } else if (numStart >= 0 && !Character.isDigit(c)) {
                numEnd = i;
                break;
            }
        }
        if (numStart < 0) return;
        if (numEnd < 0) numEnd = content.length();

        int currentMax;
        try {
            currentMax = Integer.parseInt(content.substring(numStart, numEnd));
        } catch (NumberFormatException e) {
            logger.atWarning().log("[RPGLeveling] Could not parse MaxLevel value — skipping");
            return;
        }

        if (currentMax >= REQUIRED_MAX_LEVEL) {
            logger.atFine().log("[RPGLeveling] MaxLevel is %d (>= %d) — no change needed", currentMax, REQUIRED_MAX_LEVEL);
            return;
        }

        // Replace the number in-place
        String patched = content.substring(0, numStart) + REQUIRED_MAX_LEVEL + content.substring(numEnd);
        try {
            Files.writeString(mainConfig, patched);
            logger.atInfo().log("[RPGLeveling] Raised MaxLevel from %d to %d in RPGLevelingConfig.json", currentMax, REQUIRED_MAX_LEVEL);
        } catch (IOException e) {
            logger.atWarning().log("[RPGLeveling] Failed to write RPGLevelingConfig.json: %s", e.getMessage());
        }
    }

    /**
     * Find the position of the closing ']' of the "Instances" array.
     * Returns -1 if not found.
     */
    private static int findInstancesArrayEnd(String content) {
        // Find "Instances" key
        int keyIdx = content.indexOf("\"Instances\"");
        if (keyIdx < 0) return -1;

        // Find the opening '[' after the key
        int openBracket = content.indexOf('[', keyIdx);
        if (openBracket < 0) return -1;

        // Find the matching ']' — track nesting depth
        int depth = 1;
        for (int i = openBracket + 1; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) return i;
            }
            // Skip string contents to avoid false matches on brackets inside strings
            else if (c == '"') {
                i = skipString(content, i);
            }
        }
        return -1;
    }

    /**
     * Skip past a JSON string starting at the opening quote.
     * Returns the index of the closing quote.
     */
    private static int skipString(String content, int openQuote) {
        for (int i = openQuote + 1; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '\\') {
                i++; // skip escaped char
            } else if (c == '"') {
                return i;
            }
        }
        return content.length() - 1;
    }

    /**
     * Check if we need a comma before inserting a new entry at the given position.
     * Looks backwards from pos, skipping whitespace, to see if there's a '}' or another value.
     */
    private static boolean needsLeadingComma(String content, int pos) {
        for (int i = pos - 1; i >= 0; i--) {
            char c = content.charAt(i);
            if (Character.isWhitespace(c)) continue;
            // If we find '}' it means there's an existing entry — need comma
            // If we find '[' it means the array is empty — no comma needed
            return c == '}';
        }
        return false;
    }
}
