package com.pichillilorenzo.flutter_inappwebview.InAppWebView;

import android.annotation.TargetApi;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.GeolocationPermissions;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;

import com.pichillilorenzo.flutter_inappwebview.InAppBrowser.InAppBrowserActivity;
import com.pichillilorenzo.flutter_inappwebview.InAppWebViewFlutterPlugin;
import com.pichillilorenzo.flutter_inappwebview.R;
import com.pichillilorenzo.flutter_inappwebview.Shared;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;

import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;

public class InAppWebViewChromeClient extends WebChromeClient implements PluginRegistry.ActivityResultListener {

  protected static final String LOG_TAG = "IABWebChromeClient";
  private FlutterWebView flutterWebView;
  private InAppBrowserActivity inAppBrowserActivity;
  public MethodChannel channel;
  private ValueCallback<Uri> mUploadMessage;
  private final static int FILECHOOSER_RESULTCODE = 1;

  private View mCustomView;
  private WebChromeClient.CustomViewCallback mCustomViewCallback;
  private int mOriginalOrientation;
  private int mOriginalSystemUiVisibility;

  public InAppWebViewChromeClient(Object obj) {
    if (obj instanceof InAppBrowserActivity)
      this.inAppBrowserActivity = (InAppBrowserActivity) obj;
    else if (obj instanceof FlutterWebView)
      this.flutterWebView = (FlutterWebView) obj;
    this.channel = (this.inAppBrowserActivity != null) ? this.inAppBrowserActivity.channel : this.flutterWebView.channel;

    if (Shared.registrar != null)
      Shared.registrar.addActivityResultListener(this);
    else
      Shared.activityPluginBinding.addActivityResultListener(this);
  }

  public Bitmap getDefaultVideoPoster() {
    if (mCustomView == null) {
      return null;
    }
    return BitmapFactory.decodeResource(Shared.activity.getApplicationContext().getResources(), 2130837573);
  }

  public void onHideCustomView() {
    View decorView = Shared.activity.getWindow().getDecorView();
    ((FrameLayout) decorView).removeView(this.mCustomView);
    this.mCustomView = null;
    decorView.setSystemUiVisibility(this.mOriginalSystemUiVisibility);
    Shared.activity.setRequestedOrientation(this.mOriginalOrientation);
    this.mCustomViewCallback.onCustomViewHidden();
    this.mCustomViewCallback = null;
  }

