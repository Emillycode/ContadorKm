package com.example.contadorkm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

/**
 * Foreground Service responsável por rastrear a localização real do usuário
 * e calcular os km percorridos na sessão atual.
 *
 * Por que Foreground Service? Porque o Android mata serviços comuns em
 * segundo plano rapidamente. Um Foreground Service precisa mostrar uma
 * notificação contínua (fixa) enquanto roda, e é exatamente isso que
 * garante que o rastreamento continue mesmo com o app minimizado.
 */
public class LocationService extends Service {

    public static final String CHANNEL_ID_RASTREIO = "canal_rastreio_km";
    private static final int NOTIFICATION_ID_RASTREIO = 1001;

    private final IBinder binder = new LocalBinder();

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    private Location ultimaLocalizacao = null;
    private double kmSessaoAtualMetros = 0.0;

    private KmUpdateListener listener;

    /** A Activity implementa isso para receber os km atualizados em tempo real. */
    public interface KmUpdateListener {
        void onKmAtualizado(double kmTotalSessao);
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
        criarCanalNotificacao();

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

    /** Para o rastreamento e encerra o foreground service. */
    public void pararRastreamento() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
        stopForeground(true);
        stopSelf();
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
            }
        }
        ultimaLocalizacao = novaLocalizacao;

        if (listener != null) {
            listener.onKmAtualizado(getKmSessaoAtual());
        }

        // TODO: registrar o timestamp desta atualização — a próxima etapa
        // (timer de 40 min de inatividade) vai comparar "agora" com o
        // horário da última localização válida para decidir se para sozinho.
    }

    private void criarCanalNotificacao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel canal = new NotificationChannel(
                    CHANNEL_ID_RASTREIO,
                    "Rastreamento de km",
                    NotificationManager.IMPORTANCE_LOW);
            canal.setDescription("Notificação contínua enquanto o KmContador está rastreando sua viagem.");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(canal);
            }
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }
}
