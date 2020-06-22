package com.hifeful.notekeeper;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Calendar;

public class MainActivity extends AppCompatActivity implements RadioGroup.OnCheckedChangeListener {
    public static final String TAG = MainActivity.class.getSimpleName();
    public static final String SORT_TYPE = "sortType";
    public static final String SORT_ORDER = "sortOrder";

    // UI
    private Toolbar toolbar;
    private RecyclerView recyclerView;

    private View popupView;
    private RadioGroup sortTypes;
    private RadioGroup sortOrders;

    private ImageButton sortBy;

    // Variables
    private ArrayList<Note> notes;
    private NoteDatabase noteDatabase = new NoteDatabase(this);
    private NoteAdapter noteAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toolbar = findViewById(R.id.main_toolbar);
        setSupportActionBar(toolbar);

        popupView = View.inflate(this, R.layout.layout_popup_sort, null);
        sortTypes = popupView.findViewById(R.id.sortTypes);
        sortOrders = popupView.findViewById(R.id.sortOrders);

        recyclerView = findViewById(R.id.note_recycler);

        notes = new ArrayList<>();

        noteAdapter = new NoteAdapter(MainActivity.this, notes, noteDatabase);
        recyclerView.setAdapter(noteAdapter);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);

        SwipeController swipeController = new SwipeController(this, noteAdapter, recyclerView);
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(swipeController);
        itemTouchHelper.attachToRecyclerView(recyclerView);

        new GetAllNotesTask().execute();

        FloatingActionButton fab = findViewById(R.id.add_button);
        fab.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, NoteActivity.class);
            intent.putExtra(NoteActivity.ACTION, false);
            intent.putExtra(NoteActivity.COLOR, Color.parseColor("#FAFAFA"));

            startActivityForResult(intent, noteAdapter.getItemCount());
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        noteDatabase.close();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);

        sortBy = (ImageButton) menu.findItem(R.id.action_sortBy).getActionView();

        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                noteAdapter.getNotesFilter().filter(newText);
                return false;
            }
        });

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_sortBy) {
            int width = LinearLayout.LayoutParams.WRAP_CONTENT;
            int height = LinearLayout.LayoutParams.WRAP_CONTENT;

            PopupWindow popupWindow = new PopupWindow(popupView, width, height, true);
            popupWindow.setElevation(24);

            popupWindow.showAtLocation(popupView, Gravity.END | Gravity.TOP, 0,
                    locateView(toolbar).bottom);

            sortTypes.setOnCheckedChangeListener(this);
            sortOrders.setOnCheckedChangeListener(this);
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        String title;
        String text;
        int color;

        if (requestCode == noteAdapter.getItemCount()) {
            if (resultCode == RESULT_OK) {
                if (data != null){
                    title = data.getStringExtra(NoteActivity.TITLE);
                    text = data.getStringExtra(NoteActivity.TEXT);
                    color = data.getIntExtra(NoteActivity.COLOR, getResources()
                                        .getColor(android.R.color.background_light));

                    noteAdapter.addNote(title, text, Calendar.getInstance().getTime(), color);
                }
            }
        } else {
            if (resultCode == RESULT_OK) {
                if (data != null) {
                    title = data.getStringExtra(NoteActivity.TITLE);
                    text = data.getStringExtra(NoteActivity.TEXT);
                    color = data.getIntExtra(NoteActivity.COLOR, getResources()
                                            .getColor(android.R.color.background_light));

                    Note note = new Note(-1, title, text, Calendar.getInstance().getTime(), color);
                    note.setListPosition(requestCode);

                    noteAdapter.updateNote(note);
                }
            }
        }
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        sortNotes();
        saveSortStates();
    }

    private void loadSortStates() {
        SharedPreferences sharedPreferences = getPreferences(Context.MODE_PRIVATE);
        ((RadioButton)sortTypes.getChildAt(sharedPreferences.getInt(SORT_TYPE, 1)))
                .setChecked(true);
        ((RadioButton)sortOrders.getChildAt(sharedPreferences.getInt(SORT_ORDER, 1)))
                .setChecked(true);
        sortNotes();
    }

    private void saveSortStates() {
        RadioButton sortType = sortTypes.findViewById(sortTypes.getCheckedRadioButtonId());
        RadioButton sortOrder = sortOrders.findViewById(sortOrders.getCheckedRadioButtonId());

        SharedPreferences sharedPreferences = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(SORT_TYPE, sortType.getText().equals("Title") ? 0 : 1);
        editor.putInt(SORT_ORDER, sortOrder.getText().equals("Ascending") ? 0 : 1);
        editor.apply();
    }

    private void sortNotes() {
        RadioButton sortType = sortTypes.findViewById(sortTypes.getCheckedRadioButtonId());
        RadioButton sortOrder = sortOrders.findViewById(sortOrders.getCheckedRadioButtonId());

        if (sortType.getText().equals("Title")) {
            if (sortOrder.getText().equals("Ascending")) {
                noteAdapter.sortBy("Title", "Ascending");
            } else if (sortOrder.getText().equals("Descending")) {
                noteAdapter.sortBy("Title", "Descending");
            }
        } else if (sortType.getText().equals("Date")) {
            if (sortOrder.getText().equals("Ascending")) {
                noteAdapter.sortBy("Date", "Ascending");
            } else if (sortOrder.getText().equals("Descending")) {
                noteAdapter.sortBy("Date", "Descending");
            }
        }
    }

    public static Rect locateView(View v) {
        int[] loc_int = new int[2];
        if (v == null) return null;
        try {
            v.getLocationOnScreen(loc_int);
        } catch (NullPointerException npe) {
            //Happens when the view doesn't exist on screen anymore.
            return null;
        }
        Rect location = new Rect();
        location.left = loc_int[0];
        location.top = loc_int[1];
        location.right = location.left + v.getWidth();
        location.bottom = location.top + v.getHeight();
        return location;
    }

    @SuppressLint("StaticFieldLeak")
    private class GetAllNotesTask extends AsyncTask<Void, Void, ArrayList<Note>> {

        @Override
        protected ArrayList<Note> doInBackground(Void... voids) {
            noteDatabase.open();
            return noteDatabase.getAll();
        }

        @Override
        protected void onPostExecute(ArrayList<Note> dbNotes) {
            if (dbNotes != null) {
                notes.clear();
                notes.addAll(dbNotes);
                noteAdapter.notesForFilter.addAll(dbNotes);
                noteAdapter.notifyDataSetChanged();

                loadSortStates();
            }
        }
    }
}
