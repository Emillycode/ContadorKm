package com.example.contadorkm;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

@Dao
public interface SessaoKmDao {

    @Insert
    void inserir(SessaoKm sessao);

    /**
     * Soma o km de todas as sessões que começaram a partir de
     * {@code inicioPeriodoMillis} (inclusive). Usado para os totais de
     * Hoje / Semana / Mês, passando o timestamp de início de cada período.
     * COALESCE garante 0 (em vez de null) quando não há nenhuma sessão ainda.
     */
    @Query("SELECT COALESCE(SUM(kmPercorridos), 0) FROM sessoes_km WHERE dataHoraInicio >= :inicioPeriodoMillis")
    double somarKmDesde(long inicioPeriodoMillis);

    /**
     * Mesma soma acima, mas filtrada por um veículo específico — usado para
     * mostrar Hoje / Semana / Mês apenas do veículo atualmente selecionado.
     */
    @Query("SELECT COALESCE(SUM(kmPercorridos), 0) FROM sessoes_km WHERE veiculoId = :veiculoId AND dataHoraInicio >= :inicioPeriodoMillis")
    double somarKmPorVeiculoDesde(long veiculoId, long inicioPeriodoMillis);
}
