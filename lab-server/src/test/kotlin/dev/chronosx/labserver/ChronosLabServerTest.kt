package dev.chronosx.labserver

import java.net.HttpURLConnection
import java.net.URL
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChronosLabServerTest {
    @Test
    fun `expired fixture returns a controlled expiry response`() {
        ChronosLabServer.create(port = 0).use { server ->
            server.start()
            val connection = URL("http://127.0.0.1:${server.port}/v1/time-policy?fixture=expired")
                .openConnection() as HttpURLConnection

            assertEquals(401, connection.responseCode)
            assertTrue(connection.errorStream.bufferedReader().readText().contains("expired"))
        }
    }

    @Test
    fun `health endpoint is available on loopback`() {
        ChronosLabServer.create(port = 0).use { server ->
            server.start()
            val connection = URL("http://127.0.0.1:${server.port}/health")
                .openConnection() as HttpURLConnection

            assertEquals(200, connection.responseCode)
            assertTrue(connection.inputStream.bufferedReader().readText().contains("chronosx-lab"))
        }
    }

    @Test
    fun `explicit fixture kind lets a custom scenario control owned mock behavior`() {
        ChronosLabServer.create(port = 0).use { server ->
            server.start()
            val connection = URL(
                "http://127.0.0.1:${server.port}/v1/time-policy?fixture=quarter-end&kind=STALE",
            ).openConnection() as HttpURLConnection

            assertEquals(200, connection.responseCode)
            assertTrue(connection.inputStream.bufferedReader().readText().contains("stale"))
        }
    }
}
