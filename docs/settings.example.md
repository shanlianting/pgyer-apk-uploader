# Example settings (do not commit real secrets)

Configure credentials in the IDE — **Settings → Tools → Pgyer APK Uploader**.

| Setting | Example value | Notes |
| --- | --- | --- |
| API Key | `xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx` | From [Pgyer API info](https://www.pgyer.com/account/api). Stored in IDE PasswordSafe. |
| Default install type | `Public (1)` | `1` public / `2` password / `3` invite |
| Default install password | _(empty)_ | Only needed when install type is Password |
| Default update description | `CI build from Android Studio` | Maps to `buildUpdateDescription` |

Pgyer API 2.0 upload uses `_api_key` only. Legacy **User Key** is not required for this plugin.

Never put real API keys in `gradle.properties`, source files, or git commits.
