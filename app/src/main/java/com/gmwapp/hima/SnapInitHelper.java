package com.gmwapp.hima;

import android.content.Context;
import com.snap.appadskit.external.SAAKApplication;
import java.util.List;

/**
 * Java helper to initialize Snap App Ads Kit.
 * Calling from Java (not Kotlin) avoids a Kotlin metadata bug in appadskit 2.1.0
 * where the Companion.init() method is incorrectly annotated as returning an
 * internal type I3 instead of void, causing NoSuchMethodError at runtime.
 */
public class SnapInitHelper {
    public static void init(Context context, List<String> snapAppIds) {
        SAAKApplication.Companion.init(context, snapAppIds);
    }
}
