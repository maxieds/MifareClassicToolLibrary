package com.maxieds.MifareClassicToolLibrary;

import android.os.Build;
import android.content.Intent;
import android.app.PendingIntent;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.nfc.Tag;
import android.nfc.tech.NfcA;
import android.app.Activity;
import android.content.Context;
import android.provider.Settings;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Stack;
import java.util.Locale;

public class MifareClassicToolLibrary {

    private static final String TAG = MifareClassicToolLibrary.class.getSimpleName();

    private static MifareClassicDataInterface localMFCDataIface = null;

    public static String GetLibraryVersion() {
        return String.format(Locale.US, "v%s (%s)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE);
    }

    public static boolean Initialized() {
        return localMFCDataIface != null;
    }

    public static Context GetApplicationContext() {
        if(!Initialized()) {
            return null;
        }
        return localMFCDataIface.GetApplicationContext();
    }

    public static boolean InitializeLibrary(MifareClassicDataInterface mfcDataIface) {
        localMFCDataIface = mfcDataIface;
        return Initialized();
    }

    private static NfcAdapter GetContextNFCAdapter() {
        if(!Initialized()) {
            return null;
        }
        Context appContext = localMFCDataIface.GetApplicationContext();
        NfcManager nfcManager = (NfcManager) appContext.getSystemService(Context.NFC_SERVICE);
        return nfcManager.getDefaultAdapter();
    }

    public static boolean CheckNFCEnabled(boolean promptUser) {
        if(!MifareClassicToolLibrary.Initialized()) {
            return false;
        }
        NfcAdapter nfcAdapter = GetContextNFCAdapter();
        if(nfcAdapter != null && nfcAdapter.isEnabled()) {
            return true;
        }
        else if(nfcAdapter != null && promptUser) {
            Intent startNFCIntent;
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                startNFCIntent = new Intent(Settings.ACTION_NFC_SETTINGS);
            }
            else {
                startNFCIntent = new Intent(Settings.ACTION_NFC_SETTINGS);
            }
            startNFCIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
            GetApplicationContext().startActivity(startNFCIntent);
            return CheckNFCEnabled(false);
        }
        return false;
    }

    public static boolean CheckNFCEnabled() {
        return CheckNFCEnabled(true);
    }

    public static boolean CheckPhoneMFCSupport() {
        // Check if ther is any NFC hardware at all.
        if(!Initialized() || NfcAdapter.getDefaultAdapter(localMFCDataIface.GetApplicationContext()) == null) {
            return false;
        }
        // Check if there is the NFC device "bcm2079x-i2c".
        // Chips by Broadcom don't support MIFARE Classic.
        // This could fail because on a lot of devices apps don't have
        // the sufficient permissions.
        // Another exception:
        // The Lenovo P2 has a device at "/dev/bcm2079x-i2c" but is still
        // able of reading/writing MIFARE Classic tags. I don't know why...
        // https://github.com/ikarus23/MifareClassicTool/issues/152
        boolean isLenovoP2 = Build.MANUFACTURER.equals("LENOVO") && Build.MODEL.equals("Lenovo P2a42");
        File nfcDevice = new File("/dev/bcm2079x-i2c");
        if (!isLenovoP2 && nfcDevice.exists()) {
            return false;
        }
        // Check if there is the NFC device "pn544".
        // The PN544 NFC chip is manufactured by NXP.
        // Chips by NXP support MIFARE Classic.
        nfcDevice = new File("/dev/pn544");
        if (nfcDevice.exists()) {
            return true;
        }
        // Check if there are NFC libs with "brcm" in their names.
        // "brcm" libs are for devices with Broadcom chips. Broadcom chips
        // don't support MIFARE Classic.
        File libsFolder = new File("/system/lib");
        File[] libs = libsFolder.listFiles();
        for (File lib : libs) {
            if (lib.isFile() && lib.getName().startsWith("libnfc") && lib.getName().contains("brcm")) {
                return false;
            }
        }
        return true;
    }