  public void onShowCustomView(View paramView, WebChromeClient.CustomViewCallback paramCustomViewCallback) {
    if (this.mCustomView != null) {
      onHideCustomView();
      return;
    }
    View decorView = Shared.activity.getWindow().getDecorView();
    this.mCustomView = paramView;
    this.mOriginalSystemUiVisibility = decorView.getSystemUiVisibility();
    this.mOriginalOrientation = Shared.activity.getRequestedOrientation();
    this.mCustomViewCallback = paramCustomViewCallback;
    this.mCustomView.setBackgroundColor(Color.parseColor("#000000"));
    ((FrameLayout) decorView).addView(this.mCustomView, new FrameLayout.LayoutParams(-1, -1));
    decorView.setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            // Set the content to appear under the system bars so that the
            // content doesn't resize when the system bars hide and show.
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            // Hide the nav bar and status bar
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN);
  }

  @Override
  public boolean onJsAlert(final WebView view, String url, final String message,
                           final JsResult result) {
    Map<String, Object> obj = new HashMap<>();
    if (inAppBrowserActivity != null)
      obj.put("uuid", inAppBrowserActivity.uuid);
    obj.put("message", message);

    channel.invokeMethod("onJsAlert", obj, new MethodChannel.Result() {
      @Override
      public void success(Object response) {
        String responseMessage = null;
        String confirmButtonTitle = null;

        if (response != null) {
          Map<String, Object> responseMap = (Map<String, Object>) response;
          responseMessage = (String) responseMap.get("message");
          confirmButtonTitle = (String) responseMap.get("confirmButtonTitle");
          Boolean handledByClient = (Boolean) responseMap.get("handledByClient");
          if (handledByClient != null && handledByClient) {
            Integer action = (Integer) responseMap.get("action");
            action = action != null ? action : 1;
            switch (action) {
              case 0:
                result.confirm();
                break;
              case 1:
              default:
                result.cancel();
            }
            return;
          }
        }

        createAlertDialog(view, message, result, responseMessage, confirmButtonTitle);
      }

      @Override
      public void error(String s, String s1, Object o) {
        Log.e(LOG_TAG, s + ", " + s1);
        result.cancel();
      }

      @Override
      public void notImplemented() {
        createAlertDialog(view, message, result, null, null);
      }
    });

    return true;
  }

  public void createAlertDialog(WebView view, String message, final JsResult result, String responseMessage, String confirmButtonTitle) {
    String alertMessage = (responseMessage != null && !responseMessage.isEmpty()) ? responseMessage : message;

    DialogInterface.OnClickListener clickListener = new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        result.confirm();
        dialog.dismiss();
      }
    };

    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(Shared.activity, R.style.Theme_AppCompat_Dialog_Alert);
    alertDialogBuilder.setMessage(alertMessage);
    if (confirmButtonTitle != null && !confirmButtonTitle.isEmpty()) {
      alertDialogBuilder.setPositiveButton(confirmButtonTitle, clickListener);
    } else {
      alertDialogBuilder.setPositiveButton(android.R.string.ok, clickListener);
    }

    alertDialogBuilder.setOnCancelListener(new DialogInterface.OnCancelListener() {
      @Override
      public void onCancel(DialogInterface dialog) {
        result.cancel();
        dialog.dismiss();
      }
    });

    AlertDialog alertDialog = alertDialogBuilder.create();
    alertDialog.show();
  }

  @Override
  public boolean onJsConfirm(final WebView view, String url, final String message,
                             final JsResult result) {
    Map<String, Object> obj = new HashMap<>();
    if (inAppBrowserActivity != null)
      obj.put("uuid", inAppBrowserActivity.uuid);
    obj.put("message", message);

    channel.invokeMethod("onJsConfirm", obj, new MethodChannel.Result() {
      @Override
      public void success(Object response) {
        String responseMessage = null;
        String confirmButtonTitle = null;
        String cancelButtonTitle = null;

        if (response != null) {
          Map<String, Object> responseMap = (Map<String, Object>) response;
          responseMessage = (String) responseMap.get("message");
          confirmButtonTitle = (String) responseMap.get("confirmButtonTitle");
          cancelButtonTitle = (String) responseMap.get("cancelButtonTitle");
          Boolean handledByClient = (Boolean) responseMap.get("handledByClient");
          if (handledByClient != null && handledByClient) {
            Integer action = (Integer) responseMap.get("action");
            action = action != null ? action : 1;
            switch (action) {
              case 0:
                result.confirm();
                break;
              case 1:
              default:
                result.cancel();
            }
            return;
          }
        }

        createConfirmDialog(view, message, result, responseMessage, confirmButtonTitle, cancelButtonTitle);
      }

      @Override
      public void error(String s, String s1, Object o) {
        Log.e(LOG_TAG, s + ", " + s1);
        result.cancel();
      }

      @Override
      public void notImplemented() {
        createConfirmDialog(view, message, result, null, null, null);
      }
    });

    return true;
  }

  public void createConfirmDialog(WebView view, String message, final JsResult result, String responseMessage, String confirmButtonTitle, String cancelButtonTitle) {
    String alertMessage = (responseMessage != null && !responseMessage.isEmpty()) ? responseMessage : message;
    DialogInterface.OnClickListener confirmClickListener = new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        result.confirm();
        dialog.dismiss();
      }
    };
    DialogInterface.OnClickListener cancelClickListener = new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        result.cancel();
        dialog.dismiss();
      }
    };

    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(Shared.activity, R.style.Theme_AppCompat_Dialog_Alert);
    alertDialogBuilder.setMessage(alertMessage);
    if (confirmButtonTitle != null && !confirmButtonTitle.isEmpty()) {
      alertDialogBuilder.setPositiveButton(confirmButtonTitle, confirmClickListener);
    } else {
      alertDialogBuilder.setPositiveButton(android.R.string.ok, confirmClickListener);
    }
    if (cancelButtonTitle != null && !cancelButtonTitle.isEmpty()) {
      alertDialogBuilder.setNegativeButton(cancelButtonTitle, cancelClickListener);
    } else {
      alertDialogBuilder.setNegativeButton(android.R.string.cancel, cancelClickListener);
    }

    alertDialogBuilder.setOnCancelListener(new DialogInterface.OnCancelListener() {
      @Override
      public void onCancel(DialogInterface dialog) {
        result.cancel();
        dialog.dismiss();
      }
    });

    AlertDialog alertDialog = alertDialogBuilder.create();
    alertDialog.show();
  }

  @Override
  public boolean onJsPrompt(final WebView view, String url, final String message,
                            final String defaultValue, final JsPromptResult result) {
    Map<String, Object> obj = new HashMap<>();
    if (inAppBrowserActivity != null)
      obj.put("uuid", inAppBrowserActivity.uuid);
    obj.put("message", message);
    obj.put("defaultValue", defaultValue);

    channel.invokeMethod("onJsPrompt", obj, new MethodChannel.Result() {
      @Override
      public void success(Object response) {
        String responseMessage = null;
        String responseDefaultValue = null;
        String confirmButtonTitle = null;
        String cancelButtonTitle = null;
        String value = null;

        if (response != null) {
          Map<String, Object> responseMap = (Map<String, Object>) response;
          responseMessage = (String) responseMap.get("message");
          responseDefaultValue = (String) responseMap.get("defaultValue");
          confirmButtonTitle = (String) responseMap.get("confirmButtonTitle");
          cancelButtonTitle = (String) responseMap.get("cancelButtonTitle");
          value = (String) responseMap.get("value");
          Boolean handledByClient = (Boolean) responseMap.get("handledByClient");
          if (handledByClient != null && handledByClient) {
            Integer action = (Integer) responseMap.get("action");
            action = action != null ? action : 1;
            switch (action) {
              case 0:
                result.confirm(value);
                break;
              case 1:
              default:
                result.cancel();
            }
            return;
          }
        }

        createPromptDialog(view, message, defaultValue, result, responseMessage, responseDefaultValue, value, cancelButtonTitle, confirmButtonTitle);
      }

      @Override
      public void error(String s, String s1, Object o) {
        Log.e(LOG_TAG, s + ", " + s1);
        result.cancel();
      }

      @Override
      public void notImplemented() {
        createPromptDialog(view, message, defaultValue, result, null, null, null, null, null);
      }
    });

    return true;
  }

  public void createPromptDialog(WebView view, String message, String defaultValue, final JsPromptResult result, String responseMessage, String responseDefaultValue, String value, String cancelButtonTitle, String confirmButtonTitle) {
    FrameLayout layout = new FrameLayout(view.getContext());

    final EditText input = new EditText(view.getContext());
    input.setMaxLines(1);
    input.setText((responseDefaultValue != null && !responseDefaultValue.isEmpty()) ? responseDefaultValue : defaultValue);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT);
    input.setLayoutParams(lp);

    layout.setPaddingRelative(45, 15, 45, 0);
    layout.addView(input);

    String alertMessage = (responseMessage != null && !responseMessage.isEmpty()) ? responseMessage : message;

    final String finalValue = value;
    DialogInterface.OnClickListener confirmClickListener = new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        String text = input.getText().toString();
        result.confirm(finalValue != null ? finalValue : text);
        dialog.dismiss();
      }
    };
    DialogInterface.OnClickListener cancelClickListener = new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        result.cancel();
        dialog.dismiss();
      }
    };

    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(Shared.activity, R.style.Theme_AppCompat_Dialog_Alert);
    alertDialogBuilder.setMessage(alertMessage);
    if (confirmButtonTitle != null && !confirmButtonTitle.isEmpty()) {
      alertDialogBuilder.setPositiveButton(confirmButtonTitle, confirmClickListener);
    } else {
      alertDialogBuilder.setPositiveButton(android.R.string.ok, confirmClickListener);
    }
    if (cancelButtonTitle != null && !cancelButtonTitle.isEmpty()) {
      alertDialogBuilder.setNegativeButton(cancelButtonTitle, cancelClickListener);
    } else {
      alertDialogBuilder.setNegativeButton(android.R.string.cancel, cancelClickListener);
    }

    alertDialogBuilder.setOnCancelListener(new DialogInterface.OnCancelListener() {
      @Override
      public void onCancel(DialogInterface dialog) {
        result.cancel();
        dialog.dismiss();
      }
    });

    AlertDialog alertDialog = alertDialogBuilder.create();
    alertDialog.setView(layout);
    alertDialog.show();
  }

  @Override
  public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, final Message resultMsg) {
    final Map<String, Object> obj = new HashMap<>();
    if (inAppBrowserActivity != null)
      obj.put("uuid", inAppBrowserActivity.uuid);
    obj.put("androidIsDialog", isDialog);
    obj.put("androidIsUserGesture", isUserGesture);
    obj.put("iosWKNavigationType", null);

    WebView.HitTestResult result = view.getHitTestResult();
    String data = result.getExtra();

    if (data == null) {
      // to get the URL, create a temp weview
      final WebView tempWebView = new WebView(view.getContext());
      // disable javascript
      tempWebView.getSettings().setJavaScriptEnabled(false);
      tempWebView.setWebViewClient(new WebViewClient(){
        @Override
        public void onPageStarted(WebView v, String url, Bitmap favicon) {
          super.onPageStarted(v, url, favicon);

          obj.put("url", url);
          channel.invokeMethod("onCreateWindow", obj);

          // stop webview loading
          v.stopLoading();

          // this will throw the error "Application attempted to call on a destroyed AwAutofillManager" that will kill the webview.
          // that's ok.
          v.destroy();
        }
      });
      ((WebView.WebViewTransport) resultMsg.obj).setWebView(tempWebView);
      resultMsg.sendToTarget();
      return true;
    }

    obj.put("url", data);
    channel.invokeMethod("onCreateWindow", obj);
    return false;
  }

  @Override
  public void onGeolocationPermissionsShowPrompt(final String origin, final GeolocationPermissions.Callback callback) {
    Map<String, Object> obj = new HashMap<>();
    if (inAppBrowserActivity != null)
      obj.put("uuid", inAppBrowserActivity.uuid);
    obj.put("origin", origin);
    channel.invokeMethod("onGeolocationPermissionsShowPrompt", obj, new MethodChannel.Result() {
      @Override
      public void success(Object o) {
        Map<String, Object> response = (Map<String, Object>) o;
        if (response != null)
          callback.invoke((String) response.get("origin"), (Boolean) response.get("allow"), (Boolean) response.get("retain"));
        else
          callback.invoke(origin, false, false);
      }

      @Override
      public void error(String s, String s1, Object o) {
        callback.invoke(origin, false, false);
      }

      @Override
      public void notImplemented() {
        callback.invoke(origin, false, false);
      }
    });
  }

  @Override
  public void onGeolocationPermissionsHidePrompt() {
    Map<String, Object> obj = new HashMap<>();
    if (inAppBrowserActivity != null)
      obj.put("uuid", inAppBrowserActivity.uuid);
    channel.invokeMethod("onGeolocationPermissionsHidePrompt", obj);
  }

  @Override
  public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
    Map<String, Object> obj = new HashMap<>();
    if (inAppBrowserActivity != null)
      obj.put("uuid", inAppBrowserActivity.uuid);
    obj.put("message", consoleMessage.message());
    obj.put("messageLevel", consoleMessage.messageLevel().ordinal());
    channel.invokeMethod("onConsoleMessage", obj);
    return true;
  }

  @Override
  public void onProgressChanged(WebView view, int progress) {
    if (inAppBrowserActivity != null && inAppBrowserActivity.progressBar != null) {
      inAppBrowserActivity.progressBar.setVisibility(View.VISIBLE);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        inAppBrowserActivity.progressBar.setProgress(progress, true);
      } else {
        inAppBrowserActivity.progressBar.setProgress(progress);
      }
      if (progress == 100) {
        inAppBrowserActivity.progressBar.setVisibility(View.GONE);
      }
    }

    Map<String, Object> obj = new HashMap<>();
    if (inAppBrowserActivity != null)
      obj.put("uuid", inAppBrowserActivity.uuid);
    obj.put("progress", progress);
    channel.invokeMethod("onProgressChanged", obj);

    super.onProgressChanged(view, progress);
  }

  @Override
  public void onReceivedTitle(WebView view, String title) {
    super.onReceivedTitle(view, title);
    if (inAppBrowserActivity != null && inAppBrowserActivity.actionBar != null && inAppBrowserActivity.options.toolbarTopFixedTitle.isEmpty())
      inAppBrowserActivity.actionBar.setTitle(title);
  }

  @Override
  public void onReceivedIcon(WebView view, Bitmap icon) {
    super.onReceivedIcon(view, icon);
  }

  //The undocumented magic method override
  //Eclipse will swear at you if you try to put @Override here
  // For Android 3.0+
  public void openFileChooser(ValueCallback<Uri> uploadMsg) {

    mUploadMessage = uploadMsg;
    Intent i = new Intent(Intent.ACTION_GET_CONTENT);
    i.addCategory(Intent.CATEGORY_OPENABLE);
    i.setType("image/*");
    Shared.activity.startActivityForResult(Intent.createChooser(i, "File Chooser"), FILECHOOSER_RESULTCODE);

  }

  // For Android 3.0+
  public void openFileChooser(ValueCallback uploadMsg, String acceptType) {
    mUploadMessage = uploadMsg;
    Intent i = new Intent(Intent.ACTION_GET_CONTENT);
    i.addCategory(Intent.CATEGORY_OPENABLE);
    i.setType("*/*");
    Shared.activity.startActivityForResult(
            Intent.createChooser(i, "File Browser"),
            FILECHOOSER_RESULTCODE);
  }

  //For Android 4.1
  public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType, String capture) {
    mUploadMessage = uploadMsg;
    Intent i = new Intent(Intent.ACTION_GET_CONTENT);
    i.addCategory(Intent.CATEGORY_OPENABLE);
    i.setType("image/*");
    Shared.activity.startActivityForResult(Intent.createChooser(i, "File Chooser"), FILECHOOSER_RESULTCODE);

  }

  //For Android 5.0+
  public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
    InAppWebViewFlutterPlugin.uploadMessageArray = filePathCallback;
    try {
      Intent contentSelectionIntent = new Intent(Intent.ACTION_GET_CONTENT);
      contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE);
      contentSelectionIntent.setType("*/*");
      Intent[] intentArray;
      intentArray = new Intent[0];

      Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
      chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent);
      chooserIntent.putExtra(Intent.EXTRA_TITLE, "Image Chooser");
      chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray);
      Shared.activity.startActivityForResult(chooserIntent, FILECHOOSER_RESULTCODE);
    } catch (ActivityNotFoundException e) {
      e.printStackTrace();
      return false;
    }
    return true;
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  @Override
  public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == FILECHOOSER_RESULTCODE && (resultCode == RESULT_OK || resultCode == RESULT_CANCELED)) {
      InAppWebViewFlutterPlugin.uploadMessageArray.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
    }
    return true;
  }

  @Override
  public void onPermissionRequest(final PermissionRequest request) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      Map<String, Object> obj = new HashMap<>();
      if (inAppBrowserActivity != null)
        obj.put("uuid", inAppBrowserActivity.uuid);
      obj.put("origin", request.getOrigin().toString());
      obj.put("resources", Arrays.asList(request.getResources()));
      channel.invokeMethod("onPermissionRequest", obj, new MethodChannel.Result() {
        @Override
        public void success(Object response) {
          if (response != null) {
            Map<String, Object> responseMap = (Map<String, Object>) response;
            Integer action = (Integer) responseMap.get("action");
            List<String> resourceList = (List<String>) responseMap.get("resources");
            if (resourceList == null)
              resourceList = new ArrayList<String>();
            String[] resources = new String[resourceList.size()];
            resources = resourceList.toArray(resources);
            if (action != null) {
              switch (action) {
                case 1:
                  request.grant(resources);
                  return;
                case 0:
                default:
                  request.deny();
                  return;
              }
            }
          }
          request.deny();
        }

        @Override
        public void error(String s, String s1, Object o) {
          Log.e(LOG_TAG, s + ", " + s1);
        }

        @Override
        public void notImplemented() {
          request.deny();
        }
      });
    }
  }
}
