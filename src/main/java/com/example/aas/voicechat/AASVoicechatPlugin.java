package com.example.aas.voicechat;

import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketRadioVoiceActivity;
import com.example.aas.network.PacketVoiceChannelActivity;
import com.example.aas.config.AASConfig;
import com.example.aas.world.AASWorldData;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.packets.LocationalSoundPacket;
import de.maxhenkel.voicechat.api.packets.MicrophonePacket;
import de.maxhenkel.voicechat.api.packets.StaticSoundPacket;
import de.maxhenkel.voicechat.api.events.PlayerConnectedEvent;
import de.maxhenkel.voicechat.api.events.PlayerDisconnectedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ForgeVoicechatPlugin
public class AASVoicechatPlugin implements VoicechatPlugin {
    private static VoicechatServerApi serverApi;

    // Связывает AAS Squad ID с самим объектом Group (не только с UUID!).
    // Важно: группы с setPersistent(false) не гарантированно находятся через
    // serverApi.getGroup(uuid) до того, как в них зашел первый игрок, поэтому
    // храним ссылку на сам объект Group, возвращенный builder'ом, и используем
    // ее напрямую вместо повторного поиска по id.
    private static final Map<Integer, Group> squadToGroupMap = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastVoiceActivity = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastRadioActivity = new ConcurrentHashMap<>();

    // Namespace предмета(ов) walkie-talkie мода. Подтверждено логом сервера:
    // "Registering C2S receiver with id walkietalkie:..." и NBT предмета
    // ({walkietalkie.activate:0b, walkietalkie.canal:1, walkietalkie.mute:0b}).
    private static final String WALKIETALKIE_NAMESPACE = "walkietalkie";

    // Точные имена NBT-тегов рации, подтверждённые логом:
    private static final String TAG_ACTIVATE = "walkietalkie.activate"; // byte (0/1) — рация включена
    private static final String TAG_CANAL = "walkietalkie.canal";       // int — номер канала/частоты
    private static final String TAG_MUTE = "walkietalkie.mute";         // byte (0/1) — микрофон заглушен

    // ---- Командирский чат: «в правом ухе 100%, в левом ~30%» ----
    // Simple Voice Chat играет StaticSoundPacket одинаково в оба уха, поэтому панорамирование делаем так:
    // шлём каждому слушателю ПОЗИЦИОННЫЙ пакет, а позицию ставим рядом с его головой, правее направления взгляда
    // (пересчитывается по yaw на каждый пакет, т.е. звук «приклеен» к голове). Громкость не падает: расстояние ~2 блока.
    // Левое ухо ≈ 30% громкости правого получается при угле PAN_AZIMUTH_DEG ≈ 39° (0° = прямо, 90° = только правое ухо).
    // Хочешь тише слева — увеличь угол (до 90), громче слева — уменьши.
    private static final double COMMAND_PAN_AZIMUTH_DEG = 39.0;
    private static final double COMMAND_PAN_DISTANCE = 2.0;    // блоков от головы слушателя
    private static final float COMMAND_PAN_RANGE = 32.0f;      // дальность пакета (должна быть > COMMAND_PAN_DISTANCE)

    @Override
    public String getPluginId() {
        return "aas_voicechat";
    }

