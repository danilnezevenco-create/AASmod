package com.example.aas.events;

/**
 * Тип начисления. Значения берутся из ScoreValues.
 * Для типов с динамическими очками (техника, перевозка) tp/sp = 0,
 * реальные значения передаются в StatsHandler.addScoreCustom(...).
 * langKey - ключ локализации причины (показывается в сообщении над хотбаром).
 */
public enum ScoreType {
    ENEMY_KILLED      (ScoreValues.KILL_ENEMY_TP, 0, "aas.score.enemy_killed"),

    REVIVE            (ScoreValues.REVIVE_TP, 0, "aas.score.revive"),
    REVIVE_SQUAD      (ScoreValues.REVIVE_SQUAD_TP, ScoreValues.REVIVE_SQUAD_SP, "aas.score.revive_squad"),

    VEHICLE_DESTROYED (0, 0, "aas.score.vehicle_destroyed"),
    VEHICLE_ASSIST    (0, 0, "aas.score.vehicle_assist"),

    HUB_PLACED        (ScoreValues.HUB_PLACED_TP, 0, "aas.score.hub_placed"),
    HUB_SPAWN         (ScoreValues.HUB_SPAWN_TP, 0, "aas.score.hub_spawn"),
    HUB_DESTROYED     (ScoreValues.HUB_DESTROYED_TP, 0, "aas.score.hub_destroyed"),
    HUB_RESUPPLY      (ScoreValues.HUB_RESUPPLY_TP, 0, "aas.score.hub_resupply"),

    RALLY_PLACED      (0, ScoreValues.RALLY_PLACED_SP, "aas.score.rally_placed"),
    RALLY_SPAWN       (0, ScoreValues.RALLY_SPAWN_SP, "aas.score.rally_spawn"),
    RALLY_DESTROYED   (ScoreValues.RALLY_DESTROYED_TP, 0, "aas.score.rally_destroyed"),

    POINT_CAPTURED    (ScoreValues.POINT_CAPTURED_TP, ScoreValues.POINT_CAPTURED_SP, "aas.score.point_captured"),
    POINT_PRESENCE    (ScoreValues.POINT_PRESENCE_TP, 0, "aas.score.point_presence"),

    BUILD_LIGHT       (ScoreValues.BUILD_LIGHT_TP, 0, "aas.score.build_light"),
    BUILD_BUNKER      (ScoreValues.BUILD_BUNKER_TP, 0, "aas.score.build_bunker"),
    BUILD_WEAPON      (ScoreValues.BUILD_WEAPON_TP, 0, "aas.score.build_weapon"),
    BUILD_STATION     (ScoreValues.BUILD_STATION_TP, 0, "aas.score.build_station"),

    TRANSPORT         (0, 0, "aas.score.transport");

    public final int teamPoints;
    public final int squadPoints;
    public final String langKey;

    ScoreType(int teamPoints, int squadPoints, String langKey) {
        this.teamPoints = teamPoints;
        this.squadPoints = squadPoints;
        this.langKey = langKey;
    }
}
