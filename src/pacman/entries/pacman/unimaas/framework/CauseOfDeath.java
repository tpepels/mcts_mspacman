package pacman.entries.pacman.unimaas.framework;

public class CauseOfDeath {
	public static int tree, simulation, trapped, powerpill, redo;

	public static void print() {
		System.out.println("| Tree: " + tree + " simulation: " + simulation + " || trapped: " + trapped
				+ " powerpill: " + powerpill + " | redo: " + redo);
	}

	public static void reset() {
		tree = 0;
		simulation = 0;
		trapped = 0;
		powerpill = 0;
		redo = 0;
	}
}
