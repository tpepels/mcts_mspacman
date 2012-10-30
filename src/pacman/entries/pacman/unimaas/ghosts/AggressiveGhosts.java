package pacman.entries.pacman.unimaas.ghosts;

import java.util.EnumMap;
import java.util.Random;

import pacman.entries.pacman.unimaas.framework.GhostMoveGenerator;
import pacman.game.Constants.DM;
import pacman.game.Constants.GHOST;
import pacman.game.Constants.MOVE;
import pacman.game.Game;

/**
 * The Class AttractRepelGhosts.
 */
public final class AggressiveGhosts implements GhostMoveGenerator {
	private final static float CONSISTENCY = .95f;
	private Random rnd = new Random();
	private EnumMap<GHOST, MOVE> myMoves = new EnumMap<GHOST, MOVE>(GHOST.class);
	private MOVE[] moves = MOVE.values();
	private Game game;

	public AggressiveGhosts(Game game) {
		this.game = game;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see pacman.controllers.Controller#getMove(pacman.game.Game, long)
	 */
	@Override
	public EnumMap<GHOST, MOVE> generateGhostMoves() {
		myMoves.clear();

		for (GHOST ghost : GHOST.values())
			// for each ghost
			if (game.doesGhostRequireAction(ghost)) // if it requires an action
			{
				if (rnd.nextFloat() < CONSISTENCY) // approach/retreat from the
													// current node that Ms
													// Pac-Man is at
					myMoves.put(
							ghost,
							game.getApproximateNextMoveTowardsTarget(
									game.getGhostCurrentNodeIndex(ghost),
									game.getPacmanCurrentNodeIndex(),
									game.getGhostLastMoveMade(ghost), DM.PATH));
				else
					// else take a random action
					myMoves.put(ghost, moves[rnd.nextInt(moves.length)]);
			}

		return myMoves;
	}

	public void setGameStates(Game gameState) {
		this.game = gameState;
	}
}