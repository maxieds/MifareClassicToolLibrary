package com.maxieds.MifareClassicToolLibrary;

import android.content.Context;
import android.nfc.Tag;
import android.nfc.TagLostException;
import android.nfc.tech.IsoDep;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.NfcA;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;
import android.util.SparseArray;

import com.maxieds.MifareClassicToolLibrary.MCTUtils.DiffTimeTimer;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static com.maxieds.MifareClassicToolLibrary.MifareClassicLibraryException.MFCLibraryExceptionType.GenericMFCException;
import static com.maxieds.MifareClassicToolLibrary.MifareClassicLibraryException.MFCLibraryExceptionType.InvalidKeysException;
import static com.maxieds.MifareClassicToolLibrary.MifareClassicLibraryException.MFCLibraryExceptionType.NFCErrorException;
import static com.maxieds.MifareClassicToolLibrary.MifareClassicLibraryException.MFCLibraryExceptionType.NoKeysFoundException;
import static com.maxieds.MifareClassicToolLibrary.MifareClassicLibraryException.MFCLibraryExceptionType.NoTagException;
import static com.maxieds.MifareClassicToolLibrary.MifareClassicLibraryException.MFCLibraryExceptionType.PartialReadException;
import static com.maxieds.MifareClassicToolLibrary.MifareClassicLibraryException.MFCLibraryExceptionType.UnsupportedTagException;

public class MifareClassicTag {

     private static final String TAG = MifareClassicTag.class.getSimpleName();

     public static final String NO_KEY = "------------";
     public static final String NO_DATA = "--------------------------------";

     public static class MFCSector {

         public int sectorAddress;
         public int sectorSize;
         public int sectorBlockCount;
         public int sectorFirstBlock;
         public int sectorBytesPerBlock;
         public String[] sectorBlockData;
         private MifareClassic m_mfcTag;
         public long timeToRead;

         public MFCSector() {
             sectorAddress = sectorSize = sectorBlockCount = sectorFirstBlock = sectorBytesPerBlock = 0;
             sectorBlockData = new String[0];
             m_mfcTag = null;
             timeToRead = 0;
         }

         public void FromTag(Tag nfcTag, int saddr) throws MifareClassicLibraryException {
             MifareClassic mfcTag = MifareClassic.get(nfcTag);
             if(mfcTag == null) {
                 throw new MifareClassicLibraryException(UnsupportedTagException);
             }
             sectorAddress = saddr;
             sectorFirstBlock = mfcTag.sectorToBlock(sectorAddress);
             sectorBlockCount = mfcTag.getBlockCountInSector(sectorAddress);
             sectorBytesPerBlock = MifareClassic.BLOCK_SIZE;
             sectorSize = sectorBlockCount * sectorBytesPerBlock;
         }

         public void AsMifareClassic1KTag(int saddr) {
              sectorAddress = saddr;
              sectorFirstBlock = sectorAddress * MifareClassicUtils.MFCLASSIC1K_BLOCKS_PER_SECTOR;
              sectorBlockCount = MifareClassicUtils.MFCLASSIC1K_BLOCKS_PER_SECTOR;
              sectorBytesPerBlock = MifareClassicUtils.MFCLASSIC_BLOCK_SIZE;
              sectorSize = sectorBlockCount * sectorBytesPerBlock;
         }

         public boolean ReadSector(Tag nfcTag, String[] trialKeysList) throws MifareClassicLibraryException {
              if(nfcTag == null) {
                   throw new MifareClassicLibraryException(NFCErrorException);
              }
              else if(trialKeysList == null) {
                   throw new MifareClassicLibraryException(InvalidKeysException);
              }
              MifareClassic mfcTag = MifareClassic.get(nfcTag);
              if(mfcTag == null) {
                   throw new MifareClassicLibraryException(NFCErrorException);
              }
              try {
                   mfcTag.setTimeout(500);
                   mfcTag.connect();
                   if(!mfcTag.isConnected()) {
                        throw new MifareClassicLibraryException(NFCErrorException);
                   }
              } catch(IOException ioe) {
                   ioe.printStackTrace();
                   throw new MifareClassicLibraryException(NFCErrorException, ioe.getMessage());
              }
              m_mfcTag = mfcTag;
              // For all entries in keys list do:
              for (int i = 0; i < trialKeysList.length; i++) {
                   String[][] results = new String[2][];
                   try {
                        results[0] = ReadSectorHelper(sectorAddress, MCTUtils.HexStringToBytes(trialKeysList[i]), false);
                        results[1] = ReadSectorHelper(sectorAddress, MCTUtils.HexStringToBytes(trialKeysList[i]), true);
                   } catch (TagLostException e) {
                        throw new MifareClassicLibraryException(PartialReadException, e.getMessage());
                   }
                   // Merge results.
                   if (results[0] != null || results[1] != null) {
                        sectorBlockData = MergeSectorData(results[0], results[1]);
                   }
              }
              try {
                   m_mfcTag.close();
              } catch(IOException ioe) {
                   ioe.printStackTrace();
              }
              return true;
         }

