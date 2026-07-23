package com.example.contadorkm;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tela principal do KmContador.
 *
 * NESTA ETAPA:
 * - Ao clicar em "Parar" (ou quando o auto-stop por inatividade dispara),
 *   a sessão é salva no banco E o km é somado ao veículo selecionado no
 *   momento; se esse total ultrapassar o limite de troca de óleo do
 *   veículo, uma notificação de troca de óleo é disparada.
 * - A lista "Meus veículos" é carregada do banco; tocar em um veículo o
 *   torna o "veículo ativo" (destacado), usado para associar a próxima
 *   sessão. Essa seleção é lembrada entre aberturas do app.
 * - É preciso ter um veículo selecionado antes de iniciar a contagem.
 */
public class MainActivity extends AppCompatActivity implements LocationService.KmUpdateListener {

    private static final String PREFS_NOME = "kmcontador_prefs";
    private static final String PREF_VEICULO_SELECIONADO_ID = "veiculo_selecionado_id";

    private TextView tvKmSessaoAtual;
    private TextView tvKmHoje;
    private TextView tvKmSemana;
    private TextView tvKmMes;
    private TextView tvVeiculoDoResumo;
    private Button btnStartStop;
    private LinearLayout containerVeiculos;
    private TextView tvAdicionarVeiculo;

    private boolean estaContando = false;
    private long inicioSessaoMillis = 0L;

    private List<Veiculo> veiculosCarregados = new ArrayList<>();
    private long veiculoSelecionadoId = -1L;

    private LocationService locationService;
    private boolean servicoConectado = false;

