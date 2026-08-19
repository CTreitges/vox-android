package com.chris.whisperbar.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * JVM-Unit-Tests fuer die Wiederholbarkeit von Fehlern. Falsch eingestuft heisst:
 * entweder haengt der Knopf dauerhaft im Fehlerzustand (bei echtem Konfigurationsfehler)
 * oder ein langes Diktat geht bei einem kurzen Netzhaenger verloren.
 */
class ApiErrorsTest {

    @Test fun netzfehlerDarfWiederholtWerden() {
        assertTrue(ApiNetworkException(IOException("timeout")).isRetryable())
    }

    @Test fun serverfehlerDarfWiederholtWerden() {
        assertTrue(ApiHttpException(500, "boom").isRetryable())
        assertTrue(ApiHttpException(503, "unavailable").isRetryable())
        assertTrue(ApiHttpException(429, "rate limit").isRetryable())
        assertTrue(ApiHttpException(408, "timeout").isRetryable())
    }

    @Test fun falscherKeyBleibtFalsch() {
        assertFalse(ApiHttpException(401, "invalid api key").isRetryable())
        assertFalse(ApiHttpException(403, "forbidden").isRetryable())
    }

    @Test fun unbekanntesModellBleibtUnbekannt() {
        assertFalse(ApiHttpException(404, "model not found").isRetryable())
        assertFalse(ApiHttpException(400, "bad request").isRetryable())
    }

    @Test fun fehlenderKeyIstKeinWiederholungsfall() {
        assertFalse(ApiNotConfiguredException().isRetryable())
    }
}
