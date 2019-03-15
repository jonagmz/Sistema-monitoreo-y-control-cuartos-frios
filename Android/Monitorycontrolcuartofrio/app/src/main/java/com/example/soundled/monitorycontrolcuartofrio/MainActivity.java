package com.example.soundled.monitorycontrolcuartofrio;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.provider.Telephony;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.CompoundButtonCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    Button btn_actulizar;
    TextView temperatura, temperatura_actual, humedad, humedad_actual, txt_ubicacion;
    IntentFilter intentFilter;
    EditText Temp_min, Temp_max, Hum_min, Hum_max;
    Switch SwitchEstado;
    String Estado_del_switch, AlarmaTempAlta, AlarmaTempBaja, AlarmaHumBaja, AlarmaHumAlta;


    private BroadcastReceiver intentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //TextView inTxt = (TextView) findViewById(R.id.txtMsg);
            //inTxt.setText(intent.getExtras().getString("sms").toString());
            String Datos = intent.getExtras().getString("sms").toString();
            String DatosDiv[] = Datos.split(",");
            temperatura_actual.setText(DatosDiv[1] + "ºC");
            humedad_actual.setText(DatosDiv[2] + "%");
            txt_ubicacion.setText(DatosDiv[3]);

            AlarmaTempBaja = DatosDiv[4].toString();
            AlarmaTempAlta = DatosDiv[5].toString();
            AlarmaHumBaja = DatosDiv[6].toString();
            AlarmaHumAlta = DatosDiv[7].toString();



           if(AlarmaTempBaja.equals("1")){
               temperatura_actual.setTextColor(Color.BLUE);
           }

            if(AlarmaTempBaja.equals("0")){
                temperatura_actual.setTextColor(Color.BLACK);
            }

            if(AlarmaTempAlta.equals("1")){
                temperatura_actual.setTextColor(Color.RED);
            }
            if(AlarmaTempAlta.equals("0")){
                temperatura_actual.setTextColor(Color.BLACK);
            }

            if(AlarmaHumBaja.equals("1")){
                humedad_actual.setTextColor(Color.BLUE);
            }

            if(AlarmaHumBaja.equals("0")){
                humedad_actual.setTextColor(Color.BLACK);
            }

            if(AlarmaHumAlta.equals("1")){
                humedad_actual.setTextColor(Color.RED);
            }

            if(AlarmaHumAlta.equals("0")){
                humedad_actual.setTextColor(Color.BLACK);
            }


        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        intentFilter = new IntentFilter();
        intentFilter.addAction("SMS_RECEIVED_ACTION");

        temperatura = (TextView) findViewById(R.id.txtTemp);
        temperatura_actual = (TextView) findViewById(R.id.txtTempAct);
        humedad = (TextView) findViewById(R.id.txtHum);
        humedad_actual = (TextView) findViewById(R.id.txtHumAct);
        txt_ubicacion = (TextView) findViewById(R.id.txtUbicaciom);
        Temp_min = (EditText) findViewById(R.id.editTempMin);
        Temp_max = (EditText) findViewById(R.id.editTempMax);
        Hum_min = (EditText) findViewById(R.id.editHumMin);
        Hum_max = (EditText) findViewById(R.id.editHumMax);
        SwitchEstado = (Switch) findViewById(R.id.switch1);
        btn_actulizar = (Button) findViewById(R.id.btnActualizar);
        btn_actulizar.setOnClickListener(this);
        SwitchEstado.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked){
                    Estado_del_switch = "1";

                }
                else {
                    Estado_del_switch = "0";
                }
            }
        });

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {

            case R.id.btnActualizar:

                String myMsg = "@*" + Temp_min.getText().toString() + "*" + Temp_max.getText().toString() + "*" + Hum_min.getText().toString() + "*" + Hum_max.getText().toString() + "*" + Estado_del_switch;
                String theNumber = "0000000000";
                EnviarMensaje(theNumber, myMsg);
                break;
        }
    }


    private void EnviarMensaje(String theNumber, String myMsg) {

        try {
            int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS);

            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getApplicationContext(), "No se tiene permiso para enviar mensajes SMS", Toast.LENGTH_LONG).show();
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.SEND_SMS}, 255);
            } else {
                Log.i("Mensaje", "Se tiene permiso para enviar mensajes SMS");
            }


            String SENT = "Message Sent";
            String DELIVERED = "Message Delivered";




            PendingIntent sentPI = PendingIntent.getBroadcast(this,0, new Intent(SENT),0);
            PendingIntent deliveredPI = PendingIntent.getBroadcast(this,0, new Intent(DELIVERED),0);

            registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    switch (getResultCode()) {
                        case Activity.RESULT_OK:
                            Toast.makeText(MainActivity.this, "Mensaje SMS enviado", Toast.LENGTH_LONG).show();
                            break;
                        case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                            Toast.makeText(getBaseContext(), "Fallo generico", Toast.LENGTH_LONG).show();
                            break;
                        case SmsManager.RESULT_ERROR_NO_SERVICE:
                            Toast.makeText(getBaseContext(), "Sin servicio", Toast.LENGTH_LONG).show();
                            break;
                    }
                }
            }, new IntentFilter(SENT));

            registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    switch (getResultCode()) {
                        case Activity.RESULT_OK:
                            Toast.makeText(getBaseContext(), "Mensaje SMS recibido", Toast.LENGTH_LONG).show();
                            break;
                        case Activity.RESULT_CANCELED:
                            Toast.makeText(getBaseContext(), "No se recibio el mensaje SMS", Toast.LENGTH_LONG).show();
                            break;
                    }
                }
            }, new IntentFilter(DELIVERED));



            SmsManager sms = SmsManager.getDefault();
            sms.sendTextMessage(theNumber, null, myMsg, sentPI, deliveredPI);
        }

        catch (Exception e){
            Toast.makeText(getApplicationContext(),"Mensaje no enviado, verique permisos o datos", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    @Override
    protected void  onResume(){
        registerReceiver(intentReceiver,intentFilter);
        super.onResume();
    }

    @Override
    protected void onPause(){
        unregisterReceiver(intentReceiver);
        super.onPause();
    }



}
