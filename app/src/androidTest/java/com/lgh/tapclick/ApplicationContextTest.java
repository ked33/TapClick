package com.lgh.tapclick;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ApplicationContextTest {
    @Test
    public void applicationIdMatchesManifestPackage() {
        Context context = ApplicationProvider.getApplicationContext();
        assertEquals("com.lgh.tapclick", context.getPackageName());
    }
}
