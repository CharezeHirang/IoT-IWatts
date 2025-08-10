package com.example.sampleiwatts;

public class NotificationLogEntry
{
    final long id;             // unique (we’ll use timestamp)
    final String title;
    final String message;
    final String category;     // "Power usage", "Voltage", "Temperature", "Budget", or "Other"
    boolean read;
    final long timestamp;

    NotificationLogEntry(long id, String title, String message, String category, boolean read, long ts) {
        this.id = id; this.title = title; this.message = message;
        this.category = category; this.read = read; this.timestamp = ts;
    }
}
