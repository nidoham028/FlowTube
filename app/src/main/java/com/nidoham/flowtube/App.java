package com.nidoham.flowtube;

import android.app.Application;
import com.nidoham.flowtube.core.language.AppLanguage;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        initializeAppLanguage();
    }

    private void initializeAppLanguage() {
        AppLanguage.getInstance(this).initialize();
    }
}