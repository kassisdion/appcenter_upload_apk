import java.io.File
import java.lang.IllegalStateException
import java.net.HttpURLConnection
import java.net.URL

class UploadToAppCenter {

    fun act(
        apiToken: String,
        ownerName: String,
        appName: String,
        apkFile: File
    ) {
        check(appName.isNotBlank()) {
            "$LOG_TAG AppName can not be empty"
        }

        val fileSize = apkFile.length()
        check(fileSize > 0) {
            "$LOG_TAG File size must be greater than 0"
        }
        println("$LOG_TAG want to upload $apkFile in group $appName)")

        // Call 1
        println("$LOG_TAG --- STEP 1 ---")
        println("$LOG_TAG Creating upload")
        val createReleaseUploadResult = createReleaseUpload(
            apiToken = apiToken,
            ownerName = ownerName,
            appName = appName
        )
        println("$LOG_TAG Creating upload -> successful")

        // Call 2
        println("\n$LOG_TAG --- STEP 2 ---")
        println("$LOG_TAG] Setting meta data")
        val uploadDomain = requireNotNull(createReleaseUploadResult["upload_domain"])
        val packageAssetId = requireNotNull(createReleaseUploadResult["package_asset_id"])
        val urlEncodedToken = requireNotNull(createReleaseUploadResult["url_encoded_token"])

        val metaData = setReleaseUploadMetadata(
            apiToken = apiToken,
            domain = uploadDomain,
            packageAssetId = packageAssetId,
            fileName = apkFile.name,
            fileSize = apkFile.length(),
            uploadToken = urlEncodedToken
        )
        println("$LOG_TAG Setting meta data -> successful : $metaData")
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
    ): Map<String, Any> {
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

    companion object {
        const val LOG_TAG = "[UploadToAppCenter]"
        const val PREFIX = "https://api.appcenter.ms/v0.1/apps"
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

    UploadToAppCenter().act(
        apiToken = apiToken,
        ownerName = ownerName,
        appName = appName,
        apkFile = File(apkFile)
    )
}