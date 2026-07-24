/* Copyright (C) 2026 ZbogomReklame
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66.vpn;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Persists the live query/blocked counters (so they survive an app restart)
 * and keeps a small rolling history of how many domains were blocked per day,
 * for the simple 7-day graph on the Start tab.
 */
public class Stats {

    private static final String PREFS_NAME = "zbogomreklame_stats";
    private static final String KEY_QUERY_COUNT = "query_count";
    private static final String KEY_BLOCKED_COUNT = "blocked_count";
    private static final String KEY_LAST_DAY = "last_day";
    private static final String KEY_HISTORY = "history"; // comma-separated counts, oldest first
    private static final int HISTORY_DAYS = 7;

    private static final SimpleDateFormat DAY_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Call once when the app starts, to restore the counters from last time.
     */
    public static void restore(Context context) {
        SharedPreferences p = prefs(context);
        DnsPacketProxy.queryCount.set(p.getLong(KEY_QUERY_COUNT, 0));
        DnsPacketProxy.blockedCount.set(p.getLong(KEY_BLOCKED_COUNT, 0));
        rolloverIfNewDay(context);
    }

    /**
     * Call periodically (every few seconds is fine) to save the current
     * counters, and to roll the daily history forward if the day changed.
     */
    public static void save(Context context) {
        rolloverIfNewDay(context);
        prefs(context).edit()
                .putLong(KEY_QUERY_COUNT, DnsPacketProxy.queryCount.get())
                .putLong(KEY_BLOCKED_COUNT, DnsPacketProxy.blockedCount.get())
                .apply();
    }

    private static void rolloverIfNewDay(Context context) {
        SharedPreferences p = prefs(context);
        String today = DAY_FORMAT.format(new Date());
        String lastDay = p.getString(KEY_LAST_DAY, today);

        if (!lastDay.equals(today)) {
            // A new day started since we last checked - push today's blocked
            // count onto the history, and reset the live counters to zero.
            List<Long> history = getHistory(context);
            history.add(DnsPacketProxy.blockedCount.get());
            while (history.size() > HISTORY_DAYS)
                history.remove(0);

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < history.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(history.get(i));
            }

            p.edit()
                    .putString(KEY_LAST_DAY, today)
                    .putString(KEY_HISTORY, sb.toString())
                    .apply();

            DnsPacketProxy.queryCount.set(0);
            DnsPacketProxy.blockedCount.set(0);
        }
    }

    /**
     * Returns up to the last 7 days of blocked-domain counts, oldest first.
     * Does not include today (today's live count is shown separately).
     */
    public static List<Long> getHistory(Context context) {
        String raw = prefs(context).getString(KEY_HISTORY, "");
        List<Long> result = new ArrayList<>();
        if (raw.isEmpty())
            return result;
        for (String part : raw.split(",")) {
            try {
                result.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }
}
