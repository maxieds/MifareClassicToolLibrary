package com.maxieds.ParklinkMCTLibraryDemo;

import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.LayoutInflater;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;

import java.util.Locale;

import com.maxieds.ParklinkMCTLibraryDemo.R;
import com.maxieds.MifareClassicToolLibrary.MifareClassicTag;
import com.maxieds.MifareClassicToolLibrary.MCTUtils;

public class SectorUIDisplay {

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

     public static SectorUIDisplay NewInstance(String[] sectorBlockData, int sectorIndex, boolean sectorReadFailed) {
          SectorUIDisplay sectorDisplay = new SectorUIDisplay();
          LinearLayout sectorMainLayoutContainer = sectorDisplay.GetDisplayLayout();
          TextView tvSectorDisplayHeader = (TextView) sectorMainLayoutContainer.findViewById(R.id.sectorDisplayHeaderText);
          tvSectorDisplayHeader.setText(String.format(Locale.US, "Sector %02d", sectorIndex));
          if(!sectorReadFailed) {
               tvSectorDisplayHeader.append(" -- (READ FAILED)");
          }
          TextView tvSectorDisplayBytes = (TextView) sectorMainLayoutContainer.findViewById(R.id.sectorDisplayBytesText);
          for(int blk = 0; blk < sectorBlockData.length; blk++) {
               if(blk > 0) {
                    tvSectorDisplayBytes.append("\n");
               }
               if(sectorIndex == 0 && blk == 0) {
                    tvSectorDisplayBytes.append(GetColorString(sectorBlockData[blk], R.color.UIDDataBlockHighlight));
               }
               else if(blk + 1 < sectorBlockData.length) {
                    tvSectorDisplayBytes.append(sectorBlockData[blk]);
               }
               else {
                    tvSectorDisplayBytes.append(GetColorString(sectorBlockData[blk].substring(0, 12), R.color.KeyAHighlight));
                    tvSectorDisplayBytes.append(GetColorString(sectorBlockData[blk].substring(12, 20), R.color.AccessBytesHighlight));
                    tvSectorDisplayBytes.append(GetColorString(sectorBlockData[blk].substring(20, 32), R.color.KeyBHighlight));
               }
          }
          if(sectorBlockData.length == 0) {
               tvSectorDisplayHeader.append(" -- (NO DATA)");
               for(int blk = 0; blk < MifareClassicTag.MFCLASSIC1K_BLOCKS_PER_SECTOR; blk++) {
                    if(blk > 0) {
                         tvSectorDisplayBytes.append("\n");
                    }
                    tvSectorDisplayBytes.append(GetColorString(MifareClassicTag.NO_DATA, R.color.SectorBlockNoData));
               }
          }
          return sectorDisplay;
     }

     public static SpannableString GetColorString(String data, int colorResId) {
          SpannableString ss = new SpannableString(data);
          int colorValue = MainActivity.mainActivityInstance.getResources().getColor(colorResId);
          ss.setSpan(new ForegroundColorSpan(colorValue), 0, data.length(), 0);
          return ss;
     }

}
