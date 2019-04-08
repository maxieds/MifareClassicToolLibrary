package com.maxieds.ParklinkMCTLibraryDemo;

import android.app.Activity;
import android.support.v7.app.AlertDialog;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.content.DialogInterface;
import android.widget.BaseAdapter;
import android.content.Context;
import android.view.View;

import java.io.InputStream;
import java.io.File;
import java.io.FileInputStream;
import java.text.Collator;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

import com.maxieds.ParklinkMCTLibraryDemo.R;
import com.maxieds.MifareClassicToolLibrary.MCTUtils;
import  com.maxieds.MifareClassicToolLibrary.MifareClassicToolLibrary;

public class LoadKeysDialog {

     private static final String TAG = LoadKeysDialog.class.getSimpleName();

     private static List<String> presetTestKeys;
     private static Activity mainActivityRef;
     private AlertDialog displayAddKeysDialog;
     private static boolean staticVariablesInit = false;

     public static void initStaticVariablesBeforeClass() {
          presetTestKeys = new ArrayList<String>();
          String[] defaultKeyData = new String[] {
               //"000000000000",
               MCTUtils.BytesToHexString(android.nfc.tech.MifareClassic.KEY_DEFAULT),
               MCTUtils.BytesToHexString(android.nfc.tech.MifareClassic.KEY_MIFARE_APPLICATION_DIRECTORY),
               MCTUtils.BytesToHexString(android.nfc.tech.MifareClassic.KEY_NFC_FORUM)
          };
          for(int k = 0; k < defaultKeyData.length; k++) {
               presetTestKeys.add(defaultKeyData[k]);
          }
          mainActivityRef = MainActivity.mainActivityInstance;
          staticVariablesInit = true;
     }

     public static String[] GetPresetKeys() {
          if(!staticVariablesInit) {
               initStaticVariablesBeforeClass();
          }
          String[] keysStringArray = new String[presetTestKeys.size()];
          for(int s = 0; s < presetTestKeys.size(); s++) {
               keysStringArray[s] = presetTestKeys.get(s);
          }
          return keysStringArray;
     }

     public static boolean AppendPresetKeys(String[] keyData) {
          if(!staticVariablesInit) {
               initStaticVariablesBeforeClass();
          }
          if(keyData == null) {
               return false;
          }
          List<String> keyDataCopy = new ArrayList<String>();
          keyDataCopy.addAll(presetTestKeys);
          presetTestKeys.clear();
          for(int k = 0; k < keyData.length; k++) {
               String nextKey = keyData[k].toUpperCase(Locale.US);
               if(MCTUtils.IsHexAnd6Byte(nextKey) && !keyDataCopy.contains(nextKey)) {
                    keyDataCopy.add(0, nextKey);
               }
          }
          presetTestKeys.addAll(keyDataCopy);
          Collections.sort(presetTestKeys, Collator.getInstance());
          return true;
     }

     public static void ClearKeyData() {
          presetTestKeys.clear();
          initStaticVariablesBeforeClass();
     }

     public LoadKeysDialog(Activity mainActivityContext) {
          if(!staticVariablesInit) {
               initStaticVariablesBeforeClass();
          }
          mainActivityRef = mainActivityContext;
          displayAddKeysDialog = null;
     }

