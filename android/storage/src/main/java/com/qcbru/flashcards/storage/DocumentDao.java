package com.qcbru.flashcards.storage;

import androidx.room.*;
import java.util.List;
import kotlinx.coroutines.flow.Flow;

@Dao
public interface DocumentDao {
    @Query("SELECT * FROM documents WHERE uid = :uid") Flow<List<StoredDocument>> observe(String uid);
    @Query("SELECT * FROM documents WHERE uid = :uid AND kind = :kind") List<StoredDocument> list(String uid, String kind);
    @Query("SELECT * FROM documents WHERE uid = :uid AND kind = :kind AND id = :id LIMIT 1") StoredDocument get(String uid, String kind, String id);
    @Insert(onConflict = OnConflictStrategy.REPLACE) void put(StoredDocument document);
    @Insert(onConflict = OnConflictStrategy.REPLACE) void putAll(List<StoredDocument> documents);
    @Query("DELETE FROM documents WHERE uid = :uid AND kind = :kind AND id = :id") void remove(String uid, String kind, String id);
    @Query("DELETE FROM documents WHERE uid = :uid") void clear(String uid);
    @Query("DELETE FROM documents WHERE uid = :uid AND kind = :kind") void clearKind(String uid, String kind);
}
