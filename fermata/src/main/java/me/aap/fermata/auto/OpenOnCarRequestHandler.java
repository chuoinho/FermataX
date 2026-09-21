package me.aap.fermata.auto;

import java.util.function.BooleanSupplier;

/** Module-neutral typed receiver; implemented by the attached destination fragment. */
public interface OpenOnCarRequestHandler {
	AutomotiveNavigationController.OpenResult openOnCar(OpenOnCarRequest request,
			BooleanSupplier stillCurrent);
}
