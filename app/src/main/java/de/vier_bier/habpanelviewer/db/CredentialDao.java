package de.vier_bier.habpanelviewer.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface CredentialDao {
    @Query("SELECT * FROM credential where host = :host and realm = :realm")
    Credential get(String host, String realm);

    @Query("DELETE FROM credential where host = :host and realm = :realm")
    void remove(String host, String realm);

    @Delete
    void remove(Credential credential);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Credential... users);

    @Query("SELECT * FROM credential")
    List<Credential> getAll();
}
