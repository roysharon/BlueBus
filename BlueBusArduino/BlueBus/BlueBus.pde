#include <NewSoftSerial.h>

#define segboardRxPin 7
#define segboardTxPin 8

struct Outgoing {
  char x;
  char y;
  char z;
} outgoing;

struct Incoming {
  char a;
  char b;
} incoming;

NewSoftSerial serial(segboardRxPin, segboardTxPin);

struct SegBoard {
  NewSoftSerial *serial;
  byte *outptr, *inptr, *inbuf, *outbuf;
  int insize, outsize, inbufIndex, outbufIndex;
  
  void init(NewSoftSerial *serial, int baud, void *outptr, int outsize, void *inptr, int insize) {
    this->serial = serial;
    this->serial->begin(baud);
    this->outptr = (byte*)outptr;
    this->inptr = (byte*)inptr;
    this->outsize = outsize;
    this->insize = insize;
    outbuf = (byte*)malloc(outsize+1);
    inbuf = (byte*)malloc(insize+1);
    outbufIndex = 0;
    inbufIndex = 0;
  }
  
  void destroy() {
    free(outbuf);
    free(inbuf);
  }

  byte checksum(byte *buffer, int len) {
    byte r = 1;
    for (int i = 0; i < len; ++i) r = 7 * r + buffer[i];
    return r;
  }
  
  boolean incoming() {
    if (!(serial->available())) return false;
    
    while (serial->available() && inbufIndex < insize+1) {
      byte c = serial->read();
      inbuf[inbufIndex++] = c;
    }
    
    if (inbufIndex > insize) {
      inbufIndex = 0;
      if (inbuf[insize] == checksum(inbuf, insize)) {
        memcpy(inptr, inbuf, insize);
        return true;
      } else return false;
    } else return false;
  }
  
  boolean outgoing() {
    if (outbufIndex > outsize) {
      memcpy(outbuf, outptr, outsize);
      outbuf[outsize] = checksum(outptr, outsize);
      outbufIndex = 0;
    }
  
    serial->print(outbuf[outbufIndex++]);
    return outbufIndex > outsize;
  }
};

SegBoard segboard;

void setup()  {
  Serial.begin(57600);
  Serial.println("Goodnight moon!");

  segboard.init(&serial, 9600, &outgoing, sizeof(outgoing), &incoming, sizeof(incoming));
}



int outi = 0;
boolean outnew = false;

void loop() {
  if (segboard.incoming()) {
    Serial.print("a=");
    Serial.print(incoming.a);
    Serial.println();
    Serial.print("b=");
    Serial.print(incoming.b);
    Serial.println();
  }
  
  if (Serial.available()) {
    ((char*)&outgoing)[outi++ % segboard.outsize] = (char)Serial.read();
    outnew = (outi % segboard.outsize) == 0;
  }
  
  if (outnew) {
    if (segboard.outgoing()) outnew = false;
  }
}

