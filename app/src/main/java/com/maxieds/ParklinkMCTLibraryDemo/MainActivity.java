package com.maxieds.ParklinkMCTLibraryDemo;

import android.os.Bundle;
import android.util.Log;
import android.app.Activity;
import android.widget.Toolbar;
import android.view.View;
import android.content.Intent;
import android.os.Handler;
import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.view.LayoutInflater;
import android.widget.TextView;
import android.widget.Toast;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Vibrator;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.net.Uri;
import android.content.ActivityNotFoundException;
import android.os.Looper;
import android.os.Build;
import android.support.v4.content.res.ResourcesCompat;
import android.graphics.drawable.Drawable;
import android.view.WindowManager;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import com.maxieds.MifareClassicToolLibrary.MCTUtils;
import com.maxieds.MifareClassicToolLibrary.MifareClassicTag;
import com.maxieds.MifareClassicToolLibrary.MifareClassicToolLibrary;
import com.maxieds.MifareClassicToolLibrary.MifareClassicLibraryException;
import com.maxieds.MifareClassicToolLibrary.MifareClassicDataInterface;

public class MainActivity extends Activity implements MifareClassicDataInterface {

     public static final String TAG = MainActivity.class.getSimpleName();

     public static MainActivity mainActivityInstance = null;
     private static boolean currentlyTagScanning = false;
     private static boolean newMFCTagFound = false;
     private static MifareClassicTag activeMFCTag = null;

     private static final int LAUNCH_DEFAULT_TAG_DELAY = 2000;
     private static Handler delayInitialTagDisplayHandler = new Handler();
     private static Runnable delayInitialTagDisplayRunnable = new Runnable() {
          public void run() {
               if (MainActivity.mainActivityInstance == null) {
                    MainActivity.delayInitialTagDisplayHandler.postDelayed(MainActivity.delayInitialTagDisplayRunnable, MainActivity.LAUNCH_DEFAULT_TAG_DELAY / 2);
                    return;
               }
               activeMFCTag = MifareClassicTag.LoadMifareClassic1KFromResource(R.raw.mfc1k_random_content_fixed_keys);
               MainActivity.mainActivityInstance.DisplayNewMFCTag(activeMFCTag);
          }
     };

