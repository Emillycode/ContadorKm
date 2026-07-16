package com.example.contadorkm;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Representa uma sessão de contagem de km.
 */
@Entity(tableName = "sessoes_km")
public class SessaoKm {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long dataHoraInicio;
    public long dataHoraFim;
    public double kmPercorridos;

    public SessaoKm(long dataHoraInicio, long dataHoraFim, double kmPercorridos) {
        this.dataHoraInicio = dataHoraInicio;
        this.dataHoraFim = dataHoraFim;
        this.kmPercorridos = kmPercorridos;
    }
}