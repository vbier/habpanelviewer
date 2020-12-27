package de.vier_bier.habpanelviewer.db;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;

import androidx.room.Room;
import androidx.room.RoomDatabase;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteException;
import net.sqlcipher.database.SupportFactory;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import de.vier_bier.habpanelviewer.UiUtil;

public class CredentialManager extends Thread {
    private static CredentialManager ourInstance;

    private Handler mHandler;
    private AppDatabase mDb;
    private final HashSet<Credential> mSendCreds = new HashSet<>();
    private boolean mDatabaseUsed = true;

    public static synchronized CredentialManager getInstance() {
        if (ourInstance == null) {
            ourInstance = new CredentialManager();
            ourInstance.start();
        }
        return ourInstance;
    }

    @Override
    public void run() {
        Looper.prepare();

        mHandler = new Handler();

        Looper.loop();
    }

    public void clearCredentials() {
        mHandler.post(() -> {
            if (mDb != null) {
                mDb.clearAllTables();
            }
        });
    }

    public void removeCredentials(String host, String realm) {
        mHandler.post(() -> {
            if (mDb != null) {
                mDb.credentialDao().remove(host, realm);
            }
        });
    }

    public void handleAuthRequest(Context ctx, String host, String realm, final CredentialsListener l) {
        mHandler.post(() -> {
            CountDownLatch latch = new CountDownLatch(1);
            handleAuthRequest(ctx, host, realm, l, latch);
            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
    }

    private void handleAuthRequest(Context ctx, String host, String realm, final CredentialsListener l, final CountDownLatch latch) {
        if (!hasDatabase() && isDatabaseUsed()) {
            // we have an encrypted, not yet opened database
            UiUtil.showMasterPasswordDialog(ctx, new UiUtil.CredentialsListener() {
                @Override
                public void credentialsEntered(String host2, String realm2, String user, String password, boolean store) {
                    new AsyncTask<Void, Void, Void>() {
                        @Override
                        protected Void doInBackground(Void... voids) {
                            openDb(ctx, password);
                            handleAuthRequest(ctx, host, realm, l, latch);
                            return null;
                        }
                    }.execute();
                }

                @Override
                public void credentialsCancelled() {
                    CredentialManager.getInstance().setDatabaseUsed(false);
                    handleAuthRequest(ctx, host, realm, l, latch);
                    latch.countDown();
                }
            });
        } else {
            new AsyncTask<Void, Void, Credential>() {
                @Override
                protected Credential doInBackground(Void... v) {
                    Credential c = null;
                    if (hasDatabase() && isDatabaseUsed()) {
                        c = mDb.credentialDao().get(host, realm);

                        if (c != null && mSendCreds.contains(c)) {
                            // password already has been used, but seems to be not correct
                            // remove from db, open dialog
                            mSendCreds.remove(c);
                            mDb.credentialDao().remove(c);
                            c = null;
                        }
                    }
                    return c;
                }

                @Override
                protected void onPostExecute(Credential c) {
                    if (c != null) {
                        mSendCreds.add(c);
                        l.credentialsEntered(c.getUser(), c.getPasswd());
                        latch.countDown();
                    } else {
                        // could not retrieve creds from store => ask user
                        UiUtil.showPasswordDialog(ctx, host, realm, new UiUtil.CredentialsListener() {
                            @Override
                            public void credentialsEntered(String host, String realm, String user, String password, boolean store) {
                                if (store) {
                                    new AsyncTask<Void, Void, Void>() {
                                        @Override
                                        protected Void doInBackground(Void... voids) {
                                            if (mDb != null) {
                                                mDb.credentialDao().insert(new Credential(host, realm, user, password));
                                            }
                                            l.credentialsEntered(user, password);
                                            latch.countDown();
                                            return null;
                                        }
                                    }.execute();
                                } else {
                                    l.credentialsEntered(user, password);
                                    latch.countDown();
                                }
                            }

                            @Override
                            public void credentialsCancelled() {
                                l.credentialsCancelled();
                                latch.countDown();
                            }
                        });
                    }
                }
            }.execute();
        }
    }

    public boolean hasDatabase() {
        return mDb != null;
    }

    public void setDatabaseUsed(boolean used) {
        mDatabaseUsed = used;
    }

    public boolean isDatabaseUsed() {
        return mDatabaseUsed;
    }

    public void getRestCredential(String host, String realm, CredentialsListener l) {
        mHandler.post(() -> {
            Credential c = null;
            if (mDb != null) {
                c = mDb.credentialDao().get(host, realm);
            }

            final Credential c2 = c;
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(() -> {
                if (c2 != null) {
                    l.credentialsEntered(c2.getUser(), c2.getPasswd());
                } else {
                    l.credentialsCancelled();
                }
            });
        });
    }


    public State getDatabaseState(Context ctx) {
        SQLiteDatabase.loadLibs(ctx);
        File dbPath = ctx.getDatabasePath("HPV");

        if (dbPath.exists()) {
            try (SQLiteDatabase db = SQLiteDatabase.openDatabase(dbPath.getAbsolutePath(), "",
                    null, SQLiteDatabase.OPEN_READONLY)) {
                db.getVersion();

                return (State.UNENCRYPTED);
            } catch (Exception e) {
                return (State.ENCRYPTED);
            }
        }

        return(State.DOES_NOT_EXIST);
    }

    public void openDb(Context ctx, String password) {
        RoomDatabase.Builder<AppDatabase> builder = Room.databaseBuilder(ctx, AppDatabase.class, "HPV");

        if (password != null) {
            final byte[] passphrase = SQLiteDatabase.getBytes(password.toCharArray());
            final SupportFactory factory = new SupportFactory(passphrase);
            builder.openHelperFactory(factory);
        }

        AppDatabase db = builder.build();

        // check if password is correct
        final State dbState = getDatabaseState(ctx);
        if (dbState == CredentialManager.State.ENCRYPTED) {
            try {
                db.credentialDao().get("a", "b");
                mDb = db;
            } catch (SQLiteException e) {
                db.close();
                // password wrong
            }
        } else {
            mDb = db;
        }
    }

    public void encryptDb(Context ctx, String password) {
        final State dbState = getDatabaseState(ctx);

        if (dbState == CredentialManager.State.UNENCRYPTED) {
            if (mDb != null) {
                mDb.close();
                mDb = null;
            }

            AppDatabase db = Room.databaseBuilder(ctx, AppDatabase.class, "HPV").build();
            List<Credential> creds = db.credentialDao().getAll();
            db.close();

            ctx.deleteDatabase("HPV");

            openDb(ctx, password);
            mDb.credentialDao().insert(creds.toArray(new Credential[0]));
        }
    }

    public enum State {
        DOES_NOT_EXIST, UNENCRYPTED, ENCRYPTED
    }

    public interface CredentialsListener {
        void credentialsEntered(String user, String pass);
        void credentialsCancelled();
    }
}
