package com.example.lepsibakalari;

import android.content.Intent;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.lepsibakalari.api.AbsenceResponse;
import com.example.lepsibakalari.api.EventsResponse;
import com.example.lepsibakalari.api.HomeworksResponse;
import com.example.lepsibakalari.api.KomensResponse;
import com.example.lepsibakalari.api.MarksFinalResponse;
import com.example.lepsibakalari.api.MarksResponse;
import com.example.lepsibakalari.api.SubstitutionsResponse;
import com.example.lepsibakalari.api.TimetableResponse;
import com.example.lepsibakalari.databinding.ActivityMainBinding;
import com.example.lepsibakalari.databinding.ItemHomeworkBinding;
import com.example.lepsibakalari.databinding.ItemKomensBinding;
import com.example.lepsibakalari.databinding.ItemLessonBinding;
import com.example.lepsibakalari.databinding.ItemMarkBinding;
import com.example.lepsibakalari.repository.BakalariRepository;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * MainActivity - Dashboard s rozvrhem, známkami, Komens a dalšími moduly Bakalářů.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private BakalariRepository repository;
    private static final int TAB_TIMETABLE = 0;
    private static final int TAB_MARKS = 1;
    private static final int TAB_KOMENS = 2;
    private static final int TAB_MORE = 3;
    private int currentTab = TAB_TIMETABLE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        repository = new BakalariRepository(this);

        if (!repository.isLoggedIn()) {
            startLoginAndFinish();
            return;
        }

        setSupportActionBar(binding.toolbar);

        // Liquid Glass blur efekt na pozadí (API 31+)
        applyBlurEffect(binding.meshGradient);

        setupNavigation();
        loadTimetable();
    }

    private void startLoginAndFinish() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    /**
     * Aplikuje RenderEffect blur pro Liquid Glass vzhled (API 31+).
     */
    private void applyBlurEffect(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.setRenderEffect(RenderEffect.createBlurEffect(50f, 50f, Shader.TileMode.CLAMP));
        }
    }

    private void setupNavigation() {
        binding.navTimetable.setOnClickListener(v -> switchTab(TAB_TIMETABLE));
        binding.navMarks.setOnClickListener(v -> switchTab(TAB_MARKS));
        binding.navKomens.setOnClickListener(v -> switchTab(TAB_KOMENS));
        binding.navMore.setOnClickListener(v -> switchTab(TAB_MORE));
    }

    private void setAllViewsGone() {
        binding.scrollTimetable.setVisibility(View.GONE);
        binding.scrollMarks.setVisibility(View.GONE);
        binding.scrollKomens.setVisibility(View.GONE);
        binding.scrollMore.setVisibility(View.GONE);
    }

    private void setNavIconsInactive() {
        binding.navTimetableIcon.setColorFilter(0x99FFFFFF);
        binding.navMarksIcon.setColorFilter(0x99FFFFFF);
        binding.navKomensIcon.setColorFilter(0x99FFFFFF);
        binding.navMoreIcon.setColorFilter(0x99FFFFFF);
    }

    private void switchTab(int tab) {
        currentTab = tab;
        setAllViewsGone();
        setNavIconsInactive();

        switch (tab) {
            case TAB_TIMETABLE:
                binding.scrollTimetable.setVisibility(View.VISIBLE);
                binding.navTimetableIcon.setColorFilter(0xFFFFFFFF);
                loadTimetable();
                break;
            case TAB_MARKS:
                binding.scrollMarks.setVisibility(View.VISIBLE);
                binding.navMarksIcon.setColorFilter(0xFFFFFFFF);
                loadMarks();
                break;
            case TAB_KOMENS:
                binding.scrollKomens.setVisibility(View.VISIBLE);
                binding.navKomensIcon.setColorFilter(0xFFFFFFFF);
                loadKomens();
                break;
            case TAB_MORE:
                binding.scrollMore.setVisibility(View.VISIBLE);
                binding.navMoreIcon.setColorFilter(0xFFFFFFFF);
                loadMore();
                break;
        }
    }

    private void loadTimetable() {
        binding.textLoading.setVisibility(View.VISIBLE);
        binding.textLoading.setText(R.string.loading);
        binding.lessonsContainer.removeAllViews();

        repository.getTimetableToday(new Callback<TimetableResponse>() {
            @Override
            public void onResponse(Call<TimetableResponse> call, Response<TimetableResponse> response) {
                binding.textLoading.setVisibility(View.GONE);
                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(MainActivity.this, "Chyba načítání rozvrhu", Toast.LENGTH_SHORT).show();
                    return;
                }
                showTimetable(response.body());
            }

            @Override
            public void onFailure(Call<TimetableResponse> call, Throwable t) {
                binding.textLoading.setVisibility(View.GONE);
                Toast.makeText(MainActivity.this, "Chyba: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String formatDisplayDate(String dateStr) {
        if (dateStr == null || dateStr.length() < 10) return dateStr != null ? dateStr : "";
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            SimpleDateFormat out = new SimpleDateFormat("EEEE d. MMMM", Locale.forLanguageTag("cs-CZ"));
            Date d = in.parse(dateStr.substring(0, 10));
            return d != null ? out.format(d) : dateStr;
        } catch (ParseException e) {
            return dateStr;
        }
    }

    private String formatShortDate(String dateStr) {
        if (dateStr == null || dateStr.length() < 10) return "";
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            SimpleDateFormat out = new SimpleDateFormat("d.M.", Locale.getDefault());
            Date d = in.parse(dateStr.substring(0, 10));
            return d != null ? out.format(d) : dateStr.substring(0, 10);
        } catch (ParseException e) {
            return dateStr.length() >= 10 ? dateStr.substring(0, 10) : dateStr;
        }
    }

    private void showTimetable(TimetableResponse data) {
        binding.lessonsContainer.removeAllViews();
        Map<Integer, TimetableResponse.Hour> hoursMap = data.getHours() != null
                ? data.getHours().stream().collect(Collectors.toMap(TimetableResponse.Hour::getId, h -> h))
                : Map.of();
        Map<String, TimetableResponse.Subject> subjectsMap = data.getSubjects() != null
                ? data.getSubjects().stream().collect(Collectors.toMap(s -> s.getId() != null ? s.getId() : "", s -> s))
                : Map.of();
        Map<String, TimetableResponse.Room> roomsMap = data.getRooms() != null
                ? data.getRooms().stream().collect(Collectors.toMap(r -> r.getId() != null ? r.getId() : "", r -> r))
                : Map.of();

        List<TimetableResponse.Day> days = data.getDays();
        if (days == null || days.isEmpty()) {
            addEmptyView(binding.lessonsContainer, getString(R.string.no_lessons));
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        int dp24 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, getResources().getDisplayMetrics());
        int dp12 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());

        for (int dayIndex = 0; dayIndex < days.size(); dayIndex++) {
            TimetableResponse.Day day = days.get(dayIndex);
            String dateStr = day.getDate() != null ? day.getDate().substring(0, Math.min(10, day.getDate().length())) : "";
            TextView dayHeader = (TextView) inflater.inflate(R.layout.item_day_header, binding.lessonsContainer, false);
            dayHeader.setText(formatDisplayDate(dateStr));
            LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            headerParams.topMargin = dayIndex == 0 ? 0 : dp24;
            headerParams.bottomMargin = dp12;
            dayHeader.setLayoutParams(headerParams);
            binding.lessonsContainer.addView(dayHeader);

            List<TimetableResponse.Atom> atoms = day.getAtoms();
            if (atoms == null || atoms.isEmpty()) {
                addEmptyView(binding.lessonsContainer, getString(R.string.no_lessons), 14);
                continue;
            }

            atoms = new java.util.ArrayList<>(atoms);
            atoms.sort((a, b) -> Integer.compare(a.getHourId(), b.getHourId()));

            for (TimetableResponse.Atom atom : atoms) {
                ItemLessonBinding itemBinding = ItemLessonBinding.inflate(inflater, binding.lessonsContainer, false);
                TimetableResponse.Hour hour = hoursMap.get(atom.getHourId());
                itemBinding.textHour.setText(hour != null && hour.getCaption() != null ? hour.getCaption() + "." : "");

                String subjId = atom.getSubjectId() != null ? atom.getSubjectId().trim() : "";
                TimetableResponse.Subject subj = subjectsMap.get(subjId);
                if (subj == null && atom.getSubjectId() != null) subj = subjectsMap.get(atom.getSubjectId());
                itemBinding.textSubject.setText(subj != null && subj.getName() != null ? subj.getName() : "-");

                String roomId = atom.getRoomId() != null ? atom.getRoomId().trim() : "";
                TimetableResponse.Room room = roomsMap.get(roomId);
                if (room == null && atom.getRoomId() != null) room = roomsMap.get(atom.getRoomId());
                String roomText = room != null && room.getAbbrev() != null ? room.getAbbrev() : "";
                itemBinding.textRoom.setText(roomText);
                itemBinding.textRoom.setVisibility(roomText.isEmpty() ? View.GONE : View.VISIBLE);

                String theme = atom.getTheme();
                itemBinding.textTheme.setText(theme != null && !theme.isEmpty() ? theme : "");
                itemBinding.textTheme.setVisibility(theme != null && !theme.isEmpty() ? View.VISIBLE : View.GONE);

                binding.lessonsContainer.addView(itemBinding.getRoot());
            }
        }
    }

    private void loadMarks() {
        binding.marksContent.removeAllViews();
        repository.getMarks(new Callback<MarksResponse>() {
            @Override
            public void onResponse(Call<MarksResponse> call, Response<MarksResponse> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(MainActivity.this, "Chyba načítání známek", Toast.LENGTH_SHORT).show();
                    return;
                }
                showMarks(response.body());
            }

            @Override
            public void onFailure(Call<MarksResponse> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Chyba: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showMarks(MarksResponse data) {
        binding.marksContent.removeAllViews();
        List<MarksResponse.SubjectMarks> subjects = data.getSubjects();
        if (subjects == null || subjects.isEmpty()) {
            addEmptyView(binding.marksContent, "Žádné známky");
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (MarksResponse.SubjectMarks sm : subjects) {
            ItemMarkBinding itemBinding = ItemMarkBinding.inflate(inflater, binding.marksContent, false);
            String name = sm.getSubject() != null && sm.getSubject().getName() != null ? sm.getSubject().getName() : "-";
            itemBinding.textSubjectName.setText(name);
            itemBinding.textAverage.setText("Průměr: " + (sm.getAverageText() != null ? sm.getAverageText() : ""));
            StringBuilder marksStr = new StringBuilder();
            if (sm.getMarks() != null) {
                for (MarksResponse.Mark m : sm.getMarks()) {
                    if (marksStr.length() > 0) marksStr.append(", ");
                    marksStr.append(m.getMarkText() != null ? m.getMarkText() : "-");
                }
            }
            itemBinding.textMarks.setText("Známky: " + marksStr);
            binding.marksContent.addView(itemBinding.getRoot());
        }
    }

    private void loadKomens() {
        binding.textKomensLoading.setVisibility(View.VISIBLE);
        binding.komensList.removeAllViews();

        repository.getKomensReceived(new Callback<KomensResponse>() {
            @Override
            public void onResponse(Call<KomensResponse> call, Response<KomensResponse> response) {
                binding.textKomensLoading.setVisibility(View.GONE);
                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(MainActivity.this, "Chyba načítání Komens", Toast.LENGTH_SHORT).show();
                    return;
                }
                showKomens(response.body());
            }

            @Override
            public void onFailure(Call<KomensResponse> call, Throwable t) {
                binding.textKomensLoading.setVisibility(View.GONE);
                Toast.makeText(MainActivity.this, "Chyba: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showKomens(KomensResponse data) {
        binding.komensList.removeAllViews();
        List<KomensResponse.KomensMessage> messages = data.getMessages();
        if (messages == null || messages.isEmpty()) {
            addEmptyView(binding.komensList, getString(R.string.no_messages));
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (KomensResponse.KomensMessage msg : messages) {
            ItemKomensBinding itemBinding = ItemKomensBinding.inflate(inflater, binding.komensList, false);
            itemBinding.textTitle.setText(msg.getTitle() != null ? msg.getTitle() : "-");
            itemBinding.textSender.setText(msg.getSender() != null ? msg.getSender().getName() : "");
            String sentDate = msg.getSentDate();
            if (sentDate != null && sentDate.length() >= 10) {
                itemBinding.textDate.setText(formatShortDate(sentDate.substring(0, 10)) + (sentDate.length() > 11 ? " " + sentDate.substring(11, 16) : ""));
            } else {
                itemBinding.textDate.setText(sentDate != null ? sentDate : "");
            }
            binding.komensList.addView(itemBinding.getRoot());
        }
    }

    private void loadMore() {
        binding.moreContent.removeAllViews();

        LinearLayout finalMarksContainer = createSectionContainer();
        LinearLayout homeworksContainer = createSectionContainer();
        LinearLayout absenceContainer = createSectionContainer();
        LinearLayout eventsContainer = createSectionContainer();
        LinearLayout substitutionsContainer = createSectionContainer();

        addSectionHeader(binding.moreContent, getString(R.string.section_final_marks));
        binding.moreContent.addView(finalMarksContainer);

        addSectionHeader(binding.moreContent, getString(R.string.section_homeworks));
        binding.moreContent.addView(homeworksContainer);

        addSectionHeader(binding.moreContent, getString(R.string.section_absence));
        binding.moreContent.addView(absenceContainer);

        addSectionHeader(binding.moreContent, getString(R.string.section_events));
        binding.moreContent.addView(eventsContainer);

        addSectionHeader(binding.moreContent, getString(R.string.section_substitutions));
        binding.moreContent.addView(substitutionsContainer);

        repository.getMarksFinal(new Callback<MarksFinalResponse>() {
            @Override
            public void onResponse(Call<MarksFinalResponse> call, Response<MarksFinalResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    showFinalMarks(response.body(), finalMarksContainer);
                } else {
                    addEmptyView(finalMarksContainer, getString(R.string.loading), 12);
                }
            }
            @Override
            public void onFailure(Call<MarksFinalResponse> call, Throwable t) {
                addEmptyView(finalMarksContainer, "Chyba: " + t.getMessage(), 12);
            }
        });

        String from = getDateDaysAgo(-14);
        String to = getDateDaysAgo(1);
        repository.getHomeworks(from, to, new Callback<HomeworksResponse>() {
            @Override
            public void onResponse(Call<HomeworksResponse> call, Response<HomeworksResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    showHomeworks(response.body(), homeworksContainer);
                } else {
                    addEmptyView(homeworksContainer, getString(R.string.no_homeworks), 12);
                }
            }
            @Override
            public void onFailure(Call<HomeworksResponse> call, Throwable t) {
                addEmptyView(homeworksContainer, "Chyba: " + t.getMessage(), 12);
            }
        });

        repository.getAbsence(new Callback<AbsenceResponse>() {
            @Override
            public void onResponse(Call<AbsenceResponse> call, Response<AbsenceResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    showAbsence(response.body(), absenceContainer);
                } else {
                    addEmptyView(absenceContainer, "Žádná absence", 12);
                }
            }
            @Override
            public void onFailure(Call<AbsenceResponse> call, Throwable t) {
                addEmptyView(absenceContainer, "Chyba: " + t.getMessage(), 12);
            }
        });

        repository.getEvents(getDateDaysAgo(0), new Callback<EventsResponse>() {
            @Override
            public void onResponse(Call<EventsResponse> call, Response<EventsResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    showEvents(response.body(), eventsContainer);
                } else {
                    addEmptyView(eventsContainer, getString(R.string.no_events), 12);
                }
            }
            @Override
            public void onFailure(Call<EventsResponse> call, Throwable t) {
                addEmptyView(eventsContainer, "Chyba: " + t.getMessage(), 12);
            }
        });

        repository.getSubstitutions(getDateDaysAgo(0), new Callback<SubstitutionsResponse>() {
            @Override
            public void onResponse(Call<SubstitutionsResponse> call, Response<SubstitutionsResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    showSubstitutions(response.body(), substitutionsContainer);
                } else {
                    addEmptyView(substitutionsContainer, getString(R.string.no_substitutions), 12);
                }
            }
            @Override
            public void onFailure(Call<SubstitutionsResponse> call, Throwable t) {
                addEmptyView(substitutionsContainer, "Chyba: " + t.getMessage(), 12);
            }
        });
    }

    private LinearLayout createSectionContainer() {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        return ll;
    }

    private void showFinalMarks(MarksFinalResponse data, LinearLayout container) {
        List<MarksFinalResponse.CertificateTerm> terms = data.getCertificateTerms();
        if (terms == null || terms.isEmpty()) {
            addEmptyView(container, "Žádná vysvědčení", 12);
            return;
        }

        for (MarksFinalResponse.CertificateTerm term : terms) {
            TextView tv = new TextView(this);
            tv.setBackgroundResource(R.drawable.glass_card);
            int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
            tv.setPadding(pad, pad, pad, pad);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
            tv.setLayoutParams(lp);

            String year = term.getSchoolYear() != null ? term.getSchoolYear() : "";
            String sem = term.getSemesterName() != null ? term.getSemesterName() : "";
            String grade = term.getGradeName() != null ? term.getGradeName() : "";
            String achievement = term.getAchievementText() != null ? term.getAchievementText() : "";
            Double avg = term.getMarksAverage();

            StringBuilder sb = new StringBuilder();
            sb.append(year).append(" – ").append(sem).append(" pololetí (").append(grade).append(")\n");
            if (avg != null) sb.append("Průměr: ").append(String.format(Locale.getDefault(), "%.2f", avg)).append("\n");
            sb.append(achievement);

            tv.setText(sb.toString());
            tv.setTextColor(0xFFFFFFFF);
            tv.setTextSize(14);
            container.addView(tv);
        }
    }

    private void showHomeworks(HomeworksResponse data, LinearLayout container) {
        List<HomeworksResponse.Homework> list = data.getHomeworks();
        if (list == null || list.isEmpty()) {
            addEmptyView(container, getString(R.string.no_homeworks), 12);
            return;
        }

        for (HomeworksResponse.Homework hw : list) {
            ItemHomeworkBinding itemBinding = ItemHomeworkBinding.inflate(LayoutInflater.from(this), container, false);
            itemBinding.textSubject.setText(hw.getSubject() != null ? hw.getSubject().getName() : "-");
            String content = hw.getContent();
            if (content != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                itemBinding.textContent.setText(Html.fromHtml(content, Html.FROM_HTML_MODE_LEGACY).toString());
            } else {
                itemBinding.textContent.setText(content != null ? content : "");
            }
            String dateEnd = hw.getDateEnd();
            itemBinding.textDate.setText("Do: " + (dateEnd != null && dateEnd.length() >= 10 ? formatShortDate(dateEnd.substring(0, 10)) : ""));
            container.addView(itemBinding.getRoot());
        }
    }

    private void showAbsence(AbsenceResponse data, LinearLayout container) {
        List<AbsenceResponse.AbsencePerSubject> perSubject = data.getAbsencesPerSubject();
        if (perSubject != null && !perSubject.isEmpty()) {
            for (AbsenceResponse.AbsencePerSubject a : perSubject) {
                TextView tv = createGlassTextView(a.getSubjectName() + ": " + a.getBase() + " hodin absence");
                container.addView(tv);
            }
        } else {
            List<AbsenceResponse.AbsenceDay> days = data.getAbsences();
            if (days != null && !days.isEmpty()) {
                int totalMissed = 0, totalOk = 0;
                for (AbsenceResponse.AbsenceDay d : days) {
                    totalMissed += d.getMissed() + d.getUnsolved();
                    totalOk += d.getOk();
                }
                TextView tv = createGlassTextView("Omluvené: " + totalOk + ", Neomluvené: " + totalMissed);
                container.addView(tv);
            } else {
                addEmptyView(container, "Žádná absence", 12);
            }
        }
    }

    private void showEvents(EventsResponse data, LinearLayout container) {
        List<EventsResponse.Event> events = data.getEvents();
        if (events == null || events.isEmpty()) {
            addEmptyView(container, getString(R.string.no_events), 12);
            return;
        }

        for (EventsResponse.Event ev : events) {
            TextView tv = createGlassTextView((ev.getTitle() != null ? ev.getTitle() : "-") + "\n" +
                    (ev.getEventType() != null && ev.getEventType().getName() != null ? ev.getEventType().getName() : ""));
            container.addView(tv);
        }
    }

    private void showSubstitutions(SubstitutionsResponse data, LinearLayout container) {
        List<SubstitutionsResponse.Change> changes = data.getChanges();
        if (changes == null || changes.isEmpty()) {
            addEmptyView(container, getString(R.string.no_substitutions), 12);
            return;
        }

        for (SubstitutionsResponse.Change ch : changes) {
            String day = ch.getDay() != null && ch.getDay().length() >= 10 ? formatShortDate(ch.getDay().substring(0, 10)) : "";
            TextView tv = createGlassTextView(day + " " + (ch.getHours() != null ? ch.getHours() : "") + ": " + (ch.getDescription() != null ? ch.getDescription() : ""));
            container.addView(tv);
        }
    }

    private TextView createGlassTextView(String text) {
        TextView tv = new TextView(this);
        tv.setBackgroundResource(R.drawable.glass_card);
        int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
        tv.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
        tv.setLayoutParams(lp);
        tv.setText(text);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(14);
        return tv;
    }

    private void addSectionHeader(LinearLayout parent, String title) {
        TextView header = (TextView) LayoutInflater.from(this).inflate(R.layout.item_section_header, parent, false);
        header.setText(title);
        parent.addView(header);
    }

    private void addEmptyView(LinearLayout parent, String text) {
        addEmptyView(parent, text, 16);
    }

    private void addEmptyView(LinearLayout parent, String text, int sp) {
        TextView empty = new TextView(this);
        empty.setText(text);
        empty.setTextColor(0x99FFFFFF);
        empty.setTextSize(sp);
        parent.addView(empty);
    }

    private String getDateDaysAgo(int daysOffset) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, daysOffset);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.getTime());
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == R.id.action_logout) {
            confirmLogout();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.logout)
                .setMessage("Opravdu se chcete odhlásit?")
                .setPositiveButton("Ano", (dialog, which) -> {
                    repository.clearCredentials();
                    startLoginAndFinish();
                })
                .setNegativeButton("Ne", null)
                .show();
    }
}
