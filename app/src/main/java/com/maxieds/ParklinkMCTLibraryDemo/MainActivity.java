package com.maxieds.ParklinkMCTLibraryDemo;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.content.Intent;
import android.os.Handler;
import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
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
import android.support.v7.app.AppCompatActivity;
import android.widget.Toolbar;
import android.widget.Spinner;
import android.support.v7.app.AlertDialog;
import android.widget.ImageView;
import android.os.AsyncTask;
import android.app.Activity;
import android.Manifest;
import android.content.pm.PackageManager;
import android.app.DownloadManager;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.CheckBox;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import com.maxieds.MifareClassicToolLibrary.MCTUtils;
import com.maxieds.MifareClassicToolLibrary.MifareClassicTag;
import com.maxieds.MifareClassicToolLibrary.MifareClassicToolLibrary;
import com.maxieds.MifareClassicToolLibrary.MifareClassicLibraryException;
import com.maxieds.MifareClassicToolLibrary.MifareClassicDataInterface;

public class MainActivity extends AppCompatActivity implements MifareClassicDataInterface {

     public static final String TAG = MainActivity.class.getSimpleName();

     public static MainActivity mainActivityInstance = null;
     private static boolean currentlyTagScanning = false;
     private static boolean newMFCTagFound = false;
     private static MifareClassicTag activeMFCTag = null;

     private static final int LAUNCH_DEFAULT_TAG_DELAY = 250;
     private static Handler delayInitialTagDisplayHandler = new Handler();
     private static Runnable delayInitialTagDisplayRunnable = new Runnable() {
          public void run() {
               if(activeMFCTag != null) {
                    return;
               }
               MainActivity.mainActivityInstance.activeMFCTag = MifareClassicTag.LoadMifareClassic1KFromResource(R.raw.mfc1k_random_content_fixed_keys);
               MainActivity.mainActivityInstance.DisplayNewMFCTag(activeMFCTag);
          }
     };

     final private int REQUEST_CODE_ASK_PERMISSIONS = 123;