          public String[] ReadSectorHelper(int sectorIndex, byte[] key,
                                           boolean useAsKeyB) throws TagLostException {
               boolean auth = Authenticate(sectorIndex, key, useAsKeyB);
               String[] ret = null;
               // Read sector.
               if (auth) {
                    // Read all blocks.
                    ArrayList<String> blocks = new ArrayList<>();
                    int firstBlock = m_mfcTag.sectorToBlock(sectorIndex);
                    int lastBlock = firstBlock + 4;
                    if (m_mfcTag.getSize() == MifareClassic.SIZE_4K && sectorIndex > 31) {
                         lastBlock = firstBlock + 16;
                    }
                    for (int i = firstBlock; i < lastBlock; i++) {
                         try {
                              byte blockBytes[] = m_mfcTag.readBlock(i);
                              // mMFC.readBlock(i) must return 16 bytes or throw an error.
                              // At least this is what the documentation says.
                              // On Samsung's Galaxy S5 and Sony's Xperia Z2 however, it
                              // sometimes returns < 16 bytes for unknown reasons.
                              // Update: Aaand sometimes it returns more than 16 bytes...
                              // The appended byte(s) are 0x00.
                              if (blockBytes.length < 16) {
                                   throw new IOException();
                              }
                              if (blockBytes.length > 16) {
                                   blockBytes = Arrays.copyOf(blockBytes,16);
                              }

                              blocks.add(MCTUtils.BytesToHexString(blockBytes));
                         } catch (TagLostException e) {
                              throw e;
                         } catch (IOException e) {
                              // Could not read block.
                              // (Maybe due to key/authentication method.)
                              Log.d(TAG, "(Recoverable) Error while reading block " + i + " from tag.");
                              blocks.add(NO_DATA);
                              if (!m_mfcTag.isConnected()) {
                                   throw new TagLostException("Tag removed during ReadSectorHelper.");
                              }
                              // After an error, a re-authentication is needed.
                              Authenticate(sectorIndex, key, useAsKeyB);
                         }
                    }
                    ret = blocks.toArray(new String[blocks.size()]);
                    int last = ret.length -1;
                    // Validate if it was possible to read any data.
                    boolean noData = true;
                    for (int i = 0; i < ret.length; i++) {
                         if (!ret[i].equals(NO_DATA)) {
                              noData = false;
                              break;
                         }
                    }
                    if (noData) {
                         // Was is possible to read any data (especially with key B)?
                         // If Key B may be read in the corresponding Sector Trailer,
                         // it cannot serve for authentication (according to NXP).
                         // What they mean is that you can authenticate successfully,
                         // but can not read data. In this case the
                         // readBlock() result is 0 for each block.
                         // Also, a tag might be bricked in a way that the authentication
                         // works, but reading data does not.
                         ret = null;
                    } else {
                         // Merge key in last block (sector trailer).
                         if (!useAsKeyB) {
                              if (IsKeyBReadable(MCTUtils.HexStringToBytes(ret[last].substring(12, 20)))) {
                                   ret[last] = MCTUtils.BytesToHexString(key) + ret[last].substring(12, 32);
                              }
                              else {
                                   ret[last] = MCTUtils.BytesToHexString(key) + ret[last].substring(12, 20) + NO_KEY;
                              }
                         }
                         else {
                              ret[last] = NO_KEY + ret[last].substring(12, 20) + MCTUtils.BytesToHexString(key);
                         }
                    }
               }
               return ret;
          }

