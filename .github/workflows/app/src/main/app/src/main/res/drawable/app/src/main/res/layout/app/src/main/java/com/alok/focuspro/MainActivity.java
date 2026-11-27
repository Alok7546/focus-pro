package com.alok.focuspro;

import android.app.AlertDialog;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    ProgressBar goldCircle;
    TextView txtTotalHours, txtStudyTime, txtNormalTime, txtDistractTime, txtGraphInsight;
    LinearLayout chartContainer;
    ListView listApps;
    Button btnPermission, btnBlockerToggle;

    SharedPreferences prefs;
    boolean blockerRunning = false;
    List<String> appPackages = new ArrayList<>();
    List<String> appDisplayList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("FocusProPrefs", MODE_PRIVATE);

        goldCircle = findViewById(R.id.gold_circle);
        txtTotalHours = findViewById(R.id.txt_total_hours);
        txtStudyTime = findViewById(R.id.txt_study_time);
        txtNormalTime = findViewById(R.id.txt_normal_time);
        txtDistractTime = findViewById(R.id.txt_distract_time);
        txtGraphInsight = findViewById(R.id.txt_graph_insight);
        chartContainer = findViewById(R.id.chart_container);
        listApps = findViewById(R.id.list_apps);
        btnPermission = findViewById(R.id.btn_permission);
        btnBlockerToggle = findViewById(R.id.btn_blocker_toggle);

        btnPermission.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            Toast.makeText(this, "Focus Pro ko Usage Access ON karein", Toast.LENGTH_LONG).show();
        });

        btnBlockerToggle.setOnClickListener(v -> {
            Intent serviceIntent = new Intent(this, BlockerService.class);
            if (!blockerRunning) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
                blockerRunning = true;
                btnBlockerToggle.setText("Blocker: ON");
                btnBlockerToggle.setBackgroundColor(Color.parseColor("#22C55E"));
                Toast.makeText(this, "Focus Blocker & Voice Alert ON!", Toast.LENGTH_SHORT).show();
            } else {
                stopService(serviceIntent);
                blockerRunning = false;
                btnBlockerToggle.setText("Blocker: OFF");
                btnBlockerToggle.setBackgroundColor(Color.parseColor("#EF4444"));
            }
        });

        listApps.setOnItemClickListener((parent, view, position, id) -> {
            String pkg = appPackages.get(position);
            showCategoryDialog(pkg);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadUsageStats();
    }

    private void showCategoryDialog(String pkg) {
        String[] options = {"Study (Padhai)", "Normal (PhonePe/WhatsApp/Call)", "Distraction (Block & Voice Alert)"};
        new AlertDialog.Builder(this)
                .setTitle("Category Select Karein")
                .setItems(options, (dialog, which) -> {
                    String cat = (which == 0) ? "study" : (which == 1 ? "normal" : "distract");
                    prefs.edit().putString("cat_" + pkg, cat).apply();
                    loadUsageStats();
                })
                .show();
    }

    private void loadUsageStats() {
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        long startTime = cal.getTimeInMillis();
        long endTime = System.currentTimeMillis();

        Map<String, UsageStats> statsMap = usm.queryAndAggregateUsageStats(startTime, endTime);
        if (statsMap == null || statsMap.isEmpty()) {
            txtGraphInsight.setText("Upar 'Permissions' button daba kar Usage Access ON karein.");
            return;
        }

        List<UsageStats> sortedList = new ArrayList<>();
        for (UsageStats us : statsMap.values()) {
            if (us.getTotalTimeInForeground() > 60000) { // > 1 min
                sortedList.add(us);
            }
        }
        Collections.sort(sortedList, (a, b) -> Long.compare(b.getTotalTimeInForeground(), a.getTotalTimeInForeground()));

        long totalMins = 0, studyMins = 0, normalMins = 0, distractMins = 0;
        appPackages.clear();
        appDisplayList.clear();
        PackageManager pm = getPackageManager();

        for (UsageStats us : sortedList) {
            String pkg = us.getPackageName();
            long mins = us.getTotalTimeInForeground() / 60000;
            totalMins += mins;

            String cat = prefs.getString("cat_" + pkg, getDefaultCategory(pkg));
            if (cat.equals("study")) studyMins += mins;
            else if (cat.equals("distract")) distractMins += mins;
            else normalMins += mins;

            String appName = pkg;
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                appName = pm.getApplicationLabel(ai).toString();
            } catch (Exception ignored) {}

            appPackages.add(pkg);
            appDisplayList.add(appName + "  •  " + formatTime(mins) + "  [" + cat.toUpperCase() + "]");
        }

        // Update Gold Circle (Goal: 8 hours = 480 mins)
        int pct = (int) Math.min(100, (studyMins * 100) / 480);
        goldCircle.setProgress(pct);
        txtTotalHours.setText(formatTime(totalMins));
        txtStudyTime.setText("Study: " + formatTime(studyMins) + " (Baki: " + formatTime(Math.max(0, 480 - studyMins)) + ")");
        txtNormalTime.setText("Normal: " + formatTime(normalMins));
        txtDistractTime.setText("Distraction: " + formatTime(distractMins));

        // Update Top Apps Bar Chart
        chartContainer.removeAllViews();
        int barCount = Math.min(8, sortedList.size());
        long maxMins = barCount > 0 ? Math.max(1, sortedList.get(0).getTotalTimeInForeground() / 60000) : 1;

        for (int i = 0; i < barCount; i++) {
            long mins = sortedList.get(i).getTotalTimeInForeground() / 60000;
            String pkg = sortedList.get(i).getPackageName();
            String cat = prefs.getString("cat_" + pkg, getDefaultCategory(pkg));

            View bar = new View(this);
            int barHeight = (int) Math.max(15, (mins * 220) / maxMins);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, barHeight, 1f);
            lp.setMargins(6, 0, 6, 0);
            bar.setLayoutParams(lp);
            bar.setBackgroundColor(cat.equals("study") ? Color.parseColor("#22C55E") :
                    (cat.equals("distract") ? Color.parseColor("#EF4444") : Color.parseColor("#F5B942")));
            chartContainer.addView(bar);
        }

        if (!appDisplayList.isEmpty()) {
            txtGraphInsight.setText("Sabse Zyada: " + appDisplayList.get(0) + "\nSabse Kam: " + appDisplayList.get(appDisplayList.size() - 1));
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, appDisplayList);
        listApps.setAdapter(adapter);
    }

    private String getDefaultCategory(String pkg) {
        if (pkg.contains("instagram") || pkg.contains("facebook") || pkg.contains("freefire")) return "distract";
        if (pkg.contains("whatsapp") || pkg.contains("phonepe") || pkg.contains("nbu.paisa") || pkg.contains("dialer")) return "normal";
        return "normal";
    }

    private String formatTime(long mins) {
        long h = mins / 60;
        long m = mins % 60;
        return h > 0 ? h + "h " + m + "m" : m + "m";
    }
}
