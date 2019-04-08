package com.maxieds.ParklinkMCTLibraryDemo;

import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.LayoutInflater;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;

import java.util.Locale;

import com.maxieds.ParklinkMCTLibraryDemo.R;
import com.maxieds.MifareClassicToolLibrary.MifareClassicToolLibrary;
import com.maxieds.MifareClassicToolLibrary.MifareClassicTag;
import com.maxieds.MifareClassicToolLibrary.MifareClassicUtils;
import com.maxieds.MifareClassicToolLibrary.MCTUtils;

public class SectorUIDisplay {

     private static final String TAG = SectorUIDisplay.class.getSimpleName();

     private LinearLayout mainLayoutContainer;

     public SectorUIDisplay() {
          mainLayoutContainer = new LinearLayout(MainActivity.mainActivityInstance);
          mainLayoutContainer.setOrientation(LinearLayout.VERTICAL);
          LayoutInflater sectorLayoutInflater = MainActivity.mainActivityInstance.getLayoutInflater();
          LinearLayout sectorLayout = (LinearLayout) sectorLayoutInflater.inflate(R.layout.sector_ui_display, mainLayoutContainer, false);
          mainLayoutContainer.addView(sectorLayout);
     }

     public LinearLayout GetDisplayLayout() {
          return mainLayoutContainer;
     }

     public static SectorUIDisplay NewInstance(String[] sectorBlockData, int sectorIndex, boolean sectorReadFailed,
                                               long readTimeMillis, String[] expectedDiffData) {
          SectorUIDisplay sectorDisplay = new SectorUIDisplay();
          LinearLayout sectorMainLayoutContainer = sectorDisplay.GetDisplayLayout();
          TextView tvSectorDisplayHeader = (TextView) sectorMainLayoutContainer.findViewById(R.id.sectorDisplayHeaderText);
          if(readTimeMillis == 0) {
               tvSectorDisplayHeader.setText(String.format(Locale.US, "Sector %02d", sectorIndex));
          }
          else {
               tvSectorDisplayHeader.setText(String.format(Locale.US, "Sector %02d -- Read Time % 6.3g sec", sectorIndex, readTimeMillis / 1000.0));
          }
          if(!sectorReadFailed && sectorBlockData.length > 0) {
               tvSectorDisplayHeader.append(" (READ FAILED)");
          }
          TextView tvSectorDisplayBytes = (TextView) sectorMainLayoutContainer.findViewById(R.id.sectorDisplayBytesText);
          if(sectorBlockData.length == 0) {
               tvSectorDisplayHeader.append(" (NO DATA)");
               for(int blk = 0; blk < MifareClassicUtils.MFCLASSIC1K_BLOCKS_PER_SECTOR; blk++) {
                    if(blk > 0) {
                         tvSectorDisplayBytes.append("\n");
                    }
                    tvSectorDisplayBytes.append(GetColorString(MifareClassicTag.NO_DATA, R.color.SectorBlockNoData));
               }
               return sectorDisplay;
          }
          String accessBytesStr = sectorBlockData[sectorBlockData.length - 1].substring(12, 20);
          byte[][] accessBits = MifareClassicToolLibrary.GetAccessBitsArray(MCTUtils.HexStringToBytes(accessBytesStr));
          for(int blk = 0; blk < sectorBlockData.length; blk++) {
               if(blk > 0) {
                    tvSectorDisplayBytes.append("\n");
               }
               boolean isSectorTrailer = blk + 1 == sectorBlockData.length;
               String blockIDPrefix = String.format(Locale.US, "BLK-%s%d: ", isSectorTrailer ? "T" : "D", blk);
               tvSectorDisplayBytes.append(GetColorString(blockIDPrefix, R.color.BlockTypeIDPrefixHighlight));
               if(sectorIndex == 0 && blk == 0) { // no data diffing on the manufacturer block:
                    tvSectorDisplayBytes.append(GetColorString(sectorBlockData[blk].substring(0, 12), R.color.UIDDataBlockHighlight));
                    tvSectorDisplayBytes.append(GetColorString(sectorBlockData[blk].substring(12, 16), R.color.SAKAndATQADataBlockHighlight));
                    tvSectorDisplayBytes.append(GetColorString(sectorBlockData[blk].substring(16, 32), R.color.ManufacturerDataBlockHighlight));
               }
               else if(blk + 1 < sectorBlockData.length) {
                    tvSectorDisplayBytes.append(GetMarkedByteDifferences(sectorBlockData[blk], expectedDiffData[blk], R.color.NormalTextHighlight));
               }
               else {
                    tvSectorDisplayBytes.append(GetMarkedByteDifferences(sectorBlockData[blk].substring(0, 12), expectedDiffData[blk].substring(0, 12), R.color.KeyAHighlight));
                    tvSectorDisplayBytes.append(GetMarkedByteDifferences(sectorBlockData[blk].substring(12, 20), expectedDiffData[blk].substring(12, 20), R.color.AccessBytesHighlight));
                    tvSectorDisplayBytes.append(GetMarkedByteDifferences(sectorBlockData[blk].substring(20, 32), expectedDiffData[blk].substring(20, 32), R.color.KeyBHighlight));
               }
               String accessBitsStr = String.format(Locale.US, "%d%d%d", (int) accessBits[0][blk], (int) accessBits[1][blk], (int) accessBits[2][blk]);
               tvSectorDisplayBytes.append(GetColorString("  (", R.color.AccessBitsSettingsParens));
               tvSectorDisplayBytes.append(GetColorString(accessBitsStr, R.color.AccessBitsSettings));
               tvSectorDisplayBytes.append(GetColorString(")", R.color.AccessBitsSettingsParens));
               tvSectorDisplayBytes.append("\n        ");
               String accessCondsDesc = MifareClassicToolLibrary.GetAccessConditionsDescription(accessBits, blk, isSectorTrailer);
               tvSectorDisplayBytes.append(GetColorString(accessCondsDesc, R.color.AccessBitsSettingsDesc));
          }
          return sectorDisplay;
     }

     public static SpannableString GetColorString(String data, int colorResId) {
          SpannableString ss = new SpannableString(data);
          int colorValue = MainActivity.mainActivityInstance.getResources().getColor(colorResId);
          ss.setSpan(new ForegroundColorSpan(colorValue), 0, data.length(), 0);
          return ss;
     }

     private static int MARK_DIFFS_HIGHLIGHT_COLOR = MainActivity.mainActivityInstance.getResources().getColor(R.color.TagDiffBytes);

     private static SpannableString GetMarkedByteDifferences(String actualStr, String expectedStr, int normalPrintColorResId) {
          if (actualStr.length() != expectedStr.length() || (actualStr.length() % 2) != 0) {
               return null;
          }
          int normalPrintColor = MainActivity.mainActivityInstance.getResources().getColor(normalPrintColorResId);
          SpannableString ss = new SpannableString(actualStr);
          for (int bpos = 0; bpos < actualStr.length(); bpos += 2) {
               String actualByte = actualStr.substring(bpos, bpos + 2);
               String expectedByte = expectedStr.substring(bpos, bpos + 2);
               ForegroundColorSpan nextByteColor;
               if (!actualByte.equals(expectedByte)) {
                    nextByteColor = new ForegroundColorSpan(MARK_DIFFS_HIGHLIGHT_COLOR);
               } else {
                    nextByteColor = new ForegroundColorSpan(normalPrintColor);
               }
               ss.setSpan(nextByteColor, bpos, bpos + 2, 0);
          }
          return ss;
     }

}