    @Override
    public void initialize(VoicechatApi api) {}

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, this::onServerStarted);
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicPacket);
        registration.registerEvent(PlayerConnectedEvent.class, this::onPlayerConnectedVoice);
        // Игрок отключился от voice chat — освобождаем его Opus-декодер (замер громкости).
        registration.registerEvent(PlayerDisconnectedEvent.class, e -> VoiceLevelMeter.remove(e.getPlayerUuid()));
        registerNameTagIconHider(registration);
        registerConnectionListener(registration);
    }

    // Скрывает над головой ВСЕ иконки voice chat (говорит/шепчет/мьют/группа),
    // КРОМЕ иконки "нет соединения с Simple Voice Chat" - её оставляем видимой.
    // NameTagIconRenderEvent появился только в voicechat-api 2.6.0, поэтому всё
    // делается через рефлексию, чтобы код компилировался на старой версии api-jar.
    private void registerNameTagIconHider(EventRegistration registration) {
        try {
            Class<?> eventClass = Class.forName("de.maxhenkel.voicechat.api.events.NameTagIconRenderEvent");

            java.util.function.Consumer<Object> consumer = event -> {
                try {
                    Object clientApi = event.getClass().getMethod("getVoicechat").invoke(event);
                    java.util.UUID entityId = (java.util.UUID) event.getClass().getMethod("getEntityId").invoke(event);

                    java.lang.reflect.Method isDisconnectedMethod =
                            clientApi.getClass().getMethod("isDisconnected", java.util.UUID.class);
                    boolean disconnected = (boolean) isDisconnectedMethod.invoke(clientApi, entityId);

                    if (!disconnected) {
                        // Это НЕ иконка "нет коннекта" (значит говорит/шепчет/мьют/группа и т.п.) - скрываем
                        event.getClass().getMethod("cancel").invoke(event);
                    }
                    // если disconnected == true - ничего не делаем, иконка остаётся видимой
                } catch (Throwable ignored) {
                    // старый/несовместимый API - просто не трогаем рендер
                }
            };

            java.lang.reflect.Method registerEvent = EventRegistration.class.getMethod(
                    "registerEvent", Class.class, java.util.function.Consumer.class);
            registerEvent.invoke(registration, eventClass, consumer);
        } catch (Throwable t) {
            System.err.println("[AAS Voicechat] NameTagIconRenderEvent unavailable, skipping icon hiding: " + t);
        }
    }

    // Отслеживает подключение/отключение клиента от голосового сервера Simple Voice Chat.
    // ClientVoicechatConnectionEvent - через рефлексию, чтобы не зависеть от версии api-jar.
    private void registerConnectionListener(EventRegistration registration) {
        try {
            Class<?> eventClass = Class.forName("de.maxhenkel.voicechat.api.events.ClientVoicechatConnectionEvent");

            java.util.function.Consumer<Object> consumer = event -> {
                try {
                    boolean connected = (boolean) event.getClass().getMethod("isConnected").invoke(event);
                    net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                            net.minecraftforge.api.distmarker.Dist.CLIENT,
                            () -> () -> com.example.aas.client.ClientData.voicechatConnected = connected
                    );
                } catch (Throwable ignored) {}
            };

            java.lang.reflect.Method registerEvent = EventRegistration.class.getMethod(
                    "registerEvent", Class.class, java.util.function.Consumer.class);
            registerEvent.invoke(registration, eventClass, consumer);
        } catch (Throwable t) {
            System.err.println("[AAS Voicechat] ClientVoicechatConnectionEvent unavailable: " + t);
        }
    }

    private void onServerStarted(VoicechatServerStartedEvent event) {
        serverApi = event.getVoicechat();
    }

    // ======================= ГОЛОСОВЫЕ КАНАЛЫ =======================
    // V (локальный) / B (отряд) / * (командирский) — игрок зажимает клавишу, клиент сообщает
    // серверу номер канала (PacketVoiceChannel -> VoiceChannelState). Здесь мы ПЕРЕХВАТЫВАЕМ
    // каждый голосовой пакет Simple Voice Chat, отменяем стандартную обработку (иначе SVC
    // сам раздал бы звук по расстоянию / группам) и отправляем звук только тем, кому он положен:
    //   LOCAL   — все игроки в радиусе VOICE_LOCAL_RANGE (в т.ч. противники), позиционный звук;
    //   SQUAD   — только участники отряда, на любом расстоянии;
    //   COMMAND — только лидеры отрядов своей команды, на любом расстоянии.
    // Ни один канал не зажат -> пакет отбрасывается (никто не слышит).
    // Мёртвый игрок НЕ говорит в локальный чат и НЕ слышит локальный чат (отряд / командирский / рация — без изменений).
    // Рация (walkietalkie): если у игрока в руке включённая рация и ни один наш канал не зажат,
    // событие НЕ отменяется — мод рации работает как раньше.
    //
    // Громкость голоса: каждый пакет декодируется в VoiceLevelMeter, а уровень (0 тихо / 1 средний /
    // 2 нормальный) отправляется клиентам вместе с индикатором «говорит» — от него зависит иконка микрофона.
    private void onMicPacket(MicrophonePacketEvent event) {
        try {
            if (serverApi == null || ServerLifecycleHooks.getCurrentServer() == null) return;
            if (event.getSenderConnection() == null) return;

            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            UUID playerUuid = event.getSenderConnection().getPlayer().getUuid();
            ServerPlayer sender = server.getPlayerList().getPlayer(playerUuid);
            if (sender == null) return;

            int channel = VoiceChannelState.get(playerUuid);

            // --- РАЦИЯ: индикатор рации (как раньше) ---
            Integer talkerCanal = getActiveWalkieTalkieCanal(sender);
            if (talkerCanal != null) {
                notifyRadioActivity(server, sender, talkerCanal);
                if (channel == VoiceChannelState.NONE) return;   // говорит только в рацию — не вмешиваемся
            }

            // Дальше всё решаем сами: стандартную раздачу звука SVC отключаем.
            event.cancel();
            if (channel == VoiceChannelState.NONE) return;

            // Мёртвый игрок не может говорить в локальный чат (после event.cancel(), чтобы SVC не раздал звук сам)
            if (channel == VoiceChannelState.LOCAL && !sender.isAlive()) return;

            MicrophonePacket mic = event.getPacket();
            byte[] opus = mic.getOpusEncodedData();

            // Замер громкости голоса (для иконки микрофона)
            VoiceLevelMeter.update(serverApi, playerUuid, opus);

            AASWorldData data = AASWorldData.get(sender.serverLevel());
            String pName = sender.getScoreboardName();

            switch (channel) {
                case VoiceChannelState.LOCAL -> routeLocal(server, sender, mic, opus);
                case VoiceChannelState.SQUAD -> routeSquad(server, data, sender, pName, mic, opus);
                case VoiceChannelState.COMMAND -> routeCommand(server, data, sender, pName, mic, opus);
                default -> { }
            }
        } catch (Exception e) {
            // никогда не даем ошибке voicechat уронить tick сервера
            System.err.println("[AAS Voicechat] onMicPacket failed: " + e);
        }
    }

    // ---------- V: локальный чат ----------
    private void routeLocal(MinecraftServer server, ServerPlayer sender, MicrophonePacket mic, byte[] opus) {
        float range = getLocalRange();
        double r2 = (double) range * range;

        LocationalSoundPacket packet = mic.locationalSoundPacketBuilder()
                .position(serverApi.createPosition(sender.getX(), sender.getEyeY(), sender.getZ()))
                .distance(range)
                .opusEncodedData(opus)
                .build();

        boolean report = shouldReportActivity(sender.getUUID());
        // level берём только когда реально отправляем индикатор (takeLevel начинает новое окно замера)
        PacketVoiceChannelActivity activity = report
                ? new PacketVoiceChannelActivity(sender.getScoreboardName(), VoiceChannelState.LOCAL,
                VoiceLevelMeter.takeLevel(sender.getUUID()))
                : null;

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level() != sender.level()) continue;
            if (p.distanceToSqr(sender) > r2) continue;

            // Мёртвый слушатель не слышит локальный чат (индикатор «говорит» ему по-прежнему шлём ниже)
            if (p != sender && p.isAlive()) {
                VoicechatConnection conn = serverApi.getConnectionOf(p.getUUID());
                if (conn != null) serverApi.sendLocationalSoundPacketTo(conn, packet);
            }
            // Индикатор в списке слева: только союзникам (и самому говорящему). Противник не видит.
            if (report && (p == sender || isAlly(sender, p))) {
                PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> p), activity);
            }
        }
    }

    // ---------- B: чат отряда ----------
    private void routeSquad(MinecraftServer server, AASWorldData data, ServerPlayer sender, String pName,
                            MicrophonePacket mic, byte[] opus) {
        AASWorldData.Squad squad = findSquad(data, pName);
        if (squad == null) return;   // не в отряде — никто не слышит

        StaticSoundPacket packet = mic.staticSoundPacketBuilder().opusEncodedData(opus).build();
        boolean report = shouldReportActivity(sender.getUUID());
        PacketVoiceChannelActivity activity = report
                ? new PacketVoiceChannelActivity(pName, VoiceChannelState.SQUAD,
                VoiceLevelMeter.takeLevel(sender.getUUID()))
                : null;

        for (String member : new java.util.ArrayList<>(squad.members)) {
            ServerPlayer p = server.getPlayerList().getPlayerByName(member);
            if (p == null) continue;
            if (p != sender) {
                VoicechatConnection conn = serverApi.getConnectionOf(p.getUUID());
                if (conn != null) serverApi.sendStaticSoundPacketTo(conn, packet);
            }
            if (report) PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> p), activity);
        }
    }

    // ---------- *: командирский чат (только лидеры отрядов) ----------
    private void routeCommand(MinecraftServer server, AASWorldData data, ServerPlayer sender, String pName,
                              MicrophonePacket mic, byte[] opus) {
        AASWorldData.Squad own = findSquad(data, pName);
        if (own == null || !pName.equals(own.leader)) return;   // говорить может только лидер отряда

        boolean report = shouldReportActivity(sender.getUUID());
        PacketVoiceChannelActivity activity = report
                ? new PacketVoiceChannelActivity(pName, VoiceChannelState.COMMAND,
                VoiceLevelMeter.takeLevel(sender.getUUID()))
                : null;

        for (AASWorldData.Squad s : new java.util.ArrayList<>(data.squads)) {
            if (s.leader == null || s.leader.isEmpty()) continue;
            ServerPlayer p = server.getPlayerList().getPlayerByName(s.leader);
            if (p == null) continue;
            if (p != sender && !isAlly(sender, p)) continue;   // только своя команда

            if (p != sender) {
                VoicechatConnection conn = serverApi.getConnectionOf(p.getUUID());
                if (conn != null) serverApi.sendLocationalSoundPacketTo(conn, buildCommandPannedPacket(mic, opus, p));
            }
            if (report) PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> p), activity);
        }
    }

    // Позиционный пакет для слушателя: источник звука рядом с его головой, смещён вправо от направления взгляда.
    private LocationalSoundPacket buildCommandPannedPacket(MicrophonePacket mic, byte[] opus, ServerPlayer listener) {
        double yaw = Math.toRadians(listener.getYRot());
        double az = Math.toRadians(COMMAND_PAN_AZIMUTH_DEG);
        // Minecraft: yaw 0 = взгляд на +Z (юг); вперёд = (-sin yaw, cos yaw); вправо = (-cos yaw, -sin yaw)
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        double x = listener.getX() + (fx * Math.cos(az) + rx * Math.sin(az)) * COMMAND_PAN_DISTANCE;
        double z = listener.getZ() + (fz * Math.cos(az) + rz * Math.sin(az)) * COMMAND_PAN_DISTANCE;
        double y = listener.getEyeY();
        return mic.locationalSoundPacketBuilder()
                .position(serverApi.createPosition(x, y, z))
                .distance(COMMAND_PAN_RANGE)
                .opusEncodedData(opus)
                .build();
    }

    // ---------- вспомогательное ----------
    private static AASWorldData.Squad findSquad(AASWorldData data, String playerName) {
        for (AASWorldData.Squad s : data.squads) {
            if (s.members.contains(playerName)) return s;
        }
        return null;
    }

    // Союзник = тот же скорборд-команда (BLUE/RED). Игроки без команды никому не союзники.
    private static boolean isAlly(ServerPlayer a, ServerPlayer b) {
        if (a.getTeam() == null || b.getTeam() == null) return false;
        return a.getTeam().getName().equals(b.getTeam().getName());
    }

    private static float getLocalRange() {
        try {
            return (float) AASConfig.VOICE_LOCAL_RANGE.get();
        } catch (Throwable t) {
            return 30.0f;   // конфиг ещё не загружен
        }
    }

    // Индикатор «говорит» шлём не чаще раза в 150 мс на игрока (звук при этом идёт каждый пакет).
    private static boolean shouldReportActivity(UUID playerUuid) {
        long now = System.currentTimeMillis();
        if (now - lastVoiceActivity.getOrDefault(playerUuid, 0L) > 150) {
            lastVoiceActivity.put(playerUuid, now);
            return true;
        }
        return false;
    }

    // Красный индикатор рации: только игроки с рацией на том же канале (как раньше, троттлинг 150 мс).
    private void notifyRadioActivity(MinecraftServer server, ServerPlayer sender, int talkerCanal) {
        long now = System.currentTimeMillis();
        UUID id = sender.getUUID();
        if (now - lastRadioActivity.getOrDefault(id, 0L) <= 150) return;
        lastRadioActivity.put(id, now);

        PacketRadioVoiceActivity radioPacket = new PacketRadioVoiceActivity(sender.getScoreboardName());
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.getUUID().equals(id)) continue;
            if (playerHasWalkieTalkieOnCanal(p, talkerCanal)) {
                PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> p), radioPacket);
            }
        }
    }

    // Возвращает номер канала рации, через которую сейчас говорит этот игрок,
    // ИЛИ null, если он не говорит по рации (рации нет в руке, она выключена,
    // или у него включён mute). Проверяет только main hand и off hand — мод
    // walkietalkie разрешает говорить только держа рацию в руке.
    private Integer getActiveWalkieTalkieCanal(ServerPlayer player) {
        try {
            Integer canal = getActiveCanalFromStack(player.getMainHandItem());
            if (canal != null) return canal;
            return getActiveCanalFromStack(player.getOffhandItem());
        } catch (Exception e) {
            return null;
        }
    }

    private Integer getActiveCanalFromStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (!isWalkieTalkieStack(stack)) return null;

        CompoundTag tag = stack.getTag();
        if (tag == null) return null;

        boolean activated = tag.getBoolean(TAG_ACTIVATE);
        boolean muted = tag.getBoolean(TAG_MUTE);
        if (!activated || muted) return null;

        return tag.getInt(TAG_CANAL);
    }

    // Проверяет, есть ли у игрока где-либо в инвентаре (включая руки, хотбар,
    // основной инвентарь) включённая рация на указанном канале. Используется
    // для слушателя: ему достаточно ИМЕТЬ настроенную рацию, говорить в неё не нужно.
    private boolean playerHasWalkieTalkieOnCanal(ServerPlayer player, int canal) {
        try {
            for (ItemStack stack : player.getInventory().items) {
                if (matchesCanal(stack, canal)) return true;
            }
            if (matchesCanal(player.getOffhandItem(), canal)) return true;
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean matchesCanal(ItemStack stack, int canal) {
        if (stack == null || stack.isEmpty() || !isWalkieTalkieStack(stack)) return false;
        CompoundTag tag = stack.getTag();
        if (tag == null) return false;
        // Слушателю не обязательно быть "activate=true" — он просто должен быть
        // настроен на нужный канал, чтобы услышать. Если хочешь требовать,
        // чтобы рация слушателя тоже была включена, добавь: && tag.getBoolean(TAG_ACTIVATE)
        return tag.getInt(TAG_CANAL) == canal;
    }

    // Проверяет, является ли предмет рацией мода walkietalkie (любой апгрейд),
    // используя namespace регистрового имени, а не конкретный класс/RegistryObject
    // мода walkietalkie, чтобы не тянуть его API как hard-dependency и не словить
    // NoClassDefFoundError/LinkageError, если мод вдруг отсутствует или сменит классы.
    private boolean isWalkieTalkieStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && id.getNamespace().equals(WALKIETALKIE_NAMESPACE);
    }

    // Если игрок подключился к Voice Chat (зашел на сервер/перезагрузил микрофон)
    private void onPlayerConnectedVoice(PlayerConnectedEvent event) {
        try {
            if (serverApi == null || ServerLifecycleHooks.getCurrentServer() == null) return;

            UUID playerUuid = event.getConnection().getPlayer().getUuid();
            ServerPlayer player = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(playerUuid);
            if (player == null) return;

            // Раньше здесь игрок добавлялся в группу SVC. Теперь группы не нужны; на всякий случай
            // выводим из любой старой группы, чтобы она не подмешивала звук.
            leaveGroup(player);
        } catch (Exception e) {
            System.err.println("[AAS Voicechat] onPlayerConnectedVoice failed: " + e);
        }
    }

    // ================== МЕТОДЫ УПРАВЛЕНИЯ ГРУППАМИ ==================
    // Все вызовы завернуты в try/catch: какая-либо ошибка voicechat не должна
    // никогда прерывать выполнение PacketSquadAction.handle() на стороне вызывающего.

    public static void createAndJoinGroup(ServerPlayer player, int squadId, String squadName) {
        // Группы Simple Voice Chat больше НЕ используются: звук отряда раздаётся вручную (см. routeSquad),
        // потому что OPEN-группа слышала игрока даже когда он говорил с кем-то не из отряда.
        if (true) return;
        if (serverApi == null) return;
        try {
            String password = UUID.randomUUID().toString().substring(0, 8); // Случайный секретный пароль
            UUID groupId = UUID.randomUUID(); // Генерируем ID для группы

            Group group = serverApi.groupBuilder()
                    .setId(groupId)
                    .setName("Squad: " + squadName)
                    .setPassword(password)
                    .setType(Group.Type.OPEN) // OPEN - все могут слышать вас в 3D (Spatial)
                    .setPersistent(false)     // Самоудалится, когда выйдет последний
                    .setHidden(true)          // Скрытая от меню
                    .build();

            // Важно: храним сам объект Group, а не только groupId, потому что
            // setPersistent(false) группа не гарантированно "находится" через
            // getGroup(groupId) до входа в нее первого игрока.
            squadToGroupMap.put(squadId, group);

            VoicechatConnection conn = serverApi.getConnectionOf(player.getUUID());
            if (conn != null) {
                conn.setGroup(group);
            }
        } catch (Exception e) {
            System.err.println("[AAS Voicechat] createAndJoinGroup failed for squad " + squadId + ": " + e);
        }
    }

    public static void joinGroup(ServerPlayer player, int squadId) {
        if (true) return;   // см. createAndJoinGroup
        if (serverApi == null) return;
        try {
            Group group = squadToGroupMap.get(squadId);
            if (group != null) {
                VoicechatConnection conn = serverApi.getConnectionOf(player.getUUID());
                if (conn != null) conn.setGroup(group);
            }
        } catch (Exception e) {
            System.err.println("[AAS Voicechat] joinGroup failed for squad " + squadId + ": " + e);
        }
    }

    public static void leaveGroup(ServerPlayer player) {
        if (serverApi == null) return;
        try {
            VoicechatConnection conn = serverApi.getConnectionOf(player.getUUID());
            if (conn != null) {
                conn.setGroup(null);
            }
        } catch (Exception e) {
            System.err.println("[AAS Voicechat] leaveGroup failed: " + e);
        }
    }
}