package com.maxieds.MifareClassicToolLibrary;

import android.util.Log;
import android.text.format.Time;

import java.util.Random;

public class MCTUtils {

    private static final String TAG = MCTUtils.class.getSimpleName();

    public static String BytesToHexString(byte[] bytes) {
        StringBuilder ret = new StringBuilder();
        if (bytes != null) {
            for (Byte b : bytes) {
                ret.append(String.format("%02X", b.intValue() & 0xFF));
            }
        }
        return ret.toString();
    }

    public static byte[] HexStringToBytes(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        try {
            for (int i = 0; i < len; i += 2) {
                data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                     + Character.digit(s.charAt(i+1), 16));
            }
        } catch (Exception e) {
            Log.d(TAG, "Argument(s) for hexStringToByteArray(String s)"
                    + "was not a hex string");
        }
        return data;
    }

    public static boolean IsHexAnd16Byte(String hexString) {
        if (!hexString.matches("[0-9A-Fa-f]+")) { // Error, not hex.
            return false;
        }
        else if (hexString.length() != 32) { // Error, not 16 byte (32 chars).
            return false;
        }
        return true;
    }

    public static boolean IsHexAnd6Byte(String hexString) {
        if (!hexString.matches("[0-9A-Fa-f]+")) { // Error, not hex.
            return false;
        }
        else if (hexString.length() != 12) { // Error, not 6 byte (12 chars).
            return false;
        }
        return true;
    }

    public static byte[] GetRandomBytes(int numBytes) {
        if(numBytes <= 0) {
            return null;
        }
        Random rnGen = new Random(System.currentTimeMillis());
        byte[] randomBytes = new byte[numBytes];
        for(int b = 0; b < numBytes; b++) {
            randomBytes[b] = (byte) rnGen.nextInt(0xff);
        }
        return randomBytes;
    }

    public static String[] ReadKeysFromTextFile(File keyFile) {
        if(keyFile == null || !keyFile.exists()) {
            return null;
        }
        List keysList = new ArrayList<String>();
        byte[] textLineBytes = new byte[256];
        try {
            InputStream keyDataStream = new FileInputStream(keyFile);
            while (true) {
                int lineByteCount = rawFileStream.read(textLineBytes, 0, 256);
                if (lineByteCount == 0) {
                    break;
                }
                if(lineBytes[0] != '#' && lineBytes[0] != '\n') {
                    keysList.add(new String(textLineBytes, 0, lineByteCount));
                }
            }
            keyDataStream.close();
        } catch(IOException ioe) {
            Log.e(TAG, ioe.getStackTrace().toString());
            return null;
        }
        return keysList.toArray();
    }

    public static String GetTimestamp() {
        Time currentTime = new Time();
        currentTime.setToNow();
        return currentTime.format("%Y-%m-%d-%T");
    }

}