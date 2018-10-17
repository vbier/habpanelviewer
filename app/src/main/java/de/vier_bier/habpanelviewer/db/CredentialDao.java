package de.vier_bier.habpanelviewer.db;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;

@Dao
public interface CredentialDao {
    @Query("SELECT * FROM credential where host = :host and realm = :realm")
    Credential get(String host, String realm);

    @Delete
    void remove(Credential credential);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Credential... users);

}
