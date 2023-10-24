package org.mythicmc.mythicauthservice

import com.google.gson.Gson
import io.github.fridgey.telogin.TELoginPlugin
import io.github.fridgey.telogin.password.PasswordManager
import net.luckperms.api.LuckPermsProvider
import net.luckperms.api.util.Tristate
import org.bukkit.configuration.file.YamlConfiguration
import redis.clients.jedis.JedisPubSub
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.logging.Level

class RedisListener(private val plugin: MythicAuthService) : JedisPubSub() {
    private val gson = Gson()

    private fun respond(request: String, permission: String, authorised: Boolean) =
        MythicAuthService.messageWriteQueue?.add(Pair(
            "mythicauthservice:response:$permission",
            gson.toJson(AuthResponse(request, authorised))))
            ?: plugin.logger.warning("Dropped response to request $request since channel closed!")

    private fun getUUIDFromUsername(username: String) =
        UUID.nameUUIDFromBytes("OfflinePlayer:$username".toByteArray(StandardCharsets.UTF_8))

    private fun checkPermission(username: String, permission: String): CompletableFuture<Tristate> {
        val user = LuckPermsProvider.get().userManager.getUser(username)
        return if (user == null) {
            LuckPermsProvider.get().userManager
                .loadUser(getUUIDFromUsername(username), username)
                .thenApply { it.cachedData.permissionData.checkPermission(permission) }
        } else CompletableFuture.completedFuture(
            user.cachedData.permissionData.checkPermission(permission))
    }

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
        checkPermission(username, permission).handle { value, err ->
            if (err != null) {
                plugin.logger.log(Level.SEVERE, "Error occurred when checking LuckPerms!", err)
                respond(message, permission, false)
                return@handle
            }
            // If the user doesn't have permission, respond with false.
            if (value != Tristate.TRUE) {
                respond(message, permission, false)
                return@handle
            }
            // If password is null, then respond with true (as the user's authority is being queried).
            // If it's empty, then put some cautionary security in case there is no client validation.
            if (password.isNullOrEmpty()) {
                respond(message, permission, password == null)
                return@handle
            }
            // Check if password is correct, and respond in callback.
            TELoginPlugin.getInstance().dbManager.getPassword(username) {
                respond(message, permission, PasswordManager.checkPassword(username, password, it))
            }
        }
    }
}
