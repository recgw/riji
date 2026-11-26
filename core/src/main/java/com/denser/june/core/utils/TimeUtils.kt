package com.denser.june.core.utils

import java.time.YearMonth
import java.time.DayOfWeek
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Date
import java.util.Locale
import java.time.format.DateTimeFormatter

/** All wall-clock formatting in the app happens in UTC+8, never the device zone. */
private fun appTimeFormatter(pattern: String, locale: Locale = Locale.getDefault()): SimpleDateFormat =
    SimpleDateFormat(pattern, locale).apply { timeZone = AppTimeZone.timeZone }

fun Long.toYearMonth(): YearMonth {
    val localDate = AppTimeZone.toLocalDate(this)
    return YearMonth.from(localDate)
}

fun Long.toShortMonth(): String {
    return appTimeFormatter("MMM").format(Date(this))
}

fun Long.toDayOfMonth(): String {
    return appTimeFormatter("dd").format(Date(this))
}

fun Long.toDateWithDay(): String {
    return appTimeFormatter("EEE, MMM dd, yyyy").format(Date(this))
}

fun Long.toFullDate(): String {
    return appTimeFormatter("MMMM dd, yyyy").format(Date(this))
}

fun Long.toMonthDay(): String {
    return appTimeFormatter("MMM dd").format(Date(this))
}

fun Long.toMediumDate(): String {
    return appTimeFormatter("MMM d, yyyy").format(Date(this))
}

fun Long.toExifDateTimeDisplay(): String {
    val timeStr = appTimeFormatter("HH:mm").format(Date(this))
    return if (timeStr == "00:00") {
        appTimeFormatter("EEE, MMM d, yyyy").format(Date(this))
    } else {
        appTimeFormatter("EEE, MMM d, yyyy   •   HH:mm").format(Date(this))
    }
}

fun Long.toMonthDayTime(is24Hour: Boolean = false): String {
    val pattern = if (is24Hour) "MMM dd '•' HH:mm" else "MMM dd '•' hh:mm a"
    return appTimeFormatter(pattern).format(Date(this))
}

fun Long.toFullDateTime(is24Hour: Boolean = false): String {
    val pattern = if (is24Hour) "MMM dd, yyyy 'at' HH:mm" else "MMM dd, yyyy 'at' hh:mm a"
    return appTimeFormatter(pattern).format(Date(this))
}

fun Long.toHoursMinutesSeconds(): String {
    val totalSeconds = this / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

fun Long.toSongTimestamp(): String {
    val totalSeconds = (this.coerceAtLeast(0L) + 500) / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}

fun Long.toSongDurationString(): String {
    val totalSeconds = (this.coerceAtLeast(0L) + 500) / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return when {
        minutes > 0 && seconds > 0 -> "${minutes}m ${seconds}s"
        minutes > 0 -> "${minutes}m"
        else -> "${seconds}s"
    }
}

fun Long.toAudioTimestamp(): String {
    val totalMs = this.coerceAtLeast(0L)
    val totalSeconds = totalMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

fun formatAudioTimestamp(ms: Long): String = ms.toAudioTimestamp()


fun YearMonth.getDaysInMonthGrid(startOfWeek: DayOfWeek = DayOfWeek.SUNDAY): List<LocalDate?> {
    val firstDay = this.atDay(1)
    val totalDays = this.lengthOfMonth()
    val firstDayOfWeek = firstDay.dayOfWeek.value
    val startDayValue = startOfWeek.value

    val startOffset = (firstDayOfWeek - startDayValue + 7) % 7
    val list = mutableListOf<LocalDate?>()
    repeat(startOffset) { list.add(null) }
    for (i in 1..totalDays) {
        list.add(this.atDay(i))
    }
    return list
}

fun Long.isMidnight(): Boolean {
    return this.toLocalTime() == LocalTime.MIDNIGHT
}

fun Long.toLocalDate(): LocalDate = AppTimeZone.toLocalDate(this)

fun Long.toLocalTime(): LocalTime = AppTimeZone.toLocalTime(this)

fun LocalDate.toFullDate(): String =
    this.format(DateTimeFormatter.ofPattern("MMMM dd, yyyy", Locale.getDefault()))

fun LocalTime.toFullTime(is24Hour: Boolean = false): String {
    val pattern = if (is24Hour) "HH:mm" else "hh:mm a"
    return this.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}

fun combineDateAndTime(date: LocalDate, time: LocalTime?): Long {
    val dateTime = if (time != null) {
        LocalDateTime.of(date, time)
    } else {
        date.atStartOfDay()
    }
    return AppTimeZone.toEpochMilli(dateTime)
}

fun getTodayAtMidnight(): Long {
    return AppTimeZone.startOfDayEpochMilli(AppTimeZone.today())
}

fun getWeeksInMonth(yearMonth: YearMonth, startOfWeek: DayOfWeek = DayOfWeek.SUNDAY): Int {
    return yearMonth.getDaysInMonthGrid(startOfWeek).chunked(7).size
}
