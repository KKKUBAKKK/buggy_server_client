import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

fun main() {
    val serverUrl = "http://127.0.0.1:8080"
    val client = GlitchyServerClient(serverUrl)

    println("Starting download from glitchy server...")
    val downloadedData = client.downloadData()

    if (downloadedData.isEmpty()) {
        println("Download failed or no data received.")
        return
    }

    println("\nDownload complete")
    println("Downloaded data size: ${downloadedData.size} bytes -> ${downloadedData.size / 1024} KB")
    println("SHA-256 hash: ${calculateSha256(downloadedData)}")
    println("Check if this hash matches the one displayed by the server.")
}

// TODO: Update to use bigger chunks
// TODO: Update logging
class GlitchyServerClient(
    private val serverUrl: String,
) {
    private val chunkSize = CHUNK_KB * KB
    private val maxRetries = MAX_RETRIES
    private val retryDelay = RETRY_DELAY_MS

    companion object {
        const val KB = 1024
        const val MB = 1024 * KB
        const val CHUNK_KB = 64
        const val CHUNK_MB = 16 * MB
        const val MAX_RETRIES = 5
        const val RETRY_DELAY_MS = 500L
    }

    fun downloadData(): ByteArray {
        val output = ByteArrayOutputStream()
        var position = 0
        var lastProgressPercent = -1

        while (true) {
            try {
                val endPosition = position + chunkSize - 1
                val chunk = downloadChunk(position, endPosition)

                if (chunk.isEmpty()) {
                    break
                }

                output.write(chunk)
                position += chunk.size
                println("Downloaded ${(position + chunkSize) / chunkSize} chunks ($position KB)")
            } catch (e: Exception) {
                println("\nError during download: ${e.message}")
                println("Retrying in ${retryDelay}ms...")
                Thread.sleep(retryDelay)
            }
        }

        return output.toByteArray()
    }

    private fun parseContentLength(connection: HttpURLConnection): Int {
        try {
            val contentRange = connection.getHeaderField("Content-Range")
            if (contentRange != null && contentRange.contains("/")) {
                val totalSize = contentRange.substring(contentRange.lastIndexOf("/") + 1)
                if (totalSize != "*") {
                    return totalSize.toInt()
                }
            }
            return connection.contentLength
        } catch (e: Exception) {
            return -1
        }
    }

    // TODO: Update logging exceptions etc.
    // TODO: Update exceptions handling
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
                    println("Error: $responseCode\\-$responseMessage")
                    return ByteArray(0)
                }

                val chunk = connection.inputStream.readBytes()
                if (chunk.isNotEmpty()) {
                    return chunk
                } else {
                    println("Got empty response, retrying...")
                    Thread.sleep(retryDelay)
                    attempts++
                }
            } catch (e: Exception) {
                println("Exception: ${e.message}, retrying...")
                Thread.sleep(retryDelay)
                attempts++
            } finally {
                connection.disconnect()
            }
        }
        return ByteArray(0)
    }

    private fun createConnection(): HttpURLConnection {
        val connection = URL(serverUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        return connection
    }
}

fun calculateSha256(data: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest(data)
    return hashBytes.joinToString("") { "%02x".format(it) }
}
