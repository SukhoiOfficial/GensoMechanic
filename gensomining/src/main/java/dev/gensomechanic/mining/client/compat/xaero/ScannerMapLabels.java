package dev.gensomechanic.mining.client.compat.xaero;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/** Every discovered instance has one upright label; overlapping names remain visible. */
public final class ScannerMapLabels {
    public static void draw(GuiGraphics graphics, ResourceKey<Level> dimension, double cameraX, double cameraZ,
                            double ps, double pc, double zoom, double halfWidth, double halfHeight, boolean circle, float textScale, boolean minimap) {
        if (dimension == null || zoom <= 0) return;
        double viewRadius=Math.hypot(halfWidth,halfHeight)/zoom+16;
        dev.gensomechanic.mining.client.ArchiveClient.viewport(dimension.location(),cameraX,cameraZ,viewRadius,minimap);
        var view = ScannerMapBridge.view(dimension.location());
        if (dimension == null || !dimension.location().equals(view.dimension()) || view.labels().isEmpty() || textScale <= 0) return;
        var font = Minecraft.getInstance().font;
        var pose = graphics.pose(); pose.pushPose();
        try {
            java.util.Set<ScannerMapBridge.Label> visibleLabels=new java.util.LinkedHashSet<>();
            int minX=(int)Math.floor((cameraX-viewRadius)/512), maxX=(int)Math.floor((cameraX+viewRadius)/512);
            int minZ=(int)Math.floor((cameraZ-viewRadius)/512), maxZ=(int)Math.floor((cameraZ+viewRadius)/512);
            view.labelRegions().forEach((r,list)-> { int rx=net.minecraft.world.level.ChunkPos.getX(r),rz=net.minecraft.world.level.ChunkPos.getZ(r); if(rx>=minX && rx<=maxX && rz>=minZ && rz<=maxZ) visibleLabels.addAll(list); });
            for (var label : visibleLabels) {
                double x = 0, y = 0, radius = Math.min(halfWidth, halfHeight);
                boolean visible = false;
                // Stable centroid anchor first, then the nearest visible member of this same instance.
                for (var member : label.members()) {
                    double dx = member.getMinBlockX() + 8 - cameraX, dz = member.getMinBlockZ() + 8 - cameraZ;
                    x = (ps * dx - pc * dz) * zoom; y = (pc * dx + ps * dz) * zoom;
                    if (intersects(dx, dz, ps, pc, zoom, halfWidth, halfHeight, circle)) { visible = true; break; }
                }
                if (!visible) continue;
                double halfTextHeight = font.lineHeight * textScale / 2;
                double maxWidth = circle ? 2 * Math.sqrt(Math.max(0, Math.pow(radius - 2, 2) - halfTextHeight * halfTextHeight)) : 2 * halfWidth - 4;
                String text = font.plainSubstrByWidth(label.name().getString(), Math.max(0, (int)(maxWidth / textScale)));
                if (text.isEmpty()) continue;
                double halfTextWidth = font.width(text) * textScale / 2;
                // Move edge labels inward instead of dropping the entire name.
                double limitY = Math.max(0, (circle ? Math.sqrt(Math.max(0, Math.pow(radius - 2, 2) - halfTextWidth * halfTextWidth)) : halfHeight - 2) - halfTextHeight);
                y = Mth.clamp(y, -limitY, limitY);
                double limitX = Math.max(0, (circle ? Math.sqrt(Math.max(0, Math.pow(radius - 2, 2) - Math.pow(Math.abs(y) + halfTextHeight, 2))) : halfWidth - 2) - halfTextWidth);
                x = Mth.clamp(x, -limitX, limitX);
                double left = x - halfTextWidth, top = y - halfTextHeight;
                pose.pushPose(); pose.translate(left, top, 0); pose.scale(textScale, textScale, 1);
                graphics.drawString(font, text, 0, 0, 0xFFFFFFFF, true); pose.popPose();
            }
            graphics.flush();
        } finally { pose.popPose(); }
    }
    /** Exact square/circle or oriented-square/rectangle intersection, including partial edge cells. */
    public static boolean intersects(double dx, double dz, double ps, double pc, double zoom,
            double halfWidth, double halfHeight, boolean circle) {
        if (circle) {
            double x = Math.max(0, Math.abs(dx)-8)*zoom, z = Math.max(0, Math.abs(dz)-8)*zoom;
            double radius = Math.min(halfWidth, halfHeight); return x*x+z*z <= radius*radius;
        }
        double x = (ps*dx-pc*dz)*zoom, y = (pc*dx+ps*dz)*zoom;
        double halfCell = 8*Math.abs(zoom), extent = halfCell*(Math.abs(ps)+Math.abs(pc));
        return Math.abs(x)<=halfWidth+extent && Math.abs(y)<=halfHeight+extent
            && Math.abs(ps*x+pc*y)<=halfCell+Math.abs(ps)*halfWidth+Math.abs(pc)*halfHeight
            && Math.abs(-pc*x+ps*y)<=halfCell+Math.abs(pc)*halfWidth+Math.abs(ps)*halfHeight;
    }
    private ScannerMapLabels() {}
}
