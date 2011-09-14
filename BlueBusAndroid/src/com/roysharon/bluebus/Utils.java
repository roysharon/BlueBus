package com.roysharon.bluebus;

import java.util.UUID;

public class Utils {

	//----- Miscellaneous Bluetooth utilities ---------------------------------
	
	private static final String BASE_UUID_SUFFIX = "-0000-1000-8000-00805F9B34FB";
	
	public static final UUID OBEXFileTransfer = shortToUUID(0x1106);
	public static final UUID Serial = shortToUUID(0x1101);


	public static UUID shortToUUID(int uuid16or32) {
		return UUID.fromString(String.format("%08X%s", uuid16or32, BASE_UUID_SUFFIX));
	}

}
