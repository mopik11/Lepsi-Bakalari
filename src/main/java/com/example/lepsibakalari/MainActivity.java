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
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

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
import com.example.lepsibakalari.databinding.ItemMarkSingleBinding;
import com.example.lepsibakalari.databinding.LayoutMarksToggleBinding;
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
    private static final int TAB_HOMEWORKS = 3;
    private static final int TAB_MORE = 4;
    private int currentTab = TAB_TIMETABLE;
    private boolean marksByDate = false;
    private MarksResponse lastMarksData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            
            // Adjust both floating bubbles to be under status bar
            int topMargin = systemBars.top + (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
            
            ViewGroup.MarginLayoutParams lpTitle = (ViewGroup.MarginLayoutParams) binding.titleBubble.getLayoutParams();
            lpTitle.topMargin = topMargin;
            binding.titleBubble.setLayoutParams(lpTitle);

            ViewGroup.MarginLayoutParams lpMenu = (ViewGroup.MarginLayoutParams) binding.menuBubble.getLayoutParams();
            lpMenu.topMargin = topMargin;
            binding.menuBubble.setLayoutParams(lpMenu);
            
            return insets;
        });

        repository = new BakalariRepository(this);

        if (!repository.isLoggedIn()) {
            startLoginAndFinish();
            return;
        }

        setSupportActionBar(binding.toolbar);

        // Hide system navigation buttons for a truly immersive "Liquid Glass" look
        WindowInsetsControllerCompat windowInsetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars());
        windowInsetsController.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        );

        // Liquid Glass blur efekt na pozadí (API 31+)
        applyBlurEffect(binding.meshGradient);

        // Ensure floating elements are strictly on top and stay there
        binding.dockContainer.setTranslationZ(100f);
        binding.titleBubble.setTranslationZ(100f);
        binding.menuBubble.setTranslationZ(100f);
        binding.dockContainer.bringToFront();
        binding.titleBubble.bringToFront();
        binding.menuBubble.bringToFront();

        // Menu Bubble Click
        binding.menuBubble.setOnClickListener(v -> showGlassMenu());

        // Apply Glass Touch effects to top bubbles too
        applyGlassTouchEffect(binding.titleBubble);
        applyGlassTouchEffect(binding.menuBubble);

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
            // Subtle blur for the background balls
            view.setRenderEffect(RenderEffect.createBlurEffect(30f, 30f, Shader.TileMode.CLAMP));
        }
    }

    private void setupNavigation() {
        // Nastavení posluchačů kliknutí
        binding.navTimetable.setOnClickListener(v -> switchTab(TAB_TIMETABLE));
        binding.navMarks.setOnClickListener(v -> switchTab(TAB_MARKS));
        binding.navKomens.setOnClickListener(v -> switchTab(TAB_KOMENS));
        binding.navHomeworks.setOnClickListener(v -> switchTab(TAB_HOMEWORKS));
        binding.navMore.setOnClickListener(v -> switchTab(TAB_MORE));

        // Marks Toggle Listeners
        View toggleRoot = binding.marksToggle.getRoot();
        toggleRoot.findViewById(R.id.btnBySubject).setOnClickListener(v -> {
            marksByDate = false;
            updateMarksToggleUI(binding.marksToggle.getRoot());
            if (lastMarksData != null) showMarks(lastMarksData);
        });
        toggleRoot.findViewById(R.id.btnByDate).setOnClickListener(v -> {
            marksByDate = true;
            updateMarksToggleUI(binding.marksToggle.getRoot());
            if (lastMarksData != null) showMarks(lastMarksData);
        });

        // Apple Glass Touch Effect (zmenšení při dotyku)
        applyGlassTouchEffect(binding.navTimetable);
        applyGlassTouchEffect(binding.navMarks);
        applyGlassTouchEffect(binding.navKomens);
        applyGlassTouchEffect(binding.navHomeworks);
        applyGlassTouchEffect(binding.navMore);
    }

    private void updateMarksToggleUI(View root) {
        TextView btnSubj = root.findViewById(R.id.btnBySubject);
        TextView btnDate = root.findViewById(R.id.btnByDate);
        btnSubj.setAlpha(marksByDate ? 0.5f : 1.0f);
        btnSubj.setTypeface(null, marksByDate ? android.graphics.Typeface.NORMAL : android.graphics.Typeface.BOLD);
        btnDate.setAlpha(marksByDate ? 1.0f : 0.5f);
        btnDate.setTypeface(null, marksByDate ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    }

    /**
     * Aplikuje "Apple Glass" efekt při dotyku - jemné zmenšení prvku.
     * Vylepšeno o spolehlivé vracení do původního stavu.
     */
    private void applyGlassTouchEffect(final View view) {
        view.setOnTouchListener(new android.view.View.OnTouchListener() {
            @Override
            public boolean onTouch(android.view.View v, android.view.MotionEvent event) {
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        v.animate().scaleX(0.97f).scaleY(0.97f).alpha(0.85f).setDuration(100).start();
                        return true; // Musíme vrátit true, abychom dostali ACTION_UP
                    
                    case android.view.MotionEvent.ACTION_UP:
                        v.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(150).start();
                        // Pokud jsme pustili prst nad prvkem, vyvoláme click
                        float x = event.getX();
                        float y = event.getY();
                        if (x >= 0 && x <= v.getWidth() && y >= 0 && y <= v.getHeight()) {
                            v.performClick();
                        }
                        return true;

                    case android.view.MotionEvent.ACTION_CANCEL:
                    case android.view.MotionEvent.ACTION_OUTSIDE:
                        v.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(150).start();
                        return true;

                    case android.view.MotionEvent.ACTION_MOVE:
                        float mx = event.getX();
                        float my = event.getY();
                        if (mx < 0 || mx > v.getWidth() || my < 0 || my > v.getHeight()) {
                            // Vyjeli jsme prstem ven - zvětšíme zpět
                            v.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(150).start();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void setAllViewsGone() {
        binding.scrollTimetable.setVisibility(View.GONE);
        binding.scrollMarks.setVisibility(View.GONE);
        binding.scrollKomens.setVisibility(View.GONE);
        binding.scrollHomeworks.setVisibility(View.GONE);
        binding.scrollMore.setVisibility(View.GONE);
    }

    private void setNavIconsInactive() {
        // Reset all icons to half-alpha and original scale
        binding.navTimetableIcon.setColorFilter(0x80FFFFFF);
        binding.navTimetableIcon.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
        
        binding.navMarksIcon.setColorFilter(0x80FFFFFF);
        binding.navMarksIcon.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
        
        binding.navKomensIcon.setColorFilter(0x80FFFFFF);
        binding.navKomensIcon.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
        
        binding.navHomeworksIcon.setColorFilter(0x80FFFFFF);
        binding.navHomeworksIcon.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
        
        binding.navMoreIcon.setColorFilter(0x80FFFFFF);
        binding.navMoreIcon.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
    }

    private void switchTab(int tab) {
        currentTab = tab;
        setAllViewsGone();
        setNavIconsInactive();

        switch (tab) {
            case TAB_TIMETABLE:
                binding.scrollTimetable.setVisibility(View.VISIBLE);
                binding.navTimetableIcon.setColorFilter(0xFFFFFFFF);
                binding.navTimetableIcon.animate().scaleX(1.2f).scaleY(1.2f).setDuration(200).start();
                loadTimetable();
                break;
            case TAB_MARKS:
                binding.scrollMarks.setVisibility(View.VISIBLE);
                binding.navMarksIcon.setColorFilter(0xFFFFFFFF);
                binding.navMarksIcon.animate().scaleX(1.2f).scaleY(1.2f).setDuration(200).start();
                loadMarks();
                break;
            case TAB_KOMENS:
                binding.scrollKomens.setVisibility(View.VISIBLE);
                binding.navKomensIcon.setColorFilter(0xFFFFFFFF);
                binding.navKomensIcon.animate().scaleX(1.2f).scaleY(1.2f).setDuration(200).start();
                loadKomens();
                break;
            case TAB_HOMEWORKS:
                binding.scrollHomeworks.setVisibility(View.VISIBLE);
                binding.navHomeworksIcon.setColorFilter(0xFFFFFFFF);
                binding.navHomeworksIcon.animate().scaleX(1.2f).scaleY(1.2f).setDuration(200).start();
                loadHomeworksTab();
                break;
            case TAB_MORE:
                binding.scrollMore.setVisibility(View.VISIBLE);
                binding.navMoreIcon.setColorFilter(0xFFFFFFFF);
                binding.navMoreIcon.animate().scaleX(1.2f).scaleY(1.2f).setDuration(200).start();
                loadMore();
                break;
        }
    }

    private void loadTimetable() {
        binding.textLoading.setVisibility(View.GONE);
        binding.lessonsContainer.removeAllViews();
        addEmptyView(binding.lessonsContainer, getString(R.string.loading));

        repository.getTimetableToday(new Callback<TimetableResponse>() {
            @Override
            public void onResponse(Call<TimetableResponse> call, Response<TimetableResponse> response) {
                binding.lessonsContainer.removeAllViews();
                if (!response.isSuccessful() || response.body() == null) {
                    addEmptyView(binding.lessonsContainer, "Chyba načítání rozvrhu.");
                    return;
                }
                showTimetable(response.body());
            }

            @Override
            public void onFailure(Call<TimetableResponse> call, Throwable t) {
                binding.lessonsContainer.removeAllViews();
                addEmptyView(binding.lessonsContainer, "Chyba sítě: " + t.getMessage());
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

        String todayStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        for (int dayIndex = 0; dayIndex < days.size(); dayIndex++) {
            TimetableResponse.Day day = days.get(dayIndex);
            String dateStr = day.getDate() != null ? day.getDate().substring(0, Math.min(10, day.getDate().length())) : "";
            
            // Show only today and future days
            if (dateStr.compareTo(todayStr) < 0) continue;

            TextView dayHeader = (TextView) inflater.inflate(R.layout.item_day_header, binding.lessonsContainer, false);
            dayHeader.setText(formatDisplayDate(dateStr));
            LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            
            // Only no margin for the very first VISIBLE day
            headerParams.topMargin = binding.lessonsContainer.getChildCount() == 0 ? 0 : dp24;
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

                // Substitution Icon (Výměna/Suplování)
                itemBinding.icSubstitution.setVisibility(atom.getChange() != null ? View.VISIBLE : View.GONE);

                binding.lessonsContainer.addView(itemBinding.getRoot());
                applyGlassTouchEffect(itemBinding.getRoot());

                // AUTOMATIC ENTRANCE ANIMATION (The Zoomer)
                itemBinding.getRoot().setAlpha(0f);
                itemBinding.getRoot().setScaleX(0.92f);
                itemBinding.getRoot().setScaleY(0.92f);
                itemBinding.getRoot().animate()
                    .alpha(1.0f)
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(450)
                    .setStartDelay(binding.lessonsContainer.getChildCount() * 40L)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.1f))
                    .start();

                // Feature: Lesson Detail Dialog
                final TimetableResponse.Subject fSubj = subj;
                final TimetableResponse.Room fRoom = room;
                final TimetableResponse.Hour fHour = hour;
                final TimetableResponse.Atom fAtom = atom;

                itemBinding.getRoot().setOnClickListener(v -> {
                    showLessonDetail(fSubj, fRoom, fHour, fAtom, data.getTeachers(), fAtom.getTeacherId());
                });
            }
        }
    }

    private void showLessonDetail(TimetableResponse.Subject subject, 
                                  TimetableResponse.Room room, 
                                  TimetableResponse.Hour hour, 
                                  TimetableResponse.Atom atom,
                                  List<TimetableResponse.Teacher> teachers,
                                  String teacherId) {
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        // We use a custom view for the Liquid Glass look
        com.example.lepsibakalari.databinding.DialogLessonDetailBinding dBinding = 
            com.example.lepsibakalari.databinding.DialogLessonDetailBinding.inflate(getLayoutInflater());
        
        String subjName = subject != null && subject.getName() != null ? subject.getName() : "Předmět";
        dBinding.dialogSubjectName.setText(subjName);

        String tName = "-";
        if (teachers != null && teacherId != null) {
            String tid = teacherId.trim();
             TimetableResponse.Teacher t = teachers.stream()
                     .filter(te -> te.getId().trim().equals(tid))
                     .findFirst().orElse(null);
             if (t != null) tName = t.getName();
        }
        dBinding.dialogTeacher.setText("Učitel: " + tName);

        String rName = "-";
        if (room != null) {
            // Priority: Full Name -> Abbrev -> Raw ID
            if (room.getName() != null && !room.getName().trim().isEmpty()) {
                rName = room.getName();
            } else if (room.getAbbrev() != null && !room.getAbbrev().trim().isEmpty()) {
                rName = room.getAbbrev();
            } else {
                rName = atom.getRoomId();
            }
        } else if (atom.getRoomId() != null) {
            rName = atom.getRoomId();
        }
        
        dBinding.dialogRoom.setText("Místnost: " + rName);
        dBinding.dialogRoom.setVisibility(View.VISIBLE);

        String theme = atom.getTheme() != null && !atom.getTheme().isEmpty() ? atom.getTheme() : "Bez tématu";
        dBinding.dialogTheme.setText("Téma: " + theme);

        String time = hour != null ? hour.getBeginTime() + " - " + hour.getEndTime() : "";
        dBinding.dialogTime.setText(time);

        builder.setView(dBinding.getRoot());
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
                lp.dimAmount = 0.85f; // Deep dark dim for "Not White" real blur
                lp.setBlurBehindRadius(250); // Massive blur for ultra-heavy glass look
                dialog.getWindow().setAttributes(lp);
            }
        }
        dialog.show();
    }

    private void loadMarks() {
        binding.marksContent.removeAllViews();
        addEmptyView(binding.marksContent, getString(R.string.loading));

        repository.getMarks(new Callback<MarksResponse>() {
            @Override
            public void onResponse(Call<MarksResponse> call, Response<MarksResponse> response) {
                binding.marksContent.removeAllViews();
                if (!response.isSuccessful() || response.body() == null) {
                    addEmptyView(binding.marksContent, "Chyba načítání známek. Zkuste to později.");
                    return;
                }
                lastMarksData = response.body();
                showMarks(lastMarksData);
            }

            @Override
            public void onFailure(Call<MarksResponse> call, Throwable t) {
                binding.marksContent.removeAllViews();
                addEmptyView(binding.marksContent, "Chyba sítě: " + t.getMessage());
            }
        });
    }

    private void showMarks(MarksResponse data) {
        binding.marksContent.removeAllViews();
        if (marksByDate) {
            showMarksByDate(data);
        } else {
            showMarksBySubject(data);
        }
    }

    private void showMarksBySubject(MarksResponse data) {
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
            applyGlassTouchEffect(itemBinding.getRoot());

            // ANIMATION
            itemBinding.getRoot().setAlpha(0f);
            itemBinding.getRoot().setScaleX(0.92f);
            itemBinding.getRoot().setScaleY(0.92f);
            itemBinding.getRoot().animate()
                .alpha(1.0f)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(450)
                .setStartDelay(binding.marksContent.getChildCount() * 40L)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.1f))
                .start();
        }
    }

    private void showMarksByDate(MarksResponse data) {
        java.util.List<MarkWithSubject> allMarks = new java.util.ArrayList<>();
        if (data.getSubjects() != null) {
            for (MarksResponse.SubjectMarks sm : data.getSubjects()) {
                String subjName = sm.getSubject() != null ? sm.getSubject().getName() : "-";
                if (sm.getMarks() != null) {
                    for (MarksResponse.Mark m : sm.getMarks()) {
                        allMarks.add(new MarkWithSubject(m, subjName));
                    }
                }
            }
        }

        if (allMarks.isEmpty()) {
            addEmptyView(binding.marksContent, "Žádné známky");
            return;
        }

        // Sort by date - newest first
        allMarks.sort((a, b) -> {
            String da = a.mark.getDate();
            String db = b.mark.getDate();
            if (da == null) return 1;
            if (db == null) return -1;
            return db.compareTo(da);
        });

        LayoutInflater inflater = LayoutInflater.from(this);
        for (MarkWithSubject mws : allMarks) {
            ItemMarkSingleBinding itemBinding = ItemMarkSingleBinding.inflate(inflater, binding.marksContent, false);
            itemBinding.textMark.setText(mws.mark.getMarkText());
            itemBinding.textSubject.setText(mws.subjectName);
            itemBinding.textTopic.setText(mws.mark.getCaption() != null ? mws.mark.getCaption() : "-");
            
            String dateStr = mws.mark.getDate();
            if (dateStr != null && dateStr.length() >= 5) {
                // Try to extract date even if short
                String displayDate = dateStr.length() >= 10 ? formatShortDate(dateStr.substring(0, 10)) : dateStr;
                itemBinding.textDate.setText(displayDate);
                itemBinding.textDate.setVisibility(View.VISIBLE);
            } else {
                itemBinding.textDate.setVisibility(View.GONE);
            }

            binding.marksContent.addView(itemBinding.getRoot());
            applyGlassTouchEffect(itemBinding.getRoot());
            
            // AUTOMATIC ENTRANCE ANIMATION (The Zoomer)
            itemBinding.getRoot().setAlpha(0f);
            itemBinding.getRoot().setScaleX(0.85f);
            itemBinding.getRoot().setScaleY(0.85f);
            itemBinding.getRoot().animate()
                .alpha(1.0f)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(400)
                .setStartDelay(binding.marksContent.getChildCount() * 50L)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.2f))
                .start();
        }
    }

    private static class MarkWithSubject {
        MarksResponse.Mark mark;
        String subjectName;
        MarkWithSubject(MarksResponse.Mark m, String s) {
            this.mark = m;
            this.subjectName = s;
        }
    }

    private void loadKomens() {
        binding.textKomensLoading.setVisibility(View.GONE);
        binding.komensList.removeAllViews();
        addEmptyView(binding.komensList, getString(R.string.loading));

        repository.getKomensReceived(new Callback<KomensResponse>() {
            @Override
            public void onResponse(Call<KomensResponse> call, Response<KomensResponse> response) {
                binding.komensList.removeAllViews();
                if (!response.isSuccessful() || response.body() == null) {
                    addEmptyView(binding.komensList, "Chyba načítání zpráv.");
                    return;
                }
                showKomens(response.body());
            }

            @Override
            public void onFailure(Call<KomensResponse> call, Throwable t) {
                binding.komensList.removeAllViews();
                addEmptyView(binding.komensList, "Chyba sítě: " + t.getMessage());
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

        // Sort: Newest first
        messages = new java.util.ArrayList<>(messages);
        messages.sort((a, b) -> {
            String da = a.getSentDate();
            String db = b.getSentDate();
            if (da == null) return 1;
            if (db == null) return -1;
            return db.compareTo(da);
        });

        LayoutInflater inflater = LayoutInflater.from(this);
        for (KomensResponse.KomensMessage msg : messages) {
            ItemKomensBinding itemBinding = ItemKomensBinding.inflate(inflater, binding.komensList, false);
            itemBinding.textTitle.setText(msg.getTitle() != null ? msg.getTitle() : "-");
            String sName = msg.getSender() != null ? msg.getSender().getName() : "";
            itemBinding.textSender.setText(sName);
            itemBinding.textAvatar.setText(getInitials(sName));

            String sentDate = msg.getSentDate();
            if (sentDate != null && sentDate.length() >= 10) {
                // Showing date and time in the badge for messages
                String displayDate = formatShortDate(sentDate.substring(0, 10));
                if (sentDate.length() >= 16) {
                    displayDate += " " + sentDate.substring(11, 16);
                }
                itemBinding.textDate.setText(displayDate);
                itemBinding.textDate.setVisibility(View.VISIBLE);
            } else {
                itemBinding.textDate.setVisibility(View.GONE);
            }
            binding.komensList.addView(itemBinding.getRoot());
            applyGlassTouchEffect(itemBinding.getRoot());

            // ANIMATION
            itemBinding.getRoot().setAlpha(0f);
            itemBinding.getRoot().setScaleX(0.92f);
            itemBinding.getRoot().setScaleY(0.92f);
            itemBinding.getRoot().animate()
                .alpha(1.0f)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(450)
                .setStartDelay(binding.komensList.getChildCount() * 40L)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.1f))
                .start();
        }
    }

    private void loadMore() {
        binding.moreContent.removeAllViews();

        LinearLayout finalMarksContainer = createSectionContainer();
        LinearLayout eventsContainer = createSectionContainer();
        LinearLayout substitutionsContainer = createSectionContainer();

        addSectionHeader(binding.moreContent, getString(R.string.section_substitutions));
        binding.moreContent.addView(substitutionsContainer);

        addSectionHeader(binding.moreContent, getString(R.string.section_final_marks));
        binding.moreContent.addView(finalMarksContainer);

        addSectionHeader(binding.moreContent, getString(R.string.section_events));
        binding.moreContent.addView(eventsContainer);

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
        List<MarksFinalResponse.CertificateTerm> termsList = data.getCertificateTerms();
        if (termsList == null || termsList.isEmpty()) {
            addEmptyView(container, "Žádná vysvědčení", 12);
            return;
        }

        // Reverse list to show newest first
        termsList = new java.util.ArrayList<>(termsList);
        java.util.Collections.reverse(termsList);

        for (MarksFinalResponse.CertificateTerm term : termsList) {
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
            applyGlassTouchEffect(tv);

            // ANIMATION
            tv.setAlpha(0f);
            tv.setScaleX(0.95f);
            tv.animate()
                .alpha(1.0f)
                .scaleX(1.0f)
                .setDuration(400)
                .setStartDelay(container.getChildCount() * 60L)
                .start();
        }
    }

    private void loadHomeworksTab() {
        binding.homeworksContent.removeAllViews();
        addEmptyView(binding.homeworksContent, getString(R.string.loading), 14);

        // Fetch only from today onwards
        String from = getDateDaysAgo(0);
        String to = getDateDaysAgo(30);
        repository.getHomeworks(from, to, new Callback<HomeworksResponse>() {
            @Override
            public void onResponse(Call<HomeworksResponse> call, Response<HomeworksResponse> response) {
                binding.homeworksContent.removeAllViews();
                if (response.isSuccessful() && response.body() != null) {
                    showHomeworks(response.body(), binding.homeworksContent);
                } else {
                    addEmptyView(binding.homeworksContent, getString(R.string.no_homeworks), 14);
                }
            }

            @Override
            public void onFailure(Call<HomeworksResponse> call, Throwable t) {
                binding.homeworksContent.removeAllViews();
                addEmptyView(binding.homeworksContent, "Chyba: " + t.getMessage(), 14);
            }
        });
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
            
            // Local check logic
            String hwId = hw.getId() != null ? hw.getId() : (hw.getSubject() != null ? hw.getSubject().getAbbrev() : "") + hw.getDateEnd();
            boolean isChecked = getSharedPreferences("homework_prefs", MODE_PRIVATE).getBoolean(hwId, false);
            itemBinding.checkDone.setChecked(isChecked);
            
            String finalHwId = hwId;
            itemBinding.checkDone.setOnCheckedChangeListener((buttonView, isChecked1) -> {
                getSharedPreferences("homework_prefs", MODE_PRIVATE).edit().putBoolean(finalHwId, isChecked1).apply();
            });

            container.addView(itemBinding.getRoot());
            applyGlassTouchEffect(itemBinding.getRoot());

            // ANIMATION
            itemBinding.getRoot().setAlpha(0f);
            itemBinding.getRoot().setScaleX(0.92f);
            itemBinding.getRoot().setScaleY(0.92f);
            itemBinding.getRoot().animate()
                .alpha(1.0f)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(450)
                .setStartDelay(container.getChildCount() * 40L)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.1f))
                .start();
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

        // Sort: Newest first based on start time
        events = new java.util.ArrayList<>(events);
        events.sort((a, b) -> {
            String da = (a.getTimes() != null && !a.getTimes().isEmpty()) ? a.getTimes().get(0).getStartTime() : "";
            String db = (b.getTimes() != null && !b.getTimes().isEmpty()) ? b.getTimes().get(0).getStartTime() : "";
            return db.compareTo(da);
        });

        for (EventsResponse.Event ev : events) {
            TextView tv = createGlassTextView((ev.getTitle() != null ? ev.getTitle() : "-") + "\n" +
                    (ev.getEventType() != null && ev.getEventType().getName() != null ? ev.getEventType().getName() : ""));
            container.addView(tv);
            applyGlassTouchEffect(tv);

            // ANIMATION
            tv.setAlpha(0f);
            tv.setTranslationX(-20f);
            tv.animate()
                .alpha(1.0f)
                .translationX(0f)
                .setDuration(400)
                .setStartDelay(container.getChildCount() * 50L)
                .start();
        }
    }

    private void showSubstitutions(SubstitutionsResponse data, LinearLayout container) {
        List<SubstitutionsResponse.Change> changes = data.getChanges();
        if (changes == null || changes.isEmpty()) {
            addEmptyView(container, getString(R.string.no_substitutions), 12);
            return;
        }

        String today = getDateDaysAgo(0);
        // Filter: Today inclusive
        List<SubstitutionsResponse.Change> filtered = new java.util.ArrayList<>();
        for (SubstitutionsResponse.Change ch : changes) {
            String day = ch.getDay();
            if (day != null && day.compareTo(today) >= 0) {
                filtered.add(ch);
            }
        }

        if (filtered.isEmpty()) {
            addEmptyView(container, "Žádné aktuální ani budoucí změny", 12);
            return;
        }

        // Sort: Chronological (nearest first)
        filtered.sort((a, b) -> {
            String ta = a.getDay() != null ? a.getDay() : "";
            String tb = b.getDay() != null ? b.getDay() : "";
            return ta.compareTo(tb);
        });

        for (SubstitutionsResponse.Change ch : filtered) {
            String day = ch.getDay() != null && ch.getDay().length() >= 10 ? formatShortDate(ch.getDay().substring(0, 10)) : "";
            String hour = ch.getHours() != null ? ch.getHours() : "";
            TextView tv = createGlassTextView(day + " | " + hour + ". hod: " + (ch.getDescription() != null ? ch.getDescription() : ""));
            container.addView(tv);
            applyGlassTouchEffect(tv);

            // ANIMATION
            tv.setAlpha(0f);
            tv.setTranslationY(30f);
            tv.animate()
                .alpha(1.0f)
                .translationY(0f)
                .setDuration(350)
                .setStartDelay(container.getChildCount() * 60L)
                .start();
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
        empty.setTextColor(0xFFFFFFFF);
        empty.setTextSize(sp);
        empty.setGravity(android.view.Gravity.CENTER);
        empty.setBackgroundResource(R.drawable.glass_card);
        
        int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, getResources().getDisplayMetrics());
        empty.setPadding(pad, pad, pad, pad);
        
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT);
        int margin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
        lp.setMargins(0, margin, 0, margin);
        empty.setLayoutParams(lp);

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
    private String getInitials(String name) {
        if (name == null || name.isEmpty()) return "?";
        // Remove common academic titles if needed, but simple split is okay for now
        String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2) {
            // Find first and last name initials
            return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
        }
        return name.substring(0, Math.min(2, name.length())).toUpperCase();
    }

    private int getSubjectColor(String id) {
        if (id == null || id.isEmpty()) return 0xFF6366F1;
        int hash = id.hashCode();
        float[] hsv = new float[3];
        hsv[0] = Math.abs(hash % 360); 
        hsv[1] = 0.65f; 
        hsv[2] = 0.95f; 
        return android.graphics.Color.HSVToColor(hsv);
    }

    private void showGlassMenu() {
        android.app.Dialog dialog = new android.app.Dialog(this);
        View menuView = LayoutInflater.from(this).inflate(R.layout.layout_glass_menu, null);
        dialog.setContentView(menuView);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
                lp.dimAmount = 0.85f;
                lp.setBlurBehindRadius(150);
                dialog.getWindow().setAttributes(lp);
            }
        }

        View btnLogout = menuView.findViewById(R.id.btnLogout);
        applyGlassTouchEffect(btnLogout);
        btnLogout.setOnClickListener(v -> {
            dialog.dismiss();
            confirmLogout();
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.85),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
    }
}
