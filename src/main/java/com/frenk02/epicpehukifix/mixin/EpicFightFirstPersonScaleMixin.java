package com.frenk02.epicpehukifix.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import virtuoel.pehkui.api.ScaleTypes;
import yesman.epicfight.client.renderer.FirstPersonRenderer;
import yesman.epicfight.client.world.capabilites.entitypatch.player.LocalPlayerPatch;

/**
 * Fixes the vertical position mismatch between Pehkui's player scaling and
 * Epic Fight's first-person arm renderer.
 *
 * Root cause: Epic Fight's FirstPersonRenderer positions the arm model by
 * translating the PoseStack by -eyeHeight (Pehkui-scaled). However, the arm
 * geometry in model space stays at its default (scale=1) Y positions. When
 * Pehkui moves the camera up/down, the arms appear visually displaced:
 *   - Large player (scale > 1): camera higher → arms appear too low / disappear
 *   - Small player (scale < 1): camera lower → arms appear too high / block view
 *
 * Fix: pre-translate the PoseStack by scaledEyeHeight * (1 - 1/scale) so the
 * arm geometry lines up with the Pehkui-scaled camera position.
 *
 * The mixin targets Epic Fight's class directly (remap=false).
 * If Epic Fight is absent: Mixin silently skips injection (no target class found).
 * If Pehkui is absent: PEHKUI_PRESENT guard prevents any Pehkui API calls.
 */
@Mixin(value = FirstPersonRenderer.class, remap = false)
public class EpicFightFirstPersonScaleMixin {

    @Unique
    private static final boolean PEHKUI_PRESENT = checkPehkui();

    @Unique
    private static boolean checkPehkui() {
        try {
            Class.forName("virtuoel.pehkui.api.ScaleTypes");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void epicpehukifix$fixVerticalOffset(
            LocalPlayer entity,
            LocalPlayerPatch localPlayerPatch,
            LivingEntityRenderer<LocalPlayer, PlayerModel<LocalPlayer>> renderer,
            MultiBufferSource buffer,
            PoseStack poseStack,
            int packedLight,
            float partialTick,
            CallbackInfo ci) {
        if (!PEHKUI_PRESENT) return;

        float scale = ScaleTypes.BASE.getScaleData(entity).getScale(partialTick);
        if (Math.abs(scale - 1.0F) > 0.001F) {
            float scaledEyeHeight = entity.getDimensions(Pose.STANDING).eyeHeight();
            // offset = scaledEyeHeight * (1 - 1/scale)
            //   scale > 1: positive → shifts arms up to follow higher camera
            //   scale < 1: negative → shifts arms down to follow lower camera
            float offset = scaledEyeHeight * (1.0F - 1.0F / scale);
            poseStack.translate(0.0F, offset, 0.0F);
        }
    }
}
