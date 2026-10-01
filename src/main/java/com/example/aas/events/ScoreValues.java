package com.example.aas.events;

/**
 * Все числа системы очков (TP / SP) в одном месте.
 * Меняйте значения здесь, логику начислений трогать не нужно.
 * Диапазон значений за одно действие: 20 (мелочь) - 300 (крупное и важное).
 */
public final class ScoreValues {

    private ScoreValues() {}

    // ================= ОБЩЕЕ =================
    public static final int MIN_ACTION = 20;
    public static final int MAX_ACTION = 300;

    // ================= УБИЙСТВО ВРАГА (мелочь) =================
    public static final int KILL_ENEMY_TP = 20;

    // ================= РЕВАЙВ =================
    public static final int REVIVE_TP = 30;              // союзник не из моего отряда
    public static final int REVIVE_SQUAD_TP = 20;        // союзник из моего отряда: TP часть
    public static final int REVIVE_SQUAD_SP = 40;        // союзник из моего отряда: SP часть
    public static final long REVIVE_COOLDOWN_TICKS = 60L * 20L;   // анти-фарм: один и тот же игрок не чаще 1 раза в 60 сек

    // ================= ТЕХНИКА =================
    public static final int VEHICLE_TP_PER_TICKET = 15;  // TP = clamp(AAS_TicketPenalty * 15, MIN, MAX)
    public static final int VEHICLE_TP_MIN = 30;
    public static final int VEHICLE_TP_MAX = 300;
    public static final int VEHICLE_ASSIST_PERCENT = 40; // ассист = 40% от очков убийцы
    public static final long VEHICLE_ASSIST_WINDOW_TICKS = 30L * 20L; // урон за последние 30 сек

    // ================= ХАБ (HUB / FOB) =================
    public static final int HUB_PLACED_TP = 100;
    public static final int HUB_SPAWN_TP = 20;           // строителю за каждый спавн союзника
    public static final long HUB_SPAWN_COOLDOWN_TICKS = 30L * 20L; // не чаще 1 раза в 30 сек на одного спавнящегося
    public static final int HUB_DESTROYED_TP = 120;
    public static final int HUB_RESUPPLY_TP = 40;

    // ================= RALLY POINT =================
    public static final int RALLY_PLACED_SP = 40;
    public static final int RALLY_SPAWN_SP = 20;         // хозяину ралли за каждого заспавнившегося союзника по отряду
    public static final long RALLY_SPAWN_COOLDOWN_TICKS = 30L * 20L;
    public static final int RALLY_DESTROYED_TP = 50;

    // ================= ЗАХВАТ ТОЧКИ =================
    public static final int POINT_CAPTURED_TP = 200;
    public static final int POINT_CAPTURED_SP = 50;
    public static final int POINT_PRESENCE_TP = 20;      // за каждый интервал присутствия на активной точке
    public static final long POINT_PRESENCE_INTERVAL_TICKS = 30L * 20L; // каждые 30 сек
    public static final long POINT_AFK_TIMEOUT_TICKS = 15L * 20L;       // анти-афк: двигаться/что-то делать раз в 15 сек

    // ================= СТРОИТЕЛЬСТВО (при ЗАВЕРШЕНИИ постройки) =================
    public static final int BUILD_LIGHT_TP = 20;         // колючая проволока, маскировочная сеть, стена
    public static final int BUILD_BUNKER_TP = 80;
    public static final int BUILD_WEAPON_TP = 100;       // AGS-30, M2 Browning, миномёт, TOW
    public static final int BUILD_CONTRIBUTOR_PERCENT = 50; // те, кто вложился, но не завершил
    public static final long BUILD_ENTRY_TTL_TICKS = 20L * 60L * 20L; // сколько хранить недостроенные записи вкладчиков

    // ================= ПЕРЕВОЗКА КОМАНДЫ =================
    public static final int TRANSPORT_TP_PER_PASSENGER = 20; // за пассажира за минуту вождения

    // Максимальный из кулдаунов (для очистки карты кулдаунов)
    public static final long MAX_COOLDOWN_TICKS = REVIVE_COOLDOWN_TICKS;
}
