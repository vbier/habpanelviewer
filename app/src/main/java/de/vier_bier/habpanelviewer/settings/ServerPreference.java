package de.vier_bier.habpanelviewer.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.net.nsd.NsdManager;
import android.os.AsyncTask;
import android.preference.EditTextPreference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

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

            container.addView(b, ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            container.addView(cb, ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);

            final ProgressBar p = new ProgressBar(getContext());

            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    b.setEnabled(false);
                    ((AlertDialog) getDialog()).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    ((AlertDialog) getDialog()).getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);

                    AsyncTask discoveryTask = new AsyncTask() {
                        @Override
                        protected Object doInBackground(Object[] objects) {
                            ServerDiscovery mDiscovery = new ServerDiscovery((NsdManager) getContext().getSystemService(Context.NSD_SERVICE));
                            mDiscovery.discover(new ServerDiscovery.DiscoveryListener() {
                                @Override
                                public void found(final String serverUrl) {
                                    b.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            editText.setText(serverUrl);
                                        }
                                    });
                                }

                                @Override
                                public void notFound() {
                                    b.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(getContext(), getContext().getString(R.string.serverNotFound), Toast.LENGTH_LONG).show();
                                        }
                                    });
                                }
                            }, !cb.isChecked(), cb.isChecked());

                            return null;
                        }

                        @Override
                        protected void onPostExecute(Object o) {
                            b.setEnabled(true);
                            ((AlertDialog) getDialog()).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                            ((AlertDialog) getDialog()).getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
                        }
                    };
                    discoveryTask.execute();
                }
            });

        }

    }
}
