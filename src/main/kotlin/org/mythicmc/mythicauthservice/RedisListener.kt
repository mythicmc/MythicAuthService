package org.mythicmc.mythicauthservice

import com.google.gson.Gson
import io.github.fridgey.telogin.TELoginPlugin
import io.github.fridgey.telogin.password.PasswordManager
import net.luckperms.api.LuckPermsProvider
import net.luckperms.api.util.Tristate
import org.bukkit.configuration.file.YamlConfiguration
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPubSub
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.logging.Level

class RedisListener(private val pub: Jedis, private val plugin: MythicAuthService) : JedisPubSub() {
    private val gson = Gson()

    private fun respond(request: String, permission: String, authorised: Boolean) = pub.publish(
        "mythicauthservice:response:$permission",
        gson.toJson(AuthResponse(request, authorised)))

    private fun getUUIDFromUsername(username: String) =
        UUID.nameUUIDFromBytes("OfflinePlayer:$username".toByteArray(StandardCharsets.UTF_8))

    override fun onPMessage(pattern: String?, channel: String?, message: String?) {
        val permission = channel?.substring("mythicauthservice:request:".length)
        if (message.isNullOrEmpty()) {
            return plugin.logger.warning("Received empty malformed message over Redis $channel")
        } else if (permission.isNullOrEmpty()) {
            return plugin.logger.warning("Received request over Redis with no permission specified")
        }
        val json: YamlConfiguration
        try {
            json = YamlConfiguration.loadConfiguration(StringReader(message))
        } catch (e: Exception) {
            return plugin.logger.warning("Received malformed message over Redis $channel: $message")
        }
        // Validate request.
        val username = json.getString("username")
        val password = json.getString("password")
        if (username.isNullOrEmpty()) {
            return plugin.logger.warning("Received malformed message over Redis $channel: $message")
        }

        // Check if username has permission.
        try {
            // TODO: Asynchronous in future? pub isn't async-safe though.
            val user = LuckPermsProvider.get().userManager.getUser(username)
                ?: LuckPermsProvider.get().userManager
                    .loadUser(getUUIDFromUsername(username), username)
                    .get()
            val permissionData = user.cachedData.permissionData
            // If the user doesn't have permission, respond with false.
            if (permissionData.checkPermission("mythicpanel.use") != Tristate.TRUE) {
                respond(message, permission, false)
                return
            }
        } catch (e: Exception) {
            respond(message, permission, false)
            return plugin.logger.log(Level.SEVERE, "Error occurred when checking LuckPerms!", e)
        }
        // If password is null, then respond with true (as the user's authority is being queried).
        // If it's empty, then put some cautionary security in case there is no client validation.
        if (password.isNullOrEmpty()) {
            respond(message, permission, password == null)
            return
        }
        // Check if password is correct, and respond in callback.
        TELoginPlugin.getInstance().dbManager.getPassword(username) {
            respond(message, permission, PasswordManager.checkPassword(username, password, it))
        }
    }
}
