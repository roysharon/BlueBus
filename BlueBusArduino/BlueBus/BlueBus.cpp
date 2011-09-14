#include "WProgram.h"
#include "BlueBus.h"

void * operator new(size_t n)
{
  void * const p = malloc(n);
  // handle p == 0
  return p;
}

void operator delete(void * p) // or delete(void *, size_t)
{
  free(p);
}

BlueBus::BlueBus(void *outptr, int outsize, void *inptr, int insize) {
	this->outptr = (byte*)outptr;
	this->inptr = (byte*)inptr;
	this->outsize = outsize;
	this->insize = insize;
	outbuf = (byte*)malloc(outsize+1);
	inbuf = (byte*)malloc(insize+1);
	outbufIndex = 0;
	inbufIndex = 0;
	
	serial = new NewSoftSerial(bluebusRxPin, bluebusTxPin);
}

BlueBus::~BlueBus() {
	delete serial;
	free(outbuf);
	free(inbuf);
}

void BlueBus::begin(long baud) {
	serial->begin(baud);
}

byte BlueBus::checksum(byte *buffer, int len) {
	byte r = 1;
	for (int i = 0; i < len; ++i) r = 7 * r + buffer[i];
	return r;
}
  
boolean BlueBus::incoming() {
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
  
boolean BlueBus::outgoing() {
	if (outbufIndex > outsize) {
		memcpy(outbuf, outptr, outsize);
		outbuf[outsize] = checksum(outptr, outsize);
		outbufIndex = 0;
	}
	
	serial->print(outbuf[outbufIndex++]);
	return outbufIndex > outsize;
}
