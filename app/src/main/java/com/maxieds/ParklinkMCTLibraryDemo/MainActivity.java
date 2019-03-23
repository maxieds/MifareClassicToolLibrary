package com.maxieds.mifareclassictoolextension;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.content.Intent;

import java.util.ArrayList;
import java.util.Arrays;
import java.io.File;

import mifareclassictoollibrary.MCTUtils;
import mifareclassictoollibrary.MifareClassicTag;
import mifareclassictoollibrary.MifareClassicToolLibrary;

public class MainActivity extends AppCompatActivity implements MifareClassicDataInterface {

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

          Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
          setSupportActionBar(toolbar);

     }

     public void RegisterNewIntent(Intent mfcIntent) {
          onNewIntent(mfcIntent);
     }

     public Context GetApplicationContext() {
          return getContext();
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

     @Override
     protected void onNewIntent(Intent intent) {
          if(NfcAdapter.ACTION_TECH_DISCOVERED.equals(intent.getAction()) ||
             NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
               Vibrator vibObj = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
               vibObj.vibrate(300);
               Tag nfcTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
               if(MifareClassicTag.CheckMifareClassicSupport(nfcTag, getContext()) != 0) {
                    toastDisplay = Toast.makeText(getContext(), "The discovered NFC device is not a Mifare Classic tag.", Toast.LENGTH_SHORT);
                    toastDisplay.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 0);
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
                    } catch(MifareClassicLibraryException mfcLibExcpt) {
                         Log.w(TAG, mfcLibExcpt.getStackTrace().toString());
                         String toastMsg = mfcLibExcpt.ToString();
                         Toast toastDisplay = Toast.makeText(getContext(), toastMsg, Toast.LENGTH_SHORT);
                         toastDisplay.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 0);
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
                         Cursor cursor = getContentResolver().query(data.getData(), null, null, null, null, null);
                         if (cursor != null && cursor.moveToFirst()) {
                              filePath = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                              filePath = "//sdcard//Download//" + filePath;
                         }
                         throw new RuntimeException(filePath);
                    }
                    break;
          }
          super.onActivityResult(requestCode, resultCode, data);
     }

     private static void ConfigureMCTLibrary() {
          MifareClassicToolLibrary.InitializeLibrary(this);
     }

     public static File GetUserExternalFileSelection() {

          Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
          intent.addCategory(Intent.CATEGORY_OPENABLE);
          intent.setDataAndType(Uri.parse("//sdcard//Download//"), "*/*");
          try {
               mainActivityInstance.startActivityForResult(Intent.createChooser(intent, "Select a Text File of Your Keys to Upload!"), FILE_SELECT_CODE);
          } catch (android.content.ActivityNotFoundException e) {
               Log.e(TAG, "Unable to choose external file: " + e.getMessage());
               return null;
          }
          String filePathSelection = "";
          try {
               Looper.loop();
          } catch (RuntimeException rte) {
               filePathSelection = rte.getMessage().split("java.lang.RuntimeException: ")[1];
               Log.i(TAG, "User Selected Data File: " + cardFilePath);
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
          String toastNoticeMsg = String.format(Locale.US, "New Tag Found!\n\nATQA: %s\nSAK: %s\nATS: %s",
                                                mfcTagData.GetATQA(), mfcTagData.GetSAK(), mfcTagData.GetATS());
          Toast toastDisplay = Toast.makeText(getContext(), toastNoticeMsg, Toast.LENGTH_LONG);
          toastDisplay.show();
          // next, display the quick summary tag stats at the top of the screen below the toolbar:
          TextView tvTagDesc = (TextView) findResourceByID(R.id.tvStatsBarTagDesc);
          tvTagDesc.setText(mfcTagData.GetTagType());
          TextView tvTagUID = (TextView) findResourceByID(R.id.tvStatsBarTagUID);
          tvTagUID.setText(mfcTagData.GetTagUID());
          TextView tvTagSizes = (TextView) findResourceByID(R.id.tvStatsBarTagSpecs);
          tvTagSizes.setText(mfcTagData.GetTagSizeSpecString());
          // loop and add each sector to the linear layout within the scroll viewer:
          // TODO
     }

     private static final int TAG_SCANNING_TIME = 5000;
     private static Handler tagScanHandler = new Handler();
     private static Runnable tagScanRunnable = new Runnable() {
          public void run() {
               MifareClassicToolLibrary.StopLiveTagScanning(MainActivity.mainActivityInstance);
               MainActivity.mainActivityInstance.currentlyScanningTag = false;
               if(!MainActivity.mainActivityInstance.newMFCTagFound) {
                    Toast toastDisplay = Toast.makeText(MainActivity.this, "No Mifare Classic tags found!", Toast.LENGTH_SHORT);
                    toastDisplay.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 0);
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
          String outfileBasePath = String.format(Locale.ENGLISH, "MifareClassic%s-tagdump-%s",
               MifareClassicTag.GetTagByteCountString(activeMFCTag.GetTagSize()),
               MCTUtils.GetTimestamp().replace(":", ""));
          for (int ext = 0; ext < 2; ext++) {
               String fileExt = ext ? ".dmp" : ".hex";
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
               } catch (IOException ioe) {
                    Log.e(TAG, ioe.getStackTrace().toString());
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
               toastStatusMsg += "NFC is currently enabled on the device for tag reading."
          }
          else {
               toastStatusMsg += "NFC is currently DISABLED on the device for tag reading."
          }
          toastDisplay = Toast.makeToast(getContext(), toastStatusMsg, Toast.LENGTH_SHORT);
          toastDisplay.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 0);
     }

     protected void ActionButtonDisplayNFCSettings(View btnView) {
          MifareClassicToolLibrary.CheckNFCEnabled(true);
     }
}
