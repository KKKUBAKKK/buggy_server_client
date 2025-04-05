import java.security.MessageDigest
import java.util.logging.Logger

fun main() {
    val logger = Logger.getLogger("Main")

    val serverUrl = "http://127.0.0.1:8080"
    val client = BuggyServerClient(serverUrl)

    val downloadedData = client.downloadData()

    if (downloadedData.isEmpty()) {
        return
    }

    logger.info("SHA-256 hash: ${calculateSha256(downloadedData)}")
    logger.info("Check if this hash matches the one displayed by the server")
}

fun calculateSha256(data: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest(data)
    return hashBytes.joinToString("") { "%02x".format(it) }
}
