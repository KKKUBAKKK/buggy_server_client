# Buggy Server Client

A client application for downloading data from unreliable buggy server downloaded from: [buggy_server](https://gist.github.com/vladimirlagunov/dcdf90bb19e9de306344d46f20920dce). This client implements chunked downloading with retry mechanisms to handle server glitches, timeouts, and other network issues.

## Features

- **Chunked Downloading**: Downloads large files in manageable 16MB chunks
- **Retry Logic**: Automatically retries failed downloads up to 5 times
- **Range Requests**: Uses HTTP range requests to resume downloads
- **Error Handling**: Comprehensive error handling for network issues
- **Logging**: Integrated Java Logging API
- **Checksum Verification**: Calculates SHA-256 hash of downloaded data

## How It Works

The client operates by:

1. Breaking the download into configurable chunks (default 16MB)
2. Using HTTP Range headers to request specific byte ranges
3. Implementing exponential backoff for retries when server errors occur
4. Logging progress and errors throughout the download process
5. Computing a SHA-256 hash for data verification

### Download Process

The client follows these steps:
1. Initiates connection to the server
2. Requests data in chunks using Range headers
3. Processes each chunk and appends to output buffer
4. Retries failed chunks up to a configurable maximum
5. Reports progress via logging
6. Calculates SHA-256 hash of completed download

## Usage

```kotlin
// Create a client instance with the server URL
val serverUrl = "http://127.0.0.1:8080"
val client = GlitchyServerClient(serverUrl)

// Download the data
val downloadedData = client.downloadData()

// Verify the download with SHA-256 hash
val hash = calculateSha256(downloadedData)
```

## Configuration

The client has several configurable constants in the `GlitchyServerClient` companion object:

```kotlin
const val CHUNK_MB = 16 * MB         // Size of each download chunk
const val MAX_RETRIES = 5            // Maximum retry attempts
const val RETRY_DELAY_MS = 500L      // Delay between retries
const val CONNECTION_TIMEOUT_MS = 5000  // Connection timeout
const val READ_TIMEOUT_MS = 5000     // Read timeout
```

## Project Structure

- `src/main/kotlin/`
    - `Main.kt` - Entry point with download execution and hash calculation
    - `GlitchyServerClient.kt` - Main client implementation with download logic

## Building and Running

This is a Gradle project that can be built and run with:

```bash
./gradlew build
./gradlew run
```

By default, the client tries to download from `http://127.0.0.1:8080`. Modify the URL in `Main.kt` to point to your server.