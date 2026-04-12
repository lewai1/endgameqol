package endgame.plugin.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.config.AccessoryPouchData;
import endgame.plugin.config.AchievementData.PlayerAchievementState;
import endgame.plugin.config.BestiaryData.PlayerBestiaryState;
import endgame.plugin.config.BountyData.PlayerBountyState;
import endgame.plugin.config.PetData;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Persistent ECS component wrapping all per-player data.
 * Hytale auto-saves this to universe/players/{UUID}.bson — no manual saves needed.
 *
 * Replaces 6 monolithic JSON config files that hit the 128KB RawJsonReader buffer limit
 * on servers with 50-100+ players.
 */
public class PlayerEndgameComponent implements Component<EntityStore> {

    // Static ComponentType reference — set during registration in EndgameQoL.setup()
    private static volatile ComponentType<EntityStore, PlayerEndgameComponent> componentType;

    public static ComponentType<EntityStore, PlayerEndgameComponent> getComponentType() {
        return componentType;
    }

    public static void setComponentType(ComponentType<EntityStore, PlayerEndgameComponent> type) {
        componentType = type;
    }

    // Sub-states (reuse existing data classes)
    private PlayerAchievementState achievementState = new PlayerAchievementState();
    private PlayerBountyState bountyState = new PlayerBountyState();
    private PlayerBestiaryState bestiaryState = new PlayerBestiaryState();
    private AccessoryPouchData accessoryPouchData = new AccessoryPouchData();
    private PetData petData = new PetData();
    private String locale = "";
    private int comboPersonalBest = 0;
    private int dataVersion = 0; // 0 = new/empty, 1 = migrated or first-time populated

    // CODEC — 8 fields, nesting existing sub-CODECs
    @Nonnull
    public static final BuilderCodec<PlayerEndgameComponent> CODEC = BuilderCodec
            .builder(PlayerEndgameComponent.class, PlayerEndgameComponent::new)
            .append(new KeyedCodec<Integer>("DataVersion", Codec.INTEGER),
                    (c, v) -> c.dataVersion = v != null ? v : 0,
                    c -> c.dataVersion).add()
            .append(new KeyedCodec<PlayerAchievementState>("Achievements",
                            endgame.plugin.config.AchievementData.PLAYER_STATE_CODEC),
                    (c, v) -> { if (v != null) c.achievementState = v; },
                    c -> c.achievementState).add()
            .append(new KeyedCodec<PlayerBountyState>("Bounties",
                            endgame.plugin.config.BountyData.PLAYER_STATE_CODEC),
                    (c, v) -> { if (v != null) c.bountyState = v; },
                    c -> c.bountyState).add()
            .append(new KeyedCodec<PlayerBestiaryState>("Bestiary",
                            endgame.plugin.config.BestiaryData.PLAYER_STATE_CODEC),
                    (c, v) -> { if (v != null) c.bestiaryState = v; },
                    c -> c.bestiaryState).add()
            .append(new KeyedCodec<AccessoryPouchData>("AccessoryPouch", AccessoryPouchData.CODEC),
                    (c, v) -> { if (v != null) c.accessoryPouchData = v; },
                    c -> c.accessoryPouchData).add()
            .append(new KeyedCodec<String>("Locale", Codec.STRING),
                    (c, v) -> c.locale = v != null ? v : "",
                    c -> c.locale).add()
            .append(new KeyedCodec<Integer>("ComboPersonalBest", Codec.INTEGER),
                    (c, v) -> c.comboPersonalBest = v != null ? v : 0,
                    c -> c.comboPersonalBest).add()
            .append(new KeyedCodec<PetData>("PetData", PetData.CODEC),
                    (c, v) -> { if (v != null) c.petData = v; },
                    c -> c.petData).add()
            .build();

    public PlayerEndgameComponent() {}

    // Deep copy for ECS clone contract (archetype migration)
    public PlayerEndgameComponent(PlayerEndgameComponent other) {
        this.achievementState = new PlayerAchievementState(other.achievementState);
        this.bountyState = new PlayerBountyState(other.bountyState);
        this.bestiaryState = new PlayerBestiaryState(other.bestiaryState);
        this.accessoryPouchData = new AccessoryPouchData(other.accessoryPouchData);
        this.petData = new PetData(other.petData);
        this.locale = other.locale;
        this.comboPersonalBest = other.comboPersonalBest;
        this.dataVersion = other.dataVersion;
    }

    @Nullable
    @Override
    public Component<EntityStore> clone() {
        return new PlayerEndgameComponent(this);
    }

    // === Getters ===

    public int getDataVersion() { return dataVersion; }
    public void setDataVersion(int version) { this.dataVersion = version; }

    @Nonnull
    public PlayerAchievementState getAchievementState() { return achievementState; }
    public void setAchievementState(@Nonnull PlayerAchievementState state) { this.achievementState = state; }

    @Nonnull
    public PlayerBountyState getBountyState() { return bountyState; }
    public void setBountyState(@Nonnull PlayerBountyState state) { this.bountyState = state; }

    @Nonnull
    public PlayerBestiaryState getBestiaryState() { return bestiaryState; }
    public void setBestiaryState(@Nonnull PlayerBestiaryState state) { this.bestiaryState = state; }

    @Nonnull
    public AccessoryPouchData getAccessoryPouchData() { return accessoryPouchData; }
    public void setAccessoryPouchData(@Nonnull AccessoryPouchData data) { this.accessoryPouchData = data; }

    @Nonnull
    public String getLocale() { return locale; }
    public void setLocale(@Nonnull String locale) { this.locale = locale != null ? locale : ""; }

    public int getComboPersonalBest() { return comboPersonalBest; }
    public void setComboPersonalBest(int best) { this.comboPersonalBest = best; }

    @Nonnull
    public PetData getPetData() { return petData; }
    public void setPetData(@Nonnull PetData data) { this.petData = data; }
}
