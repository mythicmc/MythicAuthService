package org.mythicmc.mythicauthservice

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.plugin.java.JavaPlugin
import redis.clients.jedis.Jedis
import java.util.logging.Level

class MythicAuthService : JavaPlugin() {
    companion object {
        lateinit var plugin: MythicAuthService
            private set

        var jedisPub: Jedis? = null
            private set
        var jedisSub: Jedis? = null
            private set
        var redisListener: RedisListener? = null
            private set
    }

    override fun onLoad() {
        plugin = this
    }

    override fun onEnable() {
        reload()

        getCommand("mythicauthservice")?.setTabCompleter { _, _, _, _ -> listOf("reload") }
        getCommand("mythicauthservice")?.setExecutor { sender, _, _, args ->
            if (args.size == 1 && args[0] == "reload") {
                try {
                    reload()
                    sender.sendMessage(Component
                        .text("Successfully reloaded MythicAuthService config.")
                        .color(NamedTextColor.AQUA))
                } catch (e: Exception) {
                    sender.sendMessage(Component
                        .text("Failed to reload MythicAuthService config! Check console for error.")
                        .color(NamedTextColor.RED))
                    logger.log(Level.SEVERE, "An error occurred while loading the config!", e)
                }
                true
            } else false
        }
    }

    override fun onDisable() {
        closeRedis()
    }

    private fun closeRedis() {
        redisListener?.unsubscribe()
        redisListener = null
        jedisPub?.close()
        jedisPub = null
        jedisSub?.close()
        jedisSub = null
    }

    private fun reload() {
        // Save config.
        saveDefaultConfig()
        reloadConfig()

        // Destroy existing pool if it exists and create a new one.
        closeRedis()
        val pub = Jedis(config.getString("redis") ?: "redis://localhost:6379")
        val sub = Jedis(config.getString("redis") ?: "redis://localhost:6379")
        val listener = RedisListener(pub, this)

        // Create actor.
        server.scheduler.runTaskAsynchronously(this) { _ ->
            sub.subscribe(listener, "mythicauthservice:request:*") // FIXME: This works?
        }

        // Set global properties.
        jedisPub = pub
        jedisSub = sub
        redisListener = listener
    }
}
