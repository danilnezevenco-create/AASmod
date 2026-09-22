    // PATH: src\main\java\com\example\aas\network\PacketBuildRequest.java
    package com.example.aas.network;

    import com.example.aas.block.*;
    import com.example.aas.world.AASWorldData;
    import net.minecraft.ChatFormatting;
    import net.minecraft.core.BlockPos;
    import net.minecraft.core.Direction;
    import net.minecraft.network.FriendlyByteBuf;
    import net.minecraft.network.chat.Component;
    import net.minecraft.server.level.ServerLevel;
    import net.minecraft.server.level.ServerPlayer;
    import net.minecraft.world.level.block.HorizontalDirectionalBlock;
    import net.minecraft.world.level.block.entity.BlockEntity;
    import net.minecraft.world.level.block.state.BlockState;
    import net.minecraftforge.network.NetworkEvent;

    import java.util.ArrayList;
    import java.util.List;
    import java.util.function.Supplier;

    public class PacketBuildRequest {
        private final int structureId;
        private final BlockPos pos;
        private final int rotation;

        public PacketBuildRequest(int structureId, BlockPos pos, int rotation) {
            this.structureId = structureId;
            this.pos = pos;
            this.rotation = rotation;
        }

        public static void encode(PacketBuildRequest msg, FriendlyByteBuf buf) {
            buf.writeInt(msg.structureId);
            buf.writeBlockPos(msg.pos);
            buf.writeInt(msg.rotation);
        }

        public static PacketBuildRequest decode(FriendlyByteBuf buf) {
            return new PacketBuildRequest(buf.readInt(), buf.readBlockPos(), buf.readInt());
        }

        public static void handle(PacketBuildRequest msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                ServerLevel level = player.serverLevel();
                AASWorldData data = AASWorldData.get(level);
                String team = player.getTeam() != null ? player.getTeam().getName().toUpperCase() : "NEUTRAL";

                int cost = 5;
                if (msg.structureId == 11) cost = 5;
                if (msg.structureId == 12) cost = 10;
                if (msg.structureId == 13) cost = 15;
                if (msg.structureId == 15) cost = 25;
                if (msg.structureId == 16) cost = 20;
                if (msg.structureId == 17) cost = 50;
                if (msg.structureId == 18) cost = 100; // <--- ИМЕННО ЭТОЙ СТРОЧКИ НЕ ХВАТАЛО
                if (msg.structureId == 20) cost = 100;
                if (msg.structureId == 21) cost = 100;
                if (msg.structureId == 22) cost = 300;
                if (msg.structureId == 23) cost = 200;

                boolean consumed = false;
                if (player.isCreative()) {
                    consumed = true;
                } else {
                    int hubRadius = com.example.aas.config.AASConfig.HUB_BUILD_RADIUS.get();
                    int crateRadius = com.example.aas.config.AASConfig.CRATE_BUILD_RADIUS.get();
                    // Собираем ХАБы в радиусе, у которых вообще есть материалы
                    List<HubBlockEntity> availableHubs = new ArrayList<>();
                    for (AASWorldData.HubInfo hub : data.hubs) {
                        if (hub.team.equalsIgnoreCase(team) && hub.pos.distSqr(msg.pos) <= (double) hubRadius * hubRadius) {
                            BlockEntity be = level.getBlockEntity(hub.pos);
                            if (be instanceof HubBlockEntity hBe && hBe.getMaterials() > 0) {
                                availableHubs.add(hBe);
                            }
                        }
                    }

    // Собираем ящики в радиусе (со своим, крейтовым, радиусом)
                    List<com.example.aas.entity.SupplyCrateEntity> availableCrates =
                            level.getEntitiesOfClass(com.example.aas.entity.SupplyCrateEntity.class,
                                            new net.minecraft.world.phys.AABB(msg.pos).inflate(crateRadius))
                                    .stream()
                                    .filter(c -> (c.getTeamOwner().equals("NEUTRAL") || c.getTeamOwner().equalsIgnoreCase(team)) && c.getMaterials() > 0)
                                    .collect(java.util.stream.Collectors.toList());

                    int totalAvailable = 0;
                    for (HubBlockEntity h : availableHubs) totalAvailable += h.getMaterials();
                    for (com.example.aas.entity.SupplyCrateEntity c : availableCrates) totalAvailable += c.getMaterials();

                    if (totalAvailable >= cost) {
                        int remaining = cost;

                        // Сначала списываем с хабов
                        for (HubBlockEntity hBe : availableHubs) {
                            if (remaining <= 0) break;
                            int take = Math.min(remaining, hBe.getMaterials());
                            hBe.consumeMaterials(take);
                            remaining -= take;

                            for (AASWorldData.HubInfo h : data.hubs) {
                                if (level.getBlockEntity(h.pos) == hBe) {
                                    h.materials = hBe.getMaterials();
                                    break;
                                }
                            }
                        }

                        // Затем добираем с ящиков
                        for (com.example.aas.entity.SupplyCrateEntity crate : availableCrates) {
                            if (remaining <= 0) break;
                            int take = Math.min(remaining, crate.getMaterials());
                            crate.setMaterials(crate.getMaterials() - take);
                            remaining -= take;
                        }

                        data.setDirty();
                        consumed = true;
                    }
                }

                if (!consumed) {
                    player.sendSystemMessage(Component.literal("Not enough materials nearby!").withStyle(ChatFormatting.RED));
                    return;
                }

                level.playSound(null, msg.pos, com.example.aas.sound.ModSounds.BLUEPRINT_PLACE.get(), net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);

                Direction facing = getFacingFromRotation(msg.rotation);

                BlockState wallState = ModBlocks.WALL_BLOCK.get().defaultBlockState()
                        .setValue(WallBlock.FACING, facing)
                        .setValue(WallBlock.CONSTRUCTED, false)
                        .setValue(WallBlock.VALID, true);

                BlockState slabState = ModBlocks.WALL_SLAB_BLOCK.get().defaultBlockState()
                        .setValue(WallBlock.FACING, facing)
                        .setValue(WallBlock.CONSTRUCTED, false)
                        .setValue(WallBlock.VALID, true);

                BlockState wireState = ModBlocks.BARBED_WIRE_BLOCK.get().defaultBlockState()
                        .setValue(BarbedWireBlock.FACING, facing)
                        .setValue(BarbedWireBlock.CONSTRUCTED, false)
                        .setValue(BarbedWireBlock.VALID, true);

                List<BlockPos> placedBlocks = new ArrayList<>();

                if (msg.structureId == 10) {
                    placeAndAdd(level, msg.pos, wallState, team, placedBlocks);
                } else if (msg.structureId == 11) {
                    for (int x = 0; x <= 1; x++) {
                        for (int y = 0; y <= 1; y++) {
                            BlockPos p = getRelativePos(msg.pos, msg.rotation, x, y, 0);
                            placeAndAdd(level, p, wallState, team, placedBlocks);
                        }
                    }
                } else if (msg.structureId == 12) {
                    for (int x = -1; x <= 1; x++) {
                        for (int y = 0; y < 3; y++) {
                            BlockPos p = getRelativePos(msg.pos, msg.rotation, x, y, 0);
                            placeAndAdd(level, p, wallState, team, placedBlocks);
                        }
                    }
                } else if (msg.structureId == 13) {
                    for (int x = -1; x <= 1; x++) {
                        BlockPos p = getRelativePos(msg.pos, msg.rotation, x, 0, 0);
                        placeAndAdd(level, p, wireState, team, placedBlocks);
                    }
                } else if (msg.structureId == 15) { // Wire Wall
                    for (int x = -1; x <= 1; x++) {
                        BlockPos pStep = getRelativePos(msg.pos, msg.rotation, x, 0, 0);
                        placeAndAdd(level, pStep, wallState, team, placedBlocks);

                        BlockPos pWall1 = getRelativePos(msg.pos, msg.rotation, x, 0, -1);
                        placeAndAdd(level, pWall1, wallState, team, placedBlocks);

                        BlockPos pWall2 = getRelativePos(msg.pos, msg.rotation, x, 1, -1);
                        placeAndAdd(level, pWall2, wallState, team, placedBlocks);

                        BlockPos pWire = getRelativePos(msg.pos, msg.rotation, x, 0, -2);
                        placeAndAdd(level, pWire, wireState, team, placedBlocks);
                    }
                } else if (msg.structureId == 16) { // Loophole
                    for (int x = -1; x <= 1; x++) {
                        for (int y = 0; y < 3; y++) {
                            BlockPos p = getRelativePos(msg.pos, msg.rotation, x, y, 0);
                            if (x == 0 && y == 1) {
                                placeAndAdd(level, p, slabState, team, placedBlocks);
                            } else {
                                placeAndAdd(level, p, wallState, team, placedBlocks);
                            }
                        }
                    }
                } // Внутри handle() после всех else if (structureId == 16) ...
                else if (msg.structureId == 17) { // BUNKER
                    cost = 50;

                    for (int x = -1; x <= 1; x++) {
                        for (int y = 0; y <= 2; y++) {
                            for (int z = -1; z <= 1; z++) {
                                if (x == 0 && z == 0 && y < 2) continue;

                                BlockPos p = getRelativePos(msg.pos, msg.rotation, x, y, z);
                                BlockState stateToPlace = (y == 2) ? slabState : wallState;
                                String transform = "";
                                Direction outwardFacing = Direction.NORTH;

                                // Проверка сторон для сетки (в локальных координатах чертежа)
                                boolean isNet = false;

                                if (x == 0 && z == -1 && y < 2) { // ВХОД (спереди)
                                    isNet = true;
                                    outwardFacing = Direction.NORTH;
                                } else if (x == 0 && z == 1 && y == 1) { // ОКНО СЗАДИ
                                    isNet = true;
                                    outwardFacing = Direction.SOUTH;
                                } else if (x == -1 && z == 0 && y == 1) { // ОКНО СЛЕВА
                                    isNet = true;
                                    outwardFacing = Direction.WEST;
                                } else if (x == 1 && z == 0 && y == 1) { // ОКНО СПРАВА
                                    isNet = true;
                                    outwardFacing = Direction.EAST;
                                }

                                if (isNet) transform = "aas:camo_net";

                                if (level.getBlockState(p).canBeReplaced() || level.getBlockState(p).isAir()) {
                                    level.setBlock(p, stateToPlace, 3);
                                    BlockEntity be = level.getBlockEntity(p);
                                    if (be instanceof WallBlockEntity wbe) {
                                        wbe.setTeam(team);
                                        if (!transform.isEmpty()) {
                                            // Поворачиваем outwardFacing на нужное число "четвертей" (по часовой стрелке),
                                            // не смешивая системы координат toYRot() и msg.rotation
                                            int steps = ((-msg.rotation) / 90) % 4;
                                            if (steps < 0) steps += 4;

                                            Direction finalDir = outwardFacing;
                                            for (int i = 0; i < steps; i++) {
                                                finalDir = finalDir.getClockWise();
                                            }

                                            wbe.setTransformTo(transform, finalDir.get2DDataValue());
                                        }
                                        placedBlocks.add(p);
                                    }
                                }
                            }
                        }
                    }
                } else if (msg.structureId == 18) { // VEHICLE STATION
                    cost = 100;

                    BlockState state = ModBlocks.VEHICLE_STATION_BLOCK.get().defaultBlockState()
                            .setValue(VehicleStationBlock.FACING, facing);

                    if (level.getBlockState(msg.pos).canBeReplaced() || level.getBlockState(msg.pos).isAir()) {
                        level.setBlock(msg.pos, state, 3);

                        // ПЕРЕНЕСИТЕ СЮДА:
                        data.vehicleStations.add(new AASWorldData.StationInfo(msg.pos, team, level.dimension().location().toString()));
                        data.setDirty();

                        BlockEntity be = level.getBlockEntity(msg.pos);
                        if (be instanceof VehicleStationBlockEntity vsbe) {
                            vsbe.setTeam(team);
                        }
                        placedBlocks.add(msg.pos);
                    }
                } else if (msg.structureId == 20) { // M2
                    placeAndAdd(level, msg.pos, ModBlocks.M2_CONSTRUCTION_BLOCK.get().defaultBlockState().setValue(M2ConstructionBlock.FACING, facing), team, placedBlocks);
                } else if (msg.structureId == 21) { // AGS
                    BlockState state = ModBlocks.AGS_CONSTRUCTION_BLOCK.get().defaultBlockState()
                            .setValue(AGSConstructionBlock.FACING, player.getDirection().getOpposite())
                            .setValue(AGSConstructionBlock.VALID, true);
                    placeAndAdd(level, msg.pos, state, team, placedBlocks);
                } else if (msg.structureId == 22) { // Mortar
                    BlockState state = ModBlocks.MORTAR_CONSTRUCTION_BLOCK.get().defaultBlockState()
                            .setValue(MortarConstructionBlock.FACING, player.getDirection().getOpposite())
                            .setValue(MortarConstructionBlock.VALID, true);
                    placeAndAdd(level, msg.pos, state, team, placedBlocks);
                } else if (msg.structureId == 23) { // TOW
                    BlockState state = ModBlocks.TOW_CONSTRUCTION_BLOCK.get().defaultBlockState()
                            .setValue(TOWConstructionBlock.FACING, player.getDirection().getOpposite())
                            .setValue(TOWConstructionBlock.VALID, true);
                    placeAndAdd(level, msg.pos, state, team, placedBlocks);
                }

                // LINK BLOCKS FOR CROSS-PROPAGATION
                for (BlockPos p : placedBlocks) {
                    BlockEntity be = level.getBlockEntity(p);
                    if (be instanceof WallBlockEntity wall) {
                        wall.setLinkedWalls(placedBlocks);
                    } else if (be instanceof BarbedWireBlockEntity wire) {
                        wire.setLinkedWires(placedBlocks);
                    }
                }

                PacketHandler.sendToAllClients(level, com.example.aas.world.AASWorldData.get(level));
            });
            ctx.get().setPacketHandled(true);
        }

        private static Direction getFacingFromRotation(int rotation) {
            // Убираем отрицательные значения и приводим к диапазону 0-360
            int rot = (rotation % 360 + 360) % 360;

            // Прямое соответствие визуальному повороту чертежа:
            if (rot == 270 || rot == -90) return Direction.EAST;
            if (rot == 180 || rot == -180) return Direction.SOUTH;
            if (rot == 90 || rot == -270) return Direction.WEST;

            return Direction.NORTH; // 0 градусов
        }

        private static BlockPos getRelativePos(BlockPos base, int rotation, int xOff, int yOff, int zOff) {
            int rot = (rotation % 360 + 360) % 360;
            if (rot == 90) return base.offset(zOff, yOff, -xOff);
            if (rot == 180) return base.offset(-xOff, yOff, -zOff);
            if (rot == 270) return base.offset(-zOff, yOff, xOff);
            return base.offset(xOff, yOff, zOff);
        }

        private static void placeAndAdd(ServerLevel level, BlockPos p, BlockState state, String team, List<BlockPos> list) {
            if (level.getBlockState(p).canBeReplaced() || level.getBlockState(p).isAir()) {
                level.setBlock(p, state, 3);

                BlockEntity be = level.getBlockEntity(p);

                // ПРИСВАИВАЕМ КОМАНДУ ЧЕРТЕЖУ (Чтобы враги не могли его строить)
                if (be instanceof WallBlockEntity wall) wall.setTeam(team);
                else if (be instanceof BarbedWireBlockEntity wire) wire.setTeam(team);
                else if (be instanceof M2ConstructionBlockEntity m2) m2.setTeam(team);
                else if (be instanceof AGSConstructionBlockEntity ags) ags.setTeam(team);
                else if (be instanceof MortarConstructionBlockEntity mortar) mortar.setTeam(team);
                else if (be instanceof TOWConstructionBlockEntity tow) tow.setTeam(team);

                list.add(p);
            }
        }
    }