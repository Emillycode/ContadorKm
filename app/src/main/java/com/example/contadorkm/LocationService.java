package com.example.contadorkm;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

/**
 * Foreground Service responsável por rastrear a localização real do usuário,
 * calcular os km percorridos na sessão atual e detectar inatividade
 * prolongada (40 min sem deslocamento real) para parar sozinho.
 *
 * Por que Foreground Service? Porque o Android mata serviços comuns em
 * segundo plano rapidamente. Um Foreground Service precisa mostrar uma
 * notificação contínua (fixa) enquanto roda, e é exatamente isso que
 * garante que o rastreamento continue mesmo com o app minimizado.
 */
public class LocationService extends Service {

    public static final String CHANNEL_ID_RASTREIO = "canal_rastreio_km";
    public static final String CHANNEL_ID_ALERTA = "canal_alerta_inatividade";
    private static final int NOTIFICATION_ID_RASTREIO = 1001;
    private static final int NOTIFICATION_ID_ALERTA = 1002;

    private static final long LIMITE_INATIVIDADE_MILLIS = 40 * 60 * 1000L; // 40 minutos
    private static final long INTERVALO_VERIFICACAO_MILLIS = 60 * 1000L;   // checa a cada 1 min

    private final IBinder binder = new LocalBinder();

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    private Location ultimaLocalizacao = null;
    private double kmSessaoAtualMetros = 0.0;
    private long tempoUltimoMovimentoMillis = 0L;

    private KmUpdateListener listener;

    private final Handler handlerInatividade = new Handler(Looper.getMainLooper());
    private final Runnable verificadorDeInatividade = new Runnable() {
        @Override
        public void run() {
            long tempoParadoMillis = System.currentTimeMillis() - tempoUltimoMovimentoMillis;
            if (tempoParadoMillis >= LIMITE_INATIVIDADE_MILLIS) {
                dispararAutoStopPorInatividade();
            } else {
                handlerInatividade.postDelayed(this, INTERVALO_VERIFICACAO_MILLIS);
            }
        }
    };

    /** A Activity implementa isso para receber os km atualizados em tempo real
     *  e para ser avisada quando o serviço parar sozinho por inatividade. */
    public interface KmUpdateListener {
        void onKmAtualizado(double kmTotalSessao);
        void onParadaAutomaticaPorInatividade(double kmTotalSessao);
    }

    public class LocalBinder extends Binder {
        public LocationService getService() {
            return LocationService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        criarCanaisDeNotificacao();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;
                for (Location novaLocalizacao : locationResult.getLocations()) {
                    processarNovaLocalizacao(novaLocalizacao);
                }
            }
        };
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void setListener(KmUpdateListener listener) {
        this.listener = listener;
    }

    /** Inicia o rastreamento em primeiro plano (foreground) e os updates de localização. */
    public void iniciarRastreamento() {
        startForeground(NOTIFICATION_ID_RASTREIO, criarNotificacaoRastreio());

        tempoUltimoMovimentoMillis = System.currentTimeMillis();
        handlerInatividade.removeCallbacks(verificadorDeInatividade);
        handlerInatividade.postDelayed(verificadorDeInatividade, INTERVALO_VERIFICACAO_MILLIS);

        LocationRequest locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 5000) // pede atualização a cada 5s
                .setMinUpdateDistanceMeters(5f) // ignora "tremidas" de GPS menores que 5m
                .build();

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        } catch (SecurityException e) {
            // Não deve acontecer: a Activity garante a permissão antes de chamar este método.
        }
    }

    /** Para o rastreamento por ação do usuário (botão "Parar"). */
    public void pararRastreamento() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
        handlerInatividade.removeCallbacks(verificadorDeInatividade);
        stopForeground(true);
    }

    /** Zera a distância acumulada (chamado ao iniciar uma nova sessão). */
    public void resetarSessao() {
        kmSessaoAtualMetros = 0.0;
        ultimaLocalizacao = null;
    }

    public double getKmSessaoAtual() {
        return kmSessaoAtualMetros / 1000.0;
    }

    private static final float ACURACIA_MAXIMA_ACEITA_METROS = 30f;

    private void processarNovaLocalizacao(Location novaLocalizacao) {
        // Leitura de GPS ruim demais (ex.: sinal fraco, dentro de prédio) — ignora por completo.
        if (!novaLocalizacao.hasAccuracy() || novaLocalizacao.getAccuracy() > ACURACIA_MAXIMA_ACEITA_METROS) {
            return;
        }

        boolean houveMovimentoReal = false;

        if (ultimaLocalizacao != null) {
            float distanciaMetros = ultimaLocalizacao.distanceTo(novaLocalizacao);

            // O GPS tem uma margem de erro (accuracy) própria em cada leitura.
            // Só contamos a distância como deslocamento real se ela for maior
            // que a soma das margens de erro dos dois pontos — assim, "ruído"
            // do GPS parado não vira km andado. E descartamos saltos absurdos
            // (>200m entre leituras de 5s, sinal de erro de sinal).
            float acuraciaCombinada = ultimaLocalizacao.getAccuracy() + novaLocalizacao.getAccuracy();

            if (distanciaMetros > acuraciaCombinada && distanciaMetros < 200f) {
                kmSessaoAtualMetros += distanciaMetros;
                houveMovimentoReal = true;
            }
        }
        ultimaLocalizacao = novaLocalizacao;

        // O timer de inatividade só é resetado quando há movimento REAL — ou
        // seja, se o usuário ficar parado (mesmo recebendo leituras de GPS
        // que não geram km), o relógio da inatividade continua contando.
        if (houveMovimentoReal) {
            tempoUltimoMovimentoMillis = System.currentTimeMillis();
        }

        if (listener != null) {
            listener.onKmAtualizado(getKmSessaoAtual());
        }
    }

    /** Chamado pelo verificadorDeInatividade quando 40 min se passam sem movimento real. */
    private void dispararAutoStopPorInatividade() {
        double kmFinal = getKmSessaoAtual();

        fusedLocationClient.removeLocationUpdates(locationCallback);
        stopForeground(true);
        dispararNotificacaoInatividade();

        if (listener != null) {
            listener.onParadaAutomaticaPorInatividade(kmFinal);
        }
        // Não chamamos stopSelf() aqui: o serviço continua vivo (bound) para
        // que a Activity consiga chamar iniciarRastreamento() novamente numa
        // próxima sessão sem precisar recriar a conexão.
    }

    private void criarCanaisDeNotificacao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager == null) return;

            NotificationChannel canalRastreio = new NotificationChannel(
                    CHANNEL_ID_RASTREIO,
                    "Rastreamento de km",
                    NotificationManager.IMPORTANCE_LOW);
            canalRastreio.setDescription("Notificação contínua enquanto o KmContador está rastreando sua viagem.");
            manager.createNotificationChannel(canalRastreio);

            NotificationChannel canalAlerta = new NotificationChannel(
                    CHANNEL_ID_ALERTA,
                    "Alerta de inatividade",
                    NotificationManager.IMPORTANCE_HIGH);
            canalAlerta.setDescription("Avisa quando a contagem de km é parada automaticamente por inatividade.");
            manager.createNotificationChannel(canalAlerta);
        }
    }

    private Notification criarNotificacaoRastreio() {
        Intent intentAbrirApp = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intentAbrirApp, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID_RASTREIO)
                .setContentTitle("KmContador")
                .setContentText("Rastreando seus km em tempo real...")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void dispararNotificacaoInatividade() {
        // Em Android 13+ a notificação exige permissão POST_NOTIFICATIONS,
        // já solicitada pela MainActivity antes de iniciar a contagem.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Intent intentAbrirApp = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intentAbrirApp, PendingIntent.FLAG_IMMUTABLE);

        Notification notificacao = new NotificationCompat.Builder(this, CHANNEL_ID_ALERTA)
                .setContentTitle(getString(R.string.notificacao_ainda_contando_titulo))
                .setContentText(getString(R.string.notificacao_ainda_contando_texto))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID_ALERTA, notificacao);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        fusedLocationClient.removeLocationUpdates(locationCallback);
        handlerInatividade.removeCallbacks(verificadorDeInatividade);
    }
}
