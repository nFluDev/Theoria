package dev.theoria.gear;

import java.util.List;

/** The few gear facts other packages (and dev scenarios) may read; everything else stays package-private. */
public final class GearApi {
    private GearApi() {}

    /** Ids of the vanilla netherite gear and upgrade template that D6 removed from every obtainable source. */
    public static List<String> removedItemIds() {
        return GearData.table().removed();
    }
}
