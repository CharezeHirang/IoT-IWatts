package com.example.sampleiwatts;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ReadingReceiver extends BroadcastReceiver {
    public static final String ACTION = "com.sampleiwatts.NEW_READING";
    public static final String EX_WATTS = "watts";
    public static final String EX_BILL = "projected_bill";
    public static final String EX_V_SPIKE = "voltage_spike";

    @Override
    public void onReceive(Context context, Intent intent) {
        double watts = intent.getDoubleExtra(EX_WATTS, 0);
        double bill = intent.getDoubleExtra(EX_BILL, 0);
        boolean spike = intent.getBooleanExtra(EX_V_SPIKE, false);

        AlertSettingsStore store = new AlertSettingsStore(context);
        AlertSettings settings = store.load();
        AlertNotifier.evaluateReadingAndNotify(context, settings, watts, bill, spike);
    }

}
