package com.example.examplemod;

import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.ClientProxy;
import mchorse.blockbuster.recording.data.Record;
import mchorse.blockbuster.recording.RecordUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;

/**
 * Invalidates native Blockbuster record caches after a successful .dat export.
 * Must run on the server thread so all watching clients receive UnloadFrames.
 */
public final class EditorRecordCacheRefresh
{
    private EditorRecordCacheRefresh() {}

    public static void afterExport(final String recordName)
    {
        if (recordName == null || recordName.isEmpty()) return;
        final Minecraft mc = Minecraft.getMinecraft();
        // Native Record files are local only in integrated-server worlds.
        // A remote server's cache belongs to that server and cannot be safely
        // invalidated from the client after a local file write.
        final MinecraftServer server = mc.getIntegratedServer();
        if (server == null)
        {
            System.out.println("[BBS Editor] Record cache refresh skipped (no local server)");
            return;
        }
        server.addScheduledTask(new Runnable()
        {
            @Override
            public void run()
            {
                Record cached = CommonProxy.manager.records.remove(recordName);
                if (cached != null)
                {
                    RecordUtils.unloadRecord(cached);
                }
                // Integrated client's cache is separate from the server map.
                mc.addScheduledTask(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        ClientProxy.manager.records.remove(recordName);
                        System.out.println("[BBS Editor] Refreshed Record cache: " + recordName);
                    }
                });
            }
        });
    }
}
