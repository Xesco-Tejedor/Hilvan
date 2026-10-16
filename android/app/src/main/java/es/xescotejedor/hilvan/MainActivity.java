package es.xescotejedor.hilvan;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String URL = "https://xesco-tejedor.github.io/Hilvan/";
    private static final int FONDO = Color.rgb(22, 19, 15);
    private static final int AMBAR = Color.rgb(255, 176, 0);

    private FrameLayout raiz;
    private WebView web;
    private View errorView;
    private ProgressBar barra;
    private TextToSpeech tts;
    private boolean ttsListo;
    private SpeechRecognizer rec;
    private ValueCallback<Uri[]> archivoCb;
    private static final int REQ_MIC = 1;
    private static final int REQ_ARCHIVO = 2;
    private static final int REQ_ESCRITURA = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(FONDO);
        getWindow().setNavigationBarColor(FONDO);

        super.onRequestPermissionsResult(0, new String[0], new int[0]);
        raiz = new FrameLayout(this);
        raiz.setBackgroundColor(FONDO);

        web = new WebView(this);
        WebSettings st = web.getSettings();
        st.setJavaScriptEnabled(true);
        st.setDomStorageEnabled(true);
        st.setMediaPlaybackRequiresUserGesture(false);

        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                Uri u = req.getUrl();
                String es = u.getScheme();
                if ("http".equals(es) || "https".equals(es)) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (ActivityNotFoundException ignored) {}
                return true;
            }
            @Override public void onPageStarted(WebView v, String url, android.graphics.Bitmap fav) { barra.setVisibility(View.VISIBLE); errorView.setVisibility(View.GONE); }
            @Override public void onPageFinished(WebView v, String url) { barra.setVisibility(View.GONE); }
            @Override public void onReceivedError(WebView v, WebResourceRequest req, android.webkit.WebResourceError err) {
                if (req.isForMainFrame()) errorView.setVisibility(View.VISIBLE);
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (archivoCb != null) archivoCb.onReceiveValue(null);
                archivoCb = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                String[] accept = params.getAcceptTypes();
                ArrayList<String> tipos = new ArrayList<>();
                if (accept != null) for (String a : accept) {
                    if (a == null || a.isEmpty()) continue;
                    if (a.endsWith("/*") || a.contains("/")) tipos.add(a);
                    else if (a.equals(".json")) tipos.add("application/json");
                }
                i.setType(tipos.isEmpty() ? "*/*" : tipos.get(0));
                if (tipos.size() > 1) i.putExtra(Intent.EXTRA_MIME_TYPES, tipos.toArray(new String[0]));
                try { startActivityForResult(Intent.createChooser(i, "Elegir archivo"), REQ_ARCHIVO); }
                catch (Exception e) { archivoCb = null; return false; }
                return true;
            }
            @Override public void onProgressChanged(WebView v, int p) { barra.setProgress(p); }
        });
        web.addJavascriptInterface(new Puente(), "HilvanAndroid");
        raiz.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        barra = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        barra.setMax(100);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 6, Gravity.TOP);
        raiz.addView(barra, lp);

        errorView = vistaError();
        errorView.setVisibility(View.GONE);
        raiz.addView(errorView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(raiz);

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("es", "ES"));
                ttsListo = true;
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) {}
                    @Override public void onError(String id) { js("window.hilvanFinLectura&&window.hilvanFinLectura()"); }
                    @Override public void onDone(String id) { if (id != null && id.startsWith("fin")) js("window.hilvanFinLectura&&window.hilvanFinLectura()"); }
                });
            }
        });

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl(URL);
    }

    private View vistaError() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        l.setBackgroundColor(FONDO);
        l.setPadding(48, 48, 48, 48);
        TextView t = new TextView(this);
        t.setText("Hilván necesita conexión para cargar.\nEl relato guardado se conserva.");
        t.setTextColor(Color.rgb(240, 234, 224));
        t.setTextSize(17);
        t.setGravity(Gravity.CENTER);
        l.addView(t);
        Button b = new Button(this);
        b.setText("Reintentar");
        b.setTextColor(Color.rgb(22, 19, 15));
        b.setBackgroundColor(AMBAR);
        b.setOnClickListener(v -> { errorView.setVisibility(View.GONE); web.reload(); });
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = 40;
        l.addView(b, bp);
        return l;
    }

    private void js(String codigo) {
        runOnUiThread(() -> web.evaluateJavascript(codigo, null));
    }

    private void aviso(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    /* ---------- dictado nativo ---------- */
    private void iniciarDictado() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            aviso("Este móvil no tiene reconocimiento de voz disponible");
            js("window.hilvanDictadoFin&&window.hilvanDictadoFin()");
            return;
        }
        if (rec != null) rec.destroy();
        rec = SpeechRecognizer.createSpeechRecognizer(this);
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        rec.setRecognitionListener(new RecognitionListener() {
            private void fin() {
                if (rec != null) { rec.destroy(); rec = null; }
                js("window.hilvanDictadoFin&&window.hilvanDictadoFin()");
            }
            @Override public void onReadyForSpeech(Bundle b) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float v) {}
            @Override public void onBufferReceived(byte[] b) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onPartialResults(Bundle b) {}
            @Override public void onEvent(int t, Bundle b) {}
            @Override public void onResults(Bundle b) {
                ArrayList<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (r != null && !r.isEmpty()) js("window.hilvanDictado(" + JSONObject.quote(r.get(0)) + ")");
                fin();
            }
            @Override public void onError(int e) {
                if (e == SpeechRecognizer.ERROR_NO_MATCH || e == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) aviso("No te he oído, prueba de nuevo");
                else if (e == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) aviso("Falta el permiso de micrófono");
                else if (e != SpeechRecognizer.ERROR_CLIENT) aviso("Dictado no disponible ahora mismo");
                fin();
            }
        });
        rec.startListening(i);
    }

    /* ---------- lectura en voz alta nativa ---------- */
    private void leerUnidades(String json) {
        if (!ttsListo) { aviso("La voz todavía se está preparando, prueba en un momento"); js("window.hilvanFinLectura&&window.hilvanFinLectura()"); return; }
        tts.stop();
        try {
            JSONArray arr = new JSONArray(json);
            int n = arr.length();
            for (int k = 0; k < n; k++) {
                JSONObject u = arr.getJSONObject(k);
                tts.setPitch((float) u.optDouble("pitch", 1));
                tts.setSpeechRate((float) u.optDouble("rate", 1));
                Bundle params = new Bundle();
                String id = "u" + k;
                params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, id);
                tts.speak(u.getString("t"), k == 0 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD, params, id);
                String idPausa = (k == n - 1) ? "fin" : "p" + k;
                tts.playSilentUtterance(u.optLong("pause", 250), TextToSpeech.QUEUE_ADD, idPausa);
            }
        } catch (Exception e) {
            aviso("No se pudo leer el fragmento");
            js("window.hilvanFinLectura&&window.hilvanFinLectura()");
        }
    }

    /* ---------- exportaciones ---------- */
    private void guardarDescarga(String nombre, String mime, String datos) {
        try {
            byte[] bytes = datos.getBytes(StandardCharsets.UTF_8);
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, nombre);
                v.put(MediaStore.Downloads.MIME_TYPE, mime);
                v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (u == null) throw new IllegalStateException("sin uri");
                try (OutputStream o = getContentResolver().openOutputStream(u)) { o.write(bytes); }
            } else {
                if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_ESCRITURA);
                    aviso("Concede almacenamiento y vuelve a exportar");
                    return;
                }
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                try (OutputStream o = new FileOutputStream(new File(dir, nombre))) { o.write(bytes); }
            }
            aviso("Guardado en Descargas: " + nombre);
        } catch (Exception e) {
            aviso("No se pudo guardar el archivo");
        }
    }

    private void imprimirHtml(String titulo, String html) {
        WebView temp = new WebView(this);
        temp.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v, String url) {
                PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
                if (pm != null) pm.print(titulo, temp.createPrintDocumentAdapter(titulo), new PrintAttributes.Builder().build());
            }
        });
        temp.loadDataWithBaseURL(null, html, "text/HTML", "UTF-8", null);
    }

    /* ---------- puente JS ---------- */
    public class Puente {
        @JavascriptInterface public void dictar() {
            runOnUiThread(() -> {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) iniciarDictado();
                else requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            });
        }
        @JavascriptInterface public void pararDictar() { runOnUiThread(() -> { if (rec != null) rec.stopListening(); }); }
        @JavascriptInterface public void leer(String unidadesJson) { runOnUiThread(() -> leerUnidades(unidadesJson)); }
        @JavascriptInterface public void pararLectura() { runOnUiThread(() -> { if (tts != null) tts.stop(); }); }
        @JavascriptInterface public void guardar(String nombre, String mime, String datos) { runOnUiThread(() -> guardarDescarga(nombre, mime, datos)); }
        @JavascriptInterface public void imprimir(String titulo, String html) { runOnUiThread(() -> imprimirHtml(titulo, html)); }
    }

    @Override protected void onPause() {
        super.onPause();
        if (tts != null) tts.stop();
        if (rec != null) { rec.cancel(); }
    }

    @Override protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (rec != null) rec.destroy();
        if (web != null) web.destroy();
        super.onDestroy();
    }

    @Override public void onRequestPermissionsResult(int req, String[] perms, int[] grants) {
        super.onRequestPermissionsResult(req, perms, grants);
        boolean ok = grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED;
        if (req == REQ_MIC) {
            if (ok) iniciarDictado();
            else { aviso("Sin permiso de micrófono no puedo escucharte"); js("window.hilvanDictadoFin&&window.hilvanDictadoFin()"); }
        } else if (req == REQ_ESCRITURA && ok) aviso("Permiso concedido, vuelve a exportar");
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_ARCHIVO || archivoCb == null) return;
        Uri[] uris = null;
        if (res == RESULT_OK && data != null) {
            if (data.getClipData() != null && data.getClipData().getItemCount() > 0) {
                uris = new Uri[]{ data.getClipData().getItemAt(0).getUri() };
            } else if (data.getData() != null) {
                uris = new Uri[]{ data.getData() };
            }
        }
        archivoCb.onReceiveValue(uris);
        archivoCb = null;
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
