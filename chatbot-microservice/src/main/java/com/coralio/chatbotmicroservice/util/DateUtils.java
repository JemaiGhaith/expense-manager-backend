package com.coralio.chatbotmicroservice.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class DateUtils {

    private static final DateTimeFormatter[] DATE_FORMATTERS = {
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy")
    };

    public static LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return null;

        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(dateStr.trim(), formatter);
            } catch (DateTimeParseException e) {
                // Try next formatter
            }
        }
        return null;
    }

    public static String formatDate(LocalDate date) {
        if (date == null) return "";
        return date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    public static String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) return "";
        return dateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    public static boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    public static boolean isTunisianHoliday(LocalDate date) {
        String formatted = String.format("%02d-%02d", date.getDayOfMonth(), date.getMonthValue());
        return Constants.TUNISIAN_HOLIDAYS.contains(formatted);
    }

    public static boolean isWorkingDay(LocalDate date) {
        return !isWeekend(date) && !isTunisianHoliday(date);
    }

    public static LocalDate getNextWorkingDay(LocalDate date) {
        LocalDate next = date.plusDays(1);
        while (!isWorkingDay(next)) {
            next = next.plusDays(1);
        }
        return next;
    }
}