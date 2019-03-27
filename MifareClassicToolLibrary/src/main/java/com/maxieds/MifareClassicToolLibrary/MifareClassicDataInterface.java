package com.maxieds.MifareClassicToolLibrary;

import android.content.Intent;
import android.content.Context;
import android.app.Activity;

public interface MifareClassicDataInterface {

     void RegisterNewIntent(Intent mfcIntent);
     Context GetApplicationContext();
     Activity GetApplicationActivity();

}