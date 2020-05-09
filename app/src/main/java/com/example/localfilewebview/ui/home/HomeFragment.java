package com.example.localfilewebview.ui.home;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;

import com.example.localfilewebview.R;

import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static android.Manifest.permission.INTERNET;
import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.view.KeyEvent.KEYCODE_DPAD_DOWN;
import static android.view.KeyEvent.KEYCODE_DPAD_LEFT;
import static android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
import static android.view.KeyEvent.KEYCODE_DPAD_UP;
import static android.view.KeyEvent.KEYCODE_ENTER;

public class HomeFragment extends Fragment {

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

    private EditText urlView;

    private float downXValue;
    private float downYValue;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        HomeViewModel homeViewModel = ViewModelProviders.of(this).get(HomeViewModel.class);
        View root = inflater.inflate(R.layout.fragment_home, container, false);

        urlView = root.findViewById(R.id.url_input);
        initUrlView();

        root.findViewById(R.id.launch).setOnClickListener(view -> load());

        gameView = root.findViewById(R.id.game_view);
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

        homeViewModel.getText().observe(this, s -> {
            for (String key : PERMISSION_CHECK_MAP.keySet()) {
                checkPermissionGranted(key);
            }

            if (permissionGranted.get() == PERMISSION_CHECK_MAP.size()) {
                load();
            }
        });

        gameView.requestFocus();

        return root;
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
        int permission = ContextCompat.checkSelfPermission(getActivity(), permissionName);
        if (permission == PackageManager.PERMISSION_GRANTED) {
            permissionGranted.incrementAndGet();
            return true; // permission has been granted
        }

        ActivityCompat.requestPermissions(
                getActivity(),
                new String[]{permissionName},
                PERMISSION_CHECK_MAP.get(permissionName));
        return false;
    }

    private void load() {
        //String file = "file://" + Environment.getExternalStorageDirectory().getPath() + "/local_web_client/test/index.html";
        //file = "file:///mnt/sdcard/local_web_client/test/index.html";
        //file = "content://com.android.externalstorage.documents/document/primary%3Alocal_web_client%2Ftest%2Findex.html";
        //file = "https://www.google.com";
        //Log.i(TAG, file);
        //gameView.loadUrl(file);
        gameView.loadUrl(urlView.getText().toString());
    }
}