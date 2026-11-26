package com.denser.june.core.utils

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.TimeZone

/**
 * Single source of truth for the app's time zone.
 *
 * Everything the app reads or writes as a *wall-clock* time (entry timestamps, calendar
 * grids, daily rollover, reminders, Markdown export headers, importers that carry naive
 * timestamps) is pinned to UTC+8 / Asia/Shanghai instead of the device zone. Absolute
 * instants (epoch millis, ISO-8601 with offset, WebDAV/EXIF protocol dates) stay untouched:
 * they represent a point in time, not a wall clock.
 */
object AppTimeZone {

    const val ZONE_ID: String = "Asia/Shanghai"

    val zone: ZoneId = ZoneId.of(ZONE_ID)

    val clock: Clock = Clock.system(zone)

    /** For legacy `SimpleDateFormat`/`Calendar` call sites. */
    val timeZone: TimeZone = TimeZone.getTimeZone(zone)

    fun now(): LocalDateTime = LocalDateTime.now(clock)

    fun today(): LocalDate = LocalDate.now(clock)

    fun timeNow(): LocalTime = LocalTime.now(clock)

    fun currentYearMonth(): YearMonth = YearMonth.now(clock)

    fun toLocalDateTime(epochMilli: Long): LocalDateTime =
        Instant.ofEpochMilli(epochMilli).atZone(zone).toLocalDateTime()

    fun toLocalDate(epochMilli: Long): LocalDate =
        Instant.ofEpochMilli(epochMilli).atZone(zone).toLocalDate()

    fun toLocalTime(epochMilli: Long): LocalTime =
        Instant.ofEpochMilli(epochMilli).atZone(zone).toLocalTime()

    fun toEpochMilli(dateTime: LocalDateTime): Long =
        dateTime.atZone(zone).toInstant().toEpochMilli()

    fun startOfDayEpochMilli(date: LocalDate): Long = toEpochMilli(date.atStartOfDay())

    /**
     * Pins the process-wide default so formatters, calendars and any third-party code that
     * never asks for a zone also render in UTC+8. Called once from `Application.onCreate`; it
     * must run before anything formats a date (Koin singletons included).
     */
    fun applyAsProcessDefault() {
        TimeZone.setDefault(timeZone)
    }
}
