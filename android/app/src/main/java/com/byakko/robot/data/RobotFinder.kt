package com.byakko.robot.data

import android.content.Context
import android.net.ConnectivityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.URL

/**
 * Busca el robot en la red Wi-Fi actual (cuando el robot se unió a la red de la
 * casa/escuela en vez de crear la suya). Prueba todas las direcciones de la red
 * pidiendo /estado y se queda con la que responde "TEL ...".
 */
object RobotFinder {
    /** Todas las IPs de la red actual (usa la máscara real; máximo /22 = 1022 equipos). */
    fun candidates(context: Context): List<String> {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val props = cm.getLinkProperties(cm.activeNetwork) ?: return emptyList()
        val link = props.linkAddresses.firstOrNull { it.address is Inet4Address } ?: return emptyList()
        val b = link.address.address
        val ip = ((b[0].toLong() and 0xFF) shl 24) or ((b[1].toLong() and 0xFF) shl 16) or
            ((b[2].toLong() and 0xFF) shl 8) or (b[3].toLong() and 0xFF)
        val prefix = link.prefixLength.coerceIn(22, 30)
        val mask = (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
        val net = ip and mask
        val count = 1L shl (32 - prefix)
        return (1 until count - 1).map { net + it }.filter { it != ip }.map {
            "${(it shr 24) and 0xFF}.${(it shr 16) and 0xFF}.${(it shr 8) and 0xFF}.${it and 0xFF}"
        }
    }

    suspend fun scan(context: Context): String? = withContext(Dispatchers.IO) {
        val hosts = candidates(context)
        val limit = Semaphore(48)
        coroutineScope {
            hosts.map { host ->
                async { limit.withPermit { if (answersLikeRobot(host)) host else null } }
            }.awaitAll().firstOrNull { it != null }
        }
    }

    private fun answersLikeRobot(host: String): Boolean = runCatching {
        val conn = URL("http://$host/estado").openConnection() as HttpURLConnection
        conn.connectTimeout = 700
        conn.readTimeout = 900
        try {
            conn.inputStream.bufferedReader().readText().contains("TEL M=")
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(false)
}
