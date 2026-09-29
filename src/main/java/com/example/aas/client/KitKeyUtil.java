package com.example.aas.client;

import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketOpenKitEditor;
import com.example.aas.network.PacketPasteKit;
import com.example.aas.network.PacketRequestKitData;
import com.example.aas.world.AASWorldData;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;

/**
 * "Kit key" = one text string that holds full kit data (items, NBT, resupply flags,
 * limits, display names, alt versions). Format: "AASKIT1:" + Base64url(gzip(NBT)).
 * Copy it from one world, paste it into another one.
 *
 * Types: KIT (one kit), TEAM (all kits of one team), ALL (both teams).
 * Entries inside are stored as "TEAM|KitName" -> kit tag.
 */
public class KitKeyUtil {
    public static final String PREFIX = "AASKIT1:";

    public static class Decoded {
        public String type;
        public boolean isAlt;
        public final LinkedHashMap<String, CompoundTag> kits = new LinkedHashMap<>();
    }

    // ------------------------------------------------------------------ encode / decode

    public static String encode(String type, boolean isAlt, Map<String, CompoundTag> kits) throws Exception {
        CompoundTag root = new CompoundTag();
        root.putInt("V", 1);
        root.putString("Type", type);
        root.putBoolean("Alt", isAlt);
        CompoundTag k = new CompoundTag();
        kits.forEach(k::put);
        root.put("Kits", k);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        NbtIo.writeCompressed(root, bos);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bos.toByteArray());
    }

    public static Decoded decode(String key) throws Exception {
        if (key == null) throw new IllegalArgumentException("empty");
        key = key.trim().replaceAll("\\s+", "");
        if (!key.startsWith(PREFIX)) throw new IllegalArgumentException("not a kit key");
        byte[] raw = Base64.getUrlDecoder().decode(key.substring(PREFIX.length()));
        CompoundTag root = NbtIo.readCompressed(new ByteArrayInputStream(raw));
        Decoded d = new Decoded();
        d.type = root.getString("Type");
        d.isAlt = root.getBoolean("Alt");
        CompoundTag k = root.getCompound("Kits");
        for (String name : k.getAllKeys()) d.kits.put(name, k.getCompound(name));
        return d;
    }

    // ------------------------------------------------------------------ copy (generate key)

    /** scope: "KIT" (one kit), "TEAM" (all kits of team), "ALL" (both teams; team is ignored). */
    public static void requestCopy(String scope, String team, String kitName, boolean isAlt) {
        String t = scope.equals("ALL") ? "BOTH" : team;
        String k = scope.equals("KIT") ? kitName : "ALL";
        PacketHandler.INSTANCE.sendToServer(new PacketRequestKitData(t, k, isAlt, scope));
    }

    /** Called on the client when the server answers a key request. */
    public static void onKeyData(String scope, boolean isAlt, Map<String, CompoundTag> kits) {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (kits.isEmpty()) { msg("Nothing to copy"); return; }
            String key = encode(scope, isAlt, kits);
            mc.keyboardHandler.setClipboard(key);
            msg("Kit key copied: " + kits.size() + " kit(s), " + key.length() + " chars");
        } catch (Exception e) {
            msg("Failed to generate key: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ paste (apply key)

    private static Decoded readClipboard() {
        try {
            return decode(Minecraft.getInstance().keyboardHandler.getClipboard());
        } catch (Exception e) {
            msg("Clipboard does not contain a valid kit key");
            return null;
        }
    }

    private static String teamOf(String entryKey) { int i = entryKey.indexOf('|'); return i < 0 ? "" : entryKey.substring(0, i); }
    private static String nameOf(String entryKey) { int i = entryKey.indexOf('|'); return i < 0 ? entryKey : entryKey.substring(i + 1); }

    private static boolean validName(String n) {
        for (String s : AASWorldData.KIT_NAMES) if (s.equals(n)) return true;
        return false;
    }

    /** Both-teams screen: a key of type ALL fills both teams, a TEAM key fills the team it came from. */
    public static void importAllTeams() {
        Decoded d = readClipboard();
        if (d == null) return;
        if (d.type.equals("KIT")) { msg("This key holds a single kit - open a team / kit to paste it"); return; }
        List<String[]> jobs = new ArrayList<>();
        List<CompoundTag> tags = new ArrayList<>();
        for (Map.Entry<String, CompoundTag> e : d.kits.entrySet()) {
            String team = teamOf(e.getKey()), name = nameOf(e.getKey());
            if ((team.equals("BLUE") || team.equals("RED")) && validName(name)) { jobs.add(new String[]{team, name}); tags.add(e.getValue()); }
        }
        send(jobs, tags);
    }

    /** Kit-list screen: paste all kits of the key into this team (a TEAM key from the other team is remapped). */
    public static void importForTeam(String team) {
        Decoded d = readClipboard();
        if (d == null) return;
        if (d.type.equals("KIT")) { msg("This key holds a single kit - open the kit and paste it there"); return; }
        List<String[]> jobs = new ArrayList<>();
        List<CompoundTag> tags = new ArrayList<>();
        for (Map.Entry<String, CompoundTag> e : d.kits.entrySet())
            if (teamOf(e.getKey()).equals(team) && validName(nameOf(e.getKey()))) { jobs.add(new String[]{team, nameOf(e.getKey())}); tags.add(e.getValue()); }
        if (jobs.isEmpty() && d.type.equals("TEAM")) { // key came from the other team
            for (Map.Entry<String, CompoundTag> e : d.kits.entrySet())
                if (validName(nameOf(e.getKey()))) { jobs.add(new String[]{team, nameOf(e.getKey())}); tags.add(e.getValue()); }
        }
        send(jobs, tags);
    }

    /** Editor screen: paste one kit (or the matching kit of a team key) into the kit that is open now. */
    public static void importForEditor(String team, String kitName, boolean isAlt) {
        Decoded d = readClipboard();
        if (d == null) return;
        CompoundTag tag = null;
        if (d.type.equals("KIT")) {
            tag = d.kits.values().stream().findFirst().orElse(null);
        } else {
            tag = d.kits.get(team + "|" + kitName);
            if (tag == null) for (Map.Entry<String, CompoundTag> e : d.kits.entrySet())
                if (nameOf(e.getKey()).equals(kitName)) { tag = e.getValue(); break; }
        }
        if (tag == null) { msg("No kit \"" + kitName + "\" in this key"); return; }
        PacketHandler.INSTANCE.sendToServer(new PacketPasteKit(team, kitName, isAlt, tag));
        PacketHandler.INSTANCE.sendToServer(new PacketOpenKitEditor(team, kitName, isAlt)); // reopen with new data
        msg("Kit key pasted");
    }

    private static void send(List<String[]> jobs, List<CompoundTag> tags) {
        if (jobs.isEmpty()) { msg("Key has no kits for this target"); return; }
        for (int i = 0; i < jobs.size(); i++) {
            boolean last = (i == jobs.size() - 1); // sync to clients only once, on the last kit
            PacketHandler.INSTANCE.sendToServer(new PacketPasteKit(jobs.get(i)[0], jobs.get(i)[1], false, tags.get(i), last));
        }
        msg("Kit key pasted: " + jobs.size() + " kit(s)");
    }

    private static void msg(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(Component.literal(s), true);
    }
}