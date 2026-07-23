package com.example.contadorkm;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Banco local do app.
 *
 * A partir de agora usamos Migrations reais do Room (abaixo) em vez de
 * fallbackToDestructiveMigration(): cada mudança de schema (nova tabela,
 * nova coluna) ganha uma Migration explícita, então atualizar o app NÃO
 * apaga mais os dados já salvos no aparelho.
 *
 * IMPORTANTE para o futuro: se você (ou eu) adicionar um novo campo a
 * {@link SessaoKm} ou {@link Veiculo}, ou uma nova entidade, é preciso:
 * 1. Aumentar o "version" da anotação @Database abaixo.
 * 2. Escrever uma nova Migration (ex.: MIGRATION_3_4) com o SQL
 *    equivalente à mudança (CREATE TABLE / ALTER TABLE ADD COLUMN).
 * 3. Adicionar essa Migration em addMigrations(...) no builder.
 * Esquecer esse passo faz o app quebrar ao abrir após a atualização.
 */
@Database(entities = {SessaoKm.class, Veiculo.class}, version = 3, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase instancia;;
    public abstract SessaoKmDao sessaoKmDao();
    public abstract VeiculoDao veiculoDao();

    /** v1 (só a tabela sessoes_km) -> v2: cria a tabela de veículos e
     *  associa cada sessão a um veículo (coluna veiculoId, 0 = nenhum). */
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `veiculos` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`nome` TEXT, "
                    + "`tipoVeiculo` TEXT, "
                    + "`tipoOleo` TEXT, "
                    + "`kmDesdeTrocaOleo` REAL NOT NULL DEFAULT 0)");

            database.execSQL("ALTER TABLE `sessoes_km` "
                    + "ADD COLUMN `veiculoId` INTEGER NOT NULL DEFAULT 0");
        }
    };

    /** v2 -> v3: guarda a data da última troca de óleo de cada veículo. */
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `veiculos` "
                    + "ADD COLUMN `ultimaTrocaOleoMillis` INTEGER NOT NULL DEFAULT 0");
        }
    };

    public static AppDatabase getInstancia(Context context) {
        if (instancia == null) {
            synchronized (AppDatabase.class) {
                if (instancia == null) {
                    instancia = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "kmcontador-db"
                            )
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3 )
                            // Só apaga tudo no caso raro de downgrade (voltar para uma
                            // versão mais antiga do app), nunca em atualizações normais.
                            .fallbackToDestructiveMigrationOnDowngrade()
                            .build();
                }
            }
        }
        return instancia;
    }
}