     public boolean BuildDialog() {

          AlertDialog.Builder dialog = new AlertDialog.Builder((Context) mainActivityRef);
          dialog.setIcon(R.drawable.add_key_dialog_icon);
          dialog.setTitle(R.string.loadKeysDialogTitle);

          Spinner dialogKeyDisplaySpinner = new Spinner((Context) mainActivityRef);
          dialogKeyDisplaySpinner.setPadding(45, 80, 45, 15);
          ArrayAdapter<String> dialogKeyDisplaySpinnerAdapter = new ArrayAdapter<String>(
               mainActivityRef, android.R.layout.simple_spinner_item, presetTestKeys);
          dialogKeyDisplaySpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
          dialogKeyDisplaySpinner.setAdapter(dialogKeyDisplaySpinnerAdapter);
          final Spinner dialogKeyDisplaySpinnerFinal = dialogKeyDisplaySpinner;
          dialog.setView(dialogKeyDisplaySpinnerFinal);

          dialog.setMessage(R.string.loadKeysDialogDesc);
          dialog.setPositiveButton("Load File", null);
          dialog.setNeutralButton("Parklink Keys", null);
          dialog.setNegativeButton("Ext/Std Keys", null);
          dialog.setInverseBackgroundForced(true);
          displayAddKeysDialog = dialog.create();
          displayAddKeysDialog.setOnShowListener(new DialogInterface.OnShowListener() {

               @Override
               public void onShow(DialogInterface dialog) {
                    displayAddKeysDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setCompoundDrawablesWithIntrinsicBounds(
                         mainActivityRef.getResources().getDrawable(R.drawable.random_button_icon),
                         null,
                         null,
                         null
                    );
                    displayAddKeysDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(new View.OnClickListener() {
                         @Override
                         public void onClick(View view) {
                              MifareClassicToolLibrary.LoadStandardKeySets(true);
                              String[] standardKeysFull = MifareClassicToolLibrary.GetStandardAllKeys();
                              LoadKeysDialog.AppendPresetKeys(standardKeysFull);
                              ((BaseAdapter) dialogKeyDisplaySpinnerFinal.getAdapter()).notifyDataSetChanged();
                         }
                    });
                    displayAddKeysDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextSize(9);
                    displayAddKeysDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setCompoundDrawablesWithIntrinsicBounds(
                         mainActivityRef.getResources().getDrawable(R.drawable.load_keys_from_file_icon),
                         null,
                         null,
                         null
                    );
                    displayAddKeysDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
                         @Override
                         public void onClick(View view) {
                              InputStream keyDataStream = mainActivityRef.getResources().openRawResource(R.raw.preset_sample_keys);
                              String[] keyDataArray = MCTUtils.ReadKeysFromTextFile(keyDataStream);
                              LoadKeysDialog.AppendPresetKeys(keyDataArray);
                              ((BaseAdapter) dialogKeyDisplaySpinnerFinal.getAdapter()).notifyDataSetChanged();
                         }
                    });
                    displayAddKeysDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextSize(9);
                    displayAddKeysDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                         @Override
                         public void onClick(View view) {
                              mainActivityRef.runOnUiThread(new Runnable() {
                                   public void run() {
                                        File userKeysFileSelection = MainActivity.mainActivityInstance.GetUserExternalFileSelection();
                                        try {
                                             InputStream userKeysFileStream = new FileInputStream(userKeysFileSelection);
                                             String[] keyDataArray = MCTUtils.ReadKeysFromTextFile(userKeysFileStream);
                                             LoadKeysDialog.AppendPresetKeys(keyDataArray);
                                             ((BaseAdapter) dialogKeyDisplaySpinnerFinal.getAdapter()).notifyDataSetChanged();
                                             userKeysFileStream.close();
                                        } catch(Exception ioe) {
                                             ioe.printStackTrace();
                                             MainActivity.mainActivityInstance.DisplayToastMessage(ioe.getMessage());
                                        }
                                   }
                              });
                         }
                    });
                    displayAddKeysDialog.getButton(AlertDialog.BUTTON_POSITIVE).setCompoundDrawablesWithIntrinsicBounds(
                         mainActivityRef.getResources().getDrawable(R.drawable.done_button_icon),
                         null,
                         null,
                         null
                    );
                    displayAddKeysDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextSize(9);
               }
          });
          return displayAddKeysDialog != null;

     }

     public boolean Show() {
          if(displayAddKeysDialog != null) {
               displayAddKeysDialog.show();
               return true;
          }
          return false;
     }

}