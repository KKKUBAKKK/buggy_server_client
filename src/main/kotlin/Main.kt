fun main() {
    val serverUrl = "http://127.0.0.1:8080"
    val client = BuggyServerClient(serverUrl)
    val fileName = "downloaded_file.bin"

    client.downloadData(fileName)
}
