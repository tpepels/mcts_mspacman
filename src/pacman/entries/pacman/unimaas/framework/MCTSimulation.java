package pacman.entries.pacman.unimaas.framework;

import pacman.game.Constants.MOVE;
import pacman.game.Game;

/**
 *
 * @author Tom Pepels, Maastricht University
 */
public interface MCTSimulation {
	public MCTResult playout(DiscreteGame dGame, Game gameState, MOVE[] pathMoves, int[] pacLocations,
			int maxSimulations, SelectionType selectionType);

	public MCTResult playout(DiscreteGame dGame, Game gameState, MOVE[] pathMoves, int[] pacLocations,
			int maxSimulations, SelectionType selectionType, int target);
}
