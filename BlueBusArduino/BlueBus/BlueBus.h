#ifndef BlueBus_h
#define BlueBus_h

#include "WProgram.h"
#include "../NewSoftSerial/NewSoftSerial.h"

#ifndef bluebusRxPin
#define bluebusRxPin 7
#endif

#ifndef bluebusTxPin
#define bluebusTxPin 8
#endif

class BlueBus {
	public:
		BlueBus(void *outptr, int outsize, void *inptr, int insize);
		~BlueBus();
		void begin(long baud);
		boolean incoming();
		boolean outgoing();
		
	private:
		NewSoftSerial *serial;
		byte *outptr, *inptr, *inbuf, *outbuf;
		int insize, outsize, inbufIndex, outbufIndex;
		
		byte checksum(byte *buffer, int len);
};

#endif