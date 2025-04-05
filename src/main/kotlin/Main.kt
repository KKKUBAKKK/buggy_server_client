import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.security.MessageDigest
import java.util.logging.ConsoleHandler
import java.util.logging.Level
import java.util.logging.LogManager
import java.util.logging.Logger
import java.util.logging.SimpleFormatter

fun main() {
    val logger = Logger.getLogger("Main")

    val serverUrl = "http://127.0.0.1:8080"
    val client = GlitchyServerClient(serverUrl)

    val downloadedData = client.downloadData()

    if (downloadedData.isEmpty()) {
        return
    }

    logger.info("SHA-256 hash: ${calculateSha256(downloadedData)}")
    logger.info("Check if this hash matches the one displayed by the server")
}

class GlitchyServerClient(
    private val serverUrl: String,
) {
    private val chunkSize = CHUNK_MB
    private val maxRetries = MAX_RETRIES
    private val retryDelay = RETRY_DELAY_MS
    private val logger = Logger.getLogger(GlitchyServerClient::class.java.name)

    companion object {
        const val KB = 1024
        const val MB = 1024 * KB
        const val CHUNK_MB = 16 * MB
        const val MAX_RETRIES = 5
        const val RETRY_DELAY_MS = 500L
        const val CONNECTION_TIMEOUT_MS = 5000
        const val READ_TIMEOUT_MS = 5000

        init {
            LogManager.getLogManager().reset()
            val rootLogger = Logger.getLogger("")
            val handler = ConsoleHandler()
            handler.formatter = SimpleFormatter()
            rootLogger.addHandler(handler)
            rootLogger.level = Level.INFO
        }
    }

    fun downloadData(): ByteArray {
        val output = ByteArrayOutputStream()
        var position = 0
        var chunkCount = 0
        logger.fine("Starting download...")

        while (true) {
            try {
                val endPosition = position + chunkSize - 1
                logger.fine("Requesting chunk: bytes=$position-$endPosition")

                val chunk = downloadChunk(position, endPosition)

                if (chunk.isEmpty()) {
                    logger.info("Download complete")
                    break
                }

                output.write(chunk)
                position += chunk.size
                chunkCount++

                val positionInKB = position / KB
                val positionInMB = positionInKB / MB

                if (positionInMB > 0) {
                    logger.info("Downloaded $chunkCount chunks → $positionInMB MB, $positionInKB KB")
                } else {
                    logger.info("Downloaded $chunkCount chunks → $positionInKB KB")
                }
            } catch (e: IOException) {
                logger.log(Level.WARNING, "Network error during download", e)
                logger.info("Retrying in ${retryDelay}ms...")
                Thread.sleep(retryDelay)
            } catch (e: InterruptedException) {
                println("Download was interrupted: ${e.message}")
                break
            } catch (e: IllegalStateException) {
                logger.log(Level.SEVERE, "Client state error", e)
                logger.info("Retrying in ${retryDelay}ms...")
                Thread.sleep(retryDelay)
            }
        }

        logger.info("Download finished, total size: ${output.size()} bytes")
        return output.toByteArray()
    }

    private fun downloadChunk(
        start: Int,
        end: Int,
    ): ByteArray {
        var attempts = 0
        while (attempts < maxRetries) {
            val connection = createConnection()
            try {
                connection.setRequestProperty("Range", "bytes=$start-$end")
                val responseCode = connection.responseCode
                val responseMessage = connection.responseMessage

                if (responseCode != HttpURLConnection.HTTP_OK &&
                    responseCode != HttpURLConnection.HTTP_PARTIAL
                ) {
                    logger.warning("Error: $responseCode-$responseMessage")
                    attempts++
                    continue
                }

                val chunk = connection.inputStream.readBytes()
                if (chunk.isNotEmpty()) {
                    logger.fine("Downloaded ${chunk.size} bytes")
                    return chunk
                } else {
                    logger.warning("Got empty response, retrying...")
                    Thread.sleep(retryDelay)
                    attempts++
                }
            } catch (e: IOException) {
                logger.log(Level.WARNING, "Network error (attempt ${attempts + 1}/$maxRetries)", e)
                Thread.sleep(retryDelay)
                attempts++
            } catch (e: SocketTimeoutException) {
                logger.log(Level.WARNING, "Connection timed out (attempt ${attempts + 1}/$maxRetries)", e)
                Thread.sleep(retryDelay)
                attempts++
            } finally {
                connection.disconnect()
            }
        }
        logger.severe("Failed to download chunk after $maxRetries attempts")
        return ByteArray(0)
    }

    private fun createConnection(): HttpURLConnection {
        val connection = URI(serverUrl).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECTION_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        return connection
    }
}

fun calculateSha256(data: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest(data)
    return hashBytes.joinToString("") { "%02x".format(it) }
}
