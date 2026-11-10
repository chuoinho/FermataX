package me.aap.fermata.ui.fragment;

import static org.junit.Assert.*;
import android.content.Context;
import androidx.core.text.HtmlCompat;
import me.aap.fermata.BuildConfig;
import me.aap.fermata.R;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class AboutLegalNoticeTest {
    @Test public void existingAboutIncludesBundledLegalTextOnlyForAuto() {
        Context context = RuntimeEnvironment.getApplication();
        String html = NavBarMediator.getAboutHtml(context);
        String original = context.getString(R.string.about_html, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE);
        if (BuildConfig.AUTO) {
            String rendered = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY).toString();
            assertTrue("Auto About must actually display libadb attribution", rendered.contains("LibADB Android 3.1.1"));
            assertTrue(rendered.contains("GNU LESSER GENERAL PUBLIC LICENSE"));
            assertTrue(rendered.contains("Corresponding source"));
            assertFalse("Conscrypt serves every ADB TLS session, not only the pairing", rendered.contains("pairing TLS only"));
            assertFalse("SslUtils is one of the patched files", rendered.contains("The unmodified `SslUtils`"));
            // Conscrypt 2.8.0 LICENSE/NOTICE and licenses/ texts, and both BoringSSL licence texts, verbatim.
            assertTrue("Conscrypt LICENSE NIST terms", rendered.contains("National Institute of Standards"));
            assertTrue("Conscrypt NOTICE", rendered.contains("This product contains a modified portion of `Netty`"));
            assertTrue("Conscrypt NOTICE", rendered.contains("This product contains a modified portion of `Apache Harmony`"));
            assertTrue("Netty/Harmony licence text", rendered.contains("LICENSE.netty.txt and LICENSE.harmony.txt"));
            assertTrue("BoringSSL legacy licence", rendered.contains("OpenSSL License"));
            assertTrue("BoringSSL legacy licence", rendered.contains("Original SSLeay License"));
            assertTrue("BoringSSL legacy licence", rendered.contains("ISC license used for completely new code in BoringSSL"));
            assertTrue("BoringSSL current licence", rendered.contains("Parts of the TLS test suite are under the Go license"));
            assertTrue("Honest revision note", rendered.contains("BoringSSL revision built into conscrypt-android 2.8.0 is not known"));
            assertTrue("Page size stays reasonable", html.length() < 250_000);
        } else {
            assertEquals("Mobile About content stays unchanged", original, html);
        }
    }
}
