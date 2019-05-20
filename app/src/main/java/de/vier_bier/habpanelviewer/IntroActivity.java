package de.vier_bier.habpanelviewer;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.nsd.NsdManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.github.paolorotolo.appintro.AppIntro2;
import com.github.paolorotolo.appintro.AppIntro2Fragment;
import com.github.paolorotolo.appintro.ISlidePolicy;

import de.vier_bier.habpanelviewer.openhab.ServerDiscovery;

public class IntroActivity extends AppIntro2 {
    private int mSelectedSlide = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle bundle = getIntent().getExtras();
        boolean introOnly = bundle != null && Boolean.TRUE.equals(bundle.getBoolean(Constants.INTENT_FLAG_INTRO_ONLY));

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        boolean serverConfigured = !"".equals(prefs.getString(Constants.PREF_SERVER_URL, ""));

        showSkipButton(serverConfigured || introOnly);
        setTitle(R.string.intro_initialConfiguration);

        int bgColor = Color.parseColor("#4CAF50");


        // Ask for CAMERA permission on the second slide
        //askForPermissions(new String[]{Manifest.permission.CAMERA}, 2);

        if (!introOnly) {
            addSlide(AppIntro2Fragment.newInstance(getString(R.string.intro_welcome),
                    getString(R.string.intro_welcome_text), R.drawable.logo, bgColor));

            addSlide(AppIntro2Fragment.newInstance(getString(R.string.intro_browser),
                    getString(R.string.intro_browser_text), R.drawable.browser, bgColor));

            addSlide(AppIntro2Fragment.newInstance(getString(R.string.intro_reporting),
                    getString(R.string.intro_reporting_text), R.drawable.reporting, bgColor));

            addSlide(AppIntro2Fragment.newInstance(getString(R.string.intro_commanding),
                    getString(R.string.intro_commanding_text), R.drawable.commanding, bgColor));

            addSlide(AppIntro2Fragment.newInstance(getString(R.string.intro_credentials),
                    getString(R.string.intro_credentials_text), R.drawable.credentials, bgColor));

            addSlide(AppIntro2Fragment.newInstance(getString(R.string.intro_credentials),
                    getString(R.string.intro_credentials_text2), R.drawable.database, bgColor));
        }

        if (!serverConfigured || introOnly) {
            NsdManager nsdm = (NsdManager) getSystemService(Context.NSD_SERVICE);

            if (nsdm != null) {
                DiscoverSlide ds = new DiscoverSlide();
                ds.setSystemService(nsdm);
                addSlide(ds);
            } else {
                addSlide(AppIntro2Fragment.newInstance(getString(R.string.intro_openhabServerDetection),
                        getString(R.string.intro_discoveryNotAvailable),
                        R.drawable.server, bgColor));
            }
        } else {
            addSlide(AppIntro2Fragment.newInstance(getString(R.string.intro_openhabServerDetection),
                    getString(R.string.intro_serverConfigured, prefs.getString(Constants.PREF_SERVER_URL, "")),
                    R.drawable.server, bgColor));
        }

