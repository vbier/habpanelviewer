package de.vier_bier.habpanelviewer.db;

import android.arch.persistence.room.Database;
import android.arch.persistence.room.RoomDatabase;

@Database(entities = {Credential.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    public abstract CredentialDao credentialDao();
}