    // Toda operação no banco (Room) roda fora da thread principal.
    private final ExecutorService executorBanco = Executors.newSingleThreadExecutor();

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            LocationService.LocalBinder binder = (LocationService.LocalBinder) service;
            locationService = binder.getService();
            locationService.setListener(MainActivity.this);
            servicoConectado = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            servicoConectado = false;
            locationService = null;
        }
    };

    private final ActivityResultLauncher<String[]> solicitarPermissoesLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), resultados -> {
                boolean localizacaoConcedida = Boolean.TRUE.equals(
                        resultados.get(Manifest.permission.ACCESS_FINE_LOCATION));
                if (localizacaoConcedida) {
                    iniciarContagem();
                } else {
                    Toast.makeText(this,
                            "Preciso da permissão de localização para contar os km.",
                            Toast.LENGTH_LONG).show();
                }
            });

    private final ActivityResultLauncher<Intent> cadastroVeiculoLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), resultado -> {
                if (resultado.getResultCode() == RESULT_OK) {
                    carregarVeiculos();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvKmSessaoAtual = findViewById(R.id.tvKmSessaoAtual);
        tvKmHoje = findViewById(R.id.tvKmHoje);
        tvKmSemana = findViewById(R.id.tvKmSemana);
        tvKmMes = findViewById(R.id.tvKmMes);
        tvVeiculoDoResumo = findViewById(R.id.tvVeiculoDoResumo);
        btnStartStop = findViewById(R.id.btnStartStop);
        containerVeiculos = findViewById(R.id.containerVeiculos);
        tvAdicionarVeiculo = findViewById(R.id.tvAdicionarVeiculo);

        NotificationHelper.criarCanal(this);

        SharedPreferences prefs = getPreferences();
        veiculoSelecionadoId = prefs.getLong(PREF_VEICULO_SELECIONADO_ID, -1L);

        carregarVeiculos();

        Intent intentServico = new Intent(this, LocationService.class);
        bindService(intentServico, serviceConnection, Context.BIND_AUTO_CREATE);

        btnStartStop.setOnClickListener(v -> alternarContagem());
        tvAdicionarVeiculo.setOnClickListener(v ->
                cadastroVeiculoLauncher.launch(new Intent(this, CadastroVeiculoActivity.class)));
    }

    private SharedPreferences getPreferences() {
        return getSharedPreferences(PREFS_NOME, MODE_PRIVATE);
    }

    // ---------------------------------------------------------------
    // Veículos: carregar, exibir lista, selecionar veículo ativo
    // ---------------------------------------------------------------

    private void carregarVeiculos() {
        executorBanco.execute(() -> {
            List<Veiculo> veiculos = AppDatabase.getInstancia(getApplicationContext())
                    .veiculoDao().listarTodos();
            runOnUiThread(() -> exibirListaDeVeiculos(veiculos));
        });
    }

    private void exibirListaDeVeiculos(List<Veiculo> veiculos) {
        veiculosCarregados = veiculos;
        containerVeiculos.removeAllViews();

        if (veiculos.isEmpty()) {
            TextView placeholder = new TextView(this);
            placeholder.setText(R.string.veiculo_placeholder);
            placeholder.setTextColor(getColor(R.color.text_secondary));
            placeholder.setGravity(Gravity.CENTER);
            int padding = dpParaPx(20);
            placeholder.setPadding(padding, padding, padding, padding);
            containerVeiculos.addView(placeholder);
            veiculoSelecionadoId = -1L;
            atualizarResumoParaVeiculoSelecionado();
            return;
        }

        boolean idSelecionadoAindaExiste = false;
        for (Veiculo veiculo : veiculos) {
            if (veiculo.id == veiculoSelecionadoId) {
                idSelecionadoAindaExiste = true;
                break;
            }
        }
        if (!idSelecionadoAindaExiste) {
            // Se nada estava selecionado (ou o selecionado foi removido), assume o primeiro.
            veiculoSelecionadoId = veiculos.get(0).id;
            salvarVeiculoSelecionadoNasPrefs();
        }

        for (Veiculo veiculo : veiculos) {
            containerVeiculos.addView(criarLinhaDeVeiculo(veiculo));
        }

        atualizarResumoParaVeiculoSelecionado();
    }

    /** Atualiza o texto "Km de: <veículo>" e recarrega Hoje/Semana/Mês filtrados por ele. */
    private void atualizarResumoParaVeiculoSelecionado() {
        if (veiculoSelecionadoId == -1L) {
            tvVeiculoDoResumo.setText(R.string.resumo_sem_veiculo);
            tvKmHoje.setText(formatarKm(0));
            tvKmSemana.setText(formatarKm(0));
            tvKmMes.setText(formatarKm(0));
            return;
        }

        String nomeVeiculo = "";
        for (Veiculo veiculo : veiculosCarregados) {
            if (veiculo.id == veiculoSelecionadoId) {
                nomeVeiculo = veiculo.nome;
                break;
            }
        }
        tvVeiculoDoResumo.setText(getString(R.string.resumo_km_do_veiculo, nomeVeiculo));
        carregarResumos(veiculoSelecionadoId);
    }

    private View criarLinhaDeVeiculo(Veiculo veiculo) {
        LinearLayout linha = new LinearLayout(this);
        linha.setOrientation(LinearLayout.HORIZONTAL);
        linha.setGravity(Gravity.CENTER_VERTICAL);
        int paddingH = dpParaPx(16);
        int paddingV = dpParaPx(14);
        linha.setPadding(paddingH, paddingV, paddingH, paddingV);

        boolean selecionado = veiculo.id == veiculoSelecionadoId;
        linha.setBackgroundColor(getColor(selecionado ? R.color.background : R.color.card_background));

        TextView tvNomeTipo = new TextView(this);
        String tipoLegivel = Tipos.CARRO.equals(veiculo.tipoVeiculo)
                ? getString(R.string.opcao_carro) : getString(R.string.opcao_moto);
        tvNomeTipo.setText(String.format(Locale.getDefault(), "%s (%s)", veiculo.nome, tipoLegivel));
        tvNomeTipo.setTextColor(getColor(R.color.text_primary));
        tvNomeTipo.setTextSize(15f);
        LinearLayout.LayoutParams paramsNome = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tvNomeTipo.setLayoutParams(paramsNome);

        TextView tvStatusOleo = new TextView(this);
        double limite = veiculo.getLimiteTrocaOleoKm();
        String textoStatus = String.format(Locale.getDefault(), "%.0f / %.0f km",
                veiculo.kmDesdeTrocaOleo, limite);
        if (veiculo.ultimaTrocaOleoMillis > 0) {
            SimpleDateFormat formatoData = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            String dataFormatada = formatoData.format(new Date(veiculo.ultimaTrocaOleoMillis));
            textoStatus += "\n" + getString(R.string.texto_ultima_troca_oleo, dataFormatada);
        }
        tvStatusOleo.setText(textoStatus);
        tvStatusOleo.setTextColor(getColor(veiculo.atingiuLimiteTrocaOleo()
                ? R.color.danger : R.color.text_secondary));
        tvStatusOleo.setTextSize(13f);
        tvStatusOleo.setGravity(Gravity.END);

        TextView tvEditar = new TextView(this);
        tvEditar.setText(R.string.texto_editar_veiculo);
        tvEditar.setTextColor(getColor(R.color.primary));
        tvEditar.setTextSize(13f);
        tvEditar.setTypeface(null, android.graphics.Typeface.BOLD);
        int paddingEditar = dpParaPx(8);
        tvEditar.setPadding(paddingEditar, 0, 0, 0);
        tvEditar.setOnClickListener(v -> abrirEdicaoDeVeiculo(veiculo));

        linha.addView(tvNomeTipo);
        linha.addView(tvStatusOleo);
        linha.addView(tvEditar);

        linha.setOnClickListener(v -> {
            veiculoSelecionadoId = veiculo.id;
            salvarVeiculoSelecionadoNasPrefs();
            exibirListaDeVeiculos(veiculosCarregados); // redesenha para destacar a nova seleção
        });

        return linha;
    }

    private void abrirEdicaoDeVeiculo(Veiculo veiculo) {
        Intent intent = new Intent(this, CadastroVeiculoActivity.class);
        intent.putExtra(CadastroVeiculoActivity.EXTRA_VEICULO_ID, veiculo.id);
        cadastroVeiculoLauncher.launch(intent);
    }

    private void salvarVeiculoSelecionadoNasPrefs() {
        getPreferences().edit().putLong(PREF_VEICULO_SELECIONADO_ID, veiculoSelecionadoId).apply();
    }

    private int dpParaPx(int dp) {
        float densidade = getResources().getDisplayMetrics().density;
        return Math.round(dp * densidade);
    }

    // ---------------------------------------------------------------
    // Início / fim da contagem
    // ---------------------------------------------------------------

    private void alternarContagem() {
        if (!estaContando) {
            if (veiculoSelecionadoId == -1L) {
                Toast.makeText(this, R.string.selecione_um_veiculo_toast, Toast.LENGTH_LONG).show();
                return;
            }
            verificarPermissoesEIniciar();
        } else {
            pararContagem();
        }
    }

    private void verificarPermissoesEIniciar() {
        List<String> permissoesFaltando = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            permissoesFaltando.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            permissoesFaltando.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (!permissoesFaltando.isEmpty()) {
            solicitarPermissoesLauncher.launch(permissoesFaltando.toArray(new String[0]));
        } else {
            iniciarContagem();
        }
    }

    private void iniciarContagem() {
        if (!servicoConectado || locationService == null) {
            Toast.makeText(this,
                    "Serviço de localização ainda não conectado, tente novamente.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        estaContando = true;
        inicioSessaoMillis = System.currentTimeMillis();
        locationService.resetarSessao();
        locationService.iniciarRastreamento();

        btnStartStop.setText(R.string.botao_stop);
        btnStartStop.setBackgroundTintList(getColorStateList(R.color.danger));
    }

    private void pararContagem() {
        if (locationService != null) {
            double kmDaSessao = locationService.getKmSessaoAtual();
            locationService.pararRastreamento();
            finalizarSessaoLocalmente(kmDaSessao);
        }
        atualizarBotaoParaEstadoParado();
    }

    /** Chamado pelo LocationService quando o timer de 40 min de inatividade dispara. */
    @Override
    public void onParadaAutomaticaPorInatividade(double kmTotalSessao) {
        runOnUiThread(() -> {
            finalizarSessaoLocalmente(kmTotalSessao);
            atualizarBotaoParaEstadoParado();
            Toast.makeText(this,
                    getString(R.string.notificacao_ainda_contando_titulo),
                    Toast.LENGTH_LONG).show();
        });
    }

    /** Salva a sessão, soma o km ao veículo selecionado e verifica a troca de óleo —
     *  usado tanto pelo botão "Parar" quanto pelo auto-stop por inatividade. */
    private void finalizarSessaoLocalmente(double kmDaSessao) {
        estaContando = false;
        long fimSessaoMillis = System.currentTimeMillis();
        long veiculoDaSessao = veiculoSelecionadoId;

        if (kmDaSessao > 0) {
            salvarSessaoNoBanco(inicioSessaoMillis, fimSessaoMillis, kmDaSessao, veiculoDaSessao);
        }
    }

    private void atualizarBotaoParaEstadoParado() {
        btnStartStop.setText(R.string.botao_start);
        btnStartStop.setBackgroundTintList(getColorStateList(R.color.accent));
    }

    private void salvarSessaoNoBanco(long inicioMillis, long fimMillis, double km, long veiculoId) {
        executorBanco.execute(() -> {
            AppDatabase db = AppDatabase.getInstancia(getApplicationContext());

            SessaoKm sessao = new SessaoKm(inicioMillis, fimMillis, km, veiculoId);
            db.sessaoKmDao().inserir(sessao);

            if (veiculoId != -1L) {
                Veiculo veiculo = db.veiculoDao().buscarPorId(veiculoId);
                if (veiculo != null) {
                    veiculo.kmDesdeTrocaOleo += km;
                    db.veiculoDao().atualizar(veiculo);

                    if (veiculo.atingiuLimiteTrocaOleo()) {
                        NotificationHelper.notificarTrocaOleo(getApplicationContext(), veiculo);
                    }
                }
            }

            carregarVeiculos();
        });
    }

    /** Busca no banco a soma de km de hoje, da semana e do mês PARA UM VEÍCULO
     *  específico, e atualiza a tela. */
    private void carregarResumos(long veiculoId) {
        executorBanco.execute(() -> {
            SessaoKmDao dao = AppDatabase.getInstancia(getApplicationContext()).sessaoKmDao();

            double kmHoje = dao.somarKmPorVeiculoDesde(veiculoId, obterInicioDoDia());
            double kmSemana = dao.somarKmPorVeiculoDesde(veiculoId, obterInicioDaSemana());
            double kmMes = dao.somarKmPorVeiculoDesde(veiculoId, obterInicioDoMes());

            runOnUiThread(() -> {
                tvKmHoje.setText(formatarKm(kmHoje));
                tvKmSemana.setText(formatarKm(kmSemana));
                tvKmMes.setText(formatarKm(kmMes));
            });
        });
    }

    private String formatarKm(double km) {
        return String.format(Locale.getDefault(), "%.1f", km);
    }

    private long obterInicioDoDia() {
        Calendar calendario = Calendar.getInstance();
        calendario.set(Calendar.HOUR_OF_DAY, 0);
        calendario.set(Calendar.MINUTE, 0);
        calendario.set(Calendar.SECOND, 0);
        calendario.set(Calendar.MILLISECOND, 0);
        return calendario.getTimeInMillis();
    }

    private long obterInicioDaSemana() {
        Calendar calendario = Calendar.getInstance();
        calendario.setFirstDayOfWeek(Calendar.MONDAY);
        calendario.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        calendario.set(Calendar.HOUR_OF_DAY, 0);
        calendario.set(Calendar.MINUTE, 0);
        calendario.set(Calendar.SECOND, 0);
        calendario.set(Calendar.MILLISECOND, 0);
        return calendario.getTimeInMillis();
    }

    private long obterInicioDoMes() {
        Calendar calendario = Calendar.getInstance();
        calendario.set(Calendar.DAY_OF_MONTH, 1);
        calendario.set(Calendar.HOUR_OF_DAY, 0);
        calendario.set(Calendar.MINUTE, 0);
        calendario.set(Calendar.SECOND, 0);
        calendario.set(Calendar.MILLISECOND, 0);
        return calendario.getTimeInMillis();
    }

    @Override
    public void onKmAtualizado(double kmTotalSessao) {
        runOnUiThread(() ->
                tvKmSessaoAtual.setText(String.format(Locale.getDefault(), "%.2f", kmTotalSessao)));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (servicoConectado) {
            unbindService(serviceConnection);
            servicoConectado = false;
        }
    }
}
