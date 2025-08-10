package com.example.sampleiwatts;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class NotificationLogger {
    private static final String PREFS = "notif_log_prefs";
    private static final String KEY   = "log";

    static void log(Context ctx, String title, String message, long timeMillis, String category) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONArray arr = new JSONArray(sp.getString(KEY, "[]"));

            JSONObject obj = new JSONObject();
            obj.put("id", timeMillis);  // unique enough here
            obj.put("t", title);
            obj.put("m", message);
            obj.put("c", category == null ? "Other" : category);
            obj.put("r", false);
            obj.put("ts", timeMillis);

            JSONArray newArr = new JSONArray();
            newArr.put(obj);
            for (int i = 0; i < arr.length(); i++) newArr.put(arr.getJSONObject(i));

            sp.edit().putString(KEY, newArr.toString()).apply();
        } catch (Exception ignored) {}
    }

    static List<NotificationLogEntry> getAll(Context ctx) {
        List<NotificationLogEntry> out = new ArrayList<>();
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONArray arr = new JSONArray(sp.getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new NotificationLogEntry(
                        o.optLong("id", o.optLong("ts", System.currentTimeMillis())),
                        o.optString("t",""),
                        o.optString("m",""),
                        o.optString("c","Other"),
                        o.optBoolean("r", false),
                        o.optLong("ts", System.currentTimeMillis())
                ));
            }
        } catch (Exception ignored) {}
        return out;
    }

    static void setAllRead(Context ctx) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONArray arr = new JSONArray(sp.getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) arr.getJSONObject(i).put("r", true);
            sp.edit().putString(KEY, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    static void clear(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply();
    }

    static void removeById(Context ctx, long id) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONArray arr = new JSONArray(sp.getString(KEY, "[]"));
            JSONArray out = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                if (arr.getJSONObject(i).optLong("id", -1) != id) out.put(arr.getJSONObject(i));
            }
            sp.edit().putString(KEY, out.toString()).apply();
        } catch (Exception ignored) {}
    }
}
