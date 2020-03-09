package com.tombrus.persistentqueues;

public class HumanReadable {
    private final static String[] dictionary = {"bytes", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB"};

    public static String format(double bytes, int digits) {
        for (String unit : dictionary) {
            if (bytes < 1024) {
                return String.format("%." + digits + "f", bytes) + " " + unit;
            } else {
                bytes /= 1024;
            }
        }
        throw new Error("Most illogical");
    }
}
