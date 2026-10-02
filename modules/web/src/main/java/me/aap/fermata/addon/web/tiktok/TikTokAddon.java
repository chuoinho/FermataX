package me.aap.fermata.addon.web.tiktok;

import androidx.annotation.Keep;

import me.aap.fermata.addon.web.WebBrowserAddon;

/**
 * In-app TikTok Web Addon descriptor for FermataX.
 */
@Keep
public class TikTokAddon extends WebBrowserAddon {

    public TikTokAddon() {
        super("tiktok", "https://www.tiktok.com/foryou");
    }

    @Override
    public int getAddonId() {
        return me.aap.fermata.R.id.tiktok_fragment;
    }
}
