package com.example.aas.voicechat;

import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketRadioVoiceActivity;
import com.example.aas.network.PacketVoiceActivity;
import com.example.aas.world.AASWorldData;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.PlayerConnectedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import net.minecraft.nbt.CompoundTag;
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

    // Namespace предмета(ов) walkie-talkie мода. Подтверждено логом сервера:
    // "Registering C2S receiver with id walkietalkie:..." и NBT предмета
    // ({walkietalkie.activate:0b, walkietalkie.canal:1, walkietalkie.mute:0b}).
    private static final String WALKIETALKIE_NAMESPACE = "walkietalkie";

    // Точные имена NBT-тегов рации, подтверждённые логом:
    private static final String TAG_ACTIVATE = "walkietalkie.activate"; // byte (0/1) — рация включена
    private static final String TAG_CANAL = "walkietalkie.canal";       // int — номер канала/частоты
    private static final String TAG_MUTE = "walkietalkie.mute";         // byte (0/1) — микрофон заглушен

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

    // Рассылка пакета сокомандникам для зелёного индикатора отряда +
    // рассылка ТОЛЬКО игрокам с рацией на той же частоте для красного индикатора
    // рации (с троттлингом 150мс). Событие от Voice Chat завёрнуто в try/catch,
    // чтобы любая ошибка здесь не крашила tick сервера и не затрагивала логику отрядов.
    private void onMicPacket(MicrophonePacketEvent event) {
        try {
            if (serverApi == null || ServerLifecycleHooks.getCurrentServer() == null) return;

            UUID playerUuid = event.getSenderConnection().getPlayer().getUuid();
            long now = System.currentTimeMillis();

            if (now - lastVoiceActivity.getOrDefault(playerUuid, 0L) > 150) {
                lastVoiceActivity.put(playerUuid, now);

                // Получаем майнкрафтовского игрока через Forge
                ServerPlayer sender = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(playerUuid);
                if (sender == null) return;

                String pName = sender.getScoreboardName();

                // === РАЦИЯ (walkietalkie-forge) ===
                // Проверяем рацию в руке у говорящего. Она должна быть:
                // 1) предметом мода walkietalkie,
                // 2) включена (activate = true),
                // 3) у говорящего не должен быть включён mute (иначе мод вообще не
                //    передавал бы звук, но проверяем на всякий случай).
                Integer talkerCanal = getActiveWalkieTalkieCanal(sender);
                if (talkerCanal != null) {
                    PacketRadioVoiceActivity radioPacket = new PacketRadioVoiceActivity(pName);

                    for (ServerPlayer p : ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
                        if (p.getUUID().equals(sender.getUUID())) continue;
                        if (playerHasWalkieTalkieOnCanal(p, talkerCanal)) {
                            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> p), radioPacket);
                        }
                    }
                }

                AASWorldData data = AASWorldData.get(sender.serverLevel());

                // Ищем отряд
                for (AASWorldData.Squad s : data.squads) {
                    if (s.members.contains(pName)) {
                        PacketVoiceActivity packet = new PacketVoiceActivity(pName);
                        for (String member : s.members) {
                            ServerPlayer p = sender.server.getPlayerList().getPlayerByName(member);
                            if (p != null) {
                                PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> p), packet);
                            }
                        }
                        break;
                    }
                }
            }
        } catch (Exception e) {
            // никогда не даем ошибке voicechat уронить tick сервера
            System.err.println("[AAS Voicechat] onMicPacket failed: " + e);
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

            // Если он в отряде - закидываем в группу
            int squadId = player.getPersistentData().getInt("AAS_SquadID");
            if (squadId > 0) joinGroup(player, squadId);
        } catch (Exception e) {
            System.err.println("[AAS Voicechat] onPlayerConnectedVoice failed: " + e);
        }
    }

    // ================== МЕТОДЫ УПРАВЛЕНИЯ ГРУППАМИ ==================
    // Все вызовы завернуты в try/catch: какая-либо ошибка voicechat не должна
    // никогда прерывать выполнение PacketSquadAction.handle() на стороне вызывающего.

    public static void createAndJoinGroup(ServerPlayer player, int squadId, String squadName) {
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