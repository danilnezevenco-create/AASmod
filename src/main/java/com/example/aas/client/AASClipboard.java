package com.example.aas.client;

import net.minecraft.nbt.CompoundTag;
import java.util.HashMap;
import java.util.Map;

public class AASClipboard {
    // Хранит один конкретный кит
    public static CompoundTag kitData = null;
    // Хранит всю команду (Имя кита -> Данные)
    public static Map<String, CompoundTag> teamKitsData = null;
}