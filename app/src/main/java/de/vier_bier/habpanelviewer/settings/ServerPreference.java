package de.vier_bier.habpanelviewer.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.net.nsd.NsdManager;
import android.os.AsyncTask;
import android.preference.EditTextPreference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;

import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.openhab.ServerDiscovery;

/**
 * TextPreference with an additional button to trigger mDNS server discovery.
 */
public class ServerPreference extends EditTextPreference {
    public ServerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onAddEditTextToDialogView(View dialogView, final EditText editText) {
        super.onAddEditTextToDialogView(dialogView, editText);
        final ViewGroup container = (ViewGroup) editText.getParent();
        if (container != null) {
            final CheckBox cb = new CheckBox(getContext());
            cb.setText(R.string.discoverHttps);
            cb.setChecked(true);

            final Button b = new Button(getContext());
            b.setText(R.string.discoverServer);

            final TextView ex = new TextView(getContext());
            ex.setText(getContext().getResources().getString(R.string.pref_url_example));
            ex.setTextColor(Color.GRAY);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(15,0,0,0);
            ex.setLayoutParams(params);

            container.addView(ex);
            container.addView(b, ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            container.addView(cb, ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);

            b.setOnClickListener(view -> {
                b.setEnabled(false);
                ((AlertDialog) getDialog()).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                ((AlertDialog) getDialog()).getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);

                ResultVisualizer v = new ResultVisualizer(b, editText);
                AsyncTask discoveryTask =
                        new DiscoverTask(new ServerDiscovery((NsdManager) getContext().getSystemService(Context.NSD_SERVICE)),
                        v, !cb.isChecked(), cb.isChecked());
                discoveryTask.execute();
            });

        }

    }

    private class ResultVisualizer {
        private final Button b;
        private final EditText editText;

        ResultVisualizer(Button b, EditText editText) {
            this.b = b;
            this.editText = editText;
        }

        void serverFound(String serverUrl) {
            b.post(() -> editText.setText(serverUrl));
        }

        void serverNotFound() {
            b.post(() -> Toast.makeText(getContext(), getContext().getString(R.string.serverNotFound), Toast.LENGTH_LONG).show());
        }

        void discoveryFinished() {
            AlertDialog d = ((AlertDialog) getDialog());
            if (d != null && d.isShowing()) { // dialog may have been closed by back button
                b.setEnabled(true);
                d.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                d.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
            }
        }
    }

    private static class DiscoverTask extends AsyncTask {
        private final WeakReference<ServerDiscovery> discoveryServiceReference;
        private final WeakReference<ResultVisualizer> visualizerReference;
        private final boolean discoverHttp;
        private final boolean discoverHttps;

        DiscoverTask(ServerDiscovery serverDiscovery, ResultVisualizer visualizer, boolean discoverHttp, boolean discoverHttps) {
            discoveryServiceReference = new WeakReference<>(serverDiscovery);
            visualizerReference = new WeakReference<>(visualizer);
            this.discoverHttp = discoverHttp;
            this.discoverHttps = discoverHttps;
        }

        @Override
        protected Object doInBackground(Object[] objects) {
            ServerDiscovery mDiscovery = discoveryServiceReference.get();
            if (mDiscovery != null) {
                mDiscovery.discover(new ServerDiscovery.DiscoveryListener() {
                    @Override
                    public void found(final String serverUrl) {
                        ResultVisualizer v = visualizerReference.get();
                        if (v != null) {
                            v.serverFound(serverUrl);
                        }
                    }

                    @Override
                    public void notFound() {
                        ResultVisualizer v = visualizerReference.get();
                        if (v != null) {
                            v.serverNotFound();
                        }
                    }
                }, discoverHttp, discoverHttps);
            }

            return null;
        }

        @Override
        protected void onPostExecute(Object o) {
            ResultVisualizer v = visualizerReference.get();
            if (v != null) {
                v.discoveryFinished();
            }
        }
    }
}
