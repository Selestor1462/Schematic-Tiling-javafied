package schematic.tiling.client.util;

import schematic.tiling.cannon.PearlFlightSimulator;

/**
 * Legacy wrapper: particle trajectory rendering replaced with 3D line renderer (PearlTrajectoryLineRenderer)
 * to eliminate lag and support environments with particles disabled.
 */
public class PearlTrajectoryParticleRenderer {

    public static void displayTrajectory(PearlFlightSimulator.SimulationResult sim, int durationTicks) {
        PearlTrajectoryLineRenderer.displayTrajectory(sim, durationTicks);
    }

    public static void clearTrajectory() {
        PearlTrajectoryLineRenderer.clearTrajectory();
    }

    public static boolean isDisplaying() {
        return PearlTrajectoryLineRenderer.isDisplaying();
    }
}
