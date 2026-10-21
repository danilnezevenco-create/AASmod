// PATH: src/main/java/com/example/aas/network/KitRankProgress.java
package com.example.aas.network;

/**
 * Прогресс ранга игрока на текущем ките.
 *
 * @param level уровень (1..20)
 * @param into  сколько очков уже набрано на текущем уровне
 * @param cost  сколько очков нужно для перехода на следующий уровень (0 = максимальный уровень)
 */
public record KitRankProgress(int level, int into, int cost) {
}