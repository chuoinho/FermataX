package me.aap.fermata.ui.smarttop;

import java.util.Objects;

/**
 * Measured runtime environment shared by phone, tablet, mirror and Android Auto/DHU.
 * Space is described by the actual content viewport; interaction requirements remain a separate axis.
 */
public record SmartTopEnvironment(
		float contentWidthDp,
		float viewportHeightDp,
		float fontScale,
		SmartTopInteractionProfile interaction) {
	public SmartTopEnvironment {
		contentWidthDp = Math.max(0F, contentWidthDp);
		viewportHeightDp = Math.max(0F, viewportHeightDp);
		fontScale = Math.max(0.5F, fontScale);
		Objects.requireNonNull(interaction, "interaction");
	}
}
