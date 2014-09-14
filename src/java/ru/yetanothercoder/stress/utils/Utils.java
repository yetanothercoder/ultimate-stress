package ru.yetanothercoder.stress.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

    final private static int second = 1_000;
    final private static int minute = 60;
    final private static int hour = 60;

    private static Pattern STATUS_PATTERN = Pattern.compile("HTTP/1\\.[10]\\s+(\\d+)"); // supports only 1.0 or 1.1

    public static int parseStatus(String respLine) {
        Matcher m = STATUS_PATTERN.matcher(respLine);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return -1;
    }

    /**
     * Simple human readable formatter
     *
     * @param ms duration in millis
     * @return human readable format, ex. 1.1h or 1.2m
     */
    public static String formatLatency(long ms) {
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
