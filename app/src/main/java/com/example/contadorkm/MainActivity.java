package com.example.contadorkm;



import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.contadorkm.R;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tela principal do KmContador.
 *
 * NESTA ETAPA:
 * - Ao clicar em "Parar", a sessão (início, fim, km percorridos) é salva
 *   no banco local (Room).
 * - Os cartões Hoje / Semana / Mês agora mostram a SOMA REAL das sessões
 *   salvas, buscada no banco toda vez que a tela abre e toda vez que uma
 *   sessão nova é salva.
 *
 * PRÓXIMAS ETAPAS (ainda não implementadas aqui, de propósito):
 * 1. Timer de 40 min de inatividade com a contagem ativa — parar sozinho e
 *    notificar "Você está contando os km ainda?".
 * 2. Cadastro de veículos (carro/moto + tipo de óleo) e verificação do km
 *    acumulado contra os limites de troca de óleo.
 */
public class MainActivity extends AppCompatActivity implements LocationService.KmUpdateListener {

    private TextView tvKmSessaoAtual;
    private TextView tvKmHoje;
    private TextView tvKmSemana;
    private TextView tvKmMes;
    private Button btnStartStop;

    private boolean estaContando = false;
    private long inicioSessaoMillis = 0L;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvKmSessaoAtual = findViewById(R.id.tvKmSessaoAtual);
        tvKmHoje = findViewById(R.id.tvKmHoje);
        tvKmSemana = findViewById(R.id.tvKmSemana);
        tvKmMes = findViewById(R.id.tvKmMes);
        btnStartStop = findViewById(R.id.btnStartStop);

        carregarResumos();

        Intent intentServico = new Intent(this, LocationService.class);
        bindService(intentServico, serviceConnection, Context.BIND_AUTO_CREATE);

        btnStartStop.setOnClickListener(v -> alternarContagem());
    }

    private void alternarContagem() {
        if (!estaContando) {
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

        // TODO: agendar aqui o timer de 40 min de inatividade da próxima etapa
    }

    private void pararContagem() {
        estaContando = false;

        if (locationService != null) {
            double kmDaSessao = locationService.getKmSessaoAtual();
            long fimSessaoMillis = System.currentTimeMillis();
            locationService.pararRastreamento();

            if (kmDaSessao > 0) {
                salvarSessaoNoBanco(inicioSessaoMillis, fimSessaoMillis, kmDaSessao);
            }

            // TODO: quando os veículos existirem, verificar aqui se o km
            // acumulado do veículo selecionado atingiu o limite de troca de óleo.
        }

        btnStartStop.setText(R.string.botao_start);
        btnStartStop.setBackgroundTintList(getColorStateList(R.color.accent));
    }

    private void salvarSessaoNoBanco(long inicioMillis, long fimMillis, double km) {
        executorBanco.execute(() -> {
            SessaoKm sessao = new SessaoKm(inicioMillis, fimMillis, km);
            AppDatabase.getInstancia(getApplicationContext()).sessaoKmDao().inserir(sessao);
            carregarResumos(); // já roda em background e volta pra UI thread sozinho
        });
    }

    /** Busca no banco a soma de km de hoje, da semana e do mês, e atualiza a tela. */
    private void carregarResumos() {
        executorBanco.execute(() -> {
            SessaoKmDao dao = AppDatabase.getInstancia(getApplicationContext()).sessaoKmDao();

            double kmHoje = dao.somarKmDesde(obterInicioDoDia());
            double kmSemana = dao.somarKmDesde(obterInicioDaSemana());
            double kmMes = dao.somarKmDesde(obterInicioDoMes());

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
