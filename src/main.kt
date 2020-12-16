import java.io.File
import java.lang.IllegalStateException
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.net.HttpURLConnection
import java.net.URL

import java.util.LinkedHashSet

class UploadToAppCenter {
    fun act(
        apiToken: String,
        ownerName: String,
        appName: String,
        apkFile: File,
        releaseNoteFile: File,
        destination: String
    ) {
        // HttpURLConnection doesn't support patch, have to use a weird hack
        allowMethods("PATCH")

        check(appName.isNotBlank()) {
            "$LOG_TAG AppName can not be empty"
        }

        val fileSize = apkFile.length()
        check(fileSize > 0) {
            "$LOG_TAG File size must be greater than 0"
        }
        println("$LOG_TAG want to upload $apkFile in group $appName)")

        // Call 1
        println("$LOG_TAG # Step 1 of 7")
        println("$LOG_TAG Creating upload")
        val createReleaseUploadResult = createReleaseUpload(
            apiToken = apiToken,
            ownerName = ownerName,
            appName = appName
        )
        println("$LOG_TAG Creating upload -> successful")
        if (DEBUG) {
            println("$LOG_TAG Creating upload -> response=$createReleaseUploadResult")
        }

        // Call 2
        println("\n$LOG_TAG # Step 2 of 7")
        println("$LOG_TAG] Setting meta data")
        val uploadDomain = requireNotNull(createReleaseUploadResult["upload_domain"])
        val packageAssetId = requireNotNull(createReleaseUploadResult["package_asset_id"])
        val urlEncodedToken = requireNotNull(createReleaseUploadResult["url_encoded_token"])
        val uploadId = requireNotNull(createReleaseUploadResult["id"])

        val metaData = setReleaseUploadMetadata(
            apiToken = apiToken,
            domain = uploadDomain,
            packageAssetId = packageAssetId,
            fileName = apkFile.name,
            fileSize = apkFile.length(),
            uploadToken = urlEncodedToken
        )
        println("$LOG_TAG Setting meta data -> successful")
        if (DEBUG) {
            println("$LOG_TAG Setting meta data -> response=$metaData")
        }

        // Call 3
        println("\n$LOG_TAG # Step 3 of 7")
        val chunkSize = requireNotNull(metaData["chunk_size"]).toInt()
        println("$LOG_TAG Uploading app (file_size=$fileSize | chunk_size=$chunkSize | expected=${fileSize / chunkSize})")
        uploadBuild(
            file = apkFile,
            apiToken = apiToken,
            domain = uploadDomain,
            packageAssetsId = packageAssetId,
            token = urlEncodedToken,
            chunkSize = chunkSize
        )
        println("$LOG_TAG Uploading app -> successful")

        // Call 4
        println("\n$LOG_TAG # Step 4 of 7")
        println("$LOG_TAG Finishing release upload")
        val finishReleaseUploadResult = finishReleaseUpload(
            domain = uploadDomain,
            packageAssetsId = packageAssetId,
            token = urlEncodedToken
        )
        println("$LOG_TAG Finishing release upload -> successful")
        if (DEBUG) {
            println("$LOG_TAG Finishing release upload -> response=$finishReleaseUploadResult")
        }

        // Call 5
        println("\n$LOG_TAG --- STEP 5 ---")
        println("$LOG_TAG Notify upload finished")
        val updateReleaseUploadResult = updateReleaseUpload(
            apiToken = apiToken,
            ownerName = ownerName,
            appName = appName,
            uploadId = uploadId,
            status = "uploadFinished"
        )
        println("$LOG_TAG Notify upload finished -> success")
        if (DEBUG) {
            println("$LOG_TAG Notify upload finished -> response=$updateReleaseUploadResult")
        }
        // Call 6
        println("\n$LOG_TAG --- STEP 6 ---")
        println("$LOG_TAG Waiting for release")
        val poolResult = pollForReleaseId(
            apiToken = apiToken,
            ownerName = ownerName,
            appName = appName,
            uploadId = uploadId
        )
        println("$LOG_TAG Waiting for release -> successful")
        if (DEBUG) {
            println("$LOG_TAG Waiting for release -> response=$$poolResult")
        }
        val releaseDistinctId = poolResult["release_distinct_id"]
        println("$LOG_TAG Waiting for release -> available at https://appcenter.ms/orgs/$ownerName/apps/$appName/distribute/releases/$releaseDistinctId")

        // Call 7
        println("\n$LOG_TAG --- STEP 7 ---")
        println("$LOG_TAG Distribute release")
        val distributeReleaseResult = distributeRelease(
            ownerName = ownerName,
            appName = appName,
            releaseId = "${poolResult["release_distinct_id"]}",
            destination = destination,
            releaseNotesFile = releaseNoteFile,
            apiToken = apiToken
        )
        println("$LOG_TAG Distribute release -> successful")
        if (DEBUG) {
            println("$LOG_TAG Distribute response=$distributeReleaseResult")
        }
        println("\n$LOG_TAG Build available at : https://install.appcenter.ms/orgs/$ownerName/apps/$appName/releases/$releaseDistinctId")
    }

