package top.initsnow.edge_tts_android;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.content.pm.PackageManager;

/**
 * Narrow IPC endpoint used by the companion reader to request one upcoming
 * paragraph be synthesized while the current paragraph plays.
 */
public final class EdgeTtsPrefetchProvider extends ContentProvider {
    private static final String[] ALLOWED_CALLERS = {
            "io.legado.app.voicebeta.debug",
            "io.legado.app.voicebeta.release",
            "io.legado.app.voicebeta",
            "com.legado.app.debug",
            "com.legado.app.release"
    };
    private static final int MAX_PREFETCH_TEXT_CHARS = 6000;

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Bundle result = new Bundle();
        if (!"prefetch".equals(method) || getContext() == null) {
            result.putBoolean("accepted", false);
            return result;
        }

        int callingUid = Binder.getCallingUid();
        String[] packages = getContext().getPackageManager().getPackagesForUid(callingUid);
        boolean authorized = false;
        if (packages != null) {
            for (String packageName : packages) {
                for (String allowed : ALLOWED_CALLERS) {
                    if (allowed.equals(packageName)) {
                        authorized = true;
                        break;
                    }
                }
                if (authorized) break;
            }
        }
        if (!authorized) {
            result.putBoolean("accepted", false);
            return result;
        }

        String text = extras == null ? null : extras.getString("text");
        if (text == null || text.trim().isEmpty() || text.length() > MAX_PREFETCH_TEXT_CHARS) {
            result.putBoolean("accepted", false);
            return result;
        }

        PrefetchManager.SynthesisConfig config = PrefetchManager.getLastConfig();
        result.putBoolean("accepted", PrefetchManager.prefetch(text, config));
        return result;
    }

    @Override public boolean onCreate() { return true; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}