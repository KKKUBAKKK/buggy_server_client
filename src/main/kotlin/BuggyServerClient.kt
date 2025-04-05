import java.io.File
import java.io.FileOutputStream
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

/**
 * Client for downloading data from an unreliable server with retry mechanisms.
 * Implements chunked downloading to handle large files efficiently.
 *
 * @property serverUrl The URL of the buggy server
 * @property chunkSize Size of each download chunk in bytes
 * @property maxRetries Maximum number of retry attempts for failed chunks
 * @property retryDelay Delay between retry attempts in milliseconds
 * @property logger Logger instance for tracking download progress and errors
 */
class BuggyServerClient(
    private val serverUrl: String,
) {
    private val chunkSize = CHUNK_MB
    private val maxRetries = MAX_RETRIES
    private val retryDelay = RETRY_DELAY_MS
    private val logger = Logger.getLogger(BuggyServerClient::class.java.name)

    /**
     * Companion object containing constants and initialization logic.
     * Configures logging and defines download parameters.
     */
    companion object {
        /** Size of a kilobyte in bytes */
        const val KB = 1024

        /** Size of a megabyte in bytes */
        const val MB = 1024 * KB

        /** Default chunk size for downloads (16MB) */
        const val CHUNK_MB = 16 * MB

        /** Maximum number of retry attempts for failed downloads */
        const val MAX_RETRIES = 5

        /** Delay between retry attempts in milliseconds */
        const val RETRY_DELAY_MS = 500L

        /** Connection timeout in milliseconds */
        const val CONNECTION_TIMEOUT_MS = 5000

        /** Read timeout in milliseconds */
        const val READ_TIMEOUT_MS = 5000

        /**
         * Initializes logging configuration.
         * Sets up console handler with simple formatter and INFO level.
         */
        init {
            LogManager.getLogManager().reset()
            val rootLogger = Logger.getLogger("")
            val handler = ConsoleHandler()
            handler.formatter = SimpleFormatter()
            rootLogger.addHandler(handler)
            rootLogger.level = Level.INFO
        }
    }

    /**
     * Downloads data from the server and saves it to a file with the given name.
     *
     * @param fileName Name of the file to save the downloaded data
     * @return Number of bytes downloaded
     */
    fun downloadData(fileName: String): Int {
        val file = File(fileName)
        return downloadData(file)
    }

    /**
     * Downloads data from the server and saves it to the specified file.
     * Calculates and logs the SHA-256 hash of the downloaded data.
     *
     * @param file File to save the downloaded data
     * @return Number of bytes downloaded
     */
    fun downloadData(file: File): Int {
        val output = FileOutputStream(file)
        var length = 0
        output.use {
            length = downloadData(output)
        }

        if (length > 0) {
            logger.info("Download complete, $length bytes received")
            logger.info("SHA-256 hash: ${calculateSha256(file)}")
            logger.info("Check if this hash matches the one displayed by the server")
        } else {
            logger.warning("Download failed, no data received")
        }

        return length
    }

    /**
     * Downloads data from the server to the provided output stream.
     * Implements chunked downloading with retry logic for reliability.
     *
     * @param output FileOutputStream to write the downloaded data
     * @return Number of bytes downloaded
     */
    private fun downloadData(output: FileOutputStream): Int {
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
                val positionInMB = position / MB

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

        return position
    }

    /**
     * Downloads a specific chunk of data from the server.
     * Uses HTTP Range headers to request byte ranges.
     * Implements retry logic for handling server errors.
     *
     * @param start Starting byte position
     * @param end Ending byte position
     * @return ByteArray containing the downloaded chunk, or empty array if failed
     */
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

    /**
     * Creates and configures an HTTP connection to the server.
     * Sets connection and read timeouts.
     *
     * @return Configured HttpURLConnection instance
     * @throws IOException If connection creation fails
     */
    private fun createConnection(): HttpURLConnection {
        val connection = URI(serverUrl).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECTION_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        return connection
    }

    /**
     * Calculates SHA-256 hash for a file specified by name.
     *
     * @param fileName Name of the file to hash
     * @return Hexadecimal string representation of the SHA-256 hash
     */
    private fun calculateSha256(fileName: String): String {
        val file = File(fileName)
        return calculateSha256(file)
    }

    /**
     * Calculates SHA-256 hash for a file.
     * Streams the file in chunks to avoid loading the entire file into memory.
     *
     * @param file File to calculate hash for
     * @return Hexadecimal string representation of the SHA-256 hash
     */
    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8 * KB)
            var bytesRead = input.read(buffer)
            while (bytesRead != -1) {
                digest.update(buffer, 0, bytesRead)
                bytesRead = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
