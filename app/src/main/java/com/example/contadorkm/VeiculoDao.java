package com.example.contadorkm;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface VeiculoDao {

    @Insert
    long inserir(Veiculo veiculo);

    @Update
    void atualizar(Veiculo veiculo);

    @Delete
    void excluir(Veiculo veiculo);

    @Query("SELECT * FROM veiculos ORDER BY id ASC")
    List<Veiculo> listarTodos();

    @Query("SELECT * FROM veiculos WHERE id = :id")
    Veiculo buscarPorId(long id);
}
