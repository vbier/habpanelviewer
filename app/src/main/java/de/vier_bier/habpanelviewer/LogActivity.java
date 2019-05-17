package de.vier_bier.habpanelviewer;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class LogActivity extends ScreenControllingActivity {
    private static final String TAG = LogActivity.class.getSimpleName();

    private ProgressBar mProgressBar;
    private TextView mLogTextView;
    private ScrollView mScrollView;
    private LinearLayout mEmptyView;

    private MenuItem mClearItem;
    private MenuItem mShareItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_log);

        mLogTextView = findViewById(R.id.log);
        mProgressBar = findViewById(R.id.progressBar);
        mScrollView = findViewById(R.id.scrollview);
        mEmptyView = findViewById(android.R.id.empty);

        Toolbar myToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);
        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String theme = prefs.getString(Constants.PREF_THEME, "dark");

        if ("dark".equals(theme)) {
            myToolbar.setPopupTheme(R.style.ThemeOverlay_AppCompat_Dark);
        } else {
            myToolbar.setPopupTheme(R.style.ThemeOverlay_AppCompat_Light);
        }

        setUiState(true, false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setUiState(true, false);
        new GetLogFromAdbTask().execute(false);
    }

    @Override
    protected View getScreenOnView() {
        return mLogTextView;
    }

    private void setUiState(boolean isLoading, boolean isEmpty) {
        mProgressBar.setVisibility(isLoading && !isEmpty ? View.VISIBLE : View.GONE);
        mLogTextView.setVisibility(isLoading && !isEmpty ? View.GONE : View.VISIBLE);
        mEmptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);

        if (mClearItem != null) {
            mClearItem.setVisible(!isEmpty);
        }
        if (mShareItem != null) {
            mShareItem.setVisible(!isEmpty);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.d(TAG, "onCreateOptionsMenu()");
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.log_menu, menu);

        mClearItem = menu.findItem(R.id.delete_log);
        UiUtil.tintItemPreV21(mClearItem, getApplicationContext(), getTheme());

        mShareItem = menu.findItem(R.id.share_log);
        UiUtil.tintItemPreV21(mShareItem, getApplicationContext(), getTheme());

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.d(TAG, "onOptionsItemSelected()");
        switch (item.getItemId()) {
            case R.id.delete_log:
                setUiState(true, false);
                new GetLogFromAdbTask().execute(true);
                return true;
            case R.id.share_log:
                Intent sendIntent = new Intent();
                sendIntent.setAction(Intent.ACTION_SEND);
                sendIntent.putExtra(Intent.EXTRA_TEXT, mLogTextView.getText());
                sendIntent.setType("text/plain");
                startActivity(sendIntent);
                return true;
            case android.R.id.home:
                finish();
                // fallthrough
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private class GetLogFromAdbTask extends AsyncTask<Boolean, Void, String> {
        @Override
        protected String doInBackground(Boolean... clear) {
            StringBuilder logBuilder = new StringBuilder();
            String separator = System.getProperty("line.separator");
            Process process;
            try {
                if (clear[0]) {
                    Log.d(TAG, "Clear log");
                    Runtime.getRuntime().exec("logcat -b all -c");
                    return "";
                }
                process = Runtime.getRuntime().exec("logcat -b all -v threadtime -d");
            } catch (Exception e) {
                Log.e(TAG, "Error reading process", e);
                return Log.getStackTraceString(e);
            }
            if (process == null) {
                return "Process is null";
            }
            try (InputStreamReader reader = new InputStreamReader(process.getInputStream());
                 BufferedReader bufferedReader = new BufferedReader(reader)) {
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    logBuilder.append(line);
                    logBuilder.append(separator);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error reading log", e);
                return Log.getStackTraceString(e);
            }

            String log = logBuilder.toString();
            SharedPreferences sharedPreferences =
                    PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            log = redactHost(log,
                    sharedPreferences.getString(Constants.PREF_SERVER_URL, ""),
                    "<openhab-local-address>");
            return log;
        }

        @Override
        protected void onPostExecute(String log) {
            mLogTextView.setText(log);
            setUiState(false, TextUtils.isEmpty(log));
            mScrollView.post(() -> mScrollView.fullScroll(View.FOCUS_DOWN));
        }
    }

    private String redactHost(String text, String url, String replacement) {
        if (!TextUtils.isEmpty(url)) {
            String host = getHostFromUrl(url);
            if (host != null) {
                return text.replaceAll(host, replacement);
            }
        }
        return text;
    }

    private static String getHostFromUrl(String url) {
        Uri uri = Uri.parse(url);
        return uri.getHost();
    }
}
