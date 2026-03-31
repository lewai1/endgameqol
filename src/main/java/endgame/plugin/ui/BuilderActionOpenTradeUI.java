package endgame.plugin.ui;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.asset.builder.InstructionType;
import com.hypixel.hytale.server.npc.asset.builder.holder.AssetHolder;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.instructions.Action;

import javax.annotation.Nonnull;
import java.util.EnumSet;

/**
 * NPC Action Builder that opens the EndgameQoL custom trade UI.
 * Registered via NPCPlugin.get().registerCoreComponentType("EndgameOpenTradeUI", ...)
 *
 * JSON usage: { "Type": "EndgameOpenTradeUI", "Shop": "Endgame_Korvyn", "MerchantName": "Korvyn" }
 */
public class BuilderActionOpenTradeUI extends BuilderActionBase {

    @Nonnull
    protected final AssetHolder shopId = new AssetHolder();
    protected String merchantName;

    @Nonnull
    public String getShortDescription() {
        return "Open the EndgameQoL custom trade UI";
    }

    @Nonnull
    public String getLongDescription() {
        return getShortDescription();
    }

    @Nonnull
    public Action build(@Nonnull BuilderSupport builderSupport) {
        return new ActionOpenTradeUI(this, builderSupport);
    }

    @Nonnull
    public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Stable;
    }

    @Nonnull
    public BuilderActionOpenTradeUI readConfig(@Nonnull JsonElement data) {
        this.requireAsset(data, "Shop", this.shopId, null, BuilderDescriptorState.Stable,
                "The barter shop asset to display", null);
        this.requireInstructionType(EnumSet.of(InstructionType.Interaction));

        // MerchantName is optional — read manually from JSON
        if (data.isJsonObject() && data.getAsJsonObject().has("MerchantName")) {
            this.merchantName = data.getAsJsonObject().get("MerchantName").getAsString();
        }
        return this;
    }

    public String getShopId(@Nonnull BuilderSupport support) {
        return this.shopId.get(support.getExecutionContext());
    }

    public String getMerchantName() {
        return merchantName;
    }
}
