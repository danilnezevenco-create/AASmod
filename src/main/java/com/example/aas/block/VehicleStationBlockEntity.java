package com.example.aas.block;

import com.example.aas.config.AASConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import java.util.List;

public class VehicleStationBlockEntity extends BlockEntity {
    public static final int MAX_PROGRESS = 3000;
    private int currentProgress = 0;
    private int activeDiggers = 0;
    private boolean sapperBoost = false;

    // Команда-владелец станции. Хранится ТОЛЬКО в верхнем регистре ("BLUE"/"RED"/"NEUTRAL"),
    // чтобы сравнения с другими частями мода не ломались из-за регистра.
    private String teamOwner = "NEUTRAL";

    // Ссылка на объект звука на клиенте
    private Object clientSoundRef = null;

    public VehicleStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.VEHICLE_STATION_BE.get(), pos, state);
    }

    public void addProgress() { addProgress(false); }

    public void addProgress(boolean isSapper) {
        if (currentProgress < MAX_PROGRESS) {
            this.activeDiggers++;
            if (isSapper) this.sapperBoost = true;
        }
    }

    public void addCreativeProgress(int amount) {
        this.currentProgress = Math.min(MAX_PROGRESS, this.currentProgress + amount);
        setChanged();
    }

    public float getPercentage() { return (float) currentProgress / MAX_PROGRESS; }

    public String getTeam() { return teamOwner; }

    /**
     * Устанавливает команду-владельца станции.
     * Нормализуем регистр и защищаемся от null, чтобы станция ВСЕГДА
     * однозначно понимала, какой команде она принадлежит.
     */
    public void setTeam(String team) {
        this.teamOwner = (team == null || team.isBlank()) ? "NEUTRAL" : team.trim().toUpperCase();
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, VehicleStationBlockEntity entity) {
        // --- КЛИЕНТСКАЯ ЧАСТЬ (ЗВУК) ---
        if (level.isClientSide) {
            if (state.getValue(VehicleStationBlock.CONSTRUCTED)) {
                entity.handleSoundClient();
            }
            return;
        }

        // --- ЛОГИКА СТРОЙКИ (выкапывания) ---
        if (!state.getValue(VehicleStationBlock.CONSTRUCTED)) {
            if (state.getValue(VehicleStationBlock.BUILD_STAGE) == 0) {
                level.setBlock(pos, state.setValue(VehicleStationBlock.BUILD_STAGE, 1), 3);
            }

            if (entity.activeDiggers > 0 || entity.currentProgress > 0) {
                if (entity.activeDiggers > 0) {
                    float speed = (entity.activeDiggers == 1) ? 1.0f : (entity.activeDiggers == 2) ? 1.34f : (entity.activeDiggers == 3) ? 2.0f : 4.0f;
                    if (entity.sapperBoost) speed *= 2.0f;
                    float multiplier = com.example.aas.config.AASConfig.DIGGING_SPEED_MULTIPLIER.get().floatValue();
                    speed *= multiplier;
                    entity.currentProgress += (int) Math.ceil(speed);
                }

                if (entity.currentProgress >= MAX_PROGRESS) {
                    entity.currentProgress = MAX_PROGRESS;
                    level.setBlock(pos, state.setValue(VehicleStationBlock.CONSTRUCTED, true).setValue(VehicleStationBlock.BUILD_STAGE, 2), 3);
                } else {
                    int stage = (entity.currentProgress >= MAX_PROGRESS / 2) ? 2 : 1;
                    if (state.getValue(VehicleStationBlock.BUILD_STAGE) != stage) {
                        level.setBlock(pos, state.setValue(VehicleStationBlock.BUILD_STAGE, stage), 3);
                    }
                }
                level.sendBlockUpdated(pos, state, state, 3);
            }
            entity.activeDiggers = 0;
            entity.sapperBoost = false;
            return; // СТАНЦИЯ НЕ РАБОТАЕТ, ПОКА НЕ ПОЛНОСТЬЮ ВЫКОПАНА
        }

        // --- ЛОГИКА ПОПОЛНЕНИЯ (только для полностью выкопанной/достроенной станции) ---
        if (level.getGameTime() % 20 != 0) return;
        if (!state.getValue(VehicleStationBlock.CONSTRUCTED)) return; // защитное дублирование

        boolean stationHasTeam = entity.teamOwner != null && !entity.teamOwner.isBlank() && !entity.teamOwner.equals("NEUTRAL");

        AABB area = new AABB(pos).inflate(10);
        List<Player> nearbyPlayers = level.getEntitiesOfClass(Player.class, area);

        for (Player p : nearbyPlayers) {
            Entity vehicle = p.getVehicle();

            if (vehicle == null) {
                String stationLabel = stationHasTeam ? entity.teamOwner : "NEUTRAL";
                continue;
            }

            // 1. НИКОГДА не трогаем сам физический ящик снабжения (сущность-предмет на земле)
            if (vehicle instanceof com.example.aas.entity.SupplyCrateEntity) continue;

            // ПРИМЕЧАНИЕ: технику, которая ВЕЗЁТ ящики снабжения (грузовики, у которых в NBT
            // есть счётчик "AAS_SupplyAmmo"), мы больше НЕ пропускаем целиком — станция обязана
            // пополнять её собственный инвентарь/БК (магазины, AAS_InitialLoadout) как у любой
            // другой техники. Единственное, что запрещено трогать — это сам тег "AAS_SupplyAmmo"
            // (счётчик ящиков снабжения). Ни resupplyFromLoadout(), ни фолбэк с ITEM_HANDLER
            // этот тег не читают и не пишут, так что он в любом случае остаётся нетронутым.

            // 2. Определяем команду техники: берём существующую метку,
            //    а если её нет — назначаем по команде водителя (и сохраняем на будущее).
            String vTeam;
            if (vehicle.getPersistentData().contains("AAS_VehicleTeam")) {
                vTeam = vehicle.getPersistentData().getString("AAS_VehicleTeam");
            } else {
                vTeam = (p.getTeam() != null) ? p.getTeam().getName().toUpperCase() : "";
                if (!vTeam.isBlank()) {
                    vehicle.getPersistentData().putString("AAS_VehicleTeam", vTeam);
                }
            }

            if (vTeam == null || vTeam.isBlank()) {
                sendActionBar(p, Component.translatable("aas.msg.station_no_vehicle_team").getString(), ChatFormatting.RED);
                continue;
            }
            vTeam = vTeam.trim();

            // 3. Условие "из одной команды"
            if (!stationHasTeam) {
                sendActionBar(p, Component.translatable("aas.msg.station_no_team").getString(), ChatFormatting.RED);
                continue;
            }
            if (!vTeam.equalsIgnoreCase(entity.teamOwner)) {
                sendActionBar(p, Component.translatable("aas.msg.station_wrong_team", entity.teamOwner).getString(), ChatFormatting.RED);
                continue;
            }

            // --- Пополняем БК техники ---
            boolean didAmmoUpdate = false;
            int shownCurrent = -1;
            int shownMax = -1;

            // 4a. Специфичные орудия AAS (турели M2/AGS) - у них есть собственный счётчик патронов.
            if (vehicle instanceof com.example.aas.entity.M2BrowningEntity m2) {
                int cur = m2.getAmmoCount();
                shownMax = 200;
                if (cur < shownMax) {
                    m2.setAmmoCount(Math.min(shownMax, cur + 5));
                    didAmmoUpdate = true;
                }
                shownCurrent = m2.getAmmoCount();
            } else if (vehicle instanceof com.example.aas.entity.AGS30Entity ags) {
                int cur = ags.getAmmoCount();
                shownMax = 30;
                if (cur < shownMax) {
                    ags.setAmmoCount(Math.min(shownMax, cur + 1));
                    didAmmoUpdate = true;
                }
                shownCurrent = ags.getAmmoCount();
            }

            // 4b. Техника со спавнера (AAS_InitialLoadout) — эталонный БК/лоадаут задан
            //     самим VehicleSpawnerBlockEntity при спавне. Именно ОН знает, сколько
            //     конкретно этой технике нужно положить (магазины, ленты, снаряды и т.д.).
            LoadoutResult loadoutResult = resupplyFromLoadout(vehicle);
            if (loadoutResult != null && loadoutResult.targetTotal > 0) {
                didAmmoUpdate = didAmmoUpdate || loadoutResult.changed;
                // Если у турели (4a) не было своего счётчика, показываем прогресс по лоадауту
                if (shownMax == -1) {
                    shownCurrent = loadoutResult.currentTotal;
                    shownMax = loadoutResult.targetTotal;
                }
            }

            // 4c. Фолбэк для всего остального: просто чиним повреждённые стаки в инвентаре
            //     (техника без AAS_InitialLoadout, например сторонние машины без спавнера).
            final boolean[] repairedMags = {false};
            if (loadoutResult == null) {
                vehicle.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(h -> {
                    for (int i = 0; i < h.getSlots(); i++) {
                        ItemStack s = h.getStackInSlot(i);
                        if (!s.isEmpty() && s.isDamaged()) {
                            s.setDamageValue(0);
                            repairedMags[0] = true;
                        }
                    }
                });
            }

            // --- Обратная связь игроку в Action Bar ---
            if (shownMax != -1) {
                if (shownCurrent < shownMax) {
                    sendActionBar(p, Component.translatable("aas.msg.station_resupplying", shownCurrent, shownMax).getString(), ChatFormatting.YELLOW);
                } else {
                    sendActionBar(p, Component.translatable("aas.msg.station_ammo_full").getString(), ChatFormatting.GREEN);
                }
            } else if (didAmmoUpdate || repairedMags[0]) {
                sendActionBar(p, Component.translatable("aas.msg.station_resupplying_generic").getString(), ChatFormatting.YELLOW);
            } else {
                sendActionBar(p, Component.translatable("aas.msg.station_ammo_full").getString(), ChatFormatting.GREEN);
            }
        }
    }

    /**
     * Небольшой результат сравнения текущего инвентаря техники с эталонным лоадаутом.
     */
    private static class LoadoutResult {
        boolean changed;
        int currentTotal;
        int targetTotal;
    }

    /**
     * Сравнивает текущий инвентарь техники (капабилити ITEM_HANDLER) с эталонным
     * лоадаутом из NBT "AAS_InitialLoadout" (записан VehicleSpawnerBlockEntity при спавне)
     * и постепенно (по ~15% от нужного количества за тик станции) восполняет недостачу
     * по каждому слоту — как под-считанные магазины/патроны, так и полностью пустые слоты.
     * Возвращает null, если у техники вообще нет такого лоадаута (не со спавнера AAS).
     */
    private static LoadoutResult resupplyFromLoadout(Entity vehicle) {
        if (!vehicle.getPersistentData().contains("AAS_InitialLoadout")) return null;

        ListTag loadoutTag = vehicle.getPersistentData().getList("AAS_InitialLoadout", 10);
        if (loadoutTag.isEmpty()) return null;

        IItemHandler rawHandler = vehicle.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
        if (!(rawHandler instanceof IItemHandlerModifiable handler)) return null;

        LoadoutResult result = new LoadoutResult();

        for (int i = 0; i < loadoutTag.size(); i++) {
            CompoundTag itemTag = loadoutTag.getCompound(i);
            int slot = itemTag.getByte("Slot") & 255;
            if (slot >= handler.getSlots()) continue;

            ItemStack targetStack = ItemStack.of(itemTag);
            if (targetStack.isEmpty()) continue;

            int targetCount = targetStack.getCount();
            result.targetTotal += targetCount;

            ItemStack currentStack = handler.getStackInSlot(slot);

            // Если слот занят чем-то ДРУГИМ (не тем же предметом/тегами) — не трогаем,
            // чтобы не выкидывать то, что игрок сам туда положил.
            if (!currentStack.isEmpty() && !ItemStack.isSameItemSameTags(currentStack, targetStack)) {
                result.currentTotal += currentStack.getCount();
                continue;
            }

            int currentCount = currentStack.isEmpty() ? 0 : currentStack.getCount();
            if (currentCount >= targetCount) {
                result.currentTotal += currentCount;
                continue;
            }

            int deficit = targetCount - currentCount;
            int step = Math.max(1, (int) Math.ceil(targetCount * 0.01)); // ~15% от нормы за тик станции
            int toAdd = Math.min(deficit, step);

            ItemStack newStack = targetStack.copy();
            newStack.setCount(currentCount + toAdd);
            handler.setStackInSlot(slot, newStack);

            result.changed = true;
            result.currentTotal += (currentCount + toAdd);
        }

        return result;
    }

    private static void sendActionBar(Player player, String msg, ChatFormatting color) {
        player.displayClientMessage(Component.literal(msg).withStyle(color), true);
    }

    private void handleSoundClient() {
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
            System.out.println("[AAS DEBUG] handleSoundClient called, ref=" + this.clientSoundRef);
            this.clientSoundRef = com.example.aas.client.ClientHooks.playStationSound(this, this.clientSoundRef);
        });
    }

    @Override
    public void setRemoved() {
        if (this.level != null && this.level.isClientSide) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
                com.example.aas.client.ClientHooks.stopStationSound(this.clientSoundRef);
            });
        }
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("BuildProgress", currentProgress);
        tag.putString("TeamOwner", teamOwner);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        currentProgress = tag.getInt("BuildProgress");
        if (tag.contains("TeamOwner")) {
            String loaded = tag.getString("TeamOwner");
            teamOwner = (loaded == null || loaded.isBlank()) ? "NEUTRAL" : loaded.trim().toUpperCase();
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}