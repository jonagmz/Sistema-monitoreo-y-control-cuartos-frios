package com.example.soundled.monitorycontrolcuartofrio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.widget.Toast;

/**
 * Created by soundled on 23/06/17.
 */

public class SMSReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle bundle = intent.getExtras();
        SmsMessage[] messages = null;
        String str = "";

        if(bundle != null)
        {
            Object[] pdus = (Object[]) bundle.get("pdus");
            messages = new SmsMessage[pdus.length];
                for(int i=0; i<messages.length; i++)
                {
                    messages[i] = SmsMessage.createFromPdu((byte[])pdus[i]);
                    str += messages[i].getOriginatingAddress();
                    str += ",";
                    str += messages[i].getMessageBody();
                    //str += " \n ";
                }

            //Toast.makeText(context, str , Toast.LENGTH_LONG).show();
        }

        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction("SMS_RECEIVED_ACTION");
        broadcastIntent.putExtra("sms",str);
        context.sendBroadcast(broadcastIntent);
    }
}
