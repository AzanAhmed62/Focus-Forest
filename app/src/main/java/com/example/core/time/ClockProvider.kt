package com.example.core.time

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

interface ClockProvider {
    fun nowWallClockMs(): Long
    fun elapsedRealtimeMs(): Long
    fun bootCount(): Int
    fun zoneId(): ZoneId
    fun todayLocalDate(): LocalDate = epochMillisToLocalDate(nowWallClockMs())
    fun epochMillisToLocalDate(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zoneId()).toLocalDate()
}

class SystemClockProvider(private val context: Context) : ClockProvider {
    override fun nowWallClockMs(): Long = System.currentTimeMillis()

    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()

    override fun bootCount(): Int = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
    } catch (_: Exception) {
        0
    }

    override fun zoneId(): ZoneId = ZoneId.systemDefault()
}
