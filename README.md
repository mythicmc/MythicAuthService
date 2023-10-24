# MythicAuthService

Bukkit plugin which provides a Redis API to validate credentials with TELogin.

## API Documentation

MythicAuthService relies on Redis for IPC. Consumers simply publish a JSON request in the following format as a string:

```json
{
  "username": "<the player's username>",
  "password": "<the player's password, optional, omit if you only need to do a permission check>"
}
```

This request is published to `mythicauthservice:request:<perm>` (replace `<perm>` with the permission you need to check whether the user has).

MythicAuthService then responds on `mythicauthservice:response:<perm>` in the following format:

```json
{
  "request": "<the original request, to ensure you have the right response>",
  "authorised": true // or false, of course
}
```

For Node.js users, a library is available at [@mythicmc/auth](https://npmjs.com/package/@mythicmc/auth) to interface with this plugin.
