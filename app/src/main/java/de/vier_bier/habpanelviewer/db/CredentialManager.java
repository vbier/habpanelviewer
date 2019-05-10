package de.vier_bier.habpanelviewer.db;

import android.os.AsyncTask;
import android.webkit.HttpAuthHandler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class CredentialManager {
    private static CredentialManager ourInstance;

    private final AtomicBoolean mInitialized = new AtomicBoolean();
    private final CountDownLatch mInitLatch = new CountDownLatch(1);

    private final ArrayList<CredentialsListener> mListeners = new ArrayList<>();
    private AppDatabase mDb;
    private final HashSet<Credential> mSendCreds = new HashSet<>();
    private Credential mRestCred = null;

    public static synchronized CredentialManager getInstance() {
        if (ourInstance == null) {
            ourInstance = new CredentialManager();
        }
        return ourInstance;
    }

    public void setDatabase(AppDatabase db) {
        if (!mInitialized.getAndSet(true)) {
            try {
                mDb = db;

                new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... voids) {
                        mRestCred = mDb.credentialDao().get("openHAB server", "Rest API");
                        return null;
                    }
                }.execute();
            } finally {
                mInitLatch.countDown();
            }
        }
    }

    public void clearCredentials() {
        mRestCred = null;

        try {
            mInitLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                mDb.clearAllTables();
                return null;
            }
        }.execute();
    }

    public void registerCredentials(String host, String realm, String user, String passwd) {
        try {
            mInitLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                mDb.credentialDao().insert(new Credential(host, realm, user, passwd));
                return null;
            }
        }.execute();
    }

    public void handleAuthRequest(String host, String realm, final HttpAuthHandler handler, Runnable askPwdRunnable) {
        try {
            mInitLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        new AsyncTask<Void, Void, Credential>() {
            @Override
            protected Credential doInBackground(Void... v) {
                Credential c = mDb.credentialDao().get(host, realm);

                if (mSendCreds.contains(c)) {
                    // password already has been used, but seems to be not correct
                    // remove from db, open dialog
                    mSendCreds.remove(c);
                    mDb.credentialDao().remove(c);
                    c = null;
                }

                return c;
            }

            @Override
            protected void onPostExecute(Credential c) {
                if (c != null) {
                    mSendCreds.add(c);
                    handler.proceed(c.getUser(), c.getPasswd());
                    return;
                }

                askPwdRunnable.run();
            }
        }.execute();
    }

    public Credential getRestCredentials() {
        return mRestCred;
    }

    public void setRestCredentials(String user, String passwd, boolean store) {
        mRestCred = new Credential("openHAB server", "Rest API", user, passwd);

        // notify listeners
        synchronized (mListeners) {
            for (CredentialsListener l : mListeners) {
                l.credentialsEntered();
            }
        }

        try {
            mInitLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        if (store) {
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... voids) {
                    mDb.credentialDao().insert(mRestCred);
                    return null;
                }
            }.execute();
        }
    }

    public void addCredentialsListener(CredentialsListener l) {
        synchronized (mListeners) {
            if (!mListeners.contains(l)) {
                mListeners.add(l);

                if (mRestCred != null) {
                    l.credentialsEntered();
                }
            }
        }
    }

    public void removeCredentialsListener(CredentialsListener l) {
        synchronized (mListeners) {
            mListeners.remove(l);
        }
    }

    public interface CredentialsListener {
        void credentialsEntered();
    }
}
