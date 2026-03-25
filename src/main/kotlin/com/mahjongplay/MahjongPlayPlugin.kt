package com.mahjongplay

import com.mahjongplay.display.DefaultTextureProvider
import com.mahjongplay.display.ItemsAdderTextureProvider
import com.mahjongplay.display.MahjongTileDisplay
import com.mahjongplay.interaction.EntityInteractionListener
import com.mahjongplay.table.MahjongCommand
import com.mahjongplay.table.MahjongTableManager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin

class MahjongPlayPlugin : JavaPlugin(), Listener {

    companion object {
        lateinit var instance: MahjongPlayPlugin
            private set
    }

    lateinit var tableManager: MahjongTableManager
        private set

    override fun onEnable() {
        instance = this
        saveDefaultConfig()
        tableManager = MahjongTableManager()

        initializeTextureProvider()

        getCommand("mahjong")?.let {
            val cmd = MahjongCommand(tableManager)
            it.setExecutor(cmd)
            it.tabCompleter = cmd
        }

        server.pluginManager.registerEvents(EntityInteractionListener(tableManager), this)
        server.pluginManager.registerEvents(this, this)

        server.scheduler.runTaskLater(this, Runnable {
            tableManager.loadTables(dataFolder)
        }, 20L)

        logger.info("MahjongCraft v${pluginMeta.version} enabled!")
    }

    override fun onDisable() {
        if (::tableManager.isInitialized) {
            tableManager.saveTables(dataFolder)
            tableManager.shutdown()
        }
        logger.info("MahjongCraft disabled.")
    }

    private fun initializeTextureProvider() {
        val mode = config.getString("texture.mode", "default") ?: "default"
        val provider = if (mode.equals("itemsadder", ignoreCase = true)
            && server.pluginManager.getPlugin("ItemsAdder") != null
        ) {
            val namespace = config.getString("texture.itemsadder.namespace", "mahjong") ?: "mahjong"
            val p = ItemsAdderTextureProvider(namespace)
            if (p.isAvailable()) {
                logger.info("Texture provider: ${p.getProviderName()}")
                p
            } else {
                logger.warning("ItemsAdder API not found; falling back to default resource pack")
                DefaultTextureProvider()
            }
        } else {
            if (mode.equals("itemsadder", ignoreCase = true)) {
                logger.warning("texture.mode is 'itemsadder' but ItemsAdder is not installed; using default resource pack")
            }
            DefaultTextureProvider().also { logger.info("Texture provider: ${it.getProviderName()}") }
        }
        MahjongTileDisplay.setTextureProvider(provider)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId.toString()
        tableManager.leaveTable(uuid)
    }

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        if (tableManager.isProtectedBlock(event.block.location)) {
            event.isCancelled = true
        }
    }
}