    public static boolean StartLiveTagScanning(Activity targetActivity) {
        NfcAdapter nfcAdapter = GetContextNFCAdapter();
        if(nfcAdapter == null || !CheckPhoneMFCSupport() ||
                !CheckNFCEnabled(true)) {
            return false;
        }
        Intent startDispatchIntent = new Intent(targetActivity, targetActivity.getClass());
        startDispatchIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent startPendingIntent = PendingIntent.getActivity(targetActivity, 0, startDispatchIntent, 0);
        String[][] enableFlags = new String[][] { new String[] {NfcA.class.getName()} };
        nfcAdapter.enableForegroundDispatch(targetActivity, startPendingIntent, null, enableFlags);
        return true;
    }

    public static boolean StopLiveTagScanning(Activity targetActivity) {
         NfcAdapter nfcAdapter = GetContextNFCAdapter();
         if(nfcAdapter != null && nfcAdapter.isEnabled()) {
             nfcAdapter.disableForegroundDispatch(targetActivity);
             return true;
         }
         return false;
    }

    private static HashMap<String, MifareClassicTag> libraryTagHashMap = new HashMap<>();
    private static Stack<MifareClassicTag> libraryTagStack = new Stack<>();
    private static int libraryTagStackSize = 0;

    public static int GetLibraryTagStackSize() {
        return libraryTagStackSize;
    }

    public static boolean AddToLibraryTagStack(MifareClassicTag mfcTagData) {
        if(mfcTagData == null) {
            return false;
        }
        else if(libraryTagHashMap.get(mfcTagData.GetTagUID()) != null) {
             return false;
        }
        libraryTagHashMap.put(mfcTagData.GetTagUID(), mfcTagData);
        libraryTagStack.push(mfcTagData);
        libraryTagStackSize++;
        localMFCDataIface.RegisterNewIntent(new Intent(GetApplicationContext(), MifareClassicToolLibrary.class));
        return true;
    }

    public static MifareClassicTag PopFromLibraryTagStack() {
        if(libraryTagStackSize == 0) {
            return null;
        }
        MifareClassicTag mfcTagData = libraryTagStack.pop();
        libraryTagHashMap.remove(mfcTagData.GetTagUID());
        libraryTagStackSize--;
        return mfcTagData;
    }

    public static boolean ProcessNewTagFound(Tag nfcTag) throws MifareClassicLibraryException {
        if(nfcTag == null || !Initialized()) {
            return false;
        }
        MifareClassicTag mfcTagData = MifareClassicTag.Decode(nfcTag);
        if(mfcTagData != null) {
             return AddToLibraryTagStack(mfcTagData);
        }
        return false;
    }

    private static String[] standardKeys = null;

    public static boolean LoadStandardKeySets(boolean useExtendedKeys) {
         if(!Initialized()) {
             return true;
         }
         else if(standardKeys != null) {
             standardKeys = null;
         }
         String[] keyFiles;
         if(useExtendedKeys) {
             keyFiles = new String[] { "mct_standard_keys", "mct_extended_keys" };
         }
         else {
             keyFiles = new String[] { "mct_standard_keys" };
         }
         List<String> keysList = new ArrayList<String>();
         Context appContext = localMFCDataIface.GetApplicationContext();
         for(int kidx = 0; kidx < keyFiles.length; kidx++) {
             String resFilePath = keyFiles[kidx];
             int fileRes = appContext.getResources().getIdentifier(resFilePath, "raw", appContext.getPackageName());
             InputStream rawFileStream = appContext.getResources().openRawResource(fileRes);
             int lineByteCount = 0, MAX_LINE_SIZE = 128;
             byte[] lineBytes = new byte[MAX_LINE_SIZE];
             try {
                 while (true) {
                     lineByteCount = rawFileStream.read(lineBytes, 0, MAX_LINE_SIZE);
                     if (lineByteCount == 0) {
                         break;
                     }
                     if(lineBytes[0] != '#' && lineBytes[0] != '\n') {
                         keysList.add(new String(lineBytes, 0, lineByteCount));
                     }
                 }
                 rawFileStream.close();
             } catch(IOException ioe) {
                 ioe.printStackTrace();
                 return false;
             }
         }
         standardKeys = new String[keysList.size()];
         keysList.toArray(standardKeys);
         return true;
    }

    public static int GetStandardKeyCount() {
        if(standardKeys != null) {
            return standardKeys.length;
        }
        return 0;
    }

    public static String GetStandardKey(int kidx) {
        if(kidx < 0 || kidx >= GetStandardKeyCount()) {
            return null;
        }
        return standardKeys[kidx];
    }

}