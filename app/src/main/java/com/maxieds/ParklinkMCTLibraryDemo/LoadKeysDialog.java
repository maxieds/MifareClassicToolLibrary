package com.maxieds.ParklinkMCTLibraryDemo;

import android.app.Activity;
import android.support.v7.app.AlertDialog;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.content.DialogInterface;
import android.widget.BaseAdapter;

import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import com.maxieds.ParklinkMCTLibraryDemo.R;
import com.maxieds.MifareClassicToolLibrary.MCTUtils;

public class LoadKeysDialog {

     private static final String TAG = LoadKeysDialog.class.getSimpleName();

     private static List<String> presetTestKeys = new ArrayList<String>();
     private static Activity mainActivityRef;
     private AlertDialog displayAddKeysDialog;
     private static Spinner dialogKeyDisplaySpinner;
     private static ArrayAdapter dialogKeyDisplaySpinnerAdapter;
     static {
          LoadKeysDialog.mainActivityRef = MainActivity.mainActivityInstance;
          LoadKeysDialog.dialogKeyDisplaySpinner = new Spinner(mainActivityRef);
          LoadKeysDialog.dialogKeyDisplaySpinnerAdapter = new ArrayAdapter<String>(
               LoadKeysDialog.mainActivityRef, android.R.layout.simple_list_item_1, LoadKeysDialog.GetPresetKeys());
          LoadKeysDialog.dialogKeyDisplaySpinner.setAdapter(dialogKeyDisplaySpinnerAdapter);
     }

     public static String[] GetPresetKeys() {
          return Arrays.copyOf(presetTestKeys.toArray(), presetTestKeys.size(), String[].class);
     }

     public static boolean AppendPresetKeys(String[] keyData) {
          if(keyData == null) {
               return false;
          }
          for(int k = 0; k < keyData.length; k++) {
               String nextKey = keyData[k];
               if(MCTUtils.IsHexAnd6Byte(nextKey)) {
                    presetTestKeys.add(nextKey);
                    dialogKeyDisplaySpinnerAdapter.insert(nextKey, 0);
                    ((BaseAdapter) dialogKeyDisplaySpinnerAdapter).notifyDataSetChanged();
               }
          }
          return true;
     }

     public static void ClearKeyData() {
          presetTestKeys.clear();
     }

     public LoadKeysDialog() {
          displayAddKeysDialog = null;
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