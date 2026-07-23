package com.example.contadorkm;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Representa um veículo cadastrado pelo usuário (carro ou moto).
 *
 * tipoVeiculo: "CARRO" ou "MOTO" (ver constantes em {@link Tipos}).
 * tipoOleo: só relevante para CARRO — "SINTETICO", "SEMISSINTETICO" ou
 * "MINERAL" (ver {@link Tipos}). Para MOTO fica null, pois o limite de
 * troca de moto é fixo (1000 km) independente do óleo.
 */
@Entity(tableName = "veiculos")
public class Veiculo {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String nome;
    public String tipoVeiculo;
    public String tipoOleo;
    public double kmDesdeTrocaOleo;
    public long ultimaTrocaOleoMillis; // 0 = nunca registrada uma troca

    public Veiculo(String nome, String tipoVeiculo, String tipoOleo, double kmDesdeTrocaOleo, long ultimaTrocaOleoMillis) {
        this.nome = nome;
        this.tipoVeiculo = tipoVeiculo;
        this.tipoOleo = tipoOleo;
        this.kmDesdeTrocaOleo = kmDesdeTrocaOleo;
        this.ultimaTrocaOleoMillis = ultimaTrocaOleoMillis;
    }

    /** Retorna o km limite para troca de óleo deste veículo, conforme as regras definidas. */
    public double getLimiteTrocaOleoKm() {
        if (Tipos.MOTO.equals(tipoVeiculo)) {
            return 1000.0;
        }
        if (Tipos.SINTETICO.equals(tipoOleo)) {
            return 10000.0;
        }
        if (Tipos.SEMISSINTETICO.equals(tipoOleo)) {
            return 7500.0;
        }
        // MINERAL ou qualquer outro caso não previsto cai no limite mais conservador.
        return 5000.0;
    }

    public boolean atingiuLimiteTrocaOleo() {
        return kmDesdeTrocaOleo >= getLimiteTrocaOleoKm();
    }
}