        if (!introOnly) {
            addSlide(AppIntro2Fragment.newInstance(getString(R.string.intro_configuration),
                    getString(R.string.intro_configuration_text), R.drawable.configuration, bgColor));

            addSlide(AppIntro2Fragment.newInstance(getString(R.string.intro_help),
                    getString(R.string.intro_help_text), R.drawable.ready, bgColor));

            addSlide(AppIntro2Fragment.newInstance(getString(R.string.intro_ready),
                    getString(R.string.intro_ready_text), R.drawable.ready, bgColor));
        }
    }

    protected void onPageSelected(int position) {
        mSelectedSlide = position;
    }

    @Override
    public void onBackPressed() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        boolean serverConfigured = !"".equals(prefs.getString(Constants.PREF_SERVER_URL, ""));

        if (mSelectedSlide == 0 && !serverConfigured) {
            UiUtil.showSnackBar(getWindow().getDecorView().findViewById(android.R.id.content),
                    R.string.intro_pleaseComplete);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onSkipPressed(Fragment currentFragment) {
        onDonePressed(currentFragment);
    }

    @Override
    public void onDonePressed(Fragment currentFragment) {
        super.onDonePressed(currentFragment);

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        if (!prefs.getBoolean(Constants.PREF_INTRO_SHOWN, false)) {
            SharedPreferences.Editor editor1 = prefs.edit();
            editor1.putBoolean(Constants.PREF_INTRO_SHOWN, true);
            editor1.apply();
        }
        finish();
    }

    @Override
    public void onSlideChanged(@Nullable Fragment oldFragment, @Nullable Fragment newFragment) {
        if (oldFragment instanceof DiscoverSlide) {
            final String url = ((DiscoverSlide) oldFragment).getSelectedUrl();
            if (url != null) {
                final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

                SharedPreferences.Editor editor1 = prefs.edit();
                editor1.putString(Constants.PREF_SERVER_URL, url);
                editor1.apply();
            }
        }

        super.onSlideChanged(oldFragment, newFragment);
    }

    static class DiscoverSlide extends Fragment implements ISlidePolicy {
        private NsdManager mSystemService;
        private ServerDiscovery mDiscovery;

        void setSystemService(NsdManager systemService) {
            mSystemService = systemService;
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            if (mDiscovery == null && mSystemService != null) {
                mDiscovery = new ServerDiscovery(mSystemService);
            }
        }

        String getSelectedUrl() {
            View v = getView();
            if (v != null) {
                final RadioGroup rg = getView().findViewById(R.id.intro_discover_radioGroup);
                int id = rg.getCheckedRadioButtonId();

                final RadioButton rb = getView().findViewById(id);
                if (rb != null) {
                    String url = (String) rb.getText();

                    if (url.equals(getString(R.string.intro_urlNotFound))) {
                        return null;
                    }
                    return url;
                }
            }

            return null;
        }

        @Override
        public void onStart() {
            super.onStart();

            View v = getView();
            if (v != null && mDiscovery != null) {
                final RadioGroup rg = getView().findViewById(R.id.intro_discover_radioGroup);
                final ProgressBar pbar = getView().findViewById(R.id.intro_discover_progressBar);
                final TextView tv = getView().findViewById(R.id.intro_discover_text);

                final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getBaseContext());
                final String currentURL = prefs.getString(Constants.PREF_SERVER_URL, "");

                if (!"".equals(currentURL)) {
                    RadioButton button = new RadioButton(getActivity());
                    button.setText(currentURL);
                    rg.addView(button);
                }

                new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected void onPreExecute() {
                        tv.setText(R.string.intro_discoveryInProgress);
                        pbar.setVisibility(View.VISIBLE);
                    }

                    @Override
                    protected Void doInBackground(Void... voids) {
                        mDiscovery.discover(serverUrl -> getActivity().runOnUiThread(() -> {
                            if (!serverUrl.equals(currentURL)) {
                                RadioButton button = new RadioButton(getActivity());
                                button.setText(serverUrl);
                                rg.addView(button);
                            }
                        }));
                        return null;
                    }

                    @Override
                    protected void onPostExecute(Void aVoid) {
                        tv.setText(R.string.intro_discoveryFinished);
                        pbar.setVisibility(View.GONE);

                        RadioButton button = new RadioButton(getActivity());
                        button.setText(R.string.intro_urlNotFound);
                        rg.addView(button);
                    }
                }.execute((Void) null);
            }
        }

        @Override
        public void onDestroy() {
            if (mDiscovery != null) {
                mDiscovery.terminate();
                mDiscovery = null;
            }

            super.onDestroy();
        }

        @Override
        public void onStop() {
            if (mDiscovery != null) {
                // stop discover onStop, but do not set mDiscovery to null as it will be reused
                // in onStart
                mDiscovery.terminate();
            }

            super.onStop();
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                                 @Nullable Bundle savedInstanceState) {
            View v = inflater.inflate(R.layout.activity_intro_discover, container, false);
            v.setBackgroundColor(Color.parseColor("#4CAF50"));

            return v;
        }

        @Override
        public boolean isPolicyRespected() {
            View v = getView();
            if (v != null) {
                final RadioGroup rg = getView().findViewById(R.id.intro_discover_radioGroup);
                return rg.getCheckedRadioButtonId() != -1;
            }

            return true;
        }

        @Override
        public void onUserIllegallyRequestedNextPage() {
            UiUtil.showSnackBar(getView(), R.string.intro_pleaseSelectUrl);
        }
    }
}