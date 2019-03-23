package com.maxieds.mifareclassictoolextension;

import android.app.Activity;
import android.support.v7.app.AlertDialog;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.content.DialogInterface;

import java.io.File;
import java.util.List;
import java.util.ArrayList;

import com.maxieds.mifareclassictoollibrary.MCTUtils;

public class LoadKeysDialog {

     private static final String TAG = LoadKeysDialog.class.getSimpleName();

     private static List<String> presetTestKeys = new ArrayList<String>();
     private static Activity mainActivityRef;
     private AlertDialog displayAddKeysDialog;
     private static Spinner dialogKeyDisplaySpinner;
     private static SpinnerAdapter dialogKeyDisplaySpinnerAdapter;
     static {
          LoadKeysDialog.mainActivityRef = MainActivity.mainActivityInstance;
          LoadKeysDialog.dialogKeyDisplaySpinner = new Spinner(mainActivityRef);
          LoadKeysDialog.dialogKeyDisplaySpinnerAdapter = new ArrayAdapter<String>(
               LoadKeysDialog.mainActivityRef, android.R.layout.simple_list_item_1, LoadKeysDialog.GetPresetKeys());
          //LoadKeysDialog.dialogKeyDisplaySpinner.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
          LoadKeysDialog.dialogKeyDisplaySpinner.setAdapter(dialogKeyDisplaySpinnerAdapter);
     }

     public static String[] GetPresetKeys() {
          return presetTestKeys.toArray();
     }

     public static boolean AppendPresetKeys(String[] keyData) {
          if(keyData == null) {
               return false;
          }
          for(int k = 0; k < keyData.length; k++) {
               String nextKey = keyData[k];
               if(MCTUtils.IsHexAnd6Bytes(nextKey)) {
                    presetTestKeys.add(nextKey);
                    dialogKeyDisplaySpinnerAdapter.add(nextKey);
                    dialogKeyDisplaySpinnerAdapter.notifyDataSetChanged();
               }
          }
          return true;
     }

     public static void ClearKeyData() {
          presetTestKeys.clear();
     }

     public LoadKeysDialog() {
          displayKeysDialog = null;
     }

     public boolean BuildDialog() {

          AlertDialog.Builder dialog = new AlertDialog.Builder(mainActivityRef);
          dialog.setIcon(R.drawable.add_key_dialog_icon);
          dialog.setTitle(R.string.loadKeysDialogTitle);
          dialog.setView(dialogKeyDisplaySpinner);
          dialog.setMessage(R.string.loadKeysDialogDesc);
          dialog.setNegativeButton("Done", null);
          dialog.setNeutralButton("Load Keys", new DialogInterface.OnClickListener() {
               @Override
               public void onClick(DialogInterface dialog, int whichBtn) {
                    File keyDataFile = MainActivity.GetUserExternalFileSelection();
                    String[] keyDataArray = MCTUtils.ReadKeysFromTextFile(keyDataFile);
                    LoadKeysDialog.AppendPresetKeys(keyDataArray);
               }
          });
          dialog.setPositiveButton("Random Key", new DialogInterface.OnClickListener() {
               @Override
               public void onClick(DialogInterface dialog, int whichBtn) {
                    String randomKey = MCTUtils.BytesToHexString(MCTUtils.GetRandomBytes(6));
                    LoadKeysDialog.AppendPresetKeys(new String[] { randomKey });
               }
          });
          dialog.setInverseBackgroundForced(true);
          displayAddKeysDialog = dialog.create();
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