package yemoja.logic

import java.net.HttpURLConnection
import java.net.URI

/**
 * The JDK's own connection, which a JVM and Android both have, so no library is taken for this.
 *
 * A status the service answers with is returned with its body, the error stream being the body
 * there; a connection that cannot be made throws, which is what tells a calculator it is offline.
 */
actual fun postJson(url: String, body: String): Posted {
    val connection = URI(url).toURL().openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "POST"
        connection.connectTimeout = CONNECT_MILLIS
        connection.readTimeout = READ_MILLIS
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")
        connection.outputStream.use { it.write(body.toByteArray()) }
        val status = connection.responseCode
        val stream = if (status < HttpURLConnection.HTTP_BAD_REQUEST) connection.inputStream else connection.errorStream
        val answer = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        return Posted(status, answer)
    } finally {
        connection.disconnect()
    }
}

/** How long to wait for the service to answer the knock, in milliseconds. */
private const val CONNECT_MILLIS = 10_000

/** How long to wait for a day's readings once connected, in milliseconds. */
private const val READ_MILLIS = 30_000
