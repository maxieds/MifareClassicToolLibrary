package com.maxieds.MifareClassicToolLibrary;

import android.content.Intent;
import android.content.Context;

public interface MifareClassicDataInterface {

     void RegisterNewIntent(Intent mfcIntent);
     Context GetApplicationContext();

}