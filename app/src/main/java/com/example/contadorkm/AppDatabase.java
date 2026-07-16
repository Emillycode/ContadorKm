package com.example.contadorkm;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {SessaoKm.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase instancia;

    public abstract SessaoKmDao sessaoKmDao();

    public static AppDatabase getInstancia(Context context) {
        if (instancia == null) {
            synchronized (AppDatabase.class) {
                if (instancia == null) {
                    instancia = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "kmcontador-db"
                    ).build();
                }
            }
        }
        return instancia;
    }
}