package com.example.contadorkm;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tela de cadastro/edição de um veículo (carro ou moto).
 *
 * Modo CRIAR: aberta sem extras — salva um veículo novo.
 * Modo EDITAR: aberta com o extra {@link #EXTRA_VEICULO_ID} preenchido —
 * carrega os dados do veículo, pré-preenche os campos, muda os textos da
 * tela e mostra o botão "Excluir veículo".
 *
 * Para MOTO, o grupo de tipo de óleo é escondido, pois o limite de troca
 * de moto é fixo (1000 km) independentemente do óleo usado.
 */
public class CadastroVeiculoActivity extends AppCompatActivity {

    public static final String EXTRA_VEICULO_ID = "extra_veiculo_id";
    private static final long SEM_ID = -1L;

    private EditText etNomeVeiculo;
    private RadioGroup rgTipoVeiculo;
    private RadioGroup rgTipoOleo;
    private TextView tvLabelTipoOleo;
    private TextView tvTituloCadastro;
    private Button btnSalvarVeiculo;
    private Button btnMarcarOleoTrocado;
    private Button btnExcluirVeiculo;

    private final ExecutorService executorBanco = Executors.newSingleThreadExecutor();

    private long veiculoEmEdicaoId = SEM_ID;
    private Veiculo veiculoEmEdicao = null; // preenchido só no modo editar, após carregar do banco

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cadastro_veiculo);

        etNomeVeiculo = findViewById(R.id.etNomeVeiculo);
        rgTipoVeiculo = findViewById(R.id.rgTipoVeiculo);
        rgTipoOleo = findViewById(R.id.rgTipoOleo);
        tvLabelTipoOleo = findViewById(R.id.tvLabelTipoOleo);
        tvTituloCadastro = findViewById(R.id.tvTituloCadastro);
        btnSalvarVeiculo = findViewById(R.id.btnSalvarVeiculo);
        btnMarcarOleoTrocado = findViewById(R.id.btnMarcarOleoTrocado);
        btnExcluirVeiculo = findViewById(R.id.btnExcluirVeiculo);

        rgTipoVeiculo.setOnCheckedChangeListener((group, checkedId) -> atualizarVisibilidadeTipoOleo());
        atualizarVisibilidadeTipoOleo();

        btnSalvarVeiculo.setOnClickListener(v -> salvarVeiculo());
        btnMarcarOleoTrocado.setOnClickListener(v -> confirmarTrocaDeOleo());
        btnExcluirVeiculo.setOnClickListener(v -> confirmarExclusao());

        veiculoEmEdicaoId = getIntent().getLongExtra(EXTRA_VEICULO_ID, SEM_ID);
        if (veiculoEmEdicaoId != SEM_ID) {
            entrarEmModoEdicao();
        }
    }

    private boolean isModoEdicao() {
        return veiculoEmEdicaoId != SEM_ID;
    }

    private void entrarEmModoEdicao() {
        tvTituloCadastro.setText(R.string.titulo_editar_veiculo);
        btnSalvarVeiculo.setText(R.string.botao_salvar_alteracoes_veiculo);
        btnMarcarOleoTrocado.setVisibility(View.VISIBLE);
        btnExcluirVeiculo.setVisibility(View.VISIBLE);

        executorBanco.execute(() -> {
            Veiculo veiculo = AppDatabase.getInstancia(getApplicationContext())
                    .veiculoDao().buscarPorId(veiculoEmEdicaoId);
            if (veiculo == null) {
                runOnUiThread(this::finish); // veículo não existe mais, nada a editar
                return;
            }
            veiculoEmEdicao = veiculo;
            runOnUiThread(() -> preencherCamposComVeiculo(veiculo));
        });
    }

    private void preencherCamposComVeiculo(Veiculo veiculo) {
        etNomeVeiculo.setText(veiculo.nome);

        boolean isCarro = Tipos.CARRO.equals(veiculo.tipoVeiculo);
        rgTipoVeiculo.check(isCarro ? R.id.rbCarro : R.id.rbMoto);

        if (isCarro) {
            if (Tipos.SEMISSINTETICO.equals(veiculo.tipoOleo)) {
                rgTipoOleo.check(R.id.rbSemissintetico);
            } else if (Tipos.MINERAL.equals(veiculo.tipoOleo)) {
                rgTipoOleo.check(R.id.rbMineral);
            } else {
                rgTipoOleo.check(R.id.rbSintetico);
            }
        }
        atualizarVisibilidadeTipoOleo();
    }

    private boolean isCarroSelecionado() {
        return rgTipoVeiculo.getCheckedRadioButtonId() == R.id.rbCarro;
    }

    private void atualizarVisibilidadeTipoOleo() {
        int visibilidade = isCarroSelecionado() ? View.VISIBLE : View.GONE;
        tvLabelTipoOleo.setVisibility(visibilidade);
        rgTipoOleo.setVisibility(visibilidade);
    }

    private void salvarVeiculo() {
        String nome = etNomeVeiculo.getText().toString().trim();
        if (TextUtils.isEmpty(nome)) {
            Toast.makeText(this, R.string.erro_nome_veiculo_vazio, Toast.LENGTH_SHORT).show();
            return;
        }

        boolean carro = isCarroSelecionado();
        String tipoVeiculo = carro ? Tipos.CARRO : Tipos.MOTO;
        String tipoOleo = null;

        if (carro) {
            int idOleoSelecionado = rgTipoOleo.getCheckedRadioButtonId();
            if (idOleoSelecionado == R.id.rbSintetico) {
                tipoOleo = Tipos.SINTETICO;
            } else if (idOleoSelecionado == R.id.rbSemissintetico) {
                tipoOleo = Tipos.SEMISSINTETICO;
            } else if (idOleoSelecionado == R.id.rbMineral) {
                tipoOleo = Tipos.MINERAL;
            } else {
                Toast.makeText(this, R.string.erro_oleo_nao_selecionado, Toast.LENGTH_SHORT).show();
                return;
            }
        }

        if (isModoEdicao()) {
            atualizarVeiculoExistente(nome, tipoVeiculo, tipoOleo);
        } else {
            criarNovoVeiculo(nome, tipoVeiculo, tipoOleo);
        }
    }

    private void criarNovoVeiculo(String nome, String tipoVeiculo, String tipoOleo) {
        Veiculo veiculo = new Veiculo(nome, tipoVeiculo, tipoOleo, 0.0, 0L);
        executorBanco.execute(() -> {
            AppDatabase.getInstancia(getApplicationContext()).veiculoDao().inserir(veiculo);
            runOnUiThread(this::finalizarComSucesso);
        });
    }

    private void atualizarVeiculoExistente(String nome, String tipoVeiculo, String tipoOleo) {
        if (veiculoEmEdicao == null) return; // ainda carregando do banco, evita corrida

        veiculoEmEdicao.nome = nome;
        veiculoEmEdicao.tipoVeiculo = tipoVeiculo;
        veiculoEmEdicao.tipoOleo = tipoOleo;
        // kmDesdeTrocaOleo é preservado — editar tipo/óleo não zera o histórico acumulado.

        executorBanco.execute(() -> {
            AppDatabase.getInstancia(getApplicationContext()).veiculoDao().atualizar(veiculoEmEdicao);
            runOnUiThread(this::finalizarComSucesso);
        });
    }

    private void confirmarTrocaDeOleo() {
        if (veiculoEmEdicao == null) return;

        String mensagem = getString(R.string.confirmar_troca_oleo_mensagem, veiculoEmEdicao.kmDesdeTrocaOleo);

        new AlertDialog.Builder(this)
                .setTitle(R.string.confirmar_troca_oleo_titulo)
                .setMessage(mensagem)
                .setPositiveButton(R.string.botao_confirmar_troca_oleo, (dialog, which) -> marcarOleoComoTrocado())
                .setNegativeButton(R.string.botao_cancelar, null)
                .show();
    }

    private void marcarOleoComoTrocado() {
        veiculoEmEdicao.kmDesdeTrocaOleo = 0.0;
        veiculoEmEdicao.ultimaTrocaOleoMillis = System.currentTimeMillis();

        executorBanco.execute(() -> {
            AppDatabase.getInstancia(getApplicationContext()).veiculoDao().atualizar(veiculoEmEdicao);
            NotificationHelper.cancelarNotificacaoTrocaOleo(getApplicationContext(), veiculoEmEdicao.id);
            runOnUiThread(this::finalizarComSucesso);
        });
    }

    private void confirmarExclusao() {
        if (veiculoEmEdicao == null) return;

        String mensagem = getString(R.string.confirmar_exclusao_veiculo_mensagem, veiculoEmEdicao.nome);

        new AlertDialog.Builder(this)
                .setTitle(R.string.confirmar_exclusao_veiculo_titulo)
                .setMessage(mensagem)
                .setPositiveButton(R.string.botao_confirmar_exclusao, (dialog, which) -> excluirVeiculo())
                .setNegativeButton(R.string.botao_cancelar, null)
                .show();
    }

    private void excluirVeiculo() {
        executorBanco.execute(() -> {
            AppDatabase.getInstancia(getApplicationContext()).veiculoDao().excluir(veiculoEmEdicao);
            runOnUiThread(this::finalizarComSucesso);
        });
    }

    private void finalizarComSucesso() {
        setResult(RESULT_OK);
        finish();
    }
}
