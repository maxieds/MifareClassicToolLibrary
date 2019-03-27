package com.maxieds.ParklinkMCTLibraryDemo;

import android.app.Activity;
import android.support.v7.app.AlertDialog;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.content.DialogInterface;
import android.widget.BaseAdapter;
import android.view.ViewGroup;
import android.view.View;

import java.io.File;
import java.util.List;
import java.util.ArrayList;

import com.maxieds.ParklinkMCTLibraryDemo.R;
import com.maxieds.MifareClassicToolLibrary.MCTUtils;
import  com.maxieds.MifareClassicToolLibrary.MifareClassicToolLibrary;

public class LoadKeysDialog {

     private static final String TAG = LoadKeysDialog.class.getSimpleName();

     private static List<String> presetTestKeys;
     private static Activity mainActivityRef;
     private AlertDialog displayAddKeysDialog;
     private static Spinner dialogKeyDisplaySpinner;
     private static ArrayAdapter<String> dialogKeyDisplaySpinnerAdapter;
     private static boolean staticVariablesInit = false;

     public static void initStaticVariablesBeforeClass() {
          presetTestKeys = new ArrayList<String>();
          mainActivityRef = MainActivity.mainActivityInstance;
          dialogKeyDisplaySpinner = new Spinner(mainActivityRef);
          dialogKeyDisplaySpinner.setPadding(20, 15, 20, 5);
          dialogKeyDisplaySpinnerAdapter = new ArrayAdapter<String>(
               mainActivityRef, android.R.layout.simple_spinner_item, presetTestKeys);
          dialogKeyDisplaySpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
          dialogKeyDisplaySpinner.setAdapter(dialogKeyDisplaySpinnerAdapter);
          staticVariablesInit = true;
     }

     public static String[] GetPresetKeys() {
          if(!staticVariablesInit) {
               initStaticVariablesBeforeClass();
          }
          if(presetTestKeys.size() == 0) {
               String[] defaultKeyData = new String[] {
                    "000000000000",
                    MCTUtils.BytesToHexString(android.nfc.tech.MifareClassic.KEY_DEFAULT),
                    MCTUtils.BytesToHexString(android.nfc.tech.MifareClassic.KEY_MIFARE_APPLICATION_DIRECTORY),
                    MCTUtils.BytesToHexString(android.nfc.tech.MifareClassic.KEY_NFC_FORUM)
               };
               return defaultKeyData;
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
          for(int k = 0; k < keyData.length; k++) {
               String nextKey = keyData[k];
               if(MCTUtils.IsHexAnd6Byte(nextKey)) {
                    presetTestKeys.add(0, nextKey);
                    ((BaseAdapter) dialogKeyDisplaySpinnerAdapter).notifyDataSetChanged();
               }
          }
          return true;
     }

     public static void ClearKeyData() {
          if(!staticVariablesInit) {
               initStaticVariablesBeforeClass();
          }
          presetTestKeys.clear();
     }

     public LoadKeysDialog(Activity mainActivityContext) {
          if(!staticVariablesInit) {
               initStaticVariablesBeforeClass();
          }
          mainActivityRef = mainActivityContext;
          displayAddKeysDialog = null;
     }

     public boolean BuildDialog() {

          AlertDialog.Builder dialog = new AlertDialog.Builder(mainActivityRef);
          dialog.setIcon(R.drawable.add_key_dialog_icon);
          dialog.setTitle(R.string.loadKeysDialogTitle);
          if(dialogKeyDisplaySpinner.getParent() != null) {
               ((ViewGroup) dialogKeyDisplaySpinner.getParent()).removeView(dialogKeyDisplaySpinner);
          }
          dialog.setView(dialogKeyDisplaySpinner);
          dialog.setMessage(R.string.loadKeysDialogDesc);
          dialog.setPositiveButton("Done", null);
          dialog.setNeutralButton("Load Keys", null);
          dialog.setNegativeButton("Random Key", null);
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
                              //MifareClassicToolLibrary.LoadStandardKeySets(true);
                              //String[] standardKeysFull = MifareClassicToolLibrary.GetStandardAllKeys();
                              //LoadKeysDialog.AppendPresetKeys(standardKeysFull);
                              String randomKey = MCTUtils.BytesToHexString(MCTUtils.GetRandomBytes(6));
                              LoadKeysDialog.AppendPresetKeys(new String[]{ randomKey });
                         }
                    });
                    displayAddKeysDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextSize(12);
                    displayAddKeysDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setCompoundDrawablesWithIntrinsicBounds(
                         mainActivityRef.getResources().getDrawable(R.drawable.load_keys_from_file_icon),
                         null,
                         null,
                         null
                    );
                    displayAddKeysDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
                         @Override
                         public void onClick(View view) {
                              File keyDataFile = MainActivity.GetUserExternalFileSelection();
                              String[] keyDataArray = MCTUtils.ReadKeysFromTextFile(keyDataFile);
                              LoadKeysDialog.AppendPresetKeys(keyDataArray);
                         }
                    });
                    displayAddKeysDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextSize(12);
                    displayAddKeysDialog.getButton(AlertDialog.BUTTON_POSITIVE).setCompoundDrawablesWithIntrinsicBounds(
                         mainActivityRef.getResources().getDrawable(R.drawable.done_button_icon),
                         null,
                         null,
                         null
                    );
                    displayAddKeysDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextSize(12);
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