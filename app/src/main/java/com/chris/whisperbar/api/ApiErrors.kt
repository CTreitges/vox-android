package com.chris.whisperbar.api

import java.io.IOException

/** Kein API-Key hinterlegt — die App kann ohne ihn nicht transkribieren. */
class ApiNotConfiguredException : RuntimeException("Kein API-Key hinterlegt")

/** Der Server hat mit einem Fehlerstatus geantwortet. */
class ApiHttpException(val code: Int, val detail: String) :
    RuntimeException("API-Fehler $code: $detail")

/** Die Anfrage kam gar nicht durch (kein Netz, Timeout, DNS …). */
class ApiNetworkException(cause: IOException) :
    RuntimeException(cause.message ?: "Netzwerkfehler", cause)

/**
 * Ob ein erneuter Versuch ueberhaupt Sinn hat. Netzprobleme und serverseitige
 * Aussetzer sind voruebergehend; ein falscher Key oder ein unbekanntes Modell
 * bleiben auch beim zehnten Versuch falsch.
 */
fun Throwable.isRetryable(): Boolean = when (this) {
    is ApiNetworkException -> true
    is ApiHttpException -> code == 408 || code == 429 || code >= 500
    else -> false
}
