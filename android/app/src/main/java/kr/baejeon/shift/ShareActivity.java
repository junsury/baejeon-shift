package kr.baejeon.shift;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;

/**
 * 근무표 웹앱에서 [카톡 등으로 공유하기]를 누르면 baejeonshift://share?id=… 로 여기가 열려요.
 * 서버에 잠깐 올려 둔 이미지·PDF를 받아서 휴대폰 공유창(카카오톡 등)을 띄워요.
 * (크롬 웹 공유창이 깜빡이며 닫히는 폰이 있어서 앱이 직접 띄워요)
 */
public class ShareActivity extends Activity {
    static final String API = "https://asia-northeast3-baejeon-shift.cloudfunctions.net/shareFile?t=gyeonggi&id=";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Uri u = getIntent().getData();
        final String id = u != null ? u.getQueryParameter("id") : null;
        if (id == null || !id.matches("[A-Za-z0-9]{24,48}")) { finish(); return; }
        Toast.makeText(this, "공유 준비 중…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(API + id).openConnection();
                c.setConnectTimeout(15000); c.setReadTimeout(30000);
                if (c.getResponseCode() != 200) throw new Exception("http " + c.getResponseCode());
                String type = c.getContentType() == null ? "image/png" : c.getContentType().split(";")[0].trim();
                String name = c.getHeaderField("X-Name");
                name = name == null ? "" : URLDecoder.decode(name, "UTF-8");
                name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
                if (name.isEmpty()) name = "pdf".equals(type.substring(type.indexOf('/') + 1)) ? "근무표.pdf" : "근무표.png";
                File dir = new File(getCacheDir(), "share");
                dir.mkdirs();
                File[] olds = dir.listFiles();
                if (olds != null) for (File f : olds) f.delete();
                File f = new File(dir, name);
                try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(f)) {
                    byte[] buf = new byte[16384]; int r;
                    while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
                }
                final Uri fu = FileProvider.getUriForFile(this, getPackageName() + ".share", f);
                final String mime = type;
                runOnUiThread(() -> {
                    Intent s = new Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, fu)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    s.setClipData(ClipData.newRawUri("", fu));
                    Intent ch = Intent.createChooser(s, "보낼 곳 고르기");
                    ch.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(ch);
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "공유할 파일을 받지 못했어요. 다시 시도해 주세요.", Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        }).start();
    }
}
