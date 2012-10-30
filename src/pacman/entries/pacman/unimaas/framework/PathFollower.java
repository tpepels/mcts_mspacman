package pacman.entries.pacman.unimaas.framework;

import java.util.EnumMap;

import pacman.game.Constants.GHOST;
import pacman.game.Constants.MOVE;
import pacman.game.Game;

/**
 * 
 * @author Tom Pepels, Maastricht University
 */
public class PathFollower {

	public int movesDone, pacLivesBefore, mazeBefore;
	//

	private boolean advanceGame(MOVE pacMove, EnumMap<GHOST, MOVE> ghostMoves, Game gameState) {
		gameState.advanceGame(pacMove, ghostMoves);
		return gameState.wasPacManEaten();
	}

	public MOVE[] followPath(Game gameState, GhostMoveGenerator gMoves, MOVE direction) {
		//
		pacLivesBefore = gameState.getPacmanNumberOfLivesRemaining();
		mazeBefore = gameState.getMazeIndex();
		movesDone = 0;
		//
		int nmoves = 1;
		MOVE[] pacManMoves = null;
		MOVE pacMove = direction;
		EnumMap<GHOST, MOVE> ghostMoves = gMoves.generateGhostMoves();
		boolean died = advanceGame(pacMove, ghostMoves, gameState);

		movesDone++;
		
		if (died) {
			return new MOVE[0];
		}

		while (nmoves == 1) {
			// Check for the number of pacman moves
			pacManMoves = gameState.getPossibleMoves(gameState.getPacmanCurrentNodeIndex(),
					gameState.getPacmanLastMoveMade());
			nmoves = pacManMoves.length;

			// More than one move possible, thus start generating children
			if (nmoves > 1) {
				break;
			}

			pacMove = pacManMoves[0];
			ghostMoves.clear();
			ghostMoves = gMoves.generateGhostMoves();
			died = advanceGame(pacMove, ghostMoves, gameState);

			if (died) {
				return new MOVE[0];
			}

			if (mazeBefore != gameState.getMazeIndex()) {
				return pacManMoves;
			}

			movesDone++;
		}

		// A node was reached where a decision needs to be made.
		return pacManMoves;
	}
}
