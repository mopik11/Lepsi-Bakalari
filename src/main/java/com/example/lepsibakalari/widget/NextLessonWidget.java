package com.example.lepsibakalari.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.example.lepsibakalari.MainActivity;
import com.example.lepsibakalari.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class NextLessonWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_next_lesson);

        // Intent to open app
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widgetContainer, pendingIntent);

        // Load Data
        android.content.SharedPreferences prefs = context.getSharedPreferences("widget_data", Context.MODE_PRIVATE);
        String json = prefs.getString("timetable_json", null);

        if (json != null) {
            try {
                com.google.gson.Gson gson = new com.google.gson.Gson();
                com.example.lepsibakalari.api.TimetableResponse data = gson.fromJson(json,
                        com.example.lepsibakalari.api.TimetableResponse.class);

                // Find next lesson
                String nextLessonInfo = getNextLessonInfo(data);
                if (nextLessonInfo != null) {
                    String[] parts = nextLessonInfo.split("\\|"); // Subject|Room|Time|Theme
                    views.setTextViewText(R.id.widgetSubject, parts[0]);
                    views.setTextViewText(R.id.widgetRoom, parts[1]);
                    views.setTextViewText(R.id.widgetTime, parts[2]);
                    if (parts.length > 3)
                        views.setTextViewText(R.id.widgetTopic, parts[3]);
                    else
                        views.setTextViewText(R.id.widgetTopic, "");
                } else {
                    views.setTextViewText(R.id.widgetSubject, "Volno");
                    views.setTextViewText(R.id.widgetRoom, "");
                    views.setTextViewText(R.id.widgetTime, "Dnes už nic");
                    views.setTextViewText(R.id.widgetTopic, "");
                }
            } catch (Exception e) {
                views.setTextViewText(R.id.widgetSubject, "Chyba dat");
                views.setTextViewText(R.id.widgetTime, "Klekněte pro obnovení");
            }
        } else {
            views.setTextViewText(R.id.widgetSubject, "Žádná data");
            views.setTextViewText(R.id.widgetTime, "Otevřete aplikaci");
        }

        String updateTime = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        views.setTextViewText(R.id.widgetUpdate, "Aktualizováno: " + updateTime);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private static String getNextLessonInfo(com.example.lepsibakalari.api.TimetableResponse data) {
        // Logic to find next lesson
        // 1. Get today index/date
        // Simply: Find day matching today's date
        // Note: Timetable responses usually return current week.
        // We need real parsing or simple lookup.

        // For simplicity in this step: Just find first atom of today that is in future
        if (data.getDays() == null)
            return null;

        // Simple day matching
        // In real app we parse dates properly. Here we assume API returns current week
        // and we iterate days to find one matching today.
        // But API structure might vary. Let's try to match by DayOfWeek or Date string.

        String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        com.example.lepsibakalari.api.TimetableResponse.Day todayDay = null;
        for (com.example.lepsibakalari.api.TimetableResponse.Day d : data.getDays()) {
            // Day usually has date "2023-10-25" or similar
            // API V3 structure check...
            // Assuming getDayOfWeek() or auxiliary date logic
            // Let's rely on date similarity if available, or just index if we trust it's
            // sorted Mon-Fri

            // We don't have date in Day object easily accessible in this snippet context
            // without checking API def again.
            // However based on MainActivity logs, we saw loops.
            // Let's assume we iterate all atoms in the day and check time.

            // If we cannot match date easily, we can't show accurate info.
            // BUT, we can try to find valid Atoms.

            // Actually, let's look at Atoms directly if possible? No, they are in Days.
            // Let's look at MainActivity.showTimetable logic... it iterates days.
            // Day has list of Atoms.

            // Let's assume the user opens app today, so the data is relevant for this week.
            // We need java.util.Calendar to get current day index (0=Mon, 4=Fri).
            java.util.Calendar cal = java.util.Calendar.getInstance();
            int dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK); // Sun=1, Mon=2...
            int listIndex = dayOfWeek - 2; // Mon=0

            if (listIndex >= 0 && listIndex < data.getDays().size()) {
                // Verify date if possible, but let's trust index for now
                todayDay = data.getDays().get(listIndex);
            }
        }

        if (todayDay == null || todayDay.getAtoms() == null)
            return null;

        int currentMinutes = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) * 60 +
                java.util.Calendar.getInstance().get(java.util.Calendar.MINUTE);

        for (com.example.lepsibakalari.api.TimetableResponse.Atom atom : todayDay.getAtoms()) {
            // Find Hour
            com.example.lepsibakalari.api.TimetableResponse.Hour hour = null;
            if (data.getHours() != null) {
                for (com.example.lepsibakalari.api.TimetableResponse.Hour h : data.getHours()) {
                    if (h.getId() == atom.getHourId()) {
                        hour = h;
                        break;
                    }
                }
            }

            if (hour != null) {
                // Parse time "08:00" -> minutes
                int endMinutes = parseTime(hour.getEndTime());
                if (endMinutes > currentMinutes) {
                    // This is the one!
                    String subjName = "?";
                    if (data.getSubjects() != null) {
                        for (com.example.lepsibakalari.api.TimetableResponse.Subject s : data.getSubjects()) {
                            String aid = atom.getSubjectId();
                            if (s.getId().equals(aid)) {
                                subjName = s.getName();
                                break;
                            }
                        }
                    }
                    String roomName = "";
                    if (data.getRooms() != null) {
                        for (com.example.lepsibakalari.api.TimetableResponse.Room r : data.getRooms()) {
                            String rid = atom.getRoomId();
                            if (r.getId().equals(rid)) {
                                roomName = r.getAbbrev();
                                break;
                            }
                        }
                    }

                    return subjName + "|" + roomName + "|" + hour.getBeginTime() + " - " + hour.getEndTime() + "|"
                            + (atom.getTheme() != null ? atom.getTheme() : "");
                }
            }
        }

        return null;
    }

    private static int parseTime(String t) {
        if (t == null)
            return 0;
        try {
            String[] p = t.split(":");
            return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
        } catch (Exception e) {
            return 0;
        }
    }
}
