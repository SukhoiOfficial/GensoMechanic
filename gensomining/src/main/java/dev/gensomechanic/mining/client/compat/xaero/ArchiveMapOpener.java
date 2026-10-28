package dev.gensomechanic.mining.client.compat.xaero;

import net.minecraft.client.Minecraft;
import xaero.map.WorldMapSession;
import xaero.map.controls.ControlsRegister;

/** Loaded only on clients with both optional maps. Uses the native map-key action. */
public final class ArchiveMapOpener {
    public static void open() {
        var mc=Minecraft.getInstance(); var session=WorldMapSession.getCurrentSession();
        if(mc.player!=null && mc.level!=null && session!=null && session.getMapProcessor()!=null && session.getControlsHandler()!=null)
            session.getControlsHandler().keyDown(ControlsRegister.keyOpenMap,false,false);
    }
    private ArchiveMapOpener(){}
}