     @Override
     protected void onCreate(Bundle savedInstanceState) {

          super.onCreate(savedInstanceState);
          if (Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP)
          {
               Drawable gradientBackground = ResourcesCompat.getDrawable(getResources(), R.drawable.ui_gradient, null);
               getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
               getWindow().setStatusBarColor(getResources().getColor(R.color.transparent));
               getWindow().setNavigationBarColor(getResources().getColor(R.color.transparent));
               getWindow().setBackgroundDrawable(gradientBackground);
          }

          setContentView(R.layout.activity_main);
          ConfigureMCTLibrary();

          Toolbar toolbar = (Toolbar) findViewById(R.id.toolbarActionBar);
          //toolbar.setBackground(ContextCompat.getDrawable(this, R.drawable.ui_gradient));
          toolbar.setSubtitle(String.format(Locale.US, "App v%d (%d) / Lib v%s", BuildConfig.VERSION_NAME,
                                            BuildConfig.VERSION_CODE, MifareClassicToolLibrary.GetLibraryVersion()));
          setActionBar(toolbar);

          // set this last so we return immediately after it becomes non-NULL:
          delayInitialTagDisplayHandler.postDelayed(delayInitialTagDisplayRunnable, LAUNCH_DEFAULT_TAG_DELAY);
          mainActivityInstance = this;

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
          if(NfcAdapter.ACTION_TECH_DISCOVERED.equals(intent.getAction()) ||
             NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
               Vibrator vibObj = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
               vibObj.vibrate(300);
               Tag nfcTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
               if(MifareClassicTag.CheckMifareClassicSupport(nfcTag, this) != 0) {
                    Toast toastDisplay = Toast.makeText(this, "The discovered NFC device is not a Mifare Classic tag.", Toast.LENGTH_SHORT);
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
                         Toast toastDisplay = Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT);
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
          MifareClassicToolLibrary.InitializeLibrary(MainActivity.mainActivityInstance);
     }

     public static File GetUserExternalFileSelection() {

          Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
          intent.addCategory(Intent.CATEGORY_OPENABLE);
          intent.setDataAndType(Uri.parse("//sdcard//Download//"), "*/*");
          try {
               mainActivityInstance.startActivityForResult(Intent.createChooser(intent, "Select a Text File of Your Keys to Upload!"), FILE_SELECT_CODE);
          } catch (ActivityNotFoundException e) {
               Log.e(TAG, "Unable to choose external file: " + e.getMessage());
               return null;
          }
          String filePathSelection = "";
          try {
               Looper.loop();
          } catch (RuntimeException rte) {
               filePathSelection = rte.getMessage().split("java.lang.RuntimeException: ")[1];
               Log.i(TAG, "User Selected Data File: " + filePathSelection);
          }
          return new File(filePathSelection);

     }

     private void ClearActiveDisplayWindow() {
          TextView tvTagDesc = (TextView) findViewById(R.id.deviceStatusBarTagType);
          tvTagDesc.setText("");
          TextView tvTagUID = (TextView) findViewById(R.id.deviceStatusBarUID);
          tvTagUID.setText("");
          TextView tvTagSizes = (TextView) findViewById(R.id.deviceStatusBarSizeDims);
          tvTagSizes.setText("");
          LinearLayout mainScrollerLayout = (LinearLayout) findViewById(R.id.mainDisplayItemsListLayout);
          mainScrollerLayout.removeAllViews();
     }

     private void DisplayNewMFCTag(MifareClassicTag mfcTagData) {
          if(mfcTagData == null) {
               return;
          }
          ClearActiveDisplayWindow();
          // display a quick notice to the user of the more detailed tag information from the header sector:
          String toastNoticeMsg = String.format(Locale.US, "New Tag Found!\n\nATQA: %s\nSAK: %s\nATS: %s",
                                                mfcTagData.GetATQA(), mfcTagData.GetSAK(), mfcTagData.GetATS());
          Toast toastDisplay = Toast.makeText(this, toastNoticeMsg, Toast.LENGTH_LONG);
          toastDisplay.show();
          // next, display the quick summary tag stats at the top of the screen below the toolbar:
          TextView tvTagDesc = (TextView) findViewById(R.id.deviceStatusBarTagType);
          tvTagDesc.setText(mfcTagData.GetTagType());
          TextView tvTagUID = (TextView) findViewById(R.id.deviceStatusBarUID);
          tvTagUID.setText(mfcTagData.GetTagUID());
          TextView tvTagSizes = (TextView) findViewById(R.id.deviceStatusBarSizeDims);
          tvTagSizes.setText(mfcTagData.GetTagSizeSpecString());
          // loop and add each sector to the linear layout within the scroll viewer:
          LinearLayout mainScrollerLayout = (LinearLayout) findViewById(R.id.mainDisplayItemsListLayout);
          LayoutInflater sectorLayoutInflater = getLayoutInflater();
          for(int sec = 0; sec < mfcTagData.GetTagSectors(); sec++) {
               MifareClassicTag.MFCSector nextSectorData = mfcTagData.GetSectorByIndex(sec);
               byte[] sectorBytes = new byte[nextSectorData.sectorSize];
               boolean sectorReadFailed = mfcTagData.GetSectorReadStatus(sec);
               LinearLayout sectorDisplay = SectorUIDisplay.NewInstance(nextSectorData.sectorBlockData, sec, sectorReadFailed).GetDisplayLayout();
               mainScrollerLayout.addView(sectorDisplay);
          }
     }

     private static final int TAG_SCANNING_TIME = 5000;
     private static Handler tagScanHandler = new Handler();
     private static Runnable tagScanRunnable = new Runnable() {
          public void run() {
               MifareClassicToolLibrary.StopLiveTagScanning(MainActivity.mainActivityInstance);
               MainActivity.mainActivityInstance.currentlyTagScanning = false;
               if(!MainActivity.mainActivityInstance.newMFCTagFound) {
                    Toast toastDisplay = Toast.makeText(MainActivity.mainActivityInstance, "No Mifare Classic tags found!", Toast.LENGTH_SHORT);
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
          ClearActiveDisplayWindow();
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
          Toast toastDisplay = Toast.makeText(this, toastStatusMsg, Toast.LENGTH_SHORT);
          toastDisplay.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 0);
     }

     protected void ActionButtonDisplayNFCSettings(View btnView) {
          MifareClassicToolLibrary.CheckNFCEnabled(true);
     }
}
