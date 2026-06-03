package org.wit.vitasense.util

import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object DateUtils {
    private val zoneId: ZoneId = ZoneId.systemDefault()
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun parseOffsetDateTime(raw: String): Long = OffsetDateTime.parse(raw).toInstant().toEpochMilli()

    fun formatDate(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate().format(dateFormatter)

    fun todayString(): String = formatDate(System.currentTimeMillis())

    fun parseDate(date: String): LocalDate = LocalDate.parse(date, dateFormatter)

    fun checksum(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}