          private boolean Authenticate(int sectorIndex, byte[] key, boolean useAsKeyB) {
               // Fetch the retry authentication option. Some tags and
               // devices have strange issues and need a retry in order to work...
               // Info: https://github.com/ikarus23/MifareClassicTool/issues/134
               // and https://github.com/ikarus23/MifareClassicTool/issues/106
               boolean retryAuth = MifareClassicToolLibrary.RETRIES_TO_AUTH_KEYAB > 0;
               int retryCount = MifareClassicToolLibrary.RETRIES_TO_AUTH_KEYAB;
               boolean ret = false;
               for (int i = 0; i < retryCount+1; i++) {
                    try {
                         if (!useAsKeyB) {
                              // Key A.
                              ret = m_mfcTag.authenticateSectorWithKeyA(sectorIndex, key);
                         } else {
                              // Key B.
                              ret = m_mfcTag.authenticateSectorWithKeyB(sectorIndex, key);
                         }
                    } catch (IOException e) {
                         Log.d(TAG, "Error authenticating with tag.");
                         return false;
                    }
                    if (ret || !retryAuth) { // Retry?
                         break;
                    }
               }
               return ret;
          }

          private boolean IsKeyBReadable(byte[] ac) {
               byte c1 = (byte) ((ac[1] & 0x80) >>> 7);
               byte c2 = (byte) ((ac[2] & 0x08) >>> 3);
               byte c3 = (byte) ((ac[2] & 0x80) >>> 7);
               return c1 == 0
                    && (c2 == 0 && c3 == 0)
                    || (c2 == 1 && c3 == 0)
                    || (c2 == 0 && c3 == 1);
          }

          private String[] MergeSectorData(String[] firstResult, String[] secondResult) {
               String[] ret = null;
               if (firstResult != null || secondResult != null) {
                    if ((firstResult != null && secondResult != null)
                         && firstResult.length != secondResult.length) {
                         return null;
                    }
                    int length  = (firstResult != null) ? firstResult.length : secondResult.length;
                    ArrayList<String> blocks = new ArrayList<>();
                    // Merge data blocks.
                    for (int i = 0; i < length -1 ; i++) {
                         if (firstResult != null && firstResult[i] != null
                              && !firstResult[i].equals(NO_DATA)) {
                              blocks.add(firstResult[i]);
                         }
                         else if (secondResult != null && secondResult[i] != null
                              && !secondResult[i].equals(NO_DATA)) {
                              blocks.add(secondResult[i]);
                         }
                         else {
                              // None of the results got the data form the block.
                              blocks.add(NO_DATA);
                         }
                    }
                    ret = blocks.toArray(new String[blocks.size() + 1]);
                    int last = length - 1;
                    // Merge sector trailer.
                    if (firstResult != null && firstResult[last] != null
                         && !firstResult[last].equals(NO_DATA)) {
                         // Take first for sector trailer.
                         ret[last] = firstResult[last];
                         if (secondResult != null && secondResult[last] != null
                              && !secondResult[last].equals(NO_DATA)) {
                              // Merge key form second result to sector trailer.
                              ret[last] = ret[last].substring(0, 20) + secondResult[last].substring(20);
                         }
                    }
                    else if (secondResult != null && secondResult[last] != null
                         && !secondResult[last].equals(NO_DATA)) {
                         // No first result. Take second result as sector trailer.
                         ret[last] = secondResult[last];
                    }
                    else {
                         // No sector trailer at all.
                         ret[last] = NO_DATA;
                    }
               }
               return ret;
          }

          public boolean ContainsInvalidBlockData() {
              if(sectorBlockData == null) {
                   return false;
              }
              for(int blk = 0; blk < sectorBlockData.length; blk++) {
                   if(sectorBlockData[blk] != null && sectorBlockData[blk].contains("-")) {
                        return true;
                   }
                   else if(sectorBlockData[blk] == null) {
                        return false;
                   }
              }
              return false;
          }

     }

