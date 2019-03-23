package com.maxieds.ParklinkMCTLibraryDemo;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.content.Intent;
import android.os.Handler;
import android.content.Context;

import java.io.File;

import com.maxieds.MifareClassicToolLibrary.MCTUtils;
import com.maxieds.MifareClassicToolLibrary.MifareClassicTag;
import com.maxieds.MifareClassicToolLibrary.MifareClassicToolLibrary;

public class MainActivity extends AppCompatActivity implements com.maxieds.MifareClassicToolLibrary.MifareClassicDataInterface {

     public static final String TAG = MainActivity.class.getSimpleName();

     public static MainActivity mainActivityInstance = null;
     private static boolean currentlyTagScanning = false;
     private static boolean newMFCTagFound = false;
     private static MifareClassicTag activeMFCTag = null;

     @Override
     protected void onCreate(Bundle savedInstanceState) {

          super.onCreate(savedInstanceState);
          setContentView(R.layout.activity_main);
          mainActivityInstance = this;
          ConfigureMCTLibrary();

          Toolbar toolbar = (Toolbar) findViewById(R.id.toolbarActionBar);
          setSupportActionBar(toolbar);

     }

     public void RegisterNewIntent(Intent mfcIntent) {
          onNewIntent(mfcIntent);
     }

     public Context GetApplicationContext() {
          return this;
     }

     @Override
     public void onResume() {
          super.onResume();
     }

     @Override
     public void onPause() {
          if(currentlyTagScanning) {
               MifareClassicToolLibrary.StopLiveTagScanning(this);
               currentlyTagScanning = false;
          }
          super.onPause();
     }

     public static final int FILE_SELECT_CODE = 0;

     @Override
     protected void onNewIntent(Intent intent) {
          if(android.nfc.NfcAdapter.ACTION_TECH_DISCOVERED.equals(intent.getAction()) ||
             android.nfc.NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
               android.os.Vibrator vibObj = (android.os.Vibrator) getSystemService(android.content.Context.VIBRATOR_SERVICE);
               vibObj.vibrate(300);
               android.nfc.Tag nfcTag = intent.getParcelableExtra(android.nfc.NfcAdapter.EXTRA_TAG);
               if(MifareClassicTag.CheckMifareClassicSupport(nfcTag, this) != 0) {
                    android.widget.Toast toastDisplay = android.widget.Toast.makeText(this, "The discovered NFC device is not a Mifare Classic tag.", android.widget.Toast.LENGTH_SHORT);
                    toastDisplay.setGravity(android.view.Gravity.CENTER_HORIZONTAL | android.view.Gravity.BOTTOM, 0, 0);
                    toastDisplay.show();
               }
               else {
                    vibObj.vibrate(100);
                    vibObj.vibrate(100);
                    vibObj.vibrate(100);
                    try {
                         MifareClassicTag mfcTag = MifareClassicTag.Decode(nfcTag, LoadKeysDialog.GetPresetKeys());
                         activeMFCTag = mfcTag;
                         DisplayNewMFCTag(activeMFCTag);
                         newMFCTagFound = true;
                    } catch(com.maxieds.MifareClassicToolLibrary.MifareClassicLibraryException mfcLibExcpt) {
                         android.util.Log.w(TAG, mfcLibExcpt.getStackTrace().toString());
                         String toastMsg = mfcLibExcpt.ToString();
                         android.widget.Toast toastDisplay = android.widget.Toast.makeText(this, toastMsg, android.widget.Toast.LENGTH_SHORT);
                         toastDisplay.setGravity(android.view.Gravity.CENTER_HORIZONTAL | android.view.Gravity.BOTTOM, 0, 0);
                         toastDisplay.show();
                    }
               }
          }
     }