     @Override
     protected void onCreate(Bundle savedInstanceState) {

          if(!isTaskRoot()) {
               Log.w(TAG, "ReLaunch Intent Action: " + getIntent().getAction());
               onNewIntent(getIntent());
               return;
          }
          super.onCreate(savedInstanceState);
          mainActivityInstance = this;
          setContentView(R.layout.activity_main);
          ConfigureMCTLibrary();

          Toolbar toolbar = findViewById(R.id.toolbarActionBar);
          toolbar.setLogo(R.drawable.main_action_bar_logo_icon);
          toolbar.setSubtitle(String.format(Locale.US, "App: v%s (%s) / Lib: %s",
                                            BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE,
                                            MifareClassicToolLibrary.GetLibraryVersion()));
          setActionBar(toolbar);

          SeekBar numAuthRetriesSeekbar = (SeekBar) findViewById(R.id.libraryNumRetriesSeekbar);
          numAuthRetriesSeekbar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
               @Override
               public void onStopTrackingTouch(SeekBar seekBar) {}
               @Override
               public void onStartTrackingTouch(SeekBar seekBar) {}
               @Override
               public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    MifareClassicToolLibrary.RETRIES_TO_AUTH_KEYAB = progress;
                    String statusMsg = String.format(Locale.US, "Changed RETRIES_TO_AUTH_KEYAB setting to %d auth retries.",
                                                     progress);
                    MainActivity.mainActivityInstance.DisplayToastMessage(statusMsg);
               }
          });

          int hasReadExternalPermission = checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE);
          if (hasReadExternalPermission != PackageManager.PERMISSION_GRANTED) {
               String[] reqestedPermissions = new String[] { android.Manifest.permission.READ_EXTERNAL_STORAGE };
               requestPermissions(reqestedPermissions, REQUEST_CODE_ASK_PERMISSIONS);
          }
          int hasWriteExternalPermission = checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
          if (hasWriteExternalPermission != PackageManager.PERMISSION_GRANTED) {
               String[] reqestedPermissions = new String[] { android.Manifest.permission.WRITE_EXTERNAL_STORAGE };
               requestPermissions(reqestedPermissions, REQUEST_CODE_ASK_PERMISSIONS);
          }

          currentlyTagScanning = false;
          SetActiveTagScanningIcon(false);
          SetHaveActiveTagIcon(false);

          // set this last so we display immediately after returning:
          delayInitialTagDisplayHandler.postDelayed(delayInitialTagDisplayRunnable, LAUNCH_DEFAULT_TAG_DELAY);
          Log.i(TAG, getString(com.maxieds.ParklinkMCTLibraryDemo.R.string.app_name) + " up and running AT " + MCTUtils.GetTimestamp());

     }

     public void RegisterNewIntent(Intent mfcIntent) {
          onNewIntent(mfcIntent);
     }

     public Context GetApplicationContext() {
          return this;
     }

     public Activity GetApplicationActivity() { return this; }

     protected void DisplayToastMessage(String toastMsg, int toastLength) {
          Toast toastDisplay = Toast.makeText(this, toastMsg, toastLength);
          toastDisplay.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 0);
          toastDisplay.show();
     }

     protected void DisplayToastMessage(String toastMsg) {
          DisplayToastMessage(toastMsg, Toast.LENGTH_SHORT);
     }

     private final int ALPHA_ENABLED = 255;
     private final int ALPHA_DISABLED = 64;

     protected void SetActiveTagScanningIcon(boolean enabled) {
          ImageView tagScanningIcon = (ImageView) findViewById(R.id.activeTagScanningIcon);
          if(enabled) {
               tagScanningIcon.setAlpha(ALPHA_ENABLED);
          }
          else {
               tagScanningIcon.setAlpha(ALPHA_DISABLED);
          }
     }

     protected void SetHaveActiveTagIcon(boolean enabled) {
          ImageView tagScanningIcon = (ImageView) findViewById(R.id.haveActiveTagIcon);
          if(enabled) {
               tagScanningIcon.setAlpha(ALPHA_ENABLED);
          }
          else {
               tagScanningIcon.setAlpha(ALPHA_DISABLED);
          }
     }

     @Override
     public void onResume() {
          super.onResume();
     }

     @Override
     public void onPause() {
          if(currentlyTagScanning) {
               MifareClassicToolLibrary.StopLiveTagScanning(this);
               //currentlyTagScanning = false;
               Log.i(TAG, getString(R.string.app_name) + " : onPause AT " + MCTUtils.GetTimestamp());
          }
          super.onPause();
     }

     public static final int FILE_SELECT_CODE = 0;
     private static boolean READY_WRITE_BLANK_TAG = false;

     @Override
     protected void onNewIntent(Intent intent) {
          if(intent == null || intent.getAction() == null) {
               return;
          }
          if((currentlyTagScanning || READY_WRITE_BLANK_TAG) &&
               (intent.getAction().equals(NfcAdapter.ACTION_TAG_DISCOVERED) ||
             intent.getAction().equals(NfcAdapter.ACTION_TECH_DISCOVERED))) {
               final Tag nfcTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
               if(MifareClassicTag.CheckMifareClassicSupport(nfcTag, this) != 0) {
                    DisplayToastMessage("The discovered NFC device is not a Mifare Classic tag.");
               }
               else {
                    Vibrator vibObj = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                    vibObj.vibrate(100);
                    vibObj.vibrate(35);
                    vibObj.vibrate(100);
                    String instStr = String.format(Locale.US, getString(R.string.newTagInstructions),
                                                   MCTUtils.BytesToHexString(nfcTag.getId()));
                    DisplayToastMessage(instStr, Toast.LENGTH_LONG);
                    SetHaveActiveTagIcon(true);
                    currentlyTagScanning = false;
                    AsyncTask.execute(new Runnable() {
                         @Override
                         public void run() {
                              try {
                                   if(READY_WRITE_BLANK_TAG) {
                                        READY_WRITE_BLANK_TAG = false;
                                        Spinner rawFileSrcSpinner = (Spinner) findViewById(R.id.dumpImageRawFilesSrcSpinner);
                                        String resFilePath = rawFileSrcSpinner.getSelectedItem().toString();
                                        int rawDumpResID = MainActivity.mainActivityInstance.getResources().getIdentifier(
                                                           resFilePath, "raw", MainActivity.mainActivityInstance.getPackageName());
                                        MifareClassicTag.WriteBlankMFC1KTag(nfcTag, rawDumpResID, LoadKeysDialog.GetPresetKeys());
                                   }
                                   else {
                                        MainActivity.mainActivityInstance.newMFCTagFound = true;
                                        MifareClassicTag mfcTag = MifareClassicTag.Decode(nfcTag, LoadKeysDialog.GetPresetKeys(), true);
                                        MainActivity.mainActivityInstance.activeMFCTag = mfcTag;
                                        MainActivity.mainActivityInstance.runOnUiThread(new Runnable() {
                                             @Override
                                             public void run() {
                                                  MainActivity.mainActivityInstance.DisplayNewMFCTag(MainActivity.mainActivityInstance.activeMFCTag);
                                             }
                                        });
                                   }
                              } catch(MifareClassicLibraryException mfcLibExcpt) {
                                   mfcLibExcpt.printStackTrace();
                                   final String toastErrorMsg = mfcLibExcpt.ToString();
                                   MainActivity.mainActivityInstance.runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                             DisplayToastMessage(toastErrorMsg);
                                        }
                                   });
                              }
                              MainActivity.mainActivityInstance.runOnUiThread(new Runnable() {
                                   @Override
                                   public void run() {
                                        SetHaveActiveTagIcon(false);
                                   }
                              });
                         }
                    });
               }
          }
          else if(intent.getAction().equals(NfcAdapter.ACTION_TAG_DISCOVERED) ||
                  intent.getAction().equals(NfcAdapter.ACTION_TECH_DISCOVERED)) {
               Tag nfcTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
               if(nfcTag != null) {
                    String toastStatusMsg = String.format(Locale.US, getString(R.string.ignoringTagFmt),
                                                          MCTUtils.BytesToHexString(nfcTag.getId()));
                    DisplayToastMessage(toastStatusMsg);
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
                    return;
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
          tvTagDesc.setText("<NO-DATA>");
          TextView tvTagUID = (TextView) findViewById(R.id.deviceStatusBarUID);
          tvTagUID.setText("<NO-DATA>");
          TextView tvTagSizes = (TextView) findViewById(R.id.deviceStatusBarSizeDims);
          tvTagSizes.setText("<NO-DATA>");
          LinearLayout mainScrollerLayout = (LinearLayout) findViewById(R.id.mainDisplayItemsListLayout);
          mainScrollerLayout.removeAllViews();
     }

     private void DisplayNewMFCTag(MifareClassicTag mfcTagData) {
          if(mfcTagData == null) {
               DisplayToastMessage("Attempt to display a NULL tag!");
               return;
          }
          ClearActiveDisplayWindow();
          // display a quick notice to the user of the more detailed tag information from the header sector:
          String toastNoticeMsg = String.format(Locale.US, "New Tag Found!\nATQA: %s\nSAK: %s\nATS: %s",
                                                mfcTagData.GetATQA(), mfcTagData.GetSAK(), mfcTagData.GetATS());
          DisplayToastMessage(toastNoticeMsg, Toast.LENGTH_LONG);
          // next, display the quick summary tag stats at the top of the screen below the toolbar:
          TextView tvTagDesc = (TextView) findViewById(R.id.deviceStatusBarTagType);
          tvTagDesc.setText(mfcTagData.GetTagType());
          TextView tvTagUID = (TextView) findViewById(R.id.deviceStatusBarUID);
          tvTagUID.setText(mfcTagData.GetTagUID());
          TextView tvTagSizes = (TextView) findViewById(R.id.deviceStatusBarSizeDims);
          tvTagSizes.setText(mfcTagData.GetTagSizeSpecString());
          // loop and add each sector to the linear layout within the scroll viewer:
          LinearLayout mainScrollerLayout = (LinearLayout) findViewById(R.id.mainDisplayItemsListLayout);
          for(int sec = 0; sec < mfcTagData.GetTagSectors(); sec++) {
               MifareClassicTag.MFCSector nextSectorData = mfcTagData.GetSectorByIndex(sec);
               boolean sectorReadFailed = mfcTagData.GetSectorReadStatus(sec);
               LinearLayout sectorDisplay = SectorUIDisplay.NewInstance(nextSectorData.sectorBlockData, sec, sectorReadFailed).GetDisplayLayout();
               mainScrollerLayout.addView(sectorDisplay);
          }
     }

     public void ActionButtonDisplayAppAboutInfo(View btnView) {
          AlertDialog.Builder dialog = new AlertDialog.Builder(this);
          dialog.setIcon(R.drawable.about_gear_icon);
          dialog.setTitle(R.string.aboutAppTitle);
          dialog.setMessage(getString(R.string.aboutAppDesc) +
                            "\n\nBuild Date/Time: " + BuildConfig.BUILD_TIMESTAMP +
                            "\nGit Commit of Build: " + BuildConfig.GIT_COMMIT_HASH +
                            "\nGit Revision Date:\n" + BuildConfig.GIT_COMMIT_DATE);
          dialog.setPositiveButton("Ok", null);
          dialog.show();
     }

     public void ActionButtonLoadDumpImageFromFile(View btnView) {
          Spinner rawFileSrcSpinner = (Spinner) findViewById(R.id.dumpImageRawFilesSrcSpinner);
          String rawFilePath = rawFileSrcSpinner.getSelectedItem().toString();
          int rawFileResID = getResources().getIdentifier(rawFilePath, "raw", getPackageName());
          MainActivity.mainActivityInstance.activeMFCTag = MifareClassicTag.LoadMifareClassic1KFromResource(rawFileResID);
          MainActivity.mainActivityInstance.DisplayNewMFCTag(activeMFCTag);
     }

     public void ActionButtonWriteDumpImageToTag(View btnView) {
          READY_WRITE_BLANK_TAG = true;
          DisplayToastMessage("Hold tag to back of phone to begin write process.");
          tagScanHandler.postDelayed(tagScanRunnable, TAG_WRITE_TIMEOUT);
     }

     public void ActionButtonDisplayHelpForMCTLibrarySettingsTextView(View helpBtnView) {
          String helpMsg = "A sector read may fail if the attempted authentications with KeyA and/or KeyB" +
                           " are unsuccessful. This may happen even if the attempted auth keys are correct to" +
                           " unlock the sector data. The setting specifies the number of retries on failed" +
                           " sector auth attempts (Default: 1).";
          DisplayToastMessage(helpMsg, android.widget.Toast.LENGTH_LONG);
     }

     public void ActionButtonDisplayHelpForMCTLibrarySettingsCheckbox(View helpBtnView) {
          String helpMsg = "Sets whether to retry authentication with keyA/B selections if only one or the other" +
                           " (but not both) of KeyA/KeyB has been correctly authenticated. If both KeyA/KeyB have" +
                           " been correctly authed, this setting is irrelevent. If neither have been authed, then" +
                           " the authentication procedure is repeated according to the number of retries setting" +
                           " from above.";
          DisplayToastMessage(helpMsg, android.widget.Toast.LENGTH_LONG);
     }

     private static final int TAG_SCANNING_TIME = 5000;
     private static final int TAG_WRITE_TIMEOUT = 3500;
     private static Handler tagScanHandler = new Handler();
     private static Runnable tagScanRunnable = new Runnable() {
          public void run() {
               if(MainActivity.mainActivityInstance.currentlyTagScanning &&
                    !MainActivity.mainActivityInstance.newMFCTagFound) {
                    MainActivity.mainActivityInstance.DisplayToastMessage("No Mifare Classic tags found!");
               }
               else if(MainActivity.mainActivityInstance.READY_WRITE_BLANK_TAG) {
                    MainActivity.mainActivityInstance.DisplayToastMessage("Write new tag operation timed out!");
               }
               MainActivity.mainActivityInstance.currentlyTagScanning = false;
               MainActivity.mainActivityInstance.READY_WRITE_BLANK_TAG = false;
               MainActivity.mainActivityInstance.SetActiveTagScanningIcon(false);
          }
     };

     public void ActionButtonScanNewTag(View btnView) {
          newMFCTagFound = false;
          currentlyTagScanning = true;
          MifareClassicToolLibrary.StartLiveTagScanning(this);
          tagScanHandler.postDelayed(tagScanRunnable, TAG_SCANNING_TIME);
          SetActiveTagScanningIcon(true);
          DisplayToastMessage("Scanning for new tag ...", Toast.LENGTH_LONG);
     }

     private final String OUTPUT_FILE_APP_DIRECTORY = "MCTLibraryDemoFiles";

     public void ActionButtonWriteTagToFile(View btnView) {
          if (activeMFCTag == null) {
               DisplayToastMessage("No active MFC tag to save!");
               return;
          }
          String outfileBasePath = String.format(Locale.ENGLISH, "MifareClassic%s-tagdump-%s",
               MifareClassicTag.GetTagByteCountString(activeMFCTag.GetTagSize()),
               MCTUtils.GetTimestamp().replace(" ", "").replace("@", "-"));
          for(int ext = 0; ext < 2; ext++) {
               String fileExt = ext != 0 ? ".dmp" : ".hex";
               File downloadsFolder = new File("//sdcard//Download//" + OUTPUT_FILE_APP_DIRECTORY + "//");
               File outfile = new File(downloadsFolder, outfileBasePath + fileExt);
               boolean docsFolderExists = true;
               if (!downloadsFolder.exists()) {
                    docsFolderExists = downloadsFolder.mkdir();
               }
               if (docsFolderExists) {
                    outfile = new File(downloadsFolder.getAbsolutePath(), outfileBasePath + fileExt);
               }
               try {
                    DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                    String mimeType, downloadDesc;
                    boolean isMediaScannable;
                    if (ext == 0) {
                         activeMFCTag.ExportToHexFile(outfile.getAbsolutePath());
                         mimeType = "text/plain";
                         isMediaScannable = true;
                         downloadDesc = "MFC1K Data Dump Image (Hex Bytes)" + outfile.getName();
                    } else {
                         activeMFCTag.ExportToBinaryDumpFile(outfile.getAbsolutePath());
                         mimeType = "application/octet-stream";
                         isMediaScannable = false;
                         downloadDesc = "MFC1K Data Dump Image (Binary Format)" + outfile.getName();
                    }
                    downloadManager.addCompletedDownload(OUTPUT_FILE_APP_DIRECTORY + "/" + outfile.getName(),
                                                         downloadDesc,
                                                         isMediaScannable, mimeType, outfile.getAbsolutePath(),
                                                         outfile.length(),true);
               } catch (IOException ioe) {
                    ioe.printStackTrace();
                    return;
               }
          }
          return;
     }

     public void ActionButtonSetKeys(View btnView) {
          LoadKeysDialog loadKeysDialog = new LoadKeysDialog(this);
          loadKeysDialog.BuildDialog();
          loadKeysDialog.Show();
     }

     public void ActionButtonClear(View btnView) {
          LoadKeysDialog.ClearKeyData();
          if(activeMFCTag != null) {
               ClearActiveDisplayWindow();
               activeMFCTag = null;
          }
          ClearActiveDisplayWindow();
     }

     public void ActionButtonCheckForMFCSupport(View btnView) {
          boolean phoneMFCSupport = MifareClassicToolLibrary.CheckPhoneMFCSupport();
          boolean phoneNFCEnabled = MifareClassicToolLibrary.CheckNFCEnabled(false);
          String toastStatusMsg = "";
          if(phoneMFCSupport) {
               toastStatusMsg += "This Android device supports MFC tags. ";
          }
          else {
               toastStatusMsg += "This Android device DOES NOT support MFC tags. ";
          }
          if(phoneNFCEnabled) {
               toastStatusMsg += "NFC is currently enabled on the device for tag reading.";
          }
          else {
               toastStatusMsg += "NFC is currently DISABLED on the device for tag reading.";
          }
          DisplayToastMessage(toastStatusMsg);
     }

     public void ActionButtonDisplayNFCSettings(View btnView) {
          if(MifareClassicToolLibrary.CheckNFCEnabled(true)) {
               DisplayToastMessage("NFC is already turned on!");
          }
          else {
               DisplayToastMessage("NFC is DISABLED! Open phone settings to enable NFC.");
          }
     }
}