     /**
      * Patch a possibly broken Tag object of HTC One (m7/m8) or Sony
      * Xperia Z3 devices (with Android 5.x.)
      *
      * HTC One: "It seems, the reason of this bug is TechExtras of NfcA is null.
      * However, TechList contains MifareClassic." -- bildin.
      * This method will fix this. For more information please refer to
      * https://github.com/ikarus23/MifareClassicTool/issues/52
      * This patch was provided by bildin (https://github.com/bildin).
      *
      * Sony Xperia Z3 (+ emmulated MIFARE Classic tag): The buggy tag has
      * two NfcA in the TechList with different SAK values and a MifareClassic
      * (with the Extra of the second NfcA). Both, the second NfcA and the
      * MifareClassic technique, have a SAK of 0x20. According to NXP's
      * guidelines on identifying MIFARE tags (Page 11), this a MIFARE Plus or
      * MIFARE DESFire tag. This method creates a new Extra with the SAK
      * values of both NfcA occurrences ORed (as mentioned in NXP's
      * MIFARE type identification procedure guide) and replace the Extra of
      * the first NfcA with the new one. For more information please refer to
      * https://github.com/ikarus23/MifareClassicTool/issues/64
      * This patch was provided by bildin (https://github.com/bildin).
      *
      * @param tag The possibly broken tag.
      * @return The fixed tag.
      */
     public static Tag patchTag(Tag tag) {
          if (tag == null) {
               return null;
          }
          String[] techList = tag.getTechList();
          Parcel oldParcel = Parcel.obtain();
          tag.writeToParcel(oldParcel, 0);
          oldParcel.setDataPosition(0);
          int len = oldParcel.readInt();
          byte[] id = new byte[0];
          if (len >= 0) {
               id = new byte[len];
               oldParcel.readByteArray(id);
          }
          int[] oldTechList = new int[oldParcel.readInt()];
          oldParcel.readIntArray(oldTechList);
          Bundle[] oldTechExtras = oldParcel.createTypedArray(Bundle.CREATOR);
          int serviceHandle = oldParcel.readInt();
          int isMock = oldParcel.readInt();
          IBinder tagService;
          if (isMock == 0) {
               tagService = oldParcel.readStrongBinder();
          } else {
               tagService = null;
          }
          oldParcel.recycle();
          int nfcaIdx = -1;
          int mcIdx = -1;
          short sak = 0;
          boolean isFirstSak = true;
          for (int i = 0; i < techList.length; i++) {
               if (techList[i].equals(NfcA.class.getName())) {
                    if (nfcaIdx == -1) {
                         nfcaIdx = i;
                    }
                    if (oldTechExtras[i] != null && oldTechExtras[i].containsKey("sak")) {
                         sak = (short) (sak | oldTechExtras[i].getShort("sak"));
                         isFirstSak = nfcaIdx == i;
                    }
               } else if (techList[i].equals(MifareClassic.class.getName())) {
                    mcIdx = i;
               }
          }
          boolean modified = false;
          // Patch the double NfcA issue (with different SAK) for
          // Sony Z3 devices.
          if (!isFirstSak) {
               oldTechExtras[nfcaIdx].putShort("sak", sak);
               modified = true;
          }
          // Patch the wrong index issue for HTC One devices.
          if (nfcaIdx != -1 && mcIdx != -1 && oldTechExtras[mcIdx] == null) {
               oldTechExtras[mcIdx] = oldTechExtras[nfcaIdx];
               modified = true;
          }
          if (!modified) {
               // Old tag was not modivied. Return the old one.
               return tag;
          }
          // Old tag was modified. Create a new tag with the new data.
          Parcel newParcel = Parcel.obtain();
          newParcel.writeInt(id.length);
          newParcel.writeByteArray(id);
          newParcel.writeInt(oldTechList.length);
          newParcel.writeIntArray(oldTechList);
          newParcel.writeTypedArray(oldTechExtras, 0);
          newParcel.writeInt(serviceHandle);
          newParcel.writeInt(isMock);
          if (isMock == 0) {
               newParcel.writeStrongBinder(tagService);
          }
          newParcel.setDataPosition(0);
          Tag newTag = Tag.CREATOR.createFromParcel(newParcel);
          newParcel.recycle();
          return newTag;
     }

     private String mfcTagType;
     private int tagSize, tagSectorCount, tagBlockCount, tagBytesPerBlock;
     private String[] mfcDumpImageData;
     private List<MFCSector> failedSectors, tagSectors;
     private String rfTechCaps, tagManufacturer;
     private String tagUID, tagATQA, tagSAK, tagATS;
     private long totalTimeToRead;

     // MCT accounting variables:
     private SparseArray<byte[][]> mKeyMap;
     private int mKeyMapStatus;
     private int mLastSector;
     private int mFirstSector;
     private ArrayList<byte[]> mKeysWithOrder;

     private boolean ResetParameters() {
         mfcTagType = "";
         tagSize = tagSectorCount = tagBlockCount = tagBytesPerBlock = 0;
         mfcDumpImageData = null;
         failedSectors = new ArrayList<MFCSector>();
         tagSectors = new ArrayList<MFCSector>();
         rfTechCaps = tagManufacturer = "";
         tagUID = tagATQA = tagSAK = tagATS = "";
         totalTimeToRead = 0;
         mKeyMap = new SparseArray<>();
         mKeyMapStatus = 0;
         mLastSector = -1;
         mFirstSector = 0;
         mKeysWithOrder = new ArrayList();
         return true;
     }

