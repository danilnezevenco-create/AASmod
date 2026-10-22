package com.example.aas.uav;

/** Why a player stopped looking through the UAV. The id travels in PacketUavViewState. */
public enum UavExitReason {
    USER("user"),
    DESTROYED("destroyed"),
    EXPIRED("expired"),
    DOWNED("downed"),
    DEAD("dead"),
    NOT_CMD("not_cmd"),
    MATCH_END("match_end"),
    LOST("lost");

    public final String key;

    UavExitReason(String key) { this.key = key; }

    public String langKey() { return "aas.uav.exit." + key; }

    /** USER is a voluntary exit: no notification. */
    public boolean notifies() { return this != USER; }

    public static UavExitReason byId(int id) {
        UavExitReason[] v = values();
        return id >= 0 && id < v.length ? v[id] : LOST;
    }
}
