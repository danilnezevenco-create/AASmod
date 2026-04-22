// PATH: src\main\java\com\example\aas\mixin\PlayerSkinMixin.java
package com.example.aas.mixin;

import com.example.aas.client.ClientData;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.scores.Team;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

@Mixin(AbstractClientPlayer.class)
public class PlayerSkinMixin {

    // Кэш для текстур, чтобы не создавать новые объекты ResourceLocation каждый кадр
    private static final Map<String, ResourceLocation> SKIN_CACHE = new HashMap<>();

    @Inject(method = "getSkinTextureLocation", at = @At("HEAD"), cancellable = true)
    private void aas$overrideSkinTexture(CallbackInfoReturnable<ResourceLocation> cir) {
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;

        Team team = player.getTeam();
        if (team == null) return;

        String faction = "none";
        if (team.getName().equalsIgnoreCase("Blue")) {
            faction = ClientData.BLUE_FACTION;
        } else if (team.getName().equalsIgnoreCase("Red")) {
            faction = ClientData.RED_FACTION;
        }

        if (faction != null && !faction.equals("none")) {
            String cleanFaction = faction.toLowerCase();

            // Получаем кит игрока из синхронизированных данных
            String kit = ClientData.playerKits.getOrDefault(player.getScoreboardName(), "Unassigned");

            // Форматируем путь: {фракция}/{имя_кита}.png
            String kitFileName = "base"; // По умолчанию базовый скин

            if (!kit.equals("Unassigned") && !kit.isEmpty()) {
                // Превращаем "Anti-air" в "anti_air", "Drone Operator" в "drone_operator"
                kitFileName = kit.toLowerCase().replace(" ", "_").replace("-", "_");
            }

            // Итоговый путь: "textures/skins/russia/sniper.png"
            String cacheKey = cleanFaction + "/" + kitFileName;

            // Берем текстуру из кэша или создаем новую
            ResourceLocation skin = SKIN_CACHE.computeIfAbsent(cacheKey,
                    k -> new ResourceLocation("aas", "textures/skins/" + k + ".png"));

            cir.setReturnValue(skin);
        }
    }
}