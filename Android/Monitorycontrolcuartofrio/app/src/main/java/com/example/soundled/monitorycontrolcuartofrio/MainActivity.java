package com.example.soundled.monitorycontrolcuartofrio;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.text.InputType;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String ACCION_SMS_ENVIADO = "com.example.soundled.monitorycontrolcuartofrio.SMS_ENVIADO";
    private static final int PEDIR_PERMISOS = 1;

    private Preferencias prefs;
    private TextView txtTempAct, txtHumAct, txtUbicacion, txtEstado, txtAlarmas, txtActualizado, txtConfigEquipo,
            txtNumeroEquipo;
    private EditText editTempMin, editTempMax, editHumMin, editHumMax;
    private SwitchCompat switchSistema;
    private int colorNormal;

    private final BroadcastReceiver reporteRecibido = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String error = intent.getStringExtra(SMSReceiver.EXTRA_ERROR);
            if (error != null) {
                Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
            } else {
                mostrarUltimoReporte(true);  // rellena los campos vacíos con la configuración del equipo
            }
        }
    };

    private final BroadcastReceiver smsEnviado = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean ok = getResultCode() == Activity.RESULT_OK;
            Toast.makeText(MainActivity.this, ok ? R.string.sms_enviado : R.string.sms_no_enviado,
                    Toast.LENGTH_LONG).show();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT));  // iconos claros sobre la barra azul
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        View contenido = findViewById(R.id.contenido);
        // Desde Android 15 la app se dibuja detrás de las barras del sistema: se reserva su espacio.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.raiz), (v, insets) -> {
            Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            toolbar.setPadding(barras.left, barras.top, barras.right, 0);
            contenido.setPadding(barras.left, 0, barras.right, barras.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        prefs = new Preferencias(this);

        txtTempAct = findViewById(R.id.txtTempAct);
        txtHumAct = findViewById(R.id.txtHumAct);
        txtUbicacion = findViewById(R.id.txtUbicacion);
        txtEstado = findViewById(R.id.txtEstado);
        txtAlarmas = findViewById(R.id.txtAlarmas);
        txtActualizado = findViewById(R.id.txtActualizado);
        txtConfigEquipo = findViewById(R.id.txtConfigEquipo);
        txtNumeroEquipo = findViewById(R.id.txtNumeroEquipo);
        editTempMin = findViewById(R.id.editTempMin);
        editTempMax = findViewById(R.id.editTempMax);
        editHumMin = findViewById(R.id.editHumMin);
        editHumMax = findViewById(R.id.editHumMax);
        switchSistema = findViewById(R.id.switchSistema);
        colorNormal = txtTempAct.getCurrentTextColor();

        findViewById(R.id.btnActualizar).setOnClickListener(v -> enviarConfiguracion());
        findViewById(R.id.btnPedirLecturas).setOnClickListener(v -> enviarSms(Comandos.PEDIR_LECTURAS));
        findViewById(R.id.btnNumeroEquipo).setOnClickListener(v -> pedirNumeroEquipo());

        Notificaciones.crearCanal(this);
        ContextCompat.registerReceiver(this, smsEnviado, new IntentFilter(ACCION_SMS_ENVIADO),
                ContextCompat.RECEIVER_NOT_EXPORTED);
        pedirPermisosFaltantes();
        mostrarNumeroEquipo();
        if (prefs.numeroEquipo().isEmpty()) {
            pedirNumeroEquipo();
        }
        mostrarUltimoReporte(savedInstanceState == null);
    }

    @Override
    protected void onStart() {
        super.onStart();
        ContextCompat.registerReceiver(this, reporteRecibido, new IntentFilter(SMSReceiver.ACCION_REPORTE),
                ContextCompat.RECEIVER_NOT_EXPORTED);
        mostrarUltimoReporte(false);
    }

    @Override
    protected void onStop() {
        unregisterReceiver(reporteRecibido);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(smsEnviado);
        super.onDestroy();
    }

    private void mostrarUltimoReporte(boolean rellenarConfiguracion) {
        Reporte r = prefs.ultimoReporte();
        if (r == null) {
            txtActualizado.setText(R.string.sin_datos);
            return;
        }
        txtTempAct.setText(getString(R.string.valor_temperatura, r.temperatura));
        txtTempAct.setTextColor(colorAlarma(r.alarmaTempAlta, r.alarmaTempBaja));
        txtHumAct.setText(getString(R.string.valor_humedad, r.humedad));
        txtHumAct.setTextColor(colorAlarma(r.alarmaHumAlta, r.alarmaHumBaja));
        txtUbicacion.setText(r.ubicacion);
        txtAlarmas.setText(r.hayAlarma() ? Notificaciones.describirAlarmas(this, r) : getString(R.string.sin_alarmas));
        txtAlarmas.setTextColor(r.hayAlarma() ? ContextCompat.getColor(this, R.color.alarmaAlta) : colorNormal);
        Date hora = new Date(prefs.horaUltimoReporte());
        txtActualizado.setText(getString(R.string.actualizado,
                DateFormat.getDateFormat(this).format(hora) + " " + DateFormat.getTimeFormat(this).format(hora)));

        if (!r.tieneEstado) {
            txtEstado.setText(R.string.estado_no_disponible);
            txtConfigEquipo.setText("");
            return;
        }
        int estado = r.errorSensor ? R.string.estado_error_sensor
                : !r.sistemaEncendido ? R.string.estado_apagado
                : r.enfriando ? R.string.estado_enfriando : R.string.estado_reposo;
        txtEstado.setText(estado);
        txtConfigEquipo.setText(getString(R.string.config_equipo, r.tempMin, r.tempMax, r.humMin, r.humMax,
                getString(r.sistemaEncendido ? R.string.encendido : R.string.apagado)));
        if (rellenarConfiguracion && editTempMin.getText().length() == 0) {
            editTempMin.setText(String.valueOf(r.tempMin));
            editTempMax.setText(String.valueOf(r.tempMax));
            editHumMin.setText(String.valueOf(r.humMin));
            editHumMax.setText(String.valueOf(r.humMax));
            switchSistema.setChecked(r.sistemaEncendido);
        }
    }

    private int colorAlarma(boolean alta, boolean baja) {
        if (alta) return ContextCompat.getColor(this, R.color.alarmaAlta);
        if (baja) return ContextCompat.getColor(this, R.color.alarmaBaja);
        return colorNormal;
    }

    private void enviarConfiguracion() {
        Integer tempMin = leerEntero(editTempMin), tempMax = leerEntero(editTempMax);
        Integer humMin = leerEntero(editHumMin), humMax = leerEntero(editHumMax);
        if (tempMin == null || tempMax == null || humMin == null || humMax == null) {
            return;
        }
        int error = Comandos.validar(tempMin, tempMax, humMin, humMax);
        if (error != 0) {
            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            return;
        }
        enviarSms(Comandos.configuracion(tempMin, tempMax, humMin, humMax, switchSistema.isChecked()));
    }

    private Integer leerEntero(EditText campo) {
        try {
            return Integer.parseInt(campo.getText().toString().trim());
        } catch (NumberFormatException e) {
            campo.setError(getString(R.string.error_numero));
            campo.requestFocus();
            return null;
        }
    }

    private void enviarSms(String texto) {
        String numero = prefs.numeroEquipo();
        if (numero.isEmpty()) {
            pedirNumeroEquipo();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, R.string.falta_permiso_sms, Toast.LENGTH_LONG).show();
            pedirPermisosFaltantes();
            return;
        }
        PendingIntent enviado = PendingIntent.getBroadcast(this, 0,
                new Intent(ACCION_SMS_ENVIADO).setPackage(getPackageName()), PendingIntent.FLAG_IMMUTABLE);
        SmsManager sms = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? getSystemService(SmsManager.class) : SmsManager.getDefault();
        try {
            sms.sendTextMessage(numero, null, texto, enviado, null);
        } catch (RuntimeException e) {
            Toast.makeText(this, R.string.sms_no_enviado, Toast.LENGTH_LONG).show();
        }
    }

    private void pedirNumeroEquipo() {
        EditText campo = new EditText(this);
        campo.setInputType(InputType.TYPE_CLASS_PHONE);
        campo.setHint(R.string.hint_numero_equipo);
        campo.setText(prefs.numeroEquipo());
        AlertDialog dialogo = new AlertDialog.Builder(this)
                .setTitle(R.string.numero_equipo_titulo)
                .setMessage(R.string.numero_equipo_ayuda)
                .setView(campo)
                .setPositiveButton(R.string.guardar, null)
                .setNegativeButton(R.string.cancelar, null)
                .create();
        dialogo.setOnShowListener(d -> dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String numero = campo.getText().toString();
            if (numero.replaceAll("[^0-9]", "").length() < 10) {
                campo.setError(getString(R.string.error_numero_equipo));
                return;
            }
            prefs.guardarNumeroEquipo(numero);
            mostrarNumeroEquipo();
            dialogo.dismiss();
        }));
        dialogo.show();
    }

    private void mostrarNumeroEquipo() {
        String numero = prefs.numeroEquipo();
        txtNumeroEquipo.setText(numero.isEmpty() ? getString(R.string.numero_no_configurado)
                : getString(R.string.numero_equipo, numero));
    }

    private void pedirPermisosFaltantes() {
        List<String> faltan = new ArrayList<>();
        List<String> necesarios = new ArrayList<>();
        necesarios.add(Manifest.permission.SEND_SMS);
        necesarios.add(Manifest.permission.RECEIVE_SMS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            necesarios.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        for (String permiso : necesarios) {
            if (ContextCompat.checkSelfPermission(this, permiso) != PackageManager.PERMISSION_GRANTED) {
                faltan.add(permiso);
            }
        }
        if (!faltan.isEmpty()) {
            ActivityCompat.requestPermissions(this, faltan.toArray(new String[0]), PEDIR_PERMISOS);
        }
    }
}
