package de.vier_bier.habpanelviewer.db;

import androidx.room.Room;
import android.content.Context;
import android.os.AsyncTask;
import android.webkit.HttpAuthHandler;

import java.util.HashSet;

public class CredentialsHelper {
    private static CredentialsHelper ourInstance;

    private final AppDatabase mDb;
    private final HashSet<Credential> mSendCreds = new HashSet<>();
    private Credential mRestCred = null;

    public static synchronized CredentialsHelper getInstance(Context ctx) {
        if (ourInstance == null) {
            ourInstance = new CredentialsHelper(ctx);
        }
        return ourInstance;
    }

    private CredentialsHelper(Context ctx) {
        mDb = Room.databaseBuilder(ctx.getApplicationContext(),
                AppDatabase.class, "HPV").build();
    }

    public void clearCredentials() {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                mDb.clearAllTables();
                return null;
            }
        }.execute();
    }

    public void registerCredentials(String host, String realm, String user, String passwd) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                mDb.credentialDao().insert(new Credential(host, realm, user, passwd));
                return null;
            }
        }.execute();
    }

    public void handleAuthRequest(String host, String realm, final HttpAuthHandler handler, Runnable askPwdRunnable) {
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

    public String getRestAuth(String serverURL) {
        if (mRestCred != null && mRestCred.getHost().equals(serverURL)) {
            return mRestCred.getUser() + ":" + mRestCred.getPasswd();
        }

        Credential c = mDb.credentialDao().get(serverURL, "Rest API");

        if (c != null) {
            return c.getUser() + ":" + c.getPasswd();
        }

        return null;
    }

    public void setRestAuth(String host, String user, String passwd, boolean store) {
        mRestCred = new Credential(host, "Rest API", user, passwd);

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
}
