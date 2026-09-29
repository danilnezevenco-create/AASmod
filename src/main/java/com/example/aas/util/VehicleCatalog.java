package com.example.aas.util;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Vehicle catalog: maps a vehicle to a display category and an icon key.
 * Shared by server and client, so it must NOT import any client-only classes.
 *
 * How the category is resolved (first match wins):
 *   1) OVERRIDES_BY_VEHICLE_ID - exact vehicleIdString (e.g. "othermod:some_tank") -> category.
 *      This table is intentionally EMPTY: the entity ids of the external vehicle mod are not
 *      present in the mod sources, so they cannot be listed here. Fill it in if you want to
 *      force a category for a specific entity id.
 *   2) BY_MARKER_TYPE - the "type" string of the marker item placed in slot 0 of the spawner
 *      (VehicleMarkerItem#getType / SupplyTruckMarkerItem#getVehicleType). These strings are
 *      fully known from ModItems, so every vehicle already gets a sensible category.
 *   3) Category.OTHER.
 */
public final class VehicleCatalog {

    private VehicleCatalog() {}

    /**
     * Declaration order == list order in the panel (logistics/transport, APC/IFV, tanks, AA, helicopters).
     * langKey: EXISTING keys are reused where the mod already has one (aas.gui.map_marker.*),
     *          otherwise a new aas.gui.vehicles.category.* key is used.
     * iconKey: texture is textures/gui/vehicles/<iconKey>.png
     */
    public enum Category {
        LOGISTICS   ("logistics",   "aas.gui.vehicles.category.logistics"),
        TRANSPORT   ("transport",   "aas.gui.vehicles.category.transport"),
        APC         ("apc",         "aas.gui.map_marker.heavy_vehicle"),   // existing key (text is "APC" in all 3 langs)
        IFV         ("ifv",         "aas.gui.map_marker.light_vehicle"),   // existing key ("Combat Vehicle")
        TANK        ("tank",        "aas.gui.map_marker.tank"),            // existing key
        SPG         ("spg",         "aas.gui.map_marker.spg"),             // existing key
        AIR_DEFENSE ("air_defense", "aas.gui.map_marker.anti_air"),        // existing key
        HELICOPTER  ("helicopter",  "aas.gui.map_marker.aircraft"),        // existing key
        AIRCRAFT    ("aircraft",    "aas.gui.map_marker.cas_fighter"),     // existing key
        OTHER       ("other",       "aas.gui.vehicles.category.other");

        public final String iconKey;
        public final String langKey;

        Category(String iconKey, String langKey) {
            this.iconKey = iconKey;
            this.langKey = langKey;
        }

        /** Safe lookup by ordinal (used by the network packet). */
        public static Category byId(int id) {
            Category[] all = values();
            return (id >= 0 && id < all.length) ? all[id] : OTHER;
        }
    }

    /** vehicleIdString (entity type id) -> category. Optional manual overrides, empty by default. */
    private static final Map<String, Category> OVERRIDES_BY_VEHICLE_ID = new HashMap<>();

    /** Marker type (lower case) -> category. Values taken from ModItems. */
    private static final Map<String, Category> BY_MARKER_TYPE = new HashMap<>();

    static {
        // Example of a manual override:
        // OVERRIDES_BY_VEHICLE_ID.put("somemod:t90", Category.TANK);

        // Logistics
        BY_MARKER_TYPE.put("supply truck",      Category.LOGISTICS);
        BY_MARKER_TYPE.put("light supply",      Category.LOGISTICS);
        BY_MARKER_TYPE.put("heavy supply",      Category.LOGISTICS);
        BY_MARKER_TYPE.put("supply helicopter", Category.LOGISTICS);
        // Transport
        BY_MARKER_TYPE.put("motorcycle",        Category.TRANSPORT);
        BY_MARKER_TYPE.put("infantry vehicle",  Category.TRANSPORT);
        BY_MARKER_TYPE.put("boat",              Category.TRANSPORT);
        // Armour
        BY_MARKER_TYPE.put("apc",               Category.APC);
        BY_MARKER_TYPE.put("combat vehicle",    Category.IFV);
        BY_MARKER_TYPE.put("atgm carrier",      Category.IFV);
        BY_MARKER_TYPE.put("tank",              Category.TANK);
        BY_MARKER_TYPE.put("spg",               Category.SPG);
        // Air defence
        BY_MARKER_TYPE.put("static zu",         Category.AIR_DEFENSE);
        BY_MARKER_TYPE.put("mobile zu",         Category.AIR_DEFENSE);
        // Air
        BY_MARKER_TYPE.put("helicopter",        Category.HELICOPTER);
        BY_MARKER_TYPE.put("cas helicopter",    Category.HELICOPTER);
        BY_MARKER_TYPE.put("cas fighter",       Category.AIRCRAFT);
    }

    /** Resolves the category of a vehicle. Both arguments may be null/empty. */
    public static Category resolve(String vehicleIdString, String markerType) {
        if (vehicleIdString != null) {
            Category c = OVERRIDES_BY_VEHICLE_ID.get(vehicleIdString);
            if (c != null) return c;
        }
        if (markerType != null) {
            Category c = BY_MARKER_TYPE.get(markerType.trim().toLowerCase(Locale.ROOT));
            if (c != null) return c;
        }
        return Category.OTHER;
    }
}