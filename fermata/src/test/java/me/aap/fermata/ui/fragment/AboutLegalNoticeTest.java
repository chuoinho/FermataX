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
        } else {
            assertEquals("Mobile About content stays unchanged", original, html);
        }
    }
}
