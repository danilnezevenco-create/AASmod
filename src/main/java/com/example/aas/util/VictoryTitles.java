package com.example.aas.util;

/**
 * Заголовки экрана победы. Пороги (по числу оставшихся тикетов у ПОБЕДИВШЕЙ команды)
 * вынесены в константы, чтобы их было легко менять.
 */
public final class VictoryTitles {

    private VictoryTitles() {}

    /** 0..CLOSE_MAX тикетов: близкий исход (пиррова победа / близкое поражение). */
    public static final int CLOSE_MAX = 50;
    /** CLOSE_MAX+1..MINOR_MAX: незначительный исход. */
    public static final int MINOR_MAX = 100;
    /** MINOR_MAX+1..NORMAL_MAX: обычный исход. Всё что выше: сокрушительный. */
    public static final int NORMAL_MAX = 300;

    /** Ключ локализации заголовка. winnerTickets — тикеты победившей команды. */
    public static String titleKey(boolean localPlayerWon, int winnerTickets) {
        String prefix = localPlayerWon ? "aas.victory.title.win_" : "aas.victory.title.loss_";
        if (winnerTickets <= CLOSE_MAX) return prefix + "close";
        if (winnerTickets <= MINOR_MAX) return prefix + "minor";
        if (winnerTickets <= NORMAL_MAX) return prefix + "normal";
        return prefix + "crushing";
    }
}
