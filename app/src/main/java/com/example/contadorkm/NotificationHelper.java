package com.example.contadorkm;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

/** Cria o canal e dispara a notificação de "hora de trocar o óleo" de um veículo. */
public final class NotificationHelper {

    public static final String CHANNEL_ID_TROCA_OLEO = "canal_alerta_troca_oleo";

    private NotificationHelper() {
        // classe utilitária, não deve ser instanciada
    }

    public static void criarCanal(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel canal = new NotificationChannel(
                    CHANNEL_ID_TROCA_OLEO,
                    "Troca de óleo",
                    NotificationManager.IMPORTANCE_HIGH);
            canal.setDescription("Avisa quando um veículo atinge o km recomendado para troca de óleo.");
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(canal);
            }
        }
    }

    public static void notificarTrocaOleo(Context context, Veiculo veiculo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Intent intentAbrirApp = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intentAbrirApp, PendingIntent.FLAG_IMMUTABLE);

        String texto = context.getString(R.string.notificacao_troca_oleo_texto_com_nome, veiculo.nome);

        Notification notificacao = new NotificationCompat.Builder(context, CHANNEL_ID_TROCA_OLEO)
                .setContentTitle(context.getString(R.string.notificacao_troca_oleo_titulo))
                .setContentText(texto)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            // ID único por veículo para não sobrescrever o alerta de outro veículo.
            manager.notify(2000 + (int) veiculo.id, notificacao);
        }
    }
}
