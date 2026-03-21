package endgame.plugin.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nonnull;

public class ArmorConfig {

    @Nonnull
    public static final BuilderCodec<ArmorConfig> CODEC = BuilderCodec
            .builder(ArmorConfig.class, ArmorConfig::new)
            // Mana Regen
            .append(new KeyedCodec<Boolean>("ManaRegenEnabled", Codec.BOOLEAN),
                    (c, v) -> c.manaRegenEnabled = v != null ? v : true, c -> c.manaRegenEnabled).add()
            .append(new KeyedCodec<Float>("ManaRegenMithrilPerPiece", Codec.FLOAT),
                    (c, v) -> c.setManaRegenMithrilPerPiece(v != null ? v : 0.5f), c -> c.manaRegenMithrilPerPiece).add()
            .append(new KeyedCodec<Float>("ManaRegenOnyxiumPerPiece", Codec.FLOAT),
                    (c, v) -> c.setManaRegenOnyxiumPerPiece(v != null ? v : 0.75f), c -> c.manaRegenOnyxiumPerPiece).add()
            .append(new KeyedCodec<Float>("ManaRegenPrismaPerPiece", Codec.FLOAT),
                    (c, v) -> c.setManaRegenPrismaPerPiece(v != null ? v : 1.0f), c -> c.manaRegenPrismaPerPiece).add()
            // HP Regen
            .append(new KeyedCodec<Boolean>("HPRegenEnabled", Codec.BOOLEAN),
                    (c, v) -> c.hpRegenEnabled = v != null ? v : true, c -> c.hpRegenEnabled).add()
            .append(new KeyedCodec<Float>("HPRegenDelaySec", Codec.FLOAT),
                    (c, v) -> c.setHpRegenDelaySec(v != null ? v : 15.0f), c -> c.hpRegenDelaySec).add()
            .append(new KeyedCodec<Float>("HPRegenOnyxiumPerPiece", Codec.FLOAT),
                    (c, v) -> c.setHpRegenOnyxiumPerPiece(v != null ? v : 0.5f), c -> c.hpRegenOnyxiumPerPiece).add()
            .append(new KeyedCodec<Float>("HPRegenPrismaPerPiece", Codec.FLOAT),
                    (c, v) -> c.setHpRegenPrismaPerPiece(v != null ? v : 0.75f), c -> c.hpRegenPrismaPerPiece).add()
            .build();

    // Mana Regen
    private boolean manaRegenEnabled = true;
    private float manaRegenMithrilPerPiece = 0.5f;
    private float manaRegenOnyxiumPerPiece = 0.75f;
    private float manaRegenPrismaPerPiece = 1.0f;
    // HP Regen
    private boolean hpRegenEnabled = true;
    private float hpRegenDelaySec = 15.0f;
    private float hpRegenOnyxiumPerPiece = 0.5f;
    private float hpRegenPrismaPerPiece = 0.75f;

    // === MANA REGEN ===

    public boolean isManaRegenEnabled() { return manaRegenEnabled; }
    public void setManaRegenEnabled(boolean e) { this.manaRegenEnabled = e; }

    public float getManaRegenMithrilPerPiece() { return manaRegenMithrilPerPiece; }
    public void setManaRegenMithrilPerPiece(float v) { this.manaRegenMithrilPerPiece = Math.max(0f, Math.min(5.0f, v)); }

    public float getManaRegenOnyxiumPerPiece() { return manaRegenOnyxiumPerPiece; }
    public void setManaRegenOnyxiumPerPiece(float v) { this.manaRegenOnyxiumPerPiece = Math.max(0f, Math.min(5.0f, v)); }

    public float getManaRegenPrismaPerPiece() { return manaRegenPrismaPerPiece; }
    public void setManaRegenPrismaPerPiece(float v) { this.manaRegenPrismaPerPiece = Math.max(0f, Math.min(5.0f, v)); }

    // === HP REGEN ===

    public boolean isHPRegenEnabled() { return hpRegenEnabled; }
    public void setHPRegenEnabled(boolean e) { this.hpRegenEnabled = e; }

    public float getHpRegenDelaySec() { return hpRegenDelaySec; }
    public void setHpRegenDelaySec(float v) { this.hpRegenDelaySec = Math.max(1.0f, Math.min(60.0f, v)); }

    public float getHpRegenOnyxiumPerPiece() { return hpRegenOnyxiumPerPiece; }
    public void setHpRegenOnyxiumPerPiece(float v) { this.hpRegenOnyxiumPerPiece = Math.max(0f, Math.min(5.0f, v)); }

    public float getHpRegenPrismaPerPiece() { return hpRegenPrismaPerPiece; }
    public void setHpRegenPrismaPerPiece(float v) { this.hpRegenPrismaPerPiece = Math.max(0f, Math.min(5.0f, v)); }
}
