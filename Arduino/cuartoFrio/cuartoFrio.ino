#include <SoftwareSerial.h>
#include "DHT.h"
#include <LiquidCrystal_I2C.h>
#include <elapsedMillis.h>
#include <Separador.h>

SoftwareSerial GSM_Serial(7, 8); // Declaramos los pines RX(8) y TX(9) que vamos a usar
LiquidCrystal_I2C lcd(0x27,20,4);  // LCD address = 0x27, 20X4 display, SCL = A5, SDA = A4
#define DHTPIN 2
DHT dht(DHTPIN, DHT11);
elapsedMillis tiempoTranscurrido;

Separador s;

unsigned long intervalo = 900000;
int temperatura, humedad, tempMin, tempMax, humMin, humMax, estadoSwitch;
String ubicacion = "Cuarto frio no. 1", datos, AlarmaBajaTemp, AlarmaAltaTemp, AlarmaBajaHum, AlarmaAltaHum;

void setup() 
{
  Serial.begin(115200); //Configuracion comunicacion serie a 115200 Bs
 
  GSM_Serial.begin(9600);  //Configuracion serie Modulo GPRS SIM800L
  pinMode(3,OUTPUT);
  pinMode(4,OUTPUT);
  pinMode(5,OUTPUT);
  pinMode(6,OUTPUT);
  dht.begin();
  lcd.init();
  lcd.clear();
  lcd.backlight();
  lcd.setCursor(0,0); 
  lcd.print("Sistema de monitoreo");
  lcd.setCursor(0,1);
  lcd.print("y control de un");
  lcd.setCursor(0,2);
  lcd.print("cuarto frio");
  delay(5000);
  lcd.clear();
  lcd.setCursor(0,0);
  lcd.print("Configurando");
  lcd.setCursor(0,1);
  lcd.print("Modulo GSM ..."); 
  delay(1000);              // Pausa de 1 segundo
  GSM_Serial.print("AT+CLIP=1\r");                 // Activamos la funcion de envio de SMS
  delay(1000);
  GSM_Serial.print("AT+CMGF=1\r");                 // Activamos la funcion de envio de SMS
  delay(1000);
  GSM_Serial.println("AT+CNMI=2,2,0,0,0"); //Configuramos el módulo para que nos muestre los SMS recibidos por comunicacion serie
  delay(1000);
  lcd.setCursor(0,2);
  lcd.print("OK");
  delay(500);
  lcd.clear();  
 
}

void loop()
{
  
  if (GSM_Serial.available() > 0){          // Si la comunicacion SoftwareSerial tiene datos
    datos = GSM_Serial.readString();
    Serial.println(datos);    // Los sacamos por la comunicacion serie normal
    
    if(datos.indexOf("@")>=0){                  //Si la variable Comando contiene la palabra Info
      //posicion = datos.indexOf("P");
      tempMin = s.separa(datos, '*', 1).toInt();
      tempMax = s.separa(datos, '*', 2).toInt();
      humMin = s.separa(datos, '*', 3).toInt();
      humMax = s.separa(datos, '*', 4).toInt();
      estadoSwitch = s.separa(datos, '*', 5).toInt();
  }

  Serial.println(tempMin);
  Serial.println(tempMax);
  Serial.println(humMin);
  Serial.println(humMax);
  Serial.println(estadoSwitch);

}
 
    if (Serial.available()){            // Si la comunicacion serie normal tiene datos
        while(Serial.available()) {       // y mientras tenga datos que mostrar 
        GSM_Serial.write(Serial.read());  // Los sacamos por la comunicacion SoftwareSerial
      } 
        GSM_Serial.println();               // Enviamos un fin de linea
}

temperatura = dht.readTemperature();
humedad = dht.readHumidity();

//Muestra en el display la temperatura y la humedad relativa del cuarto frio y los "Set-Points" establecidos.
lcd.setCursor(0,0);
lcd.print("Temp. " + String(temperatura) + (char)223 + String("C Hum. ") + String(humedad) + "%");
lcd.setCursor(0,1);
lcd.print("SP Temp. | SP Hum.");
lcd.setCursor(0,2);
lcd.print("Min. " + String(tempMin) + (char)223 + String("C| Min. ") + String(humMin) + String("%"));
lcd.setCursor(0,3);
lcd.print("Max. " + String(tempMax) + (char)223 + String("C| Max. ") + String(humMax) + String("%"));

if(estadoSwitch == 1){
  controlOnOff();
  Alarma();
  }

if(estadoSwitch == 0){
    digitalWrite(3,!0);
    digitalWrite(4,!0);
    digitalWrite(5,!0);
    digitalWrite(6,!0);
  }

  if (tiempoTranscurrido >= intervalo) 
  {       
    EnviaSMS();
    tiempoTranscurrido = 0;       // reset the counter to 0 so the counting starts over...
  }
  
}

void EnviaSMS(){
 Serial.println("Enviando SMS...");
 GSM_Serial.print("AT+CMGF=1\r");  //Configura el modo texto para enviar o recibir mensajes
 delay(1000);
 GSM_Serial.println("AT+CMGS=\"+520000000000\"");  // Definimos el numero del destinatario en formato internacional
 delay(1000);                                    // Pequeña pausa
 GSM_Serial.print(temperatura);                 // Definimos el cuerpo del mensaje
 GSM_Serial.print(",");
 GSM_Serial.print(humedad);  
 GSM_Serial.print(",");
 GSM_Serial.print(ubicacion); 
 GSM_Serial.print(",");
 GSM_Serial.print(AlarmaBajaTemp);
 GSM_Serial.print(",");
 GSM_Serial.print(AlarmaAltaTemp);
 GSM_Serial.print(",");
 GSM_Serial.print(AlarmaBajaHum);
 GSM_Serial.print(",");
 GSM_Serial.print(AlarmaAltaHum);
 delay(500); 
 GSM_Serial.print(char(26));                      // Enviamos el equivalente a Control+Z 
 delay(500);                                    // Pequeña pausa
 GSM_Serial.println("");                          // Enviamos un fin de linea
 delay(100);                                    // Pequeña pausa
}

void controlOnOff(){
if(temperatura <= tempMin){
digitalWrite(3,!0);
digitalWrite(4,!0);
digitalWrite(5,!0);
digitalWrite(6,!0);
}
if(temperatura >= tempMax){
digitalWrite(3,!1);
digitalWrite(4,!1);
digitalWrite(5,!1);
digitalWrite(6,!1);
}
}

void Alarma(){
  if(temperatura < tempMin || humedad < humMin){
    AlarmaBajaTemp = "1";
    AlarmaBajaHum = "1";
    }

    else{
      AlarmaBajaTemp = "0";
      AlarmaBajaHum = "0";
      }

if(temperatura > tempMax || humedad > humMax){
  AlarmaAltaTemp = "1";
  AlarmaAltaHum = "1";
  }
  else{
    AlarmaAltaTemp = "0";
    AlarmaAltaHum = "0";
    }

  }
