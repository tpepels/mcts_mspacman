package pacman.entries.pacman;

import pacman.controllers.Controller;
import pacman.entries.pacman.unimaas.framework.DiscreteGame;
import pacman.entries.pacman.unimaas.pacman.PacManMover;
import pacman.game.Constants.GHOST;
import pacman.game.Constants.MOVE;
import pacman.game.Constants;
import pacman.game.Game;

public class SimulationPacMan extends Controller<MOVE> {
	// Set true for debugging output
	private final boolean DEBUG = false;
	
	// Maximum length of a tree-path and maximum simulation steps in simulation phase
	public double maxPathLength, maxSimulations, initMaxPathLength;
	// Safety and minimum ghost score parameters.
	public double safetyT, ghostSelectScore, reversePenalty, discount; // Decay factor for the tree decay
	//
	public boolean reuse = true, decay = true, var_depth = true, strategic_playout = true,
			max_selection = true;
	public int maxNodeDepth = 5; // For fixed node depth tests
	// Counters etc..
	private boolean atJunction = false, prevLocationWasJunction = false;
	private final boolean[] ghostsAtJunctions = new boolean[4], ghostsAtInitial = new boolean[4];
	private final int[] ghostJunctions = new int[4];
	private int lastJunction = -1, currentMaze = -1, pacLives = Constants.NUM_LIVES;
	private MOVE move, lastTurnMove, lastJunctionMove;
	// Gamestate
	private DiscreteGame dGame = null;
	private Game gameState;
	
	public SimulationPacMan() {
		
	}
	
	@Override
	public MOVE getMove(Game game, long timeDue) {
		gameState = game;
		updateDiscreteGamePreMove();
		PacManMover pmm = new PacManMover(game, dGame);
		move = pmm.generatePacManMove();
		// Reset the prevlocation value
		if (!atJunction) {
			prevLocationWasJunction = false;
		}
		return move;
	}
	
	/**
	 * Updates the discrete gamestate based on the current gamestate before move-selection
	 */
	private void updateDiscreteGamePreMove() {
		// First call should create a new discrete gamestate each game
		if (gameState.getTotalTime() == 0 || dGame == null) {
			dGame = new DiscreteGame(gameState);
			// timesDiedInFirstMaze = 0;
		}
		// Either the game just started or pacman entered a new maze
		if (gameState.getMazeIndex() != currentMaze || gameState.getTotalTime() == 0) {
			// System.out.println("Maze: " + gameState.getMazeIndex() + " pills: " +
			// gameState.getNumberOfActivePills());
			dGame.setCurrentMaze(gameState);
			//
			currentMaze = gameState.getMazeIndex();
			lastJunction = -1;
			lastJunctionMove = MOVE.NEUTRAL;
			lastTurnMove = MOVE.NEUTRAL;
			pacLives = gameState.getPacmanNumberOfLivesRemaining();
			prevLocationWasJunction = false;
		}

		// Pacman died
		if (gameState.wasPacManEaten()) {
			if (DEBUG)
				System.out.println("I Died here !!!=======================================");
			lastJunction = -1;
			lastJunctionMove = MOVE.NEUTRAL;
			lastTurnMove = MOVE.NEUTRAL;
			pacLives = gameState.getPacmanNumberOfLivesRemaining();
			prevLocationWasJunction = false;
			//
			dGame.pacmanDied();

		} else if (pacLives < gameState.getPacmanNumberOfLivesRemaining()) {
			// Pacman gained a life (happens after the first 10.000 points)
			pacLives = gameState.getPacmanNumberOfLivesRemaining();
		}
		// Store move data in the discrete game state
		if (prevLocationWasJunction) {
			// Pacman is at a junction, store this information in the gamestate.
			lastJunctionMove = gameState.getPacmanLastMoveMade();
			try {
				dGame.pacMove(lastJunction, lastJunctionMove);
			} catch (Exception ex) {
				System.err.println("Nullpointer!");
			}
		} else if (lastTurnMove == gameState.getPacmanLastMoveMade().opposite()
				|| lastTurnMove.opposite() == gameState.getPacmanLastMoveMade()) {
			// Pacman reversed on the current edge
			dGame.reversePacMan();
		} else {
			// Increase the time pacman has spent on the current edge,
			// this is used for distance measurements.
			dGame.increaseTimeCurrentEdge();
		}
		lastTurnMove = gameState.getPacmanLastMoveMade();

		// Update the number of pills remaining on the current edge
		if (gameState.wasPillEaten()) {
			dGame.eatPill();
		}
		//
		atJunction = gameState.isJunction(gameState.getPacmanCurrentNodeIndex());

		if (atJunction) {
			//
			lastJunction = gameState.getPacmanCurrentNodeIndex();
			if (DEBUG)
				System.out.println("Junction!");
			prevLocationWasJunction = true;
		}

		if (gameState.getTimeOfLastGlobalReversal() == (gameState.getTotalTime() - 1)) {
			dGame.reverseGhosts();
		}

		for (GHOST g : GHOST.values()) {
			if (ghostsAtInitial[g.ordinal()]) {
				dGame.setGhostEdgeToInitial(g.ordinal(), gameState.getGhostLastMoveMade(g));
			}

			if (gameState.getGhostCurrentNodeIndex(g) == gameState.getGhostInitialNodeIndex()) {
				ghostsAtInitial[g.ordinal()] = true;
			} else {
				ghostsAtInitial[g.ordinal()] = false;
			}

			if (ghostsAtJunctions[g.ordinal()] && !gameState.wasGhostEaten(g)
					&& gameState.getTimeOfLastGlobalReversal() != (gameState.getTotalTime() - 1)) {
				try {
					dGame.setGhostMove(g.ordinal(), ghostJunctions[g.ordinal()],
							gameState.getGhostLastMoveMade(g));
				} catch (Exception ex) {
					// System.err.println("Ghost on wrong edge!");
				}
			}
			// Reset the ghost statuses
			if (gameState.isJunction(gameState.getGhostCurrentNodeIndex(g))) {
				ghostsAtJunctions[g.ordinal()] = true;
				ghostJunctions[g.ordinal()] = gameState.getGhostCurrentNodeIndex(g);
			} else {
				ghostsAtJunctions[g.ordinal()] = false;
			}
		}
	}

}
