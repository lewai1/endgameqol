package endgame.plugin.integration;

import com.hypixel.hytale.logger.HytaleLogger;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Soft dependency bridge for SimpleClaims plugin.
 * Uses reflection to check claim permissions without compile-time dependency.
 * If SimpleClaims is not installed, all checks return true (allow).
 */
public class ClaimProtectionBridge {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.ClaimBridge");
    private static final ClaimProtectionBridge INSTANCE = new ClaimProtectionBridge();

    private volatile boolean available = false;
    private Object claimManagerInstance;
    private Method isAllowedToInteractMethod;
    private Object breakBlockPermission; // PartyOverrides.PARTY_PROTECTION_BREAK_BLOCKS
    private Predicate<?> breakBlockPredicate; // PartyInfo::isBlockBreakEnabled

    private ClaimProtectionBridge() {}

    public static ClaimProtectionBridge get() { return INSTANCE; }

    /**
     * Try to detect SimpleClaims at startup. Call from plugin setup().
     */
    public void init() {
        try {
            // Check if SimpleClaims is present
            Class<?> claimManagerClass = Class.forName("com.buuz135.simpleclaims.claim.ClaimManager");
            Method getInstanceMethod = claimManagerClass.getMethod("getInstance");
            claimManagerInstance = getInstanceMethod.invoke(null);

            // Cache the isAllowedToInteract method
            // Signature: isAllowedToInteract(UUID, String, int, int, Predicate<PartyInfo>, String)
            Class<?> partyInfoClass = Class.forName("com.buuz135.simpleclaims.claim.party.PartyInfo");
            isAllowedToInteractMethod = claimManagerClass.getMethod(
                    "isAllowedToInteract", UUID.class, String.class, int.class, int.class,
                    Predicate.class, String.class);

            // Get the permission string constant
            Class<?> overridesClass = Class.forName("com.buuz135.simpleclaims.claim.party.PartyOverrides");
            breakBlockPermission = overridesClass.getField("PARTY_PROTECTION_BREAK_BLOCKS").get(null);

            // Build predicate for PartyInfo::isBlockBreakEnabled via reflection
            Method isBlockBreakEnabled = partyInfoClass.getMethod("isBlockBreakEnabled");
            breakBlockPredicate = obj -> {
                try {
                    return (Boolean) isBlockBreakEnabled.invoke(obj);
                } catch (Exception e) {
                    return true; // fail-open
                }
            };

            available = true;
            LOGGER.atInfo().log("[ClaimBridge] SimpleClaims detected — 3x3 area break will respect claim boundaries");
        } catch (ClassNotFoundException e) {
            // SimpleClaims not installed — normal, no log spam
            available = false;
        } catch (Exception e) {
            LOGGER.atWarning().log("[ClaimBridge] SimpleClaims detected but API init failed: %s", e.getMessage());
            available = false;
        }
    }

    /**
     * Check if a player is allowed to break a block at the given position.
     * Returns true if SimpleClaims is not installed or if the player has permission.
     *
     * @param playerUUID the player's UUID
     * @param worldName  the world/dimension name
     * @param blockX     block X coordinate (NOT chunk coordinate — conversion is done internally by SimpleClaims)
     * @param blockZ     block Z coordinate
     * @return true if break is allowed
     */
    public boolean isBreakAllowed(UUID playerUUID, String worldName, int blockX, int blockZ) {
        if (!available) return true; // no claim plugin = allow

        try {
            return (Boolean) isAllowedToInteractMethod.invoke(
                    claimManagerInstance, playerUUID, worldName, blockX, blockZ,
                    breakBlockPredicate, breakBlockPermission);
        } catch (Exception e) {
            LOGGER.atFine().log("[ClaimBridge] Permission check failed, allowing: %s", e.getMessage());
            return true; // fail-open
        }
    }

    public boolean isAvailable() { return available; }
}
