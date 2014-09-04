package ru.yetanothercoder.stress.utils;

public class DurationFormatter {

    final private static int second = 1_000;
    final private static int minute = 60;
    final private static int hour = 60;

    public static String format(long ms) {
        String result;
        ms = Math.abs(ms);
        if (ms > second) { // hours
            double s = (double) ms / second;

            if (s > minute) {
                int m = (int) (s / minute);

                if (m > hour) {
                    int h = m / hour;
                    int mm = m % hour;

                    result = String.format("%d.%dh", h, mm);
                } else {
                    int ss = (int) (s % minute);

                    result = String.format("%d.%dm", m, ss);
                }
            } else {
                result = String.format("%,.2fs", s);
            }
        } else {
            result = String.format("%,dms", ms);
        }
        return result;
    }
}
