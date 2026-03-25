package com.mahjongplay.display

import com.mahjongplay.MahjongPlayPlugin
import com.mahjongplay.model.MahjongTile
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.CustomModelData
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

/**
 * Abstraction for creating ItemStacks for mahjong tiles.
 * Implementations can use the bundled resource pack or an optional ItemsAdder integration.
 */
interface TextureProvider {
    fun createTileItem(tile: MahjongTile): ItemStack
    fun isAvailable(): Boolean
    fun getProviderName(): String
}

/**
 * Default provider: uses the bundled resource pack with CustomModelData.
 */
class DefaultTextureProvider : TextureProvider {
    @Suppress("UnstableApiUsage")
    override fun createTileItem(tile: MahjongTile): ItemStack {
        val item = ItemStack(Material.PAPER)
        item.setData(
            DataComponentTypes.CUSTOM_MODEL_DATA,
            CustomModelData.customModelData()
                .addFloat((tile.code + 1).toFloat())
                .build()
        )
        return item
    }

    override fun isAvailable(): Boolean = true

    override fun getProviderName(): String = "Default (Resource Pack)"
}

/**
 * ItemsAdder provider: retrieves custom items from ItemsAdder using reflection.
 * Falls back to [DefaultTextureProvider] if ItemsAdder is unavailable or an item cannot be loaded.
 */
class ItemsAdderTextureProvider(private val namespace: String = "mahjong") : TextureProvider {

    private val fallback = DefaultTextureProvider()

    // Cached reflection references — null means ItemsAdder API is not available.
    private val customStackClass: Class<*>? = try {
        Class.forName("dev.lone.itemsadder.api.CustomStack")
    } catch (_: ClassNotFoundException) {
        null
    }
    private val getInstanceMethod = customStackClass?.getMethod("getInstance", String::class.java)
    private val getItemStackMethod = customStackClass?.getMethod("getItemStack")

    override fun createTileItem(tile: MahjongTile): ItemStack {
        if (customStackClass == null || getInstanceMethod == null || getItemStackMethod == null) {
            return fallback.createTileItem(tile)
        }
        return try {
            val itemId = "$namespace:${tileName(tile)}"
            val customStack = getInstanceMethod.invoke(null, itemId)
                ?: return fallback.createTileItem(tile).also {
                    MahjongPlayPlugin.instance.logger.warning("ItemsAdder item not found: $itemId, falling back to default")
                }
            (getItemStackMethod.invoke(customStack) as? ItemStack) ?: fallback.createTileItem(tile)
        } catch (e: Exception) {
            MahjongPlayPlugin.instance.logger.warning("Failed to load ItemsAdder item for ${tile.displayName}: ${e.message}")
            fallback.createTileItem(tile)
        }
    }

    override fun isAvailable(): Boolean = customStackClass != null

    override fun getProviderName(): String = "ItemsAdder (namespace: $namespace)"

    private fun tileName(tile: MahjongTile): String =
        "tile_${tile.displayName.lowercase().replace(" ", "_")}"
}
