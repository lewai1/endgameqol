package endgame.plugin.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Persistent 6-slot personal inventory for Void Pocket (Prisma Pickaxe ability).
 * Each slot stores an item ID and count. Serialized via BuilderCodec to EndgameConfig.
 */
public class VoidPocketData {

    public static final int MAX_SLOTS = 6;

    @Nonnull
    public static final BuilderCodec<VoidPocketData> CODEC = BuilderCodec
            .builder(VoidPocketData.class, VoidPocketData::new)
            .append(new KeyedCodec<String>("Slot0ItemId", Codec.STRING),
                    (d, v) -> d.itemIds[0] = v != null ? v : "", d -> d.itemIds[0]).add()
            .append(new KeyedCodec<Integer>("Slot0Count", Codec.INTEGER),
                    (d, v) -> d.counts[0] = v != null ? v : 0, d -> d.counts[0]).add()
            .append(new KeyedCodec<String>("Slot1ItemId", Codec.STRING),
                    (d, v) -> d.itemIds[1] = v != null ? v : "", d -> d.itemIds[1]).add()
            .append(new KeyedCodec<Integer>("Slot1Count", Codec.INTEGER),
                    (d, v) -> d.counts[1] = v != null ? v : 0, d -> d.counts[1]).add()
            .append(new KeyedCodec<String>("Slot2ItemId", Codec.STRING),
                    (d, v) -> d.itemIds[2] = v != null ? v : "", d -> d.itemIds[2]).add()
            .append(new KeyedCodec<Integer>("Slot2Count", Codec.INTEGER),
                    (d, v) -> d.counts[2] = v != null ? v : 0, d -> d.counts[2]).add()
            .append(new KeyedCodec<String>("Slot3ItemId", Codec.STRING),
                    (d, v) -> d.itemIds[3] = v != null ? v : "", d -> d.itemIds[3]).add()
            .append(new KeyedCodec<Integer>("Slot3Count", Codec.INTEGER),
                    (d, v) -> d.counts[3] = v != null ? v : 0, d -> d.counts[3]).add()
            .append(new KeyedCodec<String>("Slot4ItemId", Codec.STRING),
                    (d, v) -> d.itemIds[4] = v != null ? v : "", d -> d.itemIds[4]).add()
            .append(new KeyedCodec<Integer>("Slot4Count", Codec.INTEGER),
                    (d, v) -> d.counts[4] = v != null ? v : 0, d -> d.counts[4]).add()
            .append(new KeyedCodec<String>("Slot5ItemId", Codec.STRING),
                    (d, v) -> d.itemIds[5] = v != null ? v : "", d -> d.itemIds[5]).add()
            .append(new KeyedCodec<Integer>("Slot5Count", Codec.INTEGER),
                    (d, v) -> d.counts[5] = v != null ? v : 0, d -> d.counts[5]).add()
            .append(new KeyedCodec<Double>("Slot0Durability", Codec.DOUBLE),
                    (d, v) -> d.durabilities[0] = v != null ? v : -1, d -> d.durabilities[0]).add()
            .append(new KeyedCodec<Double>("Slot1Durability", Codec.DOUBLE),
                    (d, v) -> d.durabilities[1] = v != null ? v : -1, d -> d.durabilities[1]).add()
            .append(new KeyedCodec<Double>("Slot2Durability", Codec.DOUBLE),
                    (d, v) -> d.durabilities[2] = v != null ? v : -1, d -> d.durabilities[2]).add()
            .append(new KeyedCodec<Double>("Slot3Durability", Codec.DOUBLE),
                    (d, v) -> d.durabilities[3] = v != null ? v : -1, d -> d.durabilities[3]).add()
            .append(new KeyedCodec<Double>("Slot4Durability", Codec.DOUBLE),
                    (d, v) -> d.durabilities[4] = v != null ? v : -1, d -> d.durabilities[4]).add()
            .append(new KeyedCodec<Double>("Slot5Durability", Codec.DOUBLE),
                    (d, v) -> d.durabilities[5] = v != null ? v : -1, d -> d.durabilities[5]).add()
            .build();

    private final String[] itemIds = new String[MAX_SLOTS];
    private final int[] counts = new int[MAX_SLOTS];
    private final double[] durabilities = new double[MAX_SLOTS];

    public VoidPocketData() {
        for (int i = 0; i < MAX_SLOTS; i++) {
            itemIds[i] = "";
            counts[i] = 0;
            durabilities[i] = -1; // -1 = use max durability (backwards compat)
        }
    }

    public VoidPocketData(VoidPocketData other) {
        System.arraycopy(other.itemIds, 0, this.itemIds, 0, MAX_SLOTS);
        System.arraycopy(other.counts, 0, this.counts, 0, MAX_SLOTS);
        System.arraycopy(other.durabilities, 0, this.durabilities, 0, MAX_SLOTS);
    }

    @Nonnull
    public String getItemId(int slot) {
        if (slot < 0 || slot >= MAX_SLOTS) return "";
        return itemIds[slot] != null ? itemIds[slot] : "";
    }

    public int getCount(int slot) {
        if (slot < 0 || slot >= MAX_SLOTS) return 0;
        return counts[slot];
    }

    public boolean isSlotOccupied(int slot) {
        return !getItemId(slot).isEmpty() && getCount(slot) > 0;
    }

    public double getDurability(int slot) {
        if (slot < 0 || slot >= MAX_SLOTS) return -1;
        return durabilities[slot];
    }

    public void setItem(int slot, @Nullable String id, int count, double durability) {
        if (slot < 0 || slot >= MAX_SLOTS) return;
        itemIds[slot] = id != null ? id : "";
        counts[slot] = Math.max(0, count);
        durabilities[slot] = durability;
    }

    public void clearSlot(int slot) {
        setItem(slot, "", 0, -1);
    }

    /**
     * Returns the first empty slot index, or -1 if all slots are full.
     */
    public int getFirstEmptySlot() {
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (!isSlotOccupied(i)) return i;
        }
        return -1;
    }

    /**
     * Returns the number of occupied slots.
     */
    public int getOccupiedCount() {
        int count = 0;
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (isSlotOccupied(i)) count++;
        }
        return count;
    }
}
