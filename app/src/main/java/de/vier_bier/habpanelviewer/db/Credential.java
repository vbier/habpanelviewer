package de.vier_bier.habpanelviewer.db;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.annotation.NonNull;

import java.util.Objects;


@Entity(primaryKeys = {"host", "realm"},
        indices = {@Index(value = {"host", "realm"}, unique = true)})
public class Credential {
    @NonNull
    private final String host;
    @NonNull
    private String realm;

    private String user;
    private String passwd;

    Credential(@NonNull String host, @NonNull String realm, String user, String passwd) {
        this.host = host;
        this.realm = realm;
        this.user = user;
        this.passwd = passwd;
    }

    public String getHost() {
        return host;
    }

    String getRealm() {
        return realm;
    }

    String getUser() {
        return user;
    }

    String getPasswd() {
        return passwd;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Credential that = (Credential) o;
        return Objects.equals(host, that.host) &&
                Objects.equals(realm, that.realm);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, realm);
    }
}
