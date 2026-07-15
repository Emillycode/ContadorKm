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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tela principal do KmContador.
 *
 * NESTA ETAPA:
 * - O botão Iniciar/Parar já liga/desliga o rastreamento REAL via GPS,
 *   feito pelo LocationService (Foreground Service).
 * - Antes de iniciar, pedimos a permissão de localização (e, no Android 13+,
 *   a permissão de notificação, exigida para o Foreground Service mostrar
 *   sua notificação contínua).
 *
 * PRÓXIMAS ETAPAS (ainda não implementadas aqui, de propósito):
 * 1. Banco local (Room) — salvar cada sessão finalizada e somar por
 *    dia/semana/mês para preencher tvKmHoje, tvKmSemana e tvKmMes.
 * 2. Timer de 40 min de inatividade com a contagem ativa — parar sozinho e
 *    notificar "Você está contando os km ainda?".
 * 3. Cadastro de veículos (carro/moto + tipo de óleo) e verificação do km
 *    acumulado contra os limites de troca de óleo.
 */
public class MainActivity extends AppCompatActivity implements LocationService.KmUpdateListener {

    private TextView tvKmSessaoAtual;
    private TextView tvKmHoje;
    private TextView tvKmSemana;
    private TextView tvKmMes;
    private Button btnStartStop;

    private boolean estaContando = false;

    private LocationService locationService;
    private boolean servicoConectado = false;

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

        // TODO: carregar valores reais de hoje/semana/mês do banco local (Room)
        tvKmHoje.setText("0");
        tvKmSemana.setText("0");
        tvKmMes.setText("0");

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
        locationService.resetarSessao();
        locationService.iniciarRastreamento();

        btnStartStop.setText(R.string.botao_stop);
        btnStartStop.setBackgroundTintList(getColorStateList(R.color.danger));

        // TODO: agendar aqui o timer de 40 min de inatividade da próxima etapa
    }

    private void pararContagem() {
        estaContando = false;

        if (locationService != null) {
            locationService.pararRastreamento();

            // TODO: salvar locationService.getKmSessaoAtual() no banco (Room),
            // associado ao veículo selecionado, e então atualizar
            // tvKmHoje / tvKmSemana / tvKmMes com os novos totais.
        }

        btnStartStop.setText(R.string.botao_start);
        btnStartStop.setBackgroundTintList(getColorStateList(R.color.accent));
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