     private MifareClassicTag() {
         ResetParameters();
     }

     public static final int MFC_FULL_SUPPORT = 0;
     public static final int NO_MFC_DEVICE_SUPPORT = -1;
     public static final int NO_MFC_TAG_SUPPORT = -2;
     public static final int MFC_TAG_ERROR = -3;

     public static int CheckMifareClassicSupport(Tag nfcTag, Context appContext) {
         if(nfcTag == null || appContext == null) {
             return MFC_TAG_ERROR;
         }
         else if(Arrays.asList(nfcTag.getTechList()).contains(MifareClassic.class.getName())) {
             // Device and tag support MIFARE Classic.
             return MFC_FULL_SUPPORT;
         }
         else {
             // Check if device does not support MIFARE Classic.
             // For doing so, check if the SAK of the tag indicate that
             // it's a MIFARE Classic tag.
             // See: https://www.nxp.com/docs/en/application-note/AN10834.pdf
             NfcA nfca = NfcA.get(nfcTag);
             byte sak = (byte) nfca.getSak();
             if ((sak >> 1 & 1) == 1) { // RFU.
                 return NO_MFC_TAG_SUPPORT;
             } else {
                 if ((sak >> 3 & 1) == 1) { // SAK bit 4 = 1?
                     if ((sak >> 4 & 1) == 1) { // SAK bit 5 = 1?
                         // MIFARE Classic 4k
                         // MIFARE SmartMX 4K
                         // MIFARE PlusS 4K SL1
                         // MIFARE PlusX 4K SL1
                         return NO_MFC_DEVICE_SUPPORT;
                     } else {
                         if ((sak & 1) == 1) { // SAK bit 1 = 1?
                             // MIFARE Mini
                             return NO_MFC_DEVICE_SUPPORT;
                         } else {
                             // MIFARE Classic 1k
                             // MIFARE SmartMX 1k
                             // MIFARE PlusS 2K SL1
                             // MIFARE PlusX 2K SL2
                             return NO_MFC_DEVICE_SUPPORT;
                         }
                     }
                 } else {
                     // Some MIFARE tag, but not Classic or Classic compatible.
                     return NO_MFC_TAG_SUPPORT;
                 }
             }
         }
     }

     public static MifareClassicTag Decode(Tag nfcTag, String[] keyData, boolean displayGUIProgressBar) throws MifareClassicLibraryException {
         if(nfcTag == null) {
             throw new MifareClassicLibraryException(NoTagException);
         }
         else if(keyData == null) {
             throw new MifareClassicLibraryException(NoKeysFoundException);
         }
         else if(!CheckMFCKeys(keyData)) {
             throw new MifareClassicLibraryException(InvalidKeysException);
         }
         else if(!MifareClassicToolLibrary.Initialized()) {
             throw new MifareClassicLibraryException(GenericMFCException, "Uninitialized NFC tag data");
         }
         nfcTag = patchTag(nfcTag);
         int mfcSupportCode = 0;
         if((mfcSupportCode = CheckMifareClassicSupport(nfcTag, MifareClassicToolLibrary.GetApplicationContext())) != 0) {
             throw new MifareClassicLibraryException(UnsupportedTagException);
         }
         MifareClassicTag mfcTagData = new MifareClassicTag();
         if(!mfcTagData.ReadTagReservedData(nfcTag) ||
               !mfcTagData.IdentifyTag(nfcTag, mfcSupportCode) ||
               !mfcTagData.DumpTag(nfcTag, keyData, displayGUIProgressBar)) {
              return null;
         }
         return mfcTagData;
     }

     public static MifareClassicTag Decode(Tag nfcTag, boolean displayGUIProgressBar) throws MifareClassicLibraryException {
         String[] defaultKeyData = new String[] {
                 "000000000000",
                 MCTUtils.BytesToHexString(MifareClassic.KEY_DEFAULT),
                 MCTUtils.BytesToHexString(MifareClassic.KEY_MIFARE_APPLICATION_DIRECTORY),
                 MCTUtils.BytesToHexString(MifareClassic.KEY_NFC_FORUM)
         };
         return MifareClassicTag.Decode(nfcTag, defaultKeyData, displayGUIProgressBar);
     }

