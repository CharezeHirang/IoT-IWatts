package com.example.sampleiwatts;
public class AlertItem {
    public String id;          // key from push()
    public String type;        // POWER, VOLTAGE, BUDGET_WARN, BUDGET_CRIT
    public String title;
    public String message;
    public long   timestamp;
    public String dateKey;     // "YYYY-MM-DD" or "yyyyMM" for budget
    public String hour;        // optional
    public boolean read;       // NEW (default false)

    public AlertItem() {}
}

