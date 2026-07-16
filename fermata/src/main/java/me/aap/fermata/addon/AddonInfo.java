package me.aap.fermata.addon;

import static java.util.Arrays.asList;

import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.StringRes;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import me.aap.utils.collection.CollectionUtils;
import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.pref.PreferenceStore;

/**
 * @author Andrey Pavlenko
 */
public class AddonInfo implements Comparable<AddonInfo> {
	public final String moduleName;
	public final String className;
	@IdRes
	public final int addonId;
	@StringRes
	public final int addonName;
	@DrawableRes
	public final int icon;
	public final int order;
	public final boolean hasSettings;
	public final boolean hasFragment;
	public final boolean isAuto;
	public final boolean enableByDefault;
	public final String[] depends;
	public final PreferenceStore.Pref<BooleanSupplier> enabledPref;
	/** Stable voice routing key declared by the addon, or an empty string. */
	public final String voiceTarget;
	private final EnumSet<AddonCapability> capabilities;

	public AddonInfo(String moduleName, String className, int addonId, int addonName, int icon,
									 Integer order, Boolean hasSettings, Boolean hasFragment, Boolean enabled,
									 boolean isAuto, String depends) {
		this(moduleName, className, addonId, addonName, icon, order, hasSettings, hasFragment,
				enabled, isAuto, depends,
				Boolean.TRUE.equals(hasFragment) ? "dashboard,navigation" : "", "");
	}

	public AddonInfo(String moduleName, String className, int addonId, int addonName, int icon,
									 Integer order, Boolean hasSettings, Boolean hasFragment, Boolean enabled,
									 boolean isAuto, String depends, String capabilities) {
		this(moduleName, className, addonId, addonName, icon, order, hasSettings, hasFragment,
				enabled, isAuto, depends, capabilities, "");
	}

	public AddonInfo(String moduleName, String className, int addonId, int addonName, int icon,
									 Integer order, Boolean hasSettings, Boolean hasFragment, Boolean enabled,
									 boolean isAuto, String depends, String capabilities, String voiceTarget) {
		this.moduleName = moduleName;
		this.className = className;
		this.addonId = addonId;
		this.addonName = addonName;
		this.icon = icon;
		this.order = (order == null) ? 1000 : order;
		this.hasSettings = (hasSettings == null) || hasSettings;
		this.hasFragment = (hasFragment != null) && hasFragment;
		this.isAuto = isAuto;
		this.enableByDefault = (enabled == null) || enabled;
		this.depends = CollectionUtils.filter(asList(depends.split("[, \\[\\]]")), s -> !s.isEmpty())
				.toArray(new String[0]);
		this.voiceTarget = (voiceTarget == null) ? "" : voiceTarget.trim();
		this.capabilities = AddonCapability.parse(capabilities);
		enabledPref = PreferenceStore.Pref.b(className + "_enabled",
				(enabled == null) ? this::isInstalled : () -> enabled);
	}

	public boolean hasCapability(AddonCapability capability) {
		return capabilities.contains(capability);
	}

	public Set<AddonCapability> getCapabilities() {
		return Collections.unmodifiableSet(capabilities);
	}

	public boolean isInstalled() {
		try {
			Class.forName(className);
			return true;
		} catch (Exception ignore) {
			return false;
		}
	}

	@Override
	public int compareTo(AddonInfo ai) {
		var cmp = Integer.compare(order, ai.order);
		return cmp == 0 ? moduleName.compareTo(ai.moduleName) : cmp;
	}
}
