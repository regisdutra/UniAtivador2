package br.com.regisdutra.uniativador2;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.webkit.WebChromeClient;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

public class MainActivity extends Activity {
    private static final int REQUEST_STORAGE = 1109;
    private static final int REQUEST_PICKER = 3202;
    private static final int MAX_CONFIG_BYTES = 128 * 1024;

    private WebView webView;
    private String pendingConfig;
    private boolean waitingForStorageSettings = false;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        webView = new WebView(this);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.setBackgroundColor(0xff080f1f);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            private boolean allowed(String address) {
                return "file:///android_asset/index.html".equals(address);
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !allowed(url);
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !allowed(request.getUrl().toString());
            }
        });
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidLocal");
        webView.loadUrl("file:///android_asset/index.html");
    }

    private boolean validConfig(String content) {
        if (content == null) return false;
        int size = content.getBytes(StandardCharsets.UTF_8).length;
        return size >= 100 && size <= MAX_CONFIG_BYTES
                && content.startsWith("#personal info")
                && content.contains("key_device_id_unitvfree=")
                && content.contains("key_sn_token_unitvfree=");
    }

    public final class AndroidBridge {
        @JavascriptInterface
        public void saveConfig(final String text) {
            runOnUiThread(() -> {
                if (!validConfig(text)) {
                    status("Arquivo recusado: configuração de teste inválida ou muito grande.");
                    return;
                }
                pendingConfig = text;
                File target = getTarget();
                boolean exists = target.exists();
                String msg = "Destino:\n" + target.getAbsolutePath() +
                    "\n\nSerão escritos apenas os bytes do .config gerado nesta tela." +
                    (exists ? "\n\nJá existe um arquivo neste local. Uma cópia de segurança será salva na área privada do aplicativo antes da substituição." : "");
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle(exists ? "Substituir .config existente?" : "Gravar .config no Android?")
                    .setMessage(msg)
                    .setNegativeButton("Cancelar", (d, w) -> pendingConfig = null)
                    .setPositiveButton("Confirmar", (d, w) -> ensureStorageAndSave())
                    .show();
            });
        }

        @JavascriptInterface
        public void openPicker(final String text) {
            runOnUiThread(() -> {
                if (!validConfig(text)) {
                    status("Primeiro gere uma configuração válida.");
                    return;
                }
                pendingConfig = text;
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/octet-stream");
                intent.putExtra(Intent.EXTRA_TITLE, ".config");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                try {
                    startActivityForResult(intent, REQUEST_PICKER);
                    status("Escolha a pasta de destino na janela do Android.");
                } catch (ActivityNotFoundException ex) {
                    status("Este Android TV não possui um seletor de documentos. Tente salvar com a permissão de arquivos.");
                }
            });
        }
    }

    private File getTarget() {
        return new File(new File(Environment.getExternalStorageDirectory(), "Android"), ".config");
    }

    private void ensureStorageAndSave() {
        if (pendingConfig == null) return;
        if (Build.VERSION.SDK_INT >= 30) {
            if (Environment.isExternalStorageManager()) {
                saveDirect();
            } else {
                new AlertDialog.Builder(this)
                    .setTitle("Permissão de arquivos")
                    .setMessage("Para gravar diretamente em /storage/emulated/0/Android/.config, o Android 11 ou superior pode exigir 'Permitir gerenciamento de todos os arquivos'. Esta permissão é ampla: conceda-a somente se confiar neste aplicativo. Se preferir, use o seletor de documentos.")
                    .setNegativeButton("Cancelar", (d, w) -> status("Permissão não concedida. Use 'Escolher destino' ou conceda acesso aos arquivos."))
                    .setPositiveButton("Abrir configurações", (d, w) -> openStorageSettings())
                    .show();
            }
        } else if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE);
        } else {
            saveDirect();
        }
    }

    private void openStorageSettings() {
        waitingForStorageSettings = true;
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            } catch (Exception ex) {
                waitingForStorageSettings = false;
                status("Configurações de permissão indisponíveis nesta TV. Use o seletor de arquivos.");
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (waitingForStorageSettings && Build.VERSION.SDK_INT >= 30) {
            waitingForStorageSettings = false;
            if (Environment.isExternalStorageManager()) {
                saveDirect();
            } else {
                status("Sem permissão para escrever diretamente. Tente 'Escolher destino'.");
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] perms, int[] results) {
        super.onRequestPermissionsResult(requestCode, perms, results);
        if (requestCode == REQUEST_STORAGE) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                saveDirect();
            } else {
                status("Acesso aos arquivos negado. Use 'Escolher destino'.");
            }
        }
    }

    private static byte[] readAll(File file) throws Exception {
        try (InputStream input = new FileInputStream(file);
             ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192];
            int n;
            while ((n = input.read(chunk)) != -1) {
                buf.write(chunk, 0, n);
                if (buf.size() > MAX_CONFIG_BYTES) throw new Exception("Arquivo excedeu o limite.");
            }
            return buf.toByteArray();
        }
    }

    private void makePrivateBackup(File existing) throws Exception {
        File dir = new File(getFilesDir(), "backups");
        if (!dir.exists() && !dir.mkdirs() && !dir.exists()) {
            throw new Exception("Não foi possível criar a pasta de backup.");
        }
        File backup = new File(dir, "config-" + System.currentTimeMillis() + ".bak");
        try (InputStream input = new FileInputStream(existing);
             OutputStream output = new FileOutputStream(backup)) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) != -1) output.write(buffer, 0, length);
            output.flush();
        }
        if (backup.length() != existing.length()) throw new Exception("Backup não verificado.");
    }

    private void saveDirect() {
        if (pendingConfig == null) return;
        byte[] bytes = pendingConfig.getBytes(StandardCharsets.UTF_8);
        File target = getTarget();
        File temp = new File(target.getParentFile(), ".config.uniativador2.tmp");
        try {
            File parent = target.getParentFile();
            if (!parent.exists() && !parent.mkdirs() && !parent.exists())
                throw new Exception("Não foi possível criar o diretório Android.");

            if (target.exists()) makePrivateBackup(target);

            try (FileOutputStream output = new FileOutputStream(temp, false)) {
                output.write(bytes);
                output.flush();
                output.getFD().sync();
            }

            if (!Arrays.equals(bytes, readAll(temp))) throw new Exception("Verificação do arquivo temporário falhou.");

            if (Build.VERSION.SDK_INT >= 26) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else {
                if (target.exists() && !target.delete()) throw new Exception("Não foi possível substituir o antigo .config.");
                if (!temp.renameTo(target)) throw new Exception("Não foi possível finalizar a gravação.");
            }

            if (!Arrays.equals(bytes, readAll(target))) throw new Exception("Verificação final do .config falhou.");
            pendingConfig = null;
            status("ARQUIVO GRAVADO E VERIFICADO: " + target.getAbsolutePath() + " (" + bytes.length + " bytes). Isso não garante que o UniTV aceite a configuração.");
        } catch (Exception e) {
            status("Falha ao gravar diretamente: " + e.getMessage() + ". Use 'Escolher destino' ou verifique as permissões.");
        } finally {
            if (temp.exists()) temp.delete();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICKER) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null || pendingConfig == null) {
            status("Gravação pelo seletor cancelada.");
            return;
        }
        Uri uri = data.getData();
        byte[] content = pendingConfig.getBytes(StandardCharsets.UTF_8);
        try {
            try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                if (output == null) throw new Exception("Provedor de arquivos não abriu o destino.");
                output.write(content);
                output.flush();
            }
            boolean verified = false;
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input != null) {
                    ByteArrayOutputStream b = new ByteArrayOutputStream();
                    byte[] a = new byte[8192];
                    int k;
                    while ((k = input.read(a)) >= 0) {
                        b.write(a, 0, k);
                        if (b.size() > MAX_CONFIG_BYTES) break;
                    }
                    verified = Arrays.equals(content, b.toByteArray());
                }
            } catch (Exception ignored) {
                // Alguns provedores oferecem apenas gravação.
            }
            pendingConfig = null;
            status(verified ? "Arquivo salvo e verificado no destino escolhido." :
                "Arquivo enviado ao destino escolhido. Não foi possível confirmar a leitura posterior. Verifique no gerenciador de arquivos.");
        } catch (Exception e) {
            status("Falha no seletor de arquivos: " + e.getMessage());
        }
    }

    private void status(String message) {
        if (webView == null) return;
        String json = "\"" + message.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r") + "\"";
        webView.evaluateJavascript("if(window.nativeStatus){window.nativeStatus(" + json + ");}", null);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidLocal");
            webView.destroy();
        }
        super.onDestroy();
    }
}
