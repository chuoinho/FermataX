package me.aap.fermata.location;

import java.util.Locale;

public enum LocationSource {
 GPS, GOOGLE_FUSED, PLATFORM_FUSED;

 public String id() {
  return name().toLowerCase(Locale.ROOT);
 }
}
