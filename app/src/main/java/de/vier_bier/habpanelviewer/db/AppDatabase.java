package de.vier_bier.habpanelviewer.db;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {Credential.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract CredentialDao credentialDao();
}
