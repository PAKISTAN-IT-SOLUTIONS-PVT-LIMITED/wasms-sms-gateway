package net.wasms.smsgateway.data.remote.fcm

/**
 * FCM Service — STUBBED OUT.
 *
 * Firebase SDK has been removed from the build because placeholder
 * google-services.json credentials cause ContentProvider crashes on startup.
 *
 * When real Firebase credentials are configured:
 * 1. Uncomment firebase dependencies in build.gradle.kts
 * 2. Uncomment google-services plugin
 * 3. Restore this class to extend FirebaseMessagingService
 * 4. Uncomment the FCM service entry in AndroidManifest.xml
 * 5. Replace google-services.json with real credentials
 */
object WaSmsFcmService {
    const val ACTION_WAKEUP = "wakeup"
    const val ACTION_NEW_MESSAGES = "new_messages"
    const val ACTION_CONFIG_UPDATE = "config_update"
    const val ACTION_COMMAND = "command"
    const val ACTION_PAUSE = "pause"
    const val ACTION_RESUME = "resume"
}
