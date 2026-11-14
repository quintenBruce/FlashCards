package com.qcbru.flashcards.storage;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {StoredDocument.class}, version = 1, exportSchema = true)
public abstract class FlashDatabase extends RoomDatabase {
    public abstract DocumentDao documents();
}
