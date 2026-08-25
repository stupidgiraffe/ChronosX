package dev.chronosx.labserver

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.chronosx.core.FixtureResponseKind
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService

/**
 * Loopback-only fixture server for customer-owned apps, mocks, and staging test flows.
 *
 * It is deliberately not a proxy and never receives or alters arbitrary third-party traffic.
 */
class ChronosLabServer private constructor(
    private val server: HttpServer,
    private val executor: ExecutorService,
) : AutoCloseable {
    val port: Int get() = server.address.port

    fun start() = server.start()

    override fun close() {
        server.stop(0)
        executor.shutdownNow()
    }

    companion object {
        fun create(port: Int = DEFAULT_PORT): ChronosLabServer {
            val server = HttpServer.create(
                InetSocketAddress(InetAddress.getLoopbackAddress(), port),
                0,
            )
            val executor = Executors.newCachedThreadPool()
            server.executor = executor
            server.createContext("/health") { exchange ->
                exchange.respond(200, "application/json", "{\"service\":\"chronosx-lab\",\"status\":\"ok\"}")
            }
            server.createContext("/v1/time-policy") { exchange ->
                exchange.respondFixture(FixtureCatalog.resolve(exchange.requestURI))
            }
            return ChronosLabServer(server, executor)
        }

        const val DEFAULT_PORT = 8787
    }
}

private object FixtureCatalog {
    fun resolve(uri: URI): FixtureResponse {
        val query = uri.queryParameters()
        val requested = query["fixture"] ?: "valid"
        val delay = query["delayMillis"]?.toLongOrNull()?.coerceIn(0L, MAX_DELAY_MILLIS) ?: 0L
        val kind = query["kind"]?.toFixtureResponseKindOrNull() ?: when (requested.lowercase()) {
            "valid" -> FixtureResponseKind.VALID
            "expired" -> FixtureResponseKind.EXPIRED
            "stale" -> FixtureResponseKind.STALE
            "denied" -> FixtureResponseKind.DENIED
            "retryable", "retryable_failure" -> FixtureResponseKind.RETRYABLE_FAILURE
            "malformed", "malformed_contract" -> FixtureResponseKind.MALFORMED_CONTRACT
            else -> FixtureResponseKind.DENIED
        }
        return FixtureResponse.from(kind, delay, requested)
    }

    private const val MAX_DELAY_MILLIS = 30_000L
}

private fun String.toFixtureResponseKindOrNull(): FixtureResponseKind? =
    runCatching { FixtureResponseKind.valueOf(uppercase()) }.getOrNull()

private data class FixtureResponse(
    val status: Int,
    val contentType: String,
    val body: String,
    val delayMillis: Long,
) {
    companion object {
        fun from(kind: FixtureResponseKind, delayMillis: Long, requestedId: String): FixtureResponse = when (kind) {
            FixtureResponseKind.VALID -> FixtureResponse(
                200,
                "application/json",
                "{\"fixture\":\"$requestedId\",\"decision\":\"valid\",\"serverTimePolicy\":\"accept\"}",
                delayMillis,
            )
            FixtureResponseKind.EXPIRED -> FixtureResponse(
                401,
                "application/json",
                "{\"fixture\":\"$requestedId\",\"decision\":\"expired\",\"serverTimePolicy\":\"reauthenticate\"}",
                delayMillis,
            )
            FixtureResponseKind.STALE -> FixtureResponse(
                200,
                "application/json",
                "{\"fixture\":\"$requestedId\",\"decision\":\"stale\",\"serverTimePolicy\":\"refresh\"}",
                delayMillis,
            )
            FixtureResponseKind.DENIED -> FixtureResponse(
                403,
                "application/json",
                "{\"fixture\":\"$requestedId\",\"decision\":\"denied\",\"serverTimePolicy\":\"reject\"}",
                delayMillis,
            )
            FixtureResponseKind.RETRYABLE_FAILURE -> FixtureResponse(
                503,
                "application/json",
                "{\"fixture\":\"$requestedId\",\"decision\":\"retry\",\"retryAfterSeconds\":1}",
                delayMillis,
            )
            FixtureResponseKind.MALFORMED_CONTRACT -> FixtureResponse(
                200,
                "application/json",
                "{\"fixture\":\"$requestedId\",\"decision\":",
                delayMillis,
            )
        }
    }
}

private fun HttpExchange.respondFixture(response: FixtureResponse) {
    if (requestMethod != "GET") {
        respond(405, "application/json", "{\"error\":\"GET required\"}")
        return
    }
    if (response.delayMillis > 0L) Thread.sleep(response.delayMillis)
    respond(response.status, response.contentType, response.body)
}

private fun HttpExchange.respond(status: Int, contentType: String, body: String) {
    val bytes = body.toByteArray(StandardCharsets.UTF_8)
    responseHeaders.add("Content-Type", "$contentType; charset=utf-8")
    responseHeaders.add("Cache-Control", "no-store")
    sendResponseHeaders(status, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
}

private fun URI.queryParameters(): Map<String, String> = rawQuery
    ?.split('&')
    ?.mapNotNull { item ->
        val index = item.indexOf('=')
        if (index <= 0) null else item.substring(0, index) to item.substring(index + 1)
    }
    ?.toMap()
    .orEmpty()

fun main(args: Array<String>) {
    val port = args.firstOrNull()?.toIntOrNull() ?: ChronosLabServer.DEFAULT_PORT
    ChronosLabServer.create(port).also { server ->
        server.start()
        println("ChronosX Lab server listening on http://127.0.0.1:${server.port}")
    }
}
