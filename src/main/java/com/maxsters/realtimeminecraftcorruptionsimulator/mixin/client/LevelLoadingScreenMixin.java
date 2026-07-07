package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.gui.LoadingScreenCorruptionHooks;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LevelLoadingScreen.class)
@SuppressWarnings({"target", "rawtypes"})
public abstract class LevelLoadingScreenMixin {
    @ModifyArg(
            method = {
                    "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
                    "m_88315_(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/LevelLoadingScreen;renderChunks(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/server/level/progress/StoringChunkProgressListener;IIII)V", remap = false),
            index = 2,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for LevelLoadingScreen#render.")
    private int rmc$corruptChunkCenterX(int x) {
        return LoadingScreenCorruptionHooks.chunkCenterX(x);
    }

    @ModifyArg(
            method = {
                    "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
                    "m_88315_(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/LevelLoadingScreen;renderChunks(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/server/level/progress/StoringChunkProgressListener;IIII)V", remap = false),
            index = 3,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for LevelLoadingScreen#render.")
    private int rmc$corruptChunkCenterY(int y) {
        return LoadingScreenCorruptionHooks.chunkCenterY(y);
    }

    @ModifyArg(
            method = {
                    "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
                    "m_88315_(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/LevelLoadingScreen;renderChunks(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/server/level/progress/StoringChunkProgressListener;IIII)V", remap = false),
            index = 4,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for LevelLoadingScreen#render.")
    private int rmc$corruptChunkCellSize(int cellSize) {
        return LoadingScreenCorruptionHooks.chunkCellSize(cellSize);
    }

    @ModifyArg(
            method = {
                    "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
                    "m_88315_(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/LevelLoadingScreen;renderChunks(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/server/level/progress/StoringChunkProgressListener;IIII)V", remap = false),
            index = 5,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for LevelLoadingScreen#render.")
    private int rmc$corruptChunkPadding(int padding) {
        return LoadingScreenCorruptionHooks.chunkPadding(padding);
    }

    @ModifyArg(
            method = {
                    "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
                    "m_88315_(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/LevelLoadingScreen;m_96149_(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/server/level/progress/StoringChunkProgressListener;IIII)V", remap = false),
            index = 2,
            remap = false,
            require = 0
    )
    @Dynamic("Targets the SRG invoke of LevelLoadingScreen#renderChunks.")
    private int rmc$corruptChunkCenterXSrg(int x) {
        return LoadingScreenCorruptionHooks.chunkCenterX(x);
    }

    @ModifyArg(
            method = {
                    "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
                    "m_88315_(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/LevelLoadingScreen;m_96149_(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/server/level/progress/StoringChunkProgressListener;IIII)V", remap = false),
            index = 3,
            remap = false,
            require = 0
    )
    @Dynamic("Targets the SRG invoke of LevelLoadingScreen#renderChunks.")
    private int rmc$corruptChunkCenterYSrg(int y) {
        return LoadingScreenCorruptionHooks.chunkCenterY(y);
    }

    @ModifyArg(
            method = {
                    "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
                    "m_88315_(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/LevelLoadingScreen;m_96149_(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/server/level/progress/StoringChunkProgressListener;IIII)V", remap = false),
            index = 4,
            remap = false,
            require = 0
    )
    @Dynamic("Targets the SRG invoke of LevelLoadingScreen#renderChunks.")
    private int rmc$corruptChunkCellSizeSrg(int cellSize) {
        return LoadingScreenCorruptionHooks.chunkCellSize(cellSize);
    }

    @ModifyArg(
            method = {
                    "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
                    "m_88315_(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/LevelLoadingScreen;m_96149_(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/server/level/progress/StoringChunkProgressListener;IIII)V", remap = false),
            index = 5,
            remap = false,
            require = 0
    )
    @Dynamic("Targets the SRG invoke of LevelLoadingScreen#renderChunks.")
    private int rmc$corruptChunkPaddingSrg(int padding) {
        return LoadingScreenCorruptionHooks.chunkPadding(padding);
    }

    @ModifyArg(
            method = {
                    "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
                    "m_88315_(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawCenteredString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V", remap = false),
            index = 2,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for LevelLoadingScreen#render.")
    private int rmc$corruptProgressTextX(int x) {
        return LoadingScreenCorruptionHooks.progressTextX(x);
    }

    @ModifyArg(
            method = {
                    "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
                    "m_88315_(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawCenteredString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V", remap = false),
            index = 3,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for LevelLoadingScreen#render.")
    private int rmc$corruptProgressTextY(int y) {
        return LoadingScreenCorruptionHooks.progressTextY(y);
    }

    @ModifyArg(
            method = {
                    "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
                    "m_88315_(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawCenteredString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V", remap = false),
            index = 4,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for LevelLoadingScreen#render.")
    private int rmc$corruptProgressTextColor(int color) {
        return LoadingScreenCorruptionHooks.progressTextColor(color);
    }

    @ModifyArg(
            method = {
                    "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
                    "m_88315_(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;m_280137_(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V", remap = false),
            index = 2,
            remap = false,
            require = 0
    )
    @Dynamic("Targets the SRG invoke of GuiGraphics#drawCenteredString.")
    private int rmc$corruptProgressTextXSrg(int x) {
        return LoadingScreenCorruptionHooks.progressTextX(x);
    }

    @ModifyArg(
            method = {
                    "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
                    "m_88315_(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;m_280137_(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V", remap = false),
            index = 3,
            remap = false,
            require = 0
    )
    @Dynamic("Targets the SRG invoke of GuiGraphics#drawCenteredString.")
    private int rmc$corruptProgressTextYSrg(int y) {
        return LoadingScreenCorruptionHooks.progressTextY(y);
    }

    @ModifyArg(
            method = {
                    "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
                    "m_88315_(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;m_280137_(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V", remap = false),
            index = 4,
            remap = false,
            require = 0
    )
    @Dynamic("Targets the SRG invoke of GuiGraphics#drawCenteredString.")
    private int rmc$corruptProgressTextColorSrg(int color) {
        return LoadingScreenCorruptionHooks.progressTextColor(color);
    }

    @Redirect(
            method = {
                    "lambda$renderChunks$1(ILnet/minecraft/client/gui/GuiGraphics;IIIILnet/minecraft/server/level/progress/StoringChunkProgressListener;IIII)V",
                    "lambda$renderChunks$0(ILnet/minecraft/client/gui/GuiGraphics;IIIILnet/minecraft/server/level/progress/StoringChunkProgressListener;IIII)V",
                    "m_285672_(ILnet/minecraft/client/gui/GuiGraphics;IIIIILnet/minecraft/server/level/progress/StoringChunkProgressListener;III)V",
                    "m_285672_(ILnet/minecraft/client/gui/GuiGraphics;IIIILnet/minecraft/server/level/progress/StoringChunkProgressListener;IIII)V"
            },
            at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/objects/Object2IntMap;getInt(Ljava/lang/Object;)I", remap = false),
            remap = false,
            require = 0
    )
    @Dynamic("Targets mapped and SRG lambda bodies used by LevelLoadingScreen#renderChunks.")
    private static int rmc$corruptChunkStatusColor(Object2IntMap colors, Object status) {
        return LoadingScreenCorruptionHooks.chunkColor(colors.getInt(status), status);
    }
}
