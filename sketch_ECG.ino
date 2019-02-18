
const int analogInPin = A5;
unsigned long LoopTimer = 0;

int sensorValue = 0;

const int LoopTime = 10000;

void setup() 
{
   Serial.begin(9600);
}

void loop()
{
  if (micros() > LoopTimer)
  {
   LoopTimer += LoopTime;
   sensorValue = analogRead(analogInPin);            
   sensorValue = sensorValue >> 2;
   Serial.write(sensorValue);
   Serial.flush();
  }
}
