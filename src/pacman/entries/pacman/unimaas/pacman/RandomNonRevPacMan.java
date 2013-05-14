package pacman.entries.pacman.unimaas.pacman;

import java.util.Random;

import pacman.entries.pacman.unimaas.framework.PacManMoveGenerator;
import pacman.game.Constants.MOVE;
import pacman.game.Game;

public final class RandomNonRevPacMan implements PacManMoveGenerator {
	Random rnd = new Random();
	Game gameState;
	
	public RandomNonRevPacMan(Game game) {
		gameState = game;
	}
	
	public MOVE generatePacManMove() {
		MOVE[] possibleMoves = gameState.getPossibleMoves(gameState.getPacmanCurrentNodeIndex(),
				gameState.getPacmanLastMoveMade()); 

		return possibleMoves[rnd.nextInt(possibleMoves.length)];
	}
}