package org.mythicmc.mythicauthservice

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.plugin.java.JavaPlugin
import redis.clients.jedis.JedisPool
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import java.util.logging.Level

class MythicAuthService : JavaPlugin() {
    companion object {
        lateinit var plugin: MythicAuthService
            private set
    }

    private lateinit var jedisPool: JedisPool
    private lateinit var redisListener: RedisListener
    val writeQueue = LinkedBlockingDeque<Pair<String, String>>()

    override fun onLoad() {
        plugin = this
    }

    override fun onEnable() {
        saveDefaultConfig()
        reloadConfig()
        initialiseRedis()

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
        redisListener.punsubscribe()
        jedisPool.close()
    }

    private fun reload() {
        saveDefaultConfig()
        reloadConfig()
        closeRedis()
        initialiseRedis()
    }

    private fun initialiseRedis() {
        val pool = JedisPool(config.getString("redis") ?: "redis://localhost:6379")
        jedisPool = pool
        val listener = RedisListener(this)
        redisListener = listener

        // Create read actor.
        server.scheduler.runTaskAsynchronously(this) { _ ->
            while (!pool.isClosed) {
                try {
                    pool.resource.use { it.psubscribe(listener, "mythicauthservice:request:*") }
                } catch (e: Exception) {
                    logger.log(Level.SEVERE,
                        "Exception reading from Redis! Re-subscribing in 5s...", e)
                    Thread.sleep(5000L)
                }
            }
        }

        // Create write actor.
        server.scheduler.runTaskAsynchronously(this) { _ ->
            while (!pool.isClosed) {
                val message = writeQueue.poll(1, TimeUnit.SECONDS) ?: continue
                if (pool.isClosed) // Re-send with new actor at first priority.
                    return@runTaskAsynchronously writeQueue.addFirst(message)

                try {
                    pool.resource.use { it.publish(message.first, message.second) }
                } catch (e: Exception) {
                    logger.log(Level.SEVERE,
                        "Exception sending message over Redis! Re-sending in 5s...", e)
                    writeQueue.addFirst(message)
                    Thread.sleep(5000L)
                }
            }
        }
    }
}