     public boolean ReadTagReservedData(Tag nfcTag) throws MifareClassicLibraryException {
         tagUID = MCTUtils.BytesToHexString(nfcTag.getId());
         NfcA nfcaTag = NfcA.get(nfcTag);
         // Swap ATQA to match the common order like shown here:
         // http://nfc-tools.org/index.php?title=ISO14443A
         byte[] atqaBytes = nfcaTag.getAtqa();
         atqaBytes = new byte[] {atqaBytes[1], atqaBytes[0]};
         tagATQA = MCTUtils.BytesToHexString(atqaBytes);
         // SAK in big endian.
         byte[] sakBytes = new byte[] {
                 (byte) ((nfcaTag.getSak() >> 8) & 0xFF),
                 (byte) (nfcaTag.getSak() & 0xFF)
         };
         // Print the first SAK byte only if it is not 0.
         if (sakBytes[0] != 0x00) {
             tagSAK = MCTUtils.BytesToHexString(sakBytes);
         }
         else {
             tagSAK = MCTUtils.BytesToHexString(new byte[] { sakBytes[1] });
         }
         tagATS = "-";
         IsoDep isoTag = IsoDep.get(nfcTag);
         if (isoTag != null) {
             byte[] atsBytes = isoTag.getHistoricalBytes();
             if (atsBytes != null && atsBytes.length > 0) {
                 tagATS = MCTUtils.BytesToHexString(atsBytes);
             }
         }
         return true;
     }

     public boolean IdentifyTag(Tag nfcTag, int mfcSupportCode) throws MifareClassicLibraryException {
         // Identify tag type (by string).
         boolean successCode = true;
         int tagTypeResourceID = GetTagIdentifier(tagATQA, tagSAK, tagATS);
         if (tagTypeResourceID == R.string.tag_unknown && mfcSupportCode > NO_MFC_TAG_SUPPORT) {
             mfcTagType = MifareClassicToolLibrary.GetApplicationContext().getString(R.string.tag_unknown_mf_classic);
             successCode = false;
         }
         else {
             mfcTagType = MifareClassicToolLibrary.GetApplicationContext().getString(tagTypeResourceID);
         }
         // read in the logistical layout data (e.g., number of sectors, tag size, etc.):
         if(mfcSupportCode != 0) {
             successCode = false;
         }
         else {
             MifareClassic mfcTag = MifareClassic.get(nfcTag);
             tagSize = mfcTag.getSize();
             tagSectorCount = mfcTag.getSectorCount();
             tagBlockCount = mfcTag.getBlockCount();
             tagBytesPerBlock = MifareClassic.BLOCK_SIZE;
         }
         return successCode;
     }

     public boolean DumpTag(Tag nfcTag, String[] keyData, boolean displayGUIProgressBar) throws MifareClassicLibraryException {
          if(nfcTag == null) {
               throw new MifareClassicLibraryException(NFCErrorException);
          }
          else if(keyData == null) {
               throw new MifareClassicLibraryException(InvalidKeysException);
          }
          DiffTimeTimer tagReadTimer = new DiffTimeTimer();
          DiffTimeTimer sectorReadTimer = new DiffTimeTimer();
          tagReadTimer.startTimer();
          mfcDumpImageData = new String[tagBlockCount];
          int blockIndex = 0, sct = 0;
          while(sct < tagSectorCount) {
               if(displayGUIProgressBar) {
                    MifareClassicToolLibrary.DisplayProgressBar("SECTOR", sct, tagSectorCount);
               }
               sectorReadTimer.startTimer();
               MFCSector nextSector = new MFCSector();
               nextSector.AsMifareClassic1KTag(sct);
               if(!nextSector.ReadSector(nfcTag, keyData) || nextSector.ContainsInvalidBlockData()) {
                    failedSectors.add(nextSector);
               }
               tagSectors.add(nextSector);
               sct++;
               for(int blk = 0; blk < nextSector.sectorBlockData.length; blk++, blockIndex++) {
                    mfcDumpImageData[blockIndex] = nextSector.sectorBlockData[blk];
               }
               nextSector.timeToRead = sectorReadTimer.diffTimer();
          }
          totalTimeToRead = tagReadTimer.diffTimer();
          return true;
     }

