package endgame.plugin.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nonnull;

public class CraftingConfig {

    @Nonnull
    public static final BuilderCodec<CraftingConfig> CODEC = BuilderCodec
            .builder(CraftingConfig.class, CraftingConfig::new)
            .append(new KeyedCodec<Boolean>("EnableGlider", Codec.BOOLEAN),
                    (c, v) -> c.enableGlider = v != null ? v : true, c -> c.enableGlider).add()
            .append(new KeyedCodec<Boolean>("EnableMithrilOre", Codec.BOOLEAN),
                    (c, v) -> c.enableMithrilOre = v != null ? v : false, c -> c.enableMithrilOre).add()
            .append(new KeyedCodec<Boolean>("EnablePortalKeyTaiga", Codec.BOOLEAN),
                    (c, v) -> c.enablePortalKeyTaiga = v != null ? v : false, c -> c.enablePortalKeyTaiga).add()
            .append(new KeyedCodec<Boolean>("EnablePortalKeyHederasLair", Codec.BOOLEAN),
                    (c, v) -> c.enablePortalKeyHederasLair = v != null ? v : false, c -> c.enablePortalKeyHederasLair).add()
            .append(new KeyedCodec<Boolean>("EnablePortalHedera", Codec.BOOLEAN),
                    (c, v) -> c.enablePortalHedera = v != null ? v : true, c -> c.enablePortalHedera).add()
            .append(new KeyedCodec<Boolean>("EnablePortalGolemVoid", Codec.BOOLEAN),
                    (c, v) -> c.enablePortalGolemVoid = v != null ? v : true, c -> c.enablePortalGolemVoid).add()
            .build();

    private boolean enableGlider = true;
    private boolean enableMithrilOre = false;
    private boolean enablePortalKeyTaiga = false;
    private boolean enablePortalKeyHederasLair = false;
    private boolean enablePortalHedera = true;
    private boolean enablePortalGolemVoid = true;

    public boolean isEnableGliderCrafting() { return enableGlider; }
    public void setEnableGliderCrafting(boolean e) { this.enableGlider = e; }

    public boolean isEnableMithrilOreCrafting() { return enableMithrilOre; }
    public void setEnableMithrilOreCrafting(boolean e) { this.enableMithrilOre = e; }

    public boolean isEnablePortalKeyTaiga() { return enablePortalKeyTaiga; }
    public void setEnablePortalKeyTaiga(boolean e) { this.enablePortalKeyTaiga = e; }

    public boolean isEnablePortalKeyHederasLair() { return enablePortalKeyHederasLair; }
    public void setEnablePortalKeyHederasLair(boolean e) { this.enablePortalKeyHederasLair = e; }

    public boolean isEnablePortalHedera() { return enablePortalHedera; }
    public void setEnablePortalHedera(boolean e) { this.enablePortalHedera = e; }

    public boolean isEnablePortalGolemVoid() { return enablePortalGolemVoid; }
    public void setEnablePortalGolemVoid(boolean e) { this.enablePortalGolemVoid = e; }
}
