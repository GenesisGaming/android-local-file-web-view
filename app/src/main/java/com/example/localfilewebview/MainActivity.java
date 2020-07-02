package com.example.localfilewebview;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.net.http.SslError;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

import static android.Manifest.permission.INTERNET;
import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.view.KeyEvent.KEYCODE_DPAD_DOWN;
import static android.view.KeyEvent.KEYCODE_DPAD_LEFT;
import static android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
import static android.view.KeyEvent.KEYCODE_DPAD_UP;
import static android.view.KeyEvent.KEYCODE_ENTER;
import static android.widget.Toast.LENGTH_LONG;

public class MainActivity extends AppCompatActivity {

    enum JsKeyCode {
        UP(38), DOWN(40), LEFT(37), RIGHT(39);

        private int code;

        JsKeyCode(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }
    }

    private static final String TAG = "test";

    private static final String DEFAULT_PATH = "file:///android_asset/hello-pwa/index.html";

    private static final HashMap<String, Integer> PERMISSION_CHECK_MAP = new HashMap<>();

    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            PERMISSION_CHECK_MAP.put(READ_EXTERNAL_STORAGE, 100);
        }
        PERMISSION_CHECK_MAP.put(WRITE_EXTERNAL_STORAGE, 101);
        PERMISSION_CHECK_MAP.put(INTERNET, 200);
    }

    private AtomicInteger permissionGranted = new AtomicInteger();

    private WebView gameView;

    protected EditText urlView;

    private float downXValue;
    private float downYValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_home);

        urlView = findViewById(R.id.url_input);
        initUrlView();

        File[] extDirs = ContextCompat.getExternalFilesDirs(this, null);
        if (extDirs.length > 1) {
            File extSdFileDir = ContextCompat.getExternalFilesDirs(this, null)[1];
            while (extSdFileDir.getParentFile() != null && !extSdFileDir.getName().equals("Android")) {
                extSdFileDir = extSdFileDir.getParentFile();
            }
            File extSdRoot = extSdFileDir.getParentFile();

            //findViewById(R.id.pick).setOnClickListener(view -> launchFilePicker());
            findViewById(R.id.sdcard_dahongbaogold).setOnClickListener(view -> {
                urlView.setText("file://" + extSdRoot.getAbsolutePath() + "/genesis-gaming/dahongbaogold/index.html");
                gameView.loadUrl(urlView.getText().toString());
            });
        }

        findViewById(R.id.asset_dahongbaogold).setOnClickListener(view -> {
            urlView.setText(DEFAULT_PATH);
            load();
        });

        gameView = findViewById(R.id.game_view);
        gameView.clearCache(true);
        gameView.setWebChromeClient(new WebChromeClient());

        WebSettings settings = gameView.getSettings();
        settings.setJavaScriptEnabled(true);
        //settings.setBuiltInZoomControls(false);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            settings.setAllowFileAccessFromFileURLs(true);
            settings.setAllowUniversalAccessFromFileURLs(true);
        }

        gameView.setWebViewClient(newCustomWebViewClient());
        gameView.setOnTouchListener(newCustomOnTouchListener());

        for (String key : PERMISSION_CHECK_MAP.keySet()) {
            checkPermissionGranted(key);
        }

        if (permissionGranted.get() == PERMISSION_CHECK_MAP.size()) {
            urlView.setText(DEFAULT_PATH);
            load();
        }

        gameView.requestFocus();
    }

    private void initUrlView() {
        urlView.setOnEditorActionListener((view, actionId, keyEvent) -> {
            if (keyEvent == null || KEYCODE_ENTER == keyEvent.getKeyCode()) {
                InputMethodManager imm = (InputMethodManager) view.getContext()
                        .getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                load();
            }
            return true;
        });
    }

    private View.OnTouchListener newCustomOnTouchListener() {
        return (view, motionEvent) -> {
            if (view.getId() != R.id.game_view) {
                return false; // ignore
            }

            JsKeyCode keyCode = toJsKeyCode(motionEvent);
            if (keyCode == null) {
                return false; // ignored
            }

            // customized for snake game
            switch (keyCode) {
                case UP:
                    dispatchKeyBoardEvent(KEYCODE_DPAD_UP);
                    break;
                case DOWN:
                    dispatchKeyBoardEvent(KEYCODE_DPAD_DOWN);
                    break;
                case LEFT:
                    dispatchKeyBoardEvent(KEYCODE_DPAD_LEFT);
                    break;
                case RIGHT:
                    dispatchKeyBoardEvent(KEYCODE_DPAD_RIGHT);
                    break;
            }

            // customized for snake game but javascript:document.dispatchEvent will not work
            String script = buildJs(keyCode);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                Log.i(TAG, script);
                gameView.evaluateJavascript(script, null);
            } else {
                gameView.loadUrl(script);
            }

            return false;
        };
    }

    private WebViewClient newCustomWebViewClient() {
        return new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                Log.i(TAG, "Page loaded");
            }

            @Override
            @TargetApi(21)
            public void onReceivedError(WebView view, int errorCode, String description,
                                        String failingUrl) {
                Log.e(TAG, "Error: (" + errorCode + ")" + description + " (" + failingUrl + ")");
            }

            @Override
            @TargetApi(23)
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                Log.e(TAG, "Error: (" + error.getErrorCode() + ")" + error.getDescription());
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                Log.e(TAG, "SSL Error: (" + error.getUrl() + ")");
            }
        };
    }

    private void dispatchKeyBoardEvent(int keyCode) {
        gameView.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
    }

    private JsKeyCode toJsKeyCode(MotionEvent e) {
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                // store the X value when the user's finger was pressed down
                downXValue = e.getX();
                downYValue = e.getY();
                break;
            }
            case MotionEvent.ACTION_UP: {
                // Get the X value when the user released his/her finger
                float currentX = e.getX();
                float currentY = e.getY();
                // check if horizontal or vertical movement was bigger

                if (Math.abs(downXValue - currentX) > Math.abs(downYValue - currentY)) {
                    // going backwards: pushing stuff to the right
                    if (downXValue < currentX) {
                        return JsKeyCode.RIGHT;

                    }

                    // going forwards: pushing stuff to the left
                    if (downXValue > currentX) {
                        return JsKeyCode.LEFT;
                    }
                } else {
                    if (downYValue < currentY) {
                        return JsKeyCode.DOWN;
                    }
                    if (downYValue > currentY) {
                        return JsKeyCode.UP;
                    }
                }
                break;
            }

        }

        return null;
    }

    private String buildJs(JsKeyCode keyCode) {
        return "javascript:document.dispatchEvent(new KeyboardEvent(" +
                "'keydown', {'keyCode':" +
                keyCode.getCode() +
                ", 'which':" +
                keyCode.getCode() +
                "}));";
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        Log.i(TAG, "onRequestPermissionsResult: " + requestCode + ", result=" + Arrays.toString(grantResults));
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            permissionGranted.incrementAndGet();
            if (permissionGranted.get() == PERMISSION_CHECK_MAP.size()) {
                load();
            }
        }
    }

    private boolean checkPermissionGranted(String permissionName) {
        int permission = ContextCompat.checkSelfPermission(this, permissionName);
        if (permission == PackageManager.PERMISSION_GRANTED) {
            permissionGranted.incrementAndGet();
            return true; // permission has been granted
        }

        ActivityCompat.requestPermissions(
                this,
                new String[]{permissionName},
                PERMISSION_CHECK_MAP.get(permissionName));
        return false;
    }

    protected void load() {
        //String file = "file://" + Environment.getExternalStorageDirectory().getPath() + "/local_web_client/test/index.html";
        //file = "file:///mnt/sdcard/local_web_client/test/index.html";
        //file = "content://com.android.externalstorage.documents/document/primary%3Alocal_web_client%2Ftest%2Findex.html";
        //file = "https://www.google.com";
        //Log.i(TAG, file);
        //gameView.loadUrl(file);
        if (urlView.getText().length() == 0) {
            urlView.setText(DEFAULT_PATH);
        }

        loadPage();
    }

    private void loadPage() {
        String url = urlView.getText().toString();
        if (url.contains("session")) {
            gameView.loadUrl(url);
            return;
        }

        new GetRequest(this, (response) -> {
            try {
                if (response == null) {
                    gameView.loadUrl(url);
                    return null;
                }

                urlView.setText(urlView.getText().toString() +
                        "?partner=8c31b93c-24bd-4dfa-aa16-db96c0296b3a" +
                        "&mode=real&turbo=true&session=" + Objects.requireNonNull(
                        new ObjectMapper().readValue(response, HashMap.class)
                                .get("session_token")).toString());

                load();
            } catch (IOException e) {
                Log.e(TAG, e.getMessage(), e);
            }
            return null;
        })
                .execute("https://krug-bo.star9ad.com/m4/wallet/balance/harvey-test");
        //.execute("https://www.google.com");
    }

    public void launchFilePicker() {
        Intent mRequestFileIntent = new Intent(Intent.ACTION_GET_CONTENT);
        mRequestFileIntent.setType("*/*");
        startActivityForResult(mRequestFileIntent, 0);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent intent) {
        // If the selection didn't work
        if (resultCode != RESULT_OK) {
            // Exit without doing anything else
            return;
        }

        ParcelFileDescriptor mInputPFD;
        // Get the file's content URI from the incoming Intent
        Uri returnUri = intent.getData();

        try {
            urlView.setText(getPath(this, returnUri));
            loadPage();
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    public static String getPath(final Context context, final Uri uri) {

        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }

                // TODO handle non-primary volumes
            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {

                final String id = DocumentsContract.getDocumentId(uri);
                if (id.startsWith("raw:")) {
                    return id.substring(id.indexOf(":") + 1);
                } else {
                    final Uri contentUri = ContentUris.withAppendedId(
                            Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

                    return getDataColumn(context, contentUri, null, null);
                }
            }
            // MediaProvider
            else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[] {
                        split[1]
                };

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {

            // Return the remote address
            if (isGooglePhotosUri(uri))
                return uri.getLastPathSegment();

            return getDataColumn(context, uri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        return null;
    }

    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * @param context The context.
     * @param uri The Uri to query.
     * @param selection (Optional) Filter used in the query.
     * @param selectionArgs (Optional) Selection arguments used in the query.
     * @return The value of the _data column, which is typically a file path.
     */
    public static String getDataColumn(Context context, Uri uri, String selection,
                                       String[] selectionArgs) {

        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {
                column
        };

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                final int index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }


    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is Google Photos.
     */
    public static boolean isGooglePhotosUri(Uri uri) {
        return "com.google.android.apps.photos.content".equals(uri.getAuthority());
    }
}

class GetRequest extends AsyncTask<String, Integer, String> {

    private Context context;
    private Function<String, Void> callback;

    GetRequest(Context context, Function<String, Void> callback) {
        this.context = context;
        this.callback = callback;
    }

    protected String doInBackground(String... urls) {
        try {
            URL url = new URL(urls[0]);
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("X-Genesis-PartnerToken", "8c31b93c-24bd-4dfa-aa16-db96c0296b3a");
            connection.setRequestProperty("X-Genesis-Secret", "eeb847b7-16a2-4618-951c-8d07c78f3dd6");
            //connection.setDoOutput(true);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setSSLSocketFactory((SSLSocketFactory) SSLSocketFactory.getDefault());
            connection.connect();
            BufferedReader rd = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = rd.readLine()) != null) {
                content.append(line).append("\n");
            }
            return content.toString();
        } catch (Exception e) {
            ((Activity)context).runOnUiThread(() -> Toast.makeText(context, e.getMessage(), LENGTH_LONG));
            return null;
        }
    }

    protected void onProgressUpdate(Integer... progress) {
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    protected void onPostExecute(String result) {
        callback.apply(result);
    }

}