    private int GetTagIdentifier(String atqa, String sak, String ats) {
        String prefix = "tag_";
        ats = ats.replace("-", "");
        // First check on ATQA + SAK + ATS.
        int ret = MifareClassicToolLibrary.GetApplicationContext().getResources().getIdentifier(
                prefix + atqa + sak + ats, "string", MifareClassicToolLibrary.GetApplicationContext().getPackageName());
        if (ret == 0) {
            // Check on ATQA + SAK.
            ret = MifareClassicToolLibrary.GetApplicationContext().getResources().getIdentifier(
                    prefix + atqa + sak, "string", MifareClassicToolLibrary.GetApplicationContext().getPackageName());
        }
        if (ret == 0) {
            // Check on ATQA.
            ret = MifareClassicToolLibrary.GetApplicationContext().getResources().getIdentifier(
                    prefix + atqa, "string", MifareClassicToolLibrary.GetApplicationContext().getPackageName());
        }
        if (ret == 0) {
            // No match found return "Unknown".
            return R.string.tag_unknown;
        }
        return ret;
    }

     public static boolean CheckMFCKeys(String[] keyDataList) {
         if(keyDataList == null) {
             return false;
         }
         for(int kidx = 0; kidx < keyDataList.length; kidx++) {
             if(!MCTUtils.IsHexAnd6Byte(keyDataList[kidx])) {
                 return false;
             }
         }
         return true;
     }

     public String[] GetMFCDumpImageData() {
         return mfcDumpImageData;
     }

     public String GetTagType() {
         return mfcTagType;
     }

     public int GetTagSectors() {
         return tagSectorCount;
     }

     public int GetTagBlocks() {
         return tagBlockCount;
     }

     public int GetTagBytesPerBlock() {
         return tagBytesPerBlock;
     }

     public int GetTagSize() {
         return tagSize;
     }

     public List<MFCSector> GetFailedSectors() {
         return failedSectors;
     }

     public String GetRFTechCaps() {
         return rfTechCaps;
     }

     public String GetManufacturer() {
         return tagManufacturer;
     }

     public String GetTagUID() {
         return tagUID;
     }

     public String GetTagUID(String byteSep) {
          return tagUID.substring(0, 2) + tagUID.substring(2).replaceAll("(.{2})", byteSep + "$1");
     }

     public int GetTagUIDSize() {
         return tagUID.length() / 2;
     }

     public String GetATQA() {
         return tagATQA;
     }

     public String GetSAK() {
         return tagSAK;
     }

     public String GetATS() {
         return tagATS;
     }

     public long GetTotalReadTime() { return totalTimeToRead; }

     public static String GetTagByteCountString(int byteCount) {
          if(byteCount == 1024) {
               return "1K";
          }
          else if(byteCount == 2048) {
               return "2K";
          }
          else if(byteCount == 4096) {
               return "4K";
          }
          else if(byteCount == 8192) {
               return "8K";
          }
          return String.valueOf(byteCount) + "B";
     }

     public String GetTagSizeSpecString() {
          String specString = String.format(Locale.US, "%s | %d Sectors x %d Blocks @ %sB",
                                            GetTagByteCountString(GetTagSize()), tagSectorCount,
                                            tagBlockCount / tagSectorCount, tagBytesPerBlock);
          return specString;
     }

     public boolean ExportToHexFile(String outputFile) throws IOException {
          if(outputFile == null || mfcDumpImageData == null) {
               return false;
          }
          PrintWriter printWriter = new PrintWriter(outputFile, "UTF-8");
          for(int blk = 0; blk < tagBlockCount; blk += tagBytesPerBlock) {
               String blkDataStr = mfcDumpImageData[blk].replace("-", "0");
               printWriter.print(blkDataStr);
          }
          printWriter.close();
          return true;
     }

     public boolean ExportToBinaryDumpFile(String outputFile) throws IOException {
          if(outputFile == null || mfcDumpImageData == null) {
               return false;
          }
          FileOutputStream outStream = new FileOutputStream(outputFile);
          for(int blk = 0; blk < mfcDumpImageData.length; blk++) {
               String blkDataStr = mfcDumpImageData[blk].replace("-", "0");
               outStream.write(MCTUtils.HexStringToBytes(blkDataStr));
          }
          outStream.close();
          return true;
     }

     public MFCSector GetSectorByIndex(int index) {
          if(index < 0 || index >= tagSectors.size()) {
               return null;
          }
          return tagSectors.get(index);
     }

     public boolean GetSectorReadStatus(int index) {
          if(index < 0) {
               return false;
          }
          for(int fsec = 0; fsec < failedSectors.size(); fsec++) {
               if(failedSectors.get(fsec).sectorAddress == index) {
                    return false;
               }
          }
          return true;
     }

     public static MifareClassicTag LoadMifareClassic1KFromResource(int resID) {

          if(!MifareClassicToolLibrary.Initialized()) {
               Log.e(TAG, "ERROR: MifareClassicToolLibrary NOT initialized!");
               return null;
          }

          // initialize statically "known" fields for a MFC1K tag:
          MifareClassicTag mfcTagData = new MifareClassicTag();
          mfcTagData.mfcTagType = "Mifare Classic 1K (From Dump Image)";
          mfcTagData.tagSize = MifareClassicUtils.MFCLASSIC1K_TAG_SIZE;
          mfcTagData.tagSectorCount = 16;
          mfcTagData.tagBytesPerBlock = MifareClassicUtils.MFCLASSIC_BLOCK_SIZE;
          mfcTagData.tagBlockCount = mfcTagData.tagSize / mfcTagData.tagBytesPerBlock;
          mfcTagData.tagManufacturer = "Unknown";

          // initialize the tag data bytes from the dump image resource:
          android.content.Context appMainContext = MifareClassicToolLibrary.GetApplicationContext();
          try {
               java.io.InputStream rawFileStream = appMainContext.getResources().openRawResource(resID);
               mfcTagData.mfcDumpImageData = new String[mfcTagData.tagBlockCount];
               int bytesReadCount = 0, blkIndex = 0;
               byte[] byteReadBuffer = new byte[MifareClassicUtils.MFCLASSIC_BLOCK_SIZE];
               while (bytesReadCount < MifareClassicUtils.MFCLASSIC1K_TAG_SIZE) {
                    int readByteCount = rawFileStream.read(byteReadBuffer, 0, MifareClassicUtils.MFCLASSIC_BLOCK_SIZE);
                    if (readByteCount < 0) {
                         break;
                    }
                    mfcTagData.mfcDumpImageData[blkIndex] = MCTUtils.BytesToHexString(byteReadBuffer);
                    bytesReadCount += readByteCount;
                    blkIndex++;
               }
               if(bytesReadCount < MifareClassicUtils.MFCLASSIC1K_TAG_SIZE) {
                    Log.e(TAG, "ERROR: Only able to load " + bytesReadCount + " of " + MifareClassicUtils.MFCLASSIC1K_TAG_SIZE + "bytes from tag!");
                    return null;
               }
          } catch(IOException ioe) {
               ioe.printStackTrace();
               return null;
          }
          // setup the individual sector data:
          for(int sec = 0; sec < mfcTagData.tagSectorCount; sec++) {
               com.maxieds.MifareClassicToolLibrary.MifareClassicTag.MFCSector nextSector = new com.maxieds.MifareClassicToolLibrary.MifareClassicTag.MFCSector();
               nextSector.sectorAddress = sec;
               nextSector.sectorSize = mfcTagData.tagBlockCount * mfcTagData.tagBytesPerBlock;
               nextSector.sectorBlockCount = mfcTagData.tagBlockCount;
               nextSector.sectorFirstBlock = sec * MifareClassicUtils.MFCLASSIC1K_BLOCKS_PER_SECTOR;
               nextSector.sectorBytesPerBlock = mfcTagData.tagBytesPerBlock;
               nextSector.sectorBlockData = new String[MifareClassicUtils.MFCLASSIC1K_BLOCKS_PER_SECTOR];
               for(int blk = 0; blk < MifareClassicUtils.MFCLASSIC1K_BLOCKS_PER_SECTOR; blk++) {
                    nextSector.sectorBlockData[blk] = mfcTagData.mfcDumpImageData[sec * MifareClassicUtils.MFCLASSIC1K_BLOCKS_PER_SECTOR + blk];
               }
               mfcTagData.tagSectors.add(nextSector);
          }
          // load the rest of the first block (tag read-only) sector data for accounting:
          byte[] manuBlockBytes = MCTUtils.HexStringToBytes(mfcTagData.mfcDumpImageData[0]);
          byte[] uidBytes = new byte[4];
          System.arraycopy(manuBlockBytes, 0, uidBytes, 0, 4);
          mfcTagData.tagUID = MCTUtils.BytesToHexString(uidBytes);
          byte sakByte = manuBlockBytes[5];
          mfcTagData.tagSAK = MCTUtils.BytesToHexString(new byte[] { sakByte });
          byte[] atqaBytes = new byte[2];
          System.arraycopy(manuBlockBytes, 6, atqaBytes, 0, 2);
          mfcTagData.tagATQA = MCTUtils.BytesToHexString(atqaBytes);
          mfcTagData.tagATS = "Unknown ATS";
          mfcTagData.tagManufacturer = mfcTagData.mfcDumpImageData[1];
          return mfcTagData;

     }

}