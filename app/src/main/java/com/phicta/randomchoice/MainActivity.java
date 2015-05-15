package com.phicta.randomchoice;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;


public class MainActivity extends ActionBarActivity {

    private ListView mListView;
    private ArrayAdapter<String> mAdapter;
    private SQLiteDatabase mSQLiteDatabase;
    private MyDBHelper mMyDBHelper;
    private String[] allColumns = {MyDBHelper.COLUMN_ID, MyDBHelper.COLUMN_CONTENT};
    private Button mRandomButton;
    public final String LOG_TAG = "RandomChoice";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mMyDBHelper = new MyDBHelper(this, MyDBHelper.DATABASE_NAME, null, MyDBHelper.VERSION);
        mSQLiteDatabase = mMyDBHelper.getWritableDatabase();

        mListView = (ListView) findViewById(R.id.listView);
        mAdapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1);
        showContentsToListView();   //Read the contents from database and show on the screen.

        mRandomButton = (Button) findViewById(R.id.button);

        mRandomButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int countContents = mAdapter.getCount();

                if (countContents == 0) {
                    Toast.makeText(getApplicationContext(), getResources().getString(R.string.list_is_empty), Toast.LENGTH_LONG).show();
                    return;
                }

                Random ran = new Random(System.currentTimeMillis());
                int choosedIndex = ran.nextInt(countContents);
                String choosedString = mAdapter.getItem(choosedIndex);

                resultDialog(choosedString);
            }
        });

        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
                deleteDialog(mAdapter.getItem(arg2));
            }
        });
    }

    private void showContentsToListView() {
        mAdapter.clear();
        Cursor cursor = mSQLiteDatabase.query(MyDBHelper.DATABASE_TABLE, allColumns,
                null, null, null, null, null);
        if (cursor.getCount() == 0)
            return;
        cursor.moveToFirst();
        mAdapter.add(cursor.getString(1));
        while (cursor.moveToNext()) {
            mAdapter.add(cursor.getString(1));
        }
        mListView.setAdapter(mAdapter);
    }

    /**
     * Add content into SQLite and show on ListView.
     * Return false if content has benn existing in the database.
     * @param name: The content is inserted.
     */
    private boolean addContent(String name) {
        // Check whether redundant
        Cursor cursor = mSQLiteDatabase.query(MyDBHelper.DATABASE_TABLE, allColumns,
                MyDBHelper.COLUMN_CONTENT + "='" + name + "'", null, null, null, null);
        if (cursor.getCount() != 0) { // cursor size is not zero means this string exist at database.
            cursor.close();
            return false;
        }

        // Write the name into SQLite
        ContentValues values = new ContentValues();
        values.put(MyDBHelper.COLUMN_CONTENT, name);
        long insertId = mSQLiteDatabase.insert(MyDBHelper.DATABASE_TABLE, null, values);

        // Retrieve the content from the database immediately.
        cursor = mSQLiteDatabase.query(MyDBHelper.DATABASE_TABLE, allColumns,
                MyDBHelper.COLUMN_ID + "=" + insertId, null, null, null, null);
        cursor.moveToFirst();

        // Add this name to the ListView.
        mAdapter.add(cursor.getString(1));
        mListView.setAdapter(mAdapter);
        return true;
    }

    private void deleteContent(String name) {
        mSQLiteDatabase.delete(MyDBHelper.DATABASE_TABLE, MyDBHelper.COLUMN_CONTENT + "='" + name + "'", null);
    }

    private ArrayList<String> parseCSV(String filePath) {
        File file = new File(filePath);
        ArrayList<String> contentList = new ArrayList<String>();
        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;

            while ((line = br.readLine()) != null) {
                String[] splitString = line.split(",");
                for (String splitedContent : splitString)
                    contentList.add(splitedContent);
                
            }
            br.close();
            return contentList;
        } catch (IOException e){
            Toast.makeText(getApplicationContext(), getResources().getString(R.string.file_is_error), Toast.LENGTH_LONG);
            Log.e(LOG_TAG, "parseCSV()$" + e.toString());
        }
        return null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_import) {     // Open a window that select a file by the file manager.
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("file/*");
            Intent pickIntent = Intent.createChooser(intent, getResources().getString(R.string.choose_csv));
            try {
                startActivityForResult(pickIntent, 0);
            } catch (android.content.ActivityNotFoundException e) {
                Toast.makeText(this, getResources().getString(R.string.install_information), Toast.LENGTH_LONG).show();
            }
            return true;
        } else if (id == R.id.action_input) {   // Open a input dialog.
            inputDialog();
        } else if (id == R.id.action_clear) {   // Clear contents are shown in list.
            mSQLiteDatabase.delete(MyDBHelper.DATABASE_TABLE, null, null);
            showContentsToListView();
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Get the file path after selected the file.
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            Uri uri = data.getData();
            if (uri != null) {
                ArrayList<String> contentList = parseCSV(uri.getPath());
                if (contentList == null)
                    return;
                for (String content : contentList) {
                    addContent(content);
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mMyDBHelper.close();
    }

    private void resultDialog(String choosedString) {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle(getResources().getString(R.string.result));
        builder.setMessage(choosedString);
        builder.setPositiveButton(getResources().getString(R.string.action_ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.create().show();
    }

    private void inputDialog() {
        LayoutInflater inflater = getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_input, (ViewGroup) findViewById(R.id.dialog));
        final EditText mInputEditText = (EditText)layout.findViewById(R.id.input_content);

        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setView(layout);
        builder.setTitle(getResources().getString(R.string.input_reminder));
        builder.setPositiveButton(getResources().getString(R.string.action_enter), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String inputContent = mInputEditText.getText().toString();
                addContent(inputContent);
            }
        });
        builder.setNegativeButton(getResources().getString(R.string.action_finish), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.create().show();
    }

    private void deleteDialog(final String content) {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle(getResources().getString(R.string.action_delete));
        builder.setMessage(getResources().getString(R.string.delete_information) + content);
        builder.setPositiveButton(getResources().getString(R.string.action_yes), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                deleteContent(content);
                showContentsToListView();
            }
        });
        builder.setNegativeButton(getResources().getString(R.string.action_no), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.create().show();
    }
}
