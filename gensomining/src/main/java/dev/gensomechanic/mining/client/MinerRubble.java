package dev.gensomechanic.mining.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.gensomechanic.mining.content.miner.MechanicalMinerBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** Axis-aligned excavated centre and eight staggered rocks, with native block texture density. */
final class MinerRubble {
    record Rock(float x0, float z0, float x1, float z1, float height) {}
    static final java.util.List<Rock> ROCKS = java.util.List.of(
            new Rock(-6, -6, 6, 6, 2),
            new Rock(-8, -14, 1, -4, 5), new Rock(-1, -12, 8, -4, 4),
            new Rock(4, -5, 13, 1, 5.5f), new Rock(4, -1, 14, 8, 4.5f),
            new Rock(-1, 4, 6, 14, 5), new Rock(-8, 4, 1, 12, 3.5f),
            new Rock(-14, -1, -4, 8, 5.75f), new Rock(-13, -6, -4, 1, 4.25f));
    static void render(MechanicalMinerBlockEntity be, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!be.rubbleVisible()) return;
        var state = be.displayState();
        if (state.isAir()) return;
        Minecraft mc = Minecraft.getInstance();
        var sprite = mc.getBlockRenderer().getBlockModelShaper().getParticleIcon(state);
        if (sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation())) return;
        int color = mc.getBlockColors().getColor(state, be.getLevel(), be.getBlockPos().below(2), 0);
        if (color == -1) color = 0xFFFFFF;
        var buffer = buffers.getBuffer(RenderType.cutoutMipped());
        pose.pushPose();
        pose.translate(.5, -.999, .5);
        for (Rock rock : ROCKS) cube(rock, pose, buffer, sprite, color, light, overlay);
        pose.popPose();
    }
    private static void cube(Rock rock, PoseStack pose, VertexConsumer out, TextureAtlasSprite sprite, int color, int light, int overlay) {
        float a = rock.x0 / 16, b = rock.x1 / 16, c = rock.z0 / 16, d = rock.z1 / 16, h = rock.height / 16;
        float width = rock.x1 - rock.x0, depth = rock.z1 - rock.z0;
        face(pose, out, sprite, color, light, overlay, 0, 1, 0, width, depth, a,h,c, a,h,d, b,h,d, b,h,c);
        face(pose, out, sprite, color, light, overlay, 0,-1, 0, width, depth, a,0,d, a,0,c, b,0,c, b,0,d);
        face(pose, out, sprite, color, light, overlay, 0,0,-1, width, rock.height, b,h,c, b,0,c, a,0,c, a,h,c);
        face(pose, out, sprite, color, light, overlay, 0,0,1, width, rock.height, a,h,d, a,0,d, b,0,d, b,h,d);
        face(pose, out, sprite, color, light, overlay, 1,0,0, depth, rock.height, b,h,d, b,0,d, b,0,c, b,h,c);
        face(pose, out, sprite, color, light, overlay, -1,0,0, depth, rock.height, a,h,c, a,0,c, a,0,d, a,h,d);
    }
    private static void face(PoseStack pose, VertexConsumer out, TextureAtlasSprite sprite, int color, int light, int overlay,
                             float nx, float ny, float nz, float width, float height, float... xyz) {
        float shade = ny > 0 ? 1 : ny < 0 ? .5f : nx == 0 ? .8f : .6f;
        for (int i = 0; i < 4; i++) out.addVertex(pose.last().pose(), xyz[i*3], xyz[i*3+1], xyz[i*3+2])
                .setColor(((color >> 16) & 255) / 255f * shade, ((color >> 8) & 255) / 255f * shade, (color & 255) / 255f * shade, 1)
                .setUv(sprite.getU(i < 2 ? 0 : width / 16), sprite.getV(i == 0 || i == 3 ? 0 : height / 16))
                .setOverlay(overlay).setLight(light).setNormal(pose.last(), nx, ny, nz);
    }
    private MinerRubble() {}
}
