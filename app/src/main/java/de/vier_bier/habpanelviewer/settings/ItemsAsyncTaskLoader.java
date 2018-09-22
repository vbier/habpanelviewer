package de.vier_bier.habpanelviewer.settings;

import android.content.AsyncTaskLoader;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

import de.vier_bier.habpanelviewer.MainActivity;
import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.ssl.ConnectionUtil;

/**
 * Async Task Loader for loading list of openHAB items via rest API.
 */
class ItemsAsyncTaskLoader extends AsyncTaskLoader<List<String>> {
    private static final String TAG = "HPV-ItemsAsyncTaskLo";

    private String mServerUrl = "";
    private List<String> mData;

    ItemsAsyncTaskLoader(Context context) {
        super(context);

        try {
            ConnectionUtil.getInstance().setContext(context);
        } catch (Exception e) {
            Log.e(TAG, "failed to initialize ConnectionUtil", e);
        }
    }

    void setServerUrl(String url) {
        mServerUrl = url;
        onContentChanged();
    }

    @Override
    protected void onStartLoading() {
        if (mData != null) {
            // Use cached data
            deliverResult(mData);
        }

        if (takeContentChanged() || mData == null) {
            // We have no data, so kick off loading it
            forceLoad();
        }
    }

    @Override
    public List<String> loadInBackground() {
        if (mServerUrl == null || mServerUrl.isEmpty()) {
            return null;
        }

        try {
            HttpURLConnection urlConnection = ConnectionUtil.getInstance().createUrlConnection(mServerUrl + "/rest/items/");
            StringBuilder response = new StringBuilder();
            try {
                BufferedInputStream in = new BufferedInputStream(urlConnection.getInputStream());

                byte[] contents = new byte[1024];

                int bytesRead;
                while (!isLoadInBackgroundCanceled() && (bytesRead = in.read(contents)) != -1) {
                    response.append(new String(contents, 0, bytesRead));
                }
            } finally {
                urlConnection.disconnect();
            }

            ArrayList<String> items = new ArrayList<>();
            JSONArray itemArr = new JSONArray(response.toString());
            for (int i = 0; i < itemArr.length() && !isLoadInBackgroundCanceled(); i++) {
                JSONObject item = (JSONObject) itemArr.get(i);
                items.add(item.getString("name"));
            }

            return items;
        } catch (IOException | GeneralSecurityException | JSONException e) {
            Log.e(TAG, "Failed to fatch item names from server " + mServerUrl, e);
            return new ArrayList<>();
        }
    }

    @Override
    public void deliverResult(List<String> data) {
        mData = data;
        super.deliverResult(data);
    }

    boolean isValid(String itemName) {
        return mData != null && mData.contains(itemName);
    }
}
