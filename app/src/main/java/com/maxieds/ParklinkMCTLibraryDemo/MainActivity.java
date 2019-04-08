package com.maxieds.ParklinkMCTLibraryDemo;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;

import com.maxieds.MifareClassicToolLibrary.MCTUtils;
import com.maxieds.MifareClassicToolLibrary.MifareClassicDataInterface;
import com.maxieds.MifareClassicToolLibrary.MifareClassicLibraryException;
import com.maxieds.MifareClassicToolLibrary.MifareClassicTag;
import com.maxieds.MifareClassicToolLibrary.MifareClassicUtils;
import com.maxieds.MifareClassicToolLibrary.MifareClassicToolLibrary;

import com.nononsenseapps.filepicker.FilePickerActivity;
import com.nononsenseapps.filepicker.Utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Arrays;

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
               Spinner mfc1kDumpImageSelectSpinner = (Spinner) MainActivity.mainActivityInstance.findViewById(R.id.dumpImageRawFilesSrcSpinner);
               String dumpImageRawName = mfc1kDumpImageSelectSpinner.getSelectedItem().toString();
               String appPkgName = MainActivity.mainActivityInstance.getPackageName();
               int selectedDumpImageRawId = MainActivity.mainActivityInstance.getResources().getIdentifier(dumpImageRawName, "raw", appPkgName);
               MainActivity.mainActivityInstance.activeMFCTag = MifareClassicTag.LoadMifareClassic1KFromResource(selectedDumpImageRawId);
               MainActivity.mainActivityInstance.DisplayNewMFCTag(activeMFCTag);
          }
     };

     final private int REQUEST_CODE_ASK_PERMISSIONS = 123;

     private static int NUM_RETRIES_TO_AUTH_SETTING = 0;
     private static int MFC1K_DUMP_IMAGE_PICKER_INDEX = 0;

     private void LoadSharedSettings() {
          SharedPreferences sharedPrefs = getSharedPreferences(getPackageName(), Context.MODE_PRIVATE);
          NUM_RETRIES_TO_AUTH_SETTING = sharedPrefs.getInt("NumRetriesToAuthSetting", 1);
          MFC1K_DUMP_IMAGE_PICKER_INDEX = sharedPrefs.getInt("MFC1KDumpImagePickerIndex", 0);
          FILE_PICKER_INIT_DIRECTORY = sharedPrefs.getString("FilePickerInitDirectory", Environment.getExternalStorageDirectory().getPath());
     }

     private void UpdateSharedSettings() {
          SharedPreferences sharedPrefs = getSharedPreferences(getPackageName(), Context.MODE_PRIVATE);
          SharedPreferences.Editor sprefEditor = sharedPrefs.edit();
          sprefEditor.putInt("NumRetriesToAuthSetting", NUM_RETRIES_TO_AUTH_SETTING);
          sprefEditor.putInt("MFC1KDumpImagePickerIndex", MFC1K_DUMP_IMAGE_PICKER_INDEX);
          sprefEditor.putString("FilePickerInitDirectory", FILE_PICKER_INIT_DIRECTORY);
          sprefEditor.apply();
     }

     @Override
     protected void onCreate(Bundle savedInstanceState) {

          if(!isTaskRoot()) {
               Log.w(TAG, "ReLaunch Intent Action: " + getIntent().getAction());
               finish();
               return;
          }
          super.onCreate(savedInstanceState);
          mainActivityInstance = this;
          setContentView(R.layout.activity_main);
          LoadSharedSettings();
          ConfigureMCTLibrary();
          LoadKeysDialog.initStaticVariablesBeforeClass();

          Toolbar toolbar = findViewById(R.id.toolbarActionBar);
          toolbar.setLogo(R.drawable.main_action_bar_logo_icon);
          toolbar.setSubtitle(String.format(Locale.US, "v%s (%s) -- APK Type (%s)",
                                            BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, BuildConfig.BUILD_TYPE));
          setActionBar(toolbar);

          SeekBar numAuthRetriesSeekbar = (SeekBar) findViewById(R.id.libraryNumRetriesSeekbar);
          numAuthRetriesSeekbar.setProgress(NUM_RETRIES_TO_AUTH_SETTING);
          numAuthRetriesSeekbar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
               @Override
               public void onStopTrackingTouch(SeekBar seekBar) {}
               @Override
               public void onStartTrackingTouch(SeekBar seekBar) {}
               @Override
               public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    MifareClassicToolLibrary.RETRIES_TO_AUTH_KEYAB = progress;
                    MainActivity.NUM_RETRIES_TO_AUTH_SETTING = progress;
                    TextView tvSettingLabel = (TextView) findViewById(R.id.numRetriesToAuthSeekbarSettingLabel);
                    tvSettingLabel.setText(String.format(Locale.US, "(%d)", progress));
                    String statusMsg = String.format(Locale.US, "Changed RETRIES_TO_AUTH_KEYAB setting to %d auth retries.",
                                                     progress);
                    MainActivity.mainActivityInstance.DisplayToastMessage(statusMsg);
                    MainActivity.mainActivityInstance.UpdateSharedSettings();
               }
          });

          Spinner mfc1kDumpImageSelectSpinner = (Spinner) findViewById(R.id.dumpImageRawFilesSrcSpinner);
          mfc1kDumpImageSelectSpinner.setSelection(MFC1K_DUMP_IMAGE_PICKER_INDEX);
          mfc1kDumpImageSelectSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
               @Override
               public void onItemSelected(AdapterView parent, View view, int position, long id) {
                    MainActivity.MFC1K_DUMP_IMAGE_PICKER_INDEX = position;
                    MainActivity.mainActivityInstance.UpdateSharedSettings();
                    DIFF_COMPARE_MFC1K_DUMP_IMAGE_SOURCE = null;
               }
               @Override
               public void onNothingSelected(AdapterView parent) {}
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
          READY_WRITE_BLANK_TAG = false;
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
          Log.i(TAG, "TOAST MSG DISPLAYED: " + toastMsg);
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
          if(currentlyTagScanning) {
               MifareClassicToolLibrary.StartLiveTagScanning(this);
          }
     }

     @Override
     public void onPause() {
          if(currentlyTagScanning) {
               MifareClassicToolLibrary.StopLiveTagScanning(this);
               Log.i(TAG, getString(R.string.app_name) + " : onPause AT " + MCTUtils.GetTimestamp());
          }
          super.onPause();
     }

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
                    AsyncTask.execute(new Runnable() {
                         @Override
                         public void run() {
                              try {
                                   MifareClassicToolLibrary.EnableProgressBarDisplay(true);
                                   if(READY_WRITE_BLANK_TAG) {
                                        READY_WRITE_BLANK_TAG = false;
                                        Spinner rawFileSrcSpinner = (Spinner) findViewById(R.id.dumpImageRawFilesSrcSpinner);
                                        String resFilePath = rawFileSrcSpinner.getSelectedItem().toString();
                                        int rawDumpResID = MainActivity.mainActivityInstance.getResources().getIdentifier(
                                                           resFilePath, "raw", MainActivity.mainActivityInstance.getPackageName());
                                        MifareClassicUtils.WriteBlankMFC1KTag(nfcTag, rawDumpResID, LoadKeysDialog.GetPresetKeys());
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
                              MifareClassicToolLibrary.EnableProgressBarDisplay(false);
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

     private static void ConfigureMCTLibrary() {
          MifareClassicToolLibrary.InitializeLibrary(MainActivity.mainActivityInstance);
          MifareClassicToolLibrary.RETRIES_TO_AUTH_KEYAB = NUM_RETRIES_TO_AUTH_SETTING;
     }

     private static final int FILE_SELECT_ACTIVITY_CODE = 111;
     private static String FILE_PICKER_INIT_DIRECTORY = Environment.getExternalStorageDirectory().getPath();

     @Override
     protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
          if (requestCode == FILE_SELECT_ACTIVITY_CODE && resultCode == Activity.RESULT_OK) {
               List<Uri> selectedFiles = Utils.getSelectedFilesFromResult(intent);
               for (Uri fileUri: selectedFiles) {
                    File selectedFile = Utils.getFileForUri(fileUri);
                    FILE_PICKER_INIT_DIRECTORY = selectedFile.getParent();
                    MainActivity.mainActivityInstance.UpdateSharedSettings();
                    throw new RuntimeException(selectedFile.getAbsolutePath());
               }
          }
          super.onActivityResult(requestCode, resultCode, intent);
     }

     public static File GetUserExternalFileSelection() {
          Intent fileSelectIntent = new Intent(MainActivity.mainActivityInstance, FilePickerActivity.class);
          fileSelectIntent.putExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false);
          fileSelectIntent.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, false);
          fileSelectIntent.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_FILE);
          fileSelectIntent.putExtra(FilePickerActivity.EXTRA_START_PATH, FILE_PICKER_INIT_DIRECTORY);
          MainActivity.mainActivityInstance.startActivityForResult(fileSelectIntent, FILE_SELECT_ACTIVITY_CODE);
          try {
               Looper.loop();
          } catch(RuntimeException rte) {
               String filePathSelection = rte.getMessage().split("java.lang.RuntimeException: ")[1];
               MainActivity.mainActivityInstance.DisplayToastMessage("User Selected Data File: " + filePathSelection, Toast.LENGTH_LONG);
               // this is necessary because for some reason the app otherwise
               // freezes without bringing the original Activity context back to the front:
               MainActivity.mainActivityInstance.moveTaskToBack(false);
               Intent bringToFrontIntent = new Intent(MainActivity.mainActivityInstance, MainActivity.class);
               bringToFrontIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
               MainActivity.mainActivityInstance.startActivity(bringToFrontIntent);
               return new File(filePathSelection);
          }
          return null;
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
          String toastNoticeMsg = String.format(Locale.US, "New Tag Found!\nATQA: %s\nSAK: %s\nATS: %s\nTime to read: % 4.2g sec",
                                                mfcTagData.GetATQA(), mfcTagData.GetSAK(), mfcTagData.GetATS(), mfcTagData.GetTotalReadTime() / 1000.0);
          DisplayToastMessage(toastNoticeMsg, Toast.LENGTH_LONG);
          // next, display the quick summary tag stats at the top of the screen below the toolbar:
          TextView tvTagDesc = (TextView) findViewById(R.id.deviceStatusBarTagType);
          tvTagDesc.setText(mfcTagData.GetTagType());
          TextView tvTagUID = (TextView) findViewById(R.id.deviceStatusBarUID);
          tvTagUID.setText(mfcTagData.GetTagUID(":"));
          TextView tvTagSizes = (TextView) findViewById(R.id.deviceStatusBarSizeDims);
          tvTagSizes.setText(mfcTagData.GetTagSizeSpecString());
          // loop and add each sector to the linear layout within the scroll viewer:
          LinearLayout mainScrollerLayout = (LinearLayout) findViewById(R.id.mainDisplayItemsListLayout);
          String[] expectedTagDiffData = null;
          if(DIFF_COMPARE_MFC1K_DUMP_IMAGE_SOURCE != null) {
               int dumpImageRawResId = getResources().getIdentifier(DIFF_COMPARE_MFC1K_DUMP_IMAGE_SOURCE, "raw", getPackageName());
               expectedTagDiffData = MifareClassicUtils.GetDumpImageContents(dumpImageRawResId);
          }
          for(int sec = 0; sec < mfcTagData.GetTagSectors(); sec++) {
               MifareClassicTag.MFCSector nextSectorData = mfcTagData.GetSectorByIndex(sec);
               long timeToReadSector = nextSectorData.timeToRead;
               boolean sectorReadFailed = mfcTagData.GetSectorReadStatus(sec);
               String[] expectedSectorDiffData = nextSectorData.sectorBlockData;
               if(DIFF_COMPARE_MFC1K_DUMP_IMAGE_SOURCE != null) {
                    int compareFromBlockIndex = sec * MifareClassicUtils.MFCLASSIC1K_BLOCKS_PER_SECTOR;
                    expectedSectorDiffData = Arrays.copyOfRange(expectedTagDiffData, compareFromBlockIndex, compareFromBlockIndex + MifareClassicUtils.MFCLASSIC1K_BLOCKS_PER_SECTOR);
               }
               LinearLayout sectorDisplay = SectorUIDisplay.NewInstance(nextSectorData.sectorBlockData, sec, sectorReadFailed, timeToReadSector, expectedSectorDiffData).GetDisplayLayout();
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
                            "\nGit Revision Date:\n" + BuildConfig.GIT_COMMIT_DATE +
                            "\nMCT Library Version: " + MifareClassicToolLibrary.GetLibraryVersion() +
                            getString(R.string.aboutAppAndHelpDivider) + getString(R.string.appHelpContents));
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

     public void ActionButtonExtractKeysFromDumpImage(View btnView) {
          Spinner rawFileSrcSpinner = (Spinner) findViewById(R.id.dumpImageRawFilesSrcSpinner);
          String rawFilePath = rawFileSrcSpinner.getSelectedItem().toString();
          int rawFileResID = getResources().getIdentifier(rawFilePath, "raw", getPackageName());
          InputStream dumpFileStream = getResources().openRawResource(rawFileResID);
          String[] imageKeyData = MifareClassicUtils.ExtractMFC1TagKeysFromDumpImage(dumpFileStream);
          LoadKeysDialog.AppendPresetKeys(imageKeyData);
          DisplayToastMessage(String.format(Locale.US, getString(R.string.ExtractKeysMsg), imageKeyData.length), Toast.LENGTH_LONG);
     }

     public void ActionButtonDisplayHelpForMCTLibrarySettingsTextView(View helpBtnView) {
          String helpMsg = getString(R.string.numRetriesToAuthSettingHelp);
          DisplayToastMessage(helpMsg, android.widget.Toast.LENGTH_LONG);
     }

     private static String DIFF_COMPARE_MFC1K_DUMP_IMAGE_SOURCE = null;

     public void ActionButtonSetDumpAsTagScanDiffSource(View btnView) {
          Spinner mfc1kDumpImagesSpinner = (Spinner) findViewById(R.id.dumpImageRawFilesSrcSpinner);
          DIFF_COMPARE_MFC1K_DUMP_IMAGE_SOURCE = mfc1kDumpImagesSpinner.getSelectedItem().toString();
          DisplayToastMessage(getString(R.string.SetMFC1KDumpAsDiffMsg).replace("DUMP-IMG-NAME", DIFF_COMPARE_MFC1K_DUMP_IMAGE_SOURCE));
     }

     private static final int TAG_SCANNING_TIME = 8000;
     private static final int TAG_WRITE_TIMEOUT = 6000;

     private static Handler tagScanHandler = new Handler();
     private static Runnable tagScanRunnable = new Runnable() {
          public void run() {
               if(MainActivity.mainActivityInstance.READY_WRITE_BLANK_TAG) {
                    MainActivity.mainActivityInstance.DisplayToastMessage("Write new tag operation timed out!");
               }
               MainActivity.mainActivityInstance.READY_WRITE_BLANK_TAG = false;
          }
     };

     public void ActionButtonScanNewTag(View btnView) {
          Button tagScanningBtn = (Button) findViewById(R.id.tagScanningButton);
          if(currentlyTagScanning) {
               currentlyTagScanning = false;
               tagScanningBtn.setText("Enable Scan");
               DisplayToastMessage(getString(R.string.DisableTagScanningMsg));
               MainActivity.mainActivityInstance.SetActiveTagScanningIcon(false);
          }
          else {
               currentlyTagScanning = true;
               tagScanningBtn.setText("Disable Scan");
               DisplayToastMessage(getString(R.string.EnableTagScanningMsg));
               MainActivity.mainActivityInstance.SetActiveTagScanningIcon(true);
          }
     }

     private final String OUTPUT_FILE_APP_DIRECTORY = "MCTLibraryDemoFiles";

     public void ActionButtonWriteTagToFile(View btnView) {
          if (activeMFCTag == null) {
               DisplayToastMessage("No active MFC tag to save!");
               return;
          }
          String outfileBasePath = String.format(Locale.ENGLISH, "MFC%s-%s",
               MifareClassicTag.GetTagByteCountString(activeMFCTag.GetTagSize()),
               MCTUtils.GetTimestamp().replace(" ", "").replace("@", "-"));
          String toastStatusMsg = "";
          for(int ext = 0; ext < 2; ext++) {
               String fileExt = ext != 0 ? ".dmp" : ".hex";
               File downloadsFolder = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "//" + OUTPUT_FILE_APP_DIRECTORY + "//");
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
                         toastStatusMsg += String.format(Locale.US, "Wrote dump data file \"%s\" (Hex Bytes) @ %dB.\n",
                                                         outfile.getPath(), outfile.getTotalSpace());
                    } else {
                         activeMFCTag.ExportToBinaryDumpFile(outfile.getAbsolutePath());
                         mimeType = "application/octet-stream";
                         isMediaScannable = false;
                         downloadDesc = "MFC1K Data Dump Image (Binary Format)" + outfile.getName();
                         toastStatusMsg += String.format(Locale.US, "Wrote dump data file \"%s\" (Binary Format) @ %dB.\n",
                                                         outfile.getPath(), outfile.getTotalSpace());
                    }
                    downloadManager.addCompletedDownload(downloadDesc,
                                              OUTPUT_FILE_APP_DIRECTORY + "/" + outfile.getName(),
                                                         isMediaScannable, mimeType, outfile.getAbsolutePath(),
                                                         outfile.length(),true);
               } catch (IOException ioe) {
                    ioe.printStackTrace();
                    return;
               }
          }
          toastStatusMsg += "\nCheck your phone's downloads folder for the two dump image files.";
          DisplayToastMessage(toastStatusMsg, Toast.LENGTH_LONG);
          return;
     }

     public void ActionButtonSetKeys(View btnView) {
          LoadKeysDialog loadKeysDialog = new LoadKeysDialog(this);
          loadKeysDialog.BuildDialog();
          loadKeysDialog.Show();
     }

     public void ActionButtonClear(View btnView) {
          MifareClassicToolLibrary.EnableProgressBarDisplay(false);
          LoadKeysDialog.ClearKeyData();
          if(activeMFCTag != null) {
               ClearActiveDisplayWindow();
               activeMFCTag = null;
          }
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
