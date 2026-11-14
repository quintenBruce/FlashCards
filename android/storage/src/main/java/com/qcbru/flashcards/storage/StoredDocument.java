package com.qcbru.flashcards.storage;

import androidx.annotation.NonNull;
import androidx.room.Entity;

@Entity(tableName = "documents", primaryKeys = {"uid", "kind", "id"})
public class StoredDocument {
    @NonNull public String uid;
    @NonNull public String kind;
    @NonNull public String id;
    @NonNull public String body;
    public StoredDocument(@NonNull String uid, @NonNull String kind, @NonNull String id, @NonNull String body) {
        this.uid = uid; this.kind = kind; this.id = id; this.body = body;
    }
}
