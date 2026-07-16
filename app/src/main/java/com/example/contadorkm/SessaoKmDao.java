package com.example.contadorkm;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

@Dao
public interface SessaoKmDao {

    @Insert
    void inserir(SessaoKm sessao);

    @Query("SELECT COALESCE(SUM(kmPercorridos), 0) FROM sessoes_km WHERE dataHoraInicio >= :inicioPeriodoMillis")
    double somarKmDesde(long inicioPeriodoMillis);
}