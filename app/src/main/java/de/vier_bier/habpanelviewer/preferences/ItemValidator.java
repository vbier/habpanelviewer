package de.vier_bier.habpanelviewer.preferences;

import android.util.Log;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import de.vier_bier.habpanelviewer.connection.OkHttpClientFactory;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

class ItemValidator {
    private static final String TAG = "HPV-ItemValidator";
    private final List<String> mNames = new ArrayList<>();

    void setServerUrl(String serverUrl, ValidationStateListener l) {
        OkHttpClient client = OkHttpClientFactory.getInstance().create();

        try {
            Request request = new Request.Builder()
                    .url(serverUrl + "/rest/items")
                    .build();

            client.newCall(request)
                    .enqueue(new Callback() {
                        @Override
                        public void onFailure(@NotNull final Call call, @NotNull IOException e) {
                            Log.e(TAG, "Failed to fetch item names from server " + serverUrl, e);
                            mNames.clear();
                            l.validationUnavailable();
                        }

                        @Override
                        public void onResponse(@NotNull Call call, @NotNull final Response response) throws IOException {
                            mNames.clear();

                            if (response.code() == 200) {
                                try (ResponseBody responseBody = response.body()) {
                                    if (responseBody != null) {
                                        JSONArray itemArr = new JSONArray(responseBody.string());
                                        for (int i = 0; i < itemArr.length(); i++) {
                                            JSONObject item = (JSONObject) itemArr.get(i);
                                            mNames.add(item.getString("name"));
                                        }
                                    } else {
                                        mNames.clear();
                                        l.validationUnavailable();
                                    }
                                } catch (JSONException e) {
                                    Log.e(TAG, "Failed to fetch item names from server " + serverUrl, e);
                                }
                                l.validationAvailable(mNames);
                            } else {
                                mNames.clear();
                                l.validationUnavailable();
                            }
                        }
                    });
        } catch (IllegalArgumentException e) {
            mNames.clear();
            l.validationUnavailable();
        }
    }

    boolean isValid(String itemName) {
        return mNames.contains(itemName);
    }

    interface ValidationStateListener {
        void validationAvailable(List<String> items);
        void validationUnavailable();
    }
}
