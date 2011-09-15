#define bluebusRxPin 7
#define bluebusTxPin 8

#include <NewSoftSerial.h>
#include <BlueBus.h>

struct Outgoing {
  char x;
  char y;
  char z;
} outgoing;

struct Incoming {
  char a;
  char b;
} incoming;


BlueBus bluebus(&outgoing, sizeof(outgoing), &incoming, sizeof(incoming));

void setup()  {
  Serial.begin(57600);
  Serial.println("Howdy!");

  bluebus.begin(9600);
}


int outi = 0;
boolean outnew = false;

void loop() {
  if (bluebus.incoming()) {
    Serial.print("a=");
    Serial.print(incoming.a);
    Serial.println();
    Serial.print("b=");
    Serial.print(incoming.b);
    Serial.println();
  }
  
  if (Serial.available()) {
    ((char*)&outgoing)[outi++ % sizeof(Outgoing)] = (char)Serial.read();
    outnew = (outi % sizeof(Outgoing)) == 0;
  }
  
  if (outnew) {
    if (bluebus.outgoing()) outnew = false;
  }
}