     @Override
     protected void onActivityResult(int requestCode, int resultCode, Intent data) {
          switch (requestCode) {
               case FILE_SELECT_CODE:
                    if (resultCode == RESULT_OK) {
                         String filePath = "";
                         android.database.Cursor cursor = getContentResolver().query(data.getData(), null, null, null, null, null);
                         if (cursor != null && cursor.moveToFirst()) {
                              filePath = cursor.getString(cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME));
                              filePath = "//sdcard//Download//" + filePath;
                         }
                         throw new RuntimeException(filePath);
                    }
                    break;
          }
          super.onActivityResult(requestCode, resultCode, data);
     }

     private static void ConfigureMCTLibrary() {
          MifareClassicToolLibrary.InitializeLibrary(MainActivity.mainActivityInstance);
     }

     public static File GetUserExternalFileSelection() {

          Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
          intent.addCategory(Intent.CATEGORY_OPENABLE);
          intent.setDataAndType(android.net.Uri.parse("//sdcard//Download//"), "*/*");
          try {
               mainActivityInstance.startActivityForResult(Intent.createChooser(intent, "Select a Text File of Your Keys to Upload!"), FILE_SELECT_CODE);
          } catch (android.content.ActivityNotFoundException e) {
               android.util.Log.e(TAG, "Unable to choose external file: " + e.getMessage());
               return null;
          }
          String filePathSelection = "";
          try {
               android.os.Looper.loop();
          } catch (RuntimeException rte) {
               filePathSelection = rte.getMessage().split("java.lang.RuntimeException: ")[1];
               android.util.Log.i(TAG, "User Selected Data File: " + filePathSelection);
          }
          return new File(filePathSelection);

     }

     private void ClearActiveDisplayWindow() {
          // TODO
     }

     private void DisplayNewMFCTag(MifareClassicTag mfcTagData) {
          if(mfcTagData == null) {
               return;
          }
          ClearActiveDisplayWindow();
          // display a quick notice to the user of the more detailed tag information from the header sector:
          String toastNoticeMsg = String.format(java.util.Locale.US, "New Tag Found!\n\nATQA: %s\nSAK: %s\nATS: %s",
                                                mfcTagData.GetATQA(), mfcTagData.GetSAK(), mfcTagData.GetATS());
          android.widget.Toast toastDisplay = android.widget.Toast.makeText(this, toastNoticeMsg, android.widget.Toast.LENGTH_LONG);
          toastDisplay.show();
          // next, display the quick summary tag stats at the top of the screen below the toolbar:
          android.widget.TextView tvTagDesc = (android.widget.TextView) findViewById(R.id.deviceStatusBarTagType);
          tvTagDesc.setText(mfcTagData.GetTagType());
          android.widget.TextView tvTagUID = (android.widget.TextView) findViewById(R.id.deviceStatusBarUID);
          tvTagUID.setText(mfcTagData.GetTagUID());
          android.widget.TextView tvTagSizes = (android.widget.TextView) findViewById(R.id.deviceStatusBarSizeDims);
          tvTagSizes.setText(mfcTagData.GetTagSizeSpecString());
          // loop and add each sector to the linear layout within the scroll viewer:
          // TODO
     }

     private static final int TAG_SCANNING_TIME = 5000;
     private static Handler tagScanHandler = new Handler();
     private static Runnable tagScanRunnable = new Runnable() {
          public void run() {
               MifareClassicToolLibrary.StopLiveTagScanning(MainActivity.mainActivityInstance);
               MainActivity.mainActivityInstance.currentlyTagScanning = false;
               if(!MainActivity.mainActivityInstance.newMFCTagFound) {
                    android.widget.Toast toastDisplay = android.widget.Toast.makeText(com.maxieds.ParklinkMCTLibraryDemo.MainActivity.mainActivityInstance, "No Mifare Classic tags found!", android.widget.Toast.LENGTH_SHORT);
                    toastDisplay.setGravity(android.view.Gravity.CENTER_HORIZONTAL | android.view.Gravity.BOTTOM, 0, 0);
                    toastDisplay.show();
               }
          }
     };

     protected void ActionButtonScanNewTag(View btnView) {
          newMFCTagFound = false;
          currentlyTagScanning = true;
          MifareClassicToolLibrary.StartLiveTagScanning(this);
          tagScanHandler.postDelayed(tagScanRunnable, TAG_SCANNING_TIME);
     }

     protected void ActionButtonWriteTagToFile(View btnView) {
          if (activeMFCTag == null) {
               return;
          }
          String outfileBasePath = String.format(java.util.Locale.ENGLISH, "MifareClassic%s-tagdump-%s",
               MifareClassicTag.GetTagByteCountString(activeMFCTag.GetTagSize()),
               MCTUtils.GetTimestamp().replace(":", ""));
          for (int ext = 0; ext < 2; ext++) {
               String fileExt = ext != 0 ? ".dmp" : ".hex";
               File downloadsFolder = new File("//sdcard//Download//");
               File outfile = new File(downloadsFolder, outfileBasePath + fileExt);
               boolean docsFolderExists = true;
               if (!downloadsFolder.exists()) {
                    docsFolderExists = downloadsFolder.mkdir();
               }
               if (docsFolderExists) {
                    outfile = new File(downloadsFolder.getAbsolutePath(), outfileBasePath + fileExt);
               }
               try {
                    if (ext == 0) {
                         activeMFCTag.ExportToHexFile(outfile.getAbsolutePath());
                    } else {
                         activeMFCTag.ExportToBinaryDumpFile(outfile.getAbsolutePath());
                    }
               } catch (java.io.IOException ioe) {
                    android.util.Log.e(TAG, ioe.getStackTrace().toString());
                    return;
               }
          }
          return;
     }

     protected void ActionButtonSetKeys(View btnView) {
          LoadKeysDialog loadKeysDialog = new LoadKeysDialog();
          loadKeysDialog.BuildDialog();
          loadKeysDialog.Show();
     }

     protected void ActionButtonClear(View btnView) {
          LoadKeysDialog.ClearKeyData();
          if(activeMFCTag != null) {
               ClearActiveDisplayWindow();
               activeMFCTag = null;
          }
     }

     protected void ActionButtonCheckForMFCSupport(View btnView) {
          boolean phoneMFCSupport = MifareClassicToolLibrary.CheckPhoneMFCSupport();
          boolean phoneNFCEnabled = MifareClassicToolLibrary.CheckNFCEnabled(false);
          String toastStatusMsg = "";
          if(phoneMFCSupport) {
               toastStatusMsg += "This Android device supports MFC tags.\n";
          }
          else {
               toastStatusMsg += "This Android device DOES NOT support MFC tags.\n";
          }
          if(phoneNFCEnabled) {
               toastStatusMsg += "NFC is currently enabled on the device for tag reading.";
          }
          else {
               toastStatusMsg += "NFC is currently DISABLED on the device for tag reading.";
          }
          android.widget.Toast toastDisplay = android.widget.Toast.makeText(this, toastStatusMsg, android.widget.Toast.LENGTH_SHORT);
          toastDisplay.setGravity(android.view.Gravity.CENTER_HORIZONTAL | android.view.Gravity.BOTTOM, 0, 0);
     }

     protected void ActionButtonDisplayNFCSettings(View btnView) {
          MifareClassicToolLibrary.CheckNFCEnabled(true);
     }
}
