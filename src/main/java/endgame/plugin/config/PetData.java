package endgame.plugin.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player pet unlock and active pet state.
 * Persisted inside PlayerEndgameComponent via BuilderCodec.
 */
public class PetData {

    @Nonnull
    public static final BuilderCodec<PetData> CODEC = BuilderCodec
            .builder(PetData.class, PetData::new)
            .append(new KeyedCodec<String[]>("UnlockedPets", Codec.STRING_ARRAY),
                    (d, v) -> { if (v != null) for (String s : v) d.unlockedPets.add(s); },
                    d -> d.unlockedPets.toArray(new String[0])).add()
            .append(new KeyedCodec<String>("ActivePetId", Codec.STRING),
                    (d, v) -> d.activePetId = v != null ? v : "",
                    d -> d.activePetId).add()
            .append(new KeyedCodec<Map<String, String>>("PetTiers",
                    new MapCodec<>(Codec.STRING, ConcurrentHashMap::new, false)),
                    (d, v) -> { if (v != null) d.petTiers.putAll(v); },
                    d -> d.petTiers).add()
            .build();

    private final Set<String> unlockedPets = ConcurrentHashMap.newKeySet();
    private volatile String activePetId = "";
    private final Map<String, String> petTiers = new ConcurrentHashMap<>();

    public PetData() {}

    public PetData(PetData other) {
        this.unlockedPets.addAll(other.unlockedPets);
        this.activePetId = other.activePetId;
        this.petTiers.putAll(other.petTiers);
    }

    public boolean isUnlocked(String petId) {
        return unlockedPets.contains(petId);
    }

    public boolean unlock(String petId) {
        return unlockedPets.add(petId);
    }

    public Set<String> getUnlockedPets() {
        return unlockedPets;
    }

    public int getUnlockedCount() {
        return unlockedPets.size();
    }

    @Nonnull
    public String getActivePetId() {
        return activePetId;
    }

    public void setActivePetId(@Nonnull String petId) {
        this.activePetId = petId != null ? petId : "";
    }

    /**
     * Get the tier of a pet. Defaults to D if not explicitly set.
     */
    @Nonnull
    public PetTier getPetTier(@Nonnull String petId) {
        return PetTier.fromLabel(petTiers.get(petId));
    }

    /**
     * Set the tier for a pet.
     */
    public void setPetTier(@Nonnull String petId, @Nonnull PetTier tier) {
        petTiers.put(petId, tier.getLabel());
    }
}