    private fun createReleaseUpload(
        apiToken: String,
        ownerName: String,
        appName: String
    ): Map<String, String> {
        val url = "$PREFIX/$ownerName/$appName/uploads/releases"
        val httpClient = (URL(url).openConnection() as HttpURLConnection)
            .apply {
                requestMethod = "POST"
                addRequestProperty("Content-Type", "application/json")
                addRequestProperty("Accept", "application/json")
                addRequestProperty("X-API-Token", apiToken)
            }

        try {
            check(httpClient.responseCode == 201) {
                "$LOG_TAG createReleaseUpload > unwanted response}"
                "$LOG_TAG     code: ${httpClient.responseCode}"
                "$LOG_TAG     response :${httpClient.errorStream.bufferedReader().readText()}"
            }
            return httpClient.inputStream.bufferedReader().readText().asJsonMap()
        } finally {
            httpClient.disconnect()
        }
    }

    private fun setReleaseUploadMetadata(
        apiToken: String,
        domain: String,
        packageAssetId: String,
        fileName: String,
        fileSize: Long,
        uploadToken: String
    ): Map<String, String> {
        val url = "$domain/upload/set_metadata/$packageAssetId" +
                "?file_name=$fileName" +
                "&file_size=$fileSize" +
                "&token=$uploadToken" +
                "&content_type=application/vnd.android.package-archive"

        val body = """{}""".toByteArray()

        val httpClient = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true

            addRequestProperty("Content-Length", "${body.size}")
            addRequestProperty("Content-Type", "application/vnd.android.package-archive")
            addRequestProperty("Accept", "application/json")
            addRequestProperty("X-API-Token", apiToken)

            outputStream.write(body)
            outputStream.close()
        }
        try {
            check(httpClient.responseCode == 200) {
                "$LOG_TAG setReleaseUploadMetadata > unwanted response}"
                "$LOG_TAG     code: ${httpClient.responseCode}"
                "$LOG_TAG     response :${httpClient.errorStream.bufferedReader().readText()}"
            }
            return httpClient.inputStream.bufferedReader().readText().asJsonMap()
        } finally {
            httpClient.disconnect()
        }
    }

    private fun uploadBuild(
        file: File,
        apiToken: String,
        domain: String,
        packageAssetsId: String,
        token: String,
        chunkSize: Int
    ) {
        file.chunkedSequence(chunkSize).forEachIndexed { index, bytes ->
            val blockNumber = index + 1

            println("$LOG_TAG uploadBuild > chunk $blockNumber (size=${bytes.size})")

            val url = "$domain/upload/upload_chunk/$packageAssetsId" +
                    "?token=$token" +
                    "&block_number=$blockNumber"

            val httpClient = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true

                addRequestProperty("Content-Length", "${bytes.size}")
                addRequestProperty("Content-Type", "application/octet-stream")
                addRequestProperty("X-API-Token", apiToken)
            }
            try {
                httpClient.outputStream.use { it.write(bytes) }
                check(httpClient.responseCode == 200) {
                    "$LOG_TAG uploadBuild > unwanted response"
                    "$LOG_TAG     code: ${httpClient.responseCode}"
                    "$LOG_TAG     response :${httpClient.errorStream.bufferedReader().readText()}"
                }
            } finally {
                httpClient.disconnect()
            }
        }
    }

    private fun finishReleaseUpload(
        domain: String,
        packageAssetsId: String,
        token: String
    ): Map<String, String> {
        val url = "$domain/upload/finished/$packageAssetsId?token=$token"
        val body = """{}""".toByteArray()
        val httpClient = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true

            addRequestProperty("Content-Length", "${body.size}")
        }
        try {
            httpClient.outputStream.use { it.write(body) }
            check(httpClient.responseCode == 200) {
                "$LOG_TAG finishReleaseUpload > wrong response : ${httpClient.responseCode}"
                "$LOG_TAG     code: ${httpClient.responseCode}"
                "$LOG_TAG     response :${httpClient.errorStream.bufferedReader().readText()}"
            }
            return httpClient.inputStream.bufferedReader().readText().asJsonMap()
        } finally {
            httpClient.disconnect()
        }
    }

    private fun updateReleaseUpload(
        apiToken: String,
        ownerName: String,
        appName: String,
        uploadId: String,
        status: String
    ): Map<String, String> {
        val url = "$PREFIX/$ownerName/$appName/uploads/releases/$uploadId"
        val body = """{ "upload_status": "$status" }""".toByteArray()
        val httpClient = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "PATCH"
            doOutput = true

            addRequestProperty("Content-Type", "application/json")
            addRequestProperty("Accept", "application/json")
            addRequestProperty("X-API-Token", apiToken)
        }
        try {
            httpClient.outputStream.use { it.write(body) }
            check(httpClient.responseCode == 200) {
                "$LOG_TAG updateReleaseUpload > wrong response : ${httpClient.responseCode}"
                "$LOG_TAG     code: ${httpClient.responseCode}"
                "$LOG_TAG     response :${httpClient.errorStream.bufferedReader().readText()}"
            }
            return httpClient.inputStream.bufferedReader().readText().asJsonMap()
        } finally {
            httpClient.disconnect()
        }
    }

    private fun pollForReleaseId(
        apiToken: String,
        ownerName: String,
        appName: String,
        uploadId: String
    ): Map<String, String> {
        val url = "$PREFIX/$ownerName/$appName/uploads/releases/$uploadId"
        var attempt = 0L
        var canContinue = true

        while (canContinue) {

            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"

                addRequestProperty("X-API-Token", apiToken)
            }
            check(connection.responseCode == 200) {
                "$LOG_TAG updateReleaseUpload > wrong response : ${connection.responseCode}"
            }

            val response = connection.inputStream.bufferedReader().readText().asJsonMap()
            if (response.containsKey("release_distinct_id")) {
                return response
            }

            attempt++

            if (attempt > POLL_MAX_ATTEMPT) {
                canContinue = false
            } else {
                val waitingDelayMs = POLL_DELAY_MS * attempt
                println("$LOG_TAG pollForReleaseId > unavailable ($response), retrying in ${waitingDelayMs / 1000}s")
                Thread.sleep(waitingDelayMs)
            }
        }
        throw Exception("Max attempt reached, release unavailable, check later at $url")
    }

    private fun distributeRelease(
        ownerName: String,
        appName: String,
        releaseId: String,
        releaseNotesFile: File?,
        destination: String,
        apiToken: String
    ): Map<String, String> {
        val notes = releaseNotesFile?.readText() ?: ""
        val url = "$PREFIX/$ownerName/$appName/releases/$releaseId"

        val body = "{ \"destination_name\": \"$destination\", \"release_notes\": \"$notes\" }"
            .also {
                if (DEBUG) {
                    println("distributeRelease > sending body: $it")
                }
            }
            .toByteArray()

        val httpClient = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "PATCH"
            doOutput = true

            addRequestProperty("Content-Type", "application/json")
            addRequestProperty("Accept", "application/json")
            addRequestProperty("X-API-Token", apiToken)
        }
        try {
            httpClient.outputStream.use { it.write(body) }
            check(httpClient.responseCode == 200) {
                "$LOG_TAG distributeRelease > wrong response : ${httpClient.responseCode}"
                "$LOG_TAG     code: ${httpClient.responseCode}"
                "$LOG_TAG     response :${httpClient.errorStream.bufferedReader().readText()}"
            }
            return httpClient.inputStream.bufferedReader().readText().asJsonMap()
        } finally {
            httpClient.disconnect()
        }
    }

    companion object {
        private const val POLL_DELAY_MS = 2000
        private const val POLL_MAX_ATTEMPT = 5

        private const val LOG_TAG = "[UploadToAppCenter]"
        private const val PREFIX = "https://api.appcenter.ms/v0.1/apps"

        private fun String.asJsonMap(): Map<String, String> {
            return this
                .removePrefix("{")
                .removeSuffix("}")
                .split(",")
                .map {
                    with(it.split(":", limit = 2)) {
                        Pair(
                            get(0).removePrefix("\"").removeSuffix("\""),
                            get(1).removePrefix("\"").removeSuffix("\"")
                        )
                    }
                }
                .associateBy(
                    { it.first }, { it.second }
                )
        }

        private fun File.chunkedSequence(chunk: Int): Sequence<ByteArray> {
            val input = this.inputStream().buffered()
            val buffer = ByteArray(chunk)
            return generateSequence {
                val red = input.read(buffer)
                if (red >= 0) {
                    buffer.copyOf(red)
                } else {
                    input.close()
                    null
                }
            }
        }

        private fun allowMethods(vararg methods: String) {
            try {
                val methodsField: Field = HttpURLConnection::class.java.getDeclaredField("methods")
                val modifiersField: Field = Field::class.java.getDeclaredField("modifiers")
                modifiersField.isAccessible = true
                modifiersField.setInt(methodsField, methodsField.modifiers and Modifier.FINAL.inv())
                methodsField.isAccessible = true
                val oldMethods = methodsField.get(null) as Array<String>
                val methodsSet: MutableSet<String> = LinkedHashSet(listOf(*oldMethods))
                methodsSet.addAll(listOf(*methods))
                val newMethods = methodsSet.toTypedArray()
                methodsField.set(null /*static field*/, newMethods)
            } catch (e: NoSuchFieldException) {
                throw IllegalStateException(e)
            } catch (e: IllegalAccessException) {
                throw IllegalStateException(e)
            }
        }

    }
}

private fun Array<String>.extractArgs(key: String): String {
    val valueIdx = indexOf(key)
    val value = getOrNull(valueIdx + 1)

    if (valueIdx < 0 || value.isNullOrEmpty()) {
        throw IllegalStateException("missing value, please provide it with $key")
    }

    return value
}

fun main(args: Array<String>) {

    val apiToken = args.extractArgs("-apiToken")
    val ownerName = args.extractArgs("-ownerName")
    val appName = args.extractArgs("-appName")
    val apkFile = args.extractArgs("-file")
    val releaseNoteFile = args.extractArgs("-releaseNote")
    val destination = args.extractArgs("-destination")

    if (DEBUG) {
        println("apiToken=$apiToken ownerName=$ownerName appName=$appName apkFile=$apkFile releaseNote=$releaseNoteFile destination=$destination")
    }

    UploadToAppCenter().act(
        apiToken = apiToken,
        ownerName = ownerName,
        appName = appName,
        apkFile = File(apkFile),
        releaseNoteFile = File(releaseNoteFile),
        destination = destination
    )
}

private const val DEBUG = true
