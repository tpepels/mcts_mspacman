package pacman.entries.pacman.unimaas.simulation;

import java.util.EnumMap;

import pacman.entries.pacman.unimaas.framework.CauseOfDeath;
import pacman.entries.pacman.unimaas.framework.DiscreteGame;
import pacman.entries.pacman.unimaas.framework.GhostMoveGenerator;
import pacman.entries.pacman.unimaas.framework.MCTResult;
import pacman.entries.pacman.unimaas.framework.PacManMoveGenerator;
import pacman.entries.pacman.unimaas.framework.SelectionType;
import pacman.entries.pacman.unimaas.framework.XSRandom;
import pacman.entries.pacman.unimaas.ghosts.AggressiveGhosts;
import pacman.entries.pacman.unimaas.ghosts.PinchGhostMover;
import pacman.entries.pacman.unimaas.pacman.PacManMover;
import pacman.entries.pacman.unimaas.pacman.RandomNonRevPacMan;
import pacman.game.Constants;
import pacman.game.Constants.GHOST;
import pacman.game.Constants.MOVE;
import pacman.game.Game;

/**
 * 
 * @author Tom Pepels, Maastricht University
 */
public class StrategySimulation {
	// DEBUG
	// private GameView gv;
	// private final boolean DEBUG = false;
	// private Game debugGameState;
	// private DiscreteGame debugDGameState;
	// !DEBUG
	//
	public final int numPills[] = { 220, 240, 238, 234 };
	public static int minSteps = 1;
	public static double pillPower = 1.2;
	public static boolean trailGhost = false;
	// public static double sminGhostNorm = .4;
	// For competition
	// hardMinGhostNorm = .4, easyMinGhostNorm = .55;
	//
	// public double pp_penalty1 = .2, pp_penalty2 = .1;
	public boolean last_good_config = true;
	//
	private GhostMoveGenerator ghostMover;
	private PacManMoveGenerator pacManMover;
	private DiscreteGame dGame;
	private SelectionType selectionType;
	private Game gameState;
	//
	private int maxSimulations;
	private int lastGoodTrailGhost = -1;
	private boolean lastGoodTv = false, lastGoodTG = false;
	//
	private int pillsBefore, pacLocation, tempMaxSim;
	private boolean nextMaze, followingPath;
	private double pillNorm, ghostNorm, pillsEaten, edgePillsEaten, ghostsEaten, tempPills,
			tempGhosts;
	// minGhostNorm;
	//
	// Fields used for determining if pacman was trapped.
	boolean frontBlocked, rearBlocked;
	int pacHeading, pacRear, pacEdge, ghostJ, ghostEdge, ghostHeading, treePhase;
	//
	private int mazeBefore, pwrPillsBefore, currentEdgesVisited;
	private boolean died, atePower, ateGhost, illegalPP;
	private boolean[] ghostVector = new boolean[Constants.NUM_GHOSTS];
	private int[] edibleTimes = new int[Constants.NUM_GHOSTS];
	private MOVE pacMove, lastMove;
	//
	private Game tempGame;
	private DiscreteGame tempDGame;
	//
	public double gameCount, deathCount;

	//
	// public StrategySimulation() {
	// this.minGhostNorm = StrategySimulation.sminGhostNorm;
	// }

	// public void setDecreasedMinGhostNorm() {
	// this.minGhostNorm = StrategySimulation.hardMinGhostNorm;
	// }
	//
	// public void setEasyMinGhostNorm() {
	// this.minGhostNorm = StrategySimulation.easyMinGhostNorm;
	// }

	/**
	 * The number of steps that were made in the tree-phase.
	 */
	public int getTreePhaseSteps() {
		return treePhase;
	}

	boolean[] ghostsAtJunctions = new boolean[Constants.NUM_GHOSTS],
			ghostsAtInitial = new boolean[Constants.NUM_GHOSTS];
	int[] ghostJunctions = new int[Constants.NUM_GHOSTS];
	private EnumMap<GHOST, MOVE> ghostMoves = new EnumMap<GHOST, MOVE>(GHOST.class);

	/**
	 * Advances the current game-state by 1 time-unit.
	 */
	private void advanceGame() {
		// Store the edible time for each edible ghost.
		for (GHOST g : GHOST.values()) {
			// Reset ghosts that were at initial index before the last move
			if (ghostsAtInitial[g.ordinal()]) {
				dGame.setGhostEdgeToInitial(g.ordinal(), gameState.getGhostLastMoveMade(g));
			}
			// Remember the decisions that ghosts made at junctions
			if (ghostsAtJunctions[g.ordinal()] && !gameState.wasGhostEaten(g)
					&& gameState.getTimeOfLastGlobalReversal() != (gameState.getTotalTime() - 1)) {
				try {
					dGame.setGhostMove(g.ordinal(), ghostJunctions[g.ordinal()], ghostMoves.get(g));
				} catch (Exception ex) {
					// System.err.println("Ghost on wrong edge in simulation.");
				}
			}
			// Reset the ghost statuses
			if (gameState.isJunction(gameState.getGhostCurrentNodeIndex(g))) {
				ghostsAtJunctions[g.ordinal()] = true;
				ghostJunctions[g.ordinal()] = gameState.getGhostCurrentNodeIndex(g);
			} else {
				ghostsAtJunctions[g.ordinal()] = false;
			}
			// Check if any ghosts are at the initial ghost-index ( for next move )
			if (gameState.getGhostCurrentNodeIndex(g) == gameState.getGhostInitialNodeIndex()) {
				ghostsAtInitial[g.ordinal()] = true;
			} else {
				ghostsAtInitial[g.ordinal()] = false;
			}
			// Remember if a ghost was edible for scoring using edible times
			if (gameState.isGhostEdible(g) && gameState.getGhostLairTime(g) == 0) {
				edibleTimes[g.ordinal()] = gameState.getGhostEdibleTime(g);
			}
		}
		//
		lastMove = gameState.getPacmanLastMoveMade();
		currentEdgesVisited = dGame.getVisitedEdgeCount();
		// Generate moves for pacman and the ghosts
		ghostMoves.clear();
		ghostMoves = ghostMover.generateGhostMoves();
		if (!followingPath) { // If this is the case, pacmove is predetermined
			pacMove = pacManMover.generatePacManMove(selectionType);
		}
		gameState.advanceGame(pacMove, ghostMoves);

		// Check if pacman died this turn
		died = gameState.wasPacManEaten();
		if (died)
			return;
		// Check if the next maze was reached
		nextMaze = gameState.getMazeIndex() != mazeBefore;
		if (nextMaze)
			return;
		// Check if ghosts reversed
		if (gameState.getTimeOfLastGlobalReversal() == (gameState.getTotalTime() - 1)) {
			dGame.reverseGhosts();
		}
		//
		// if (gv != null) {
		// try {
		// Thread.sleep(40);
		// } catch (InterruptedException e) {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// }
		// gv.repaint();
		// }
		//
		//
		if (gameState.wasPillEaten()) {
			dGame.eatPill();
			pillsEaten++;
			// Check if the edge is cleared.
			if (dGame.getVisitedEdgeCount() > currentEdgesVisited) {
				edgePillsEaten += Math.pow(
						dGame.getEdgeList()[dGame.getCurrentPacmanEdgeId()].pillCount, pillPower);
			}
		}
		// Check if ghosts were eaten.
		ateGhost = false;
		if (gameState.getNumGhostsEaten() > 0) {
			ateGhost = true;
			// Any number of ghosts were eaten last turn.
			for (GHOST g : GHOST.values()) {
				if (gameState.wasGhostEaten(g)) {
					// Ghost was eaten, get score, and register in dGame.
					ghostsEaten += edibleTimes[g.ordinal()];
					dGame.ghostEaten(g.ordinal());
				}
			}
		}
		// Check if a powerpill was eaten
		atePower = false;
		if (gameState.wasPowerPillEaten()) {
			// Don't eat power pills during ghost-score selection
			illegalPP = (selectionType == SelectionType.GhostScore);
			// Further OK!
			atePower = true;
		}
		//
		if (gameState.isJunction(pacLocation)) {
			try {
				dGame.pacMove(pacLocation, pacMove);
			} catch (Exception ex) {
				System.err.println("Pacman on wrong edge in simulation.");
			}
		} else if (lastMove == pacMove.opposite() || lastMove.opposite() == pacMove) {
			// Pacman reversed on the current edge
			dGame.reversePacMan();
		} else {
			// Increase the time pacman has spent on the current edge, this is used for distance measurements.
			dGame.increaseTimeCurrentEdge();
		}
		pacLocation = gameState.getPacmanCurrentNodeIndex();
	}

	/**
	 * Determines if pacman is trapped in the current corridor.
	 */
	private boolean pacManTrapped() {
		frontBlocked = false;
		rearBlocked = false;
		// If there is a powerpill on the current edge, pac-man is probably not
		// blocked
		if (dGame.getCurrentPacmanEdge() != null) {
			if (dGame.getCurrentPacmanEdge().powerPill) {
				if (gameState
						.isPowerPillStillAvailable(dGame.getCurrentPacmanEdge().powerPillIndex)) {
					return false;
				}
			}
		} else {
			return false;
		}

		pacHeading = dGame.getPacHeading();
		pacRear = dGame.getPacRear();
		pacEdge = dGame.getCurrentPacmanEdgeId();

		for (GHOST g : GHOST.values()) {
			if (!gameState.isGhostEdible(g) && gameState.getGhostLairTime(g) == 0) {
				//
				ghostJ = dGame.getLastGhostJunction(g.ordinal());
				ghostEdge = dGame.getGhostEdgeId(g.ordinal());
				ghostHeading = dGame.getGhostHeading(g.ordinal());
				//
				if (ghostJ > 0) {
					//
					if (ghostEdge == pacEdge) {
						// The ghost is on the same edge as pacman
						if (ghostHeading == pacHeading) {
							//
							frontBlocked = true;
							// somethingBlocked = true;
							if (frontBlocked && rearBlocked) {
								return true;
							}
						} else if (ghostHeading == pacRear) {
							//
							rearBlocked = true;
							// somethingBlocked = true;
							if (frontBlocked && rearBlocked) {
								return true;
							}
						}
					}
				}
			}
		}
		return frontBlocked && rearBlocked;
	}

	public MCTResult playout(DiscreteGame discreteGame, Game state, MOVE[] pathMoves,
			int[] pacLocations, int maxSims, SelectionType selectionType, boolean strategic) {
		//
		this.selectionType = selectionType;
		gameState = state;
		dGame = discreteGame;
		maxSimulations = maxSims;
		ghostsAtJunctions = new boolean[Constants.NUM_GHOSTS];
		ghostsAtInitial = new boolean[Constants.NUM_GHOSTS];
		ghostJunctions = new int[Constants.NUM_GHOSTS];
		ghostMoves.clear();
		gameCount++;
		//
		tempDGame = null;
		tempGame = null;

		// TODO DEBUG
		// debugGameState = null;
		// debugDGameState = null;
		// if (XSRandom.r.nextInt(1000) <= 2) {
		// gv = new GameView(gameState).showGame();
		// }

		pacLocation = gameState.getPacmanCurrentNodeIndex();
		if (strategic) {
			ghostMover = new PinchGhostMover(gameState, dGame);
		} else {
			ghostMover = new AggressiveGhosts(gameState);
		}

		// Set the targets for the ghosts
		if (trailGhost) {

			if (!lastGoodTG || !last_good_config) {
				lastGoodTrailGhost = XSRandom.r.nextInt(Constants.NUM_GHOSTS);
			}

			((PinchGhostMover) ghostMover).setTrailGhost(lastGoodTrailGhost);
		} else {

			if (!lastGoodTv || !last_good_config) {
				for (int i = 0; i < Constants.NUM_GHOSTS; i++) {
					ghostVector[i] = XSRandom.r.nextBoolean();
				}
			}

			((PinchGhostMover) ghostMover).setTargetVector(ghostVector);
		}
		//
		if (strategic) {
			pacManMover = new PacManMover(gameState, dGame);
		} else {
			pacManMover = new RandomNonRevPacMan(gameState);
		}
		// Set the pre-game variables, these will be used for scoring later
		// pillsBefore = gameState.getNumberOfActivePills();
		pillsBefore = numPills[gameState.getMazeIndex()];
		mazeBefore = gameState.getMazeIndex();
		pwrPillsBefore = gameState.getNumberOfActivePowerPills();
		//
		double ghostDivisor = 0.;
		// The edibletimes for the ghosts at the start of the simulation
		for (GHOST g : GHOST.values()) {
			// Remember if a ghost was edible for scoring using edible times
			if (gameState.isGhostEdible(g) && gameState.getGhostLairTime(g) == 0) {
				ghostDivisor += gameState.getGhostEdibleTime(g);
			}
		}

		// Reset the scoring values
		pillsEaten = 0;
		edgePillsEaten = 0;
		tempPills = 0;
		tempGhosts = 0;
		tempMaxSim = 0;
		ghostsEaten = 0;
		//
		nextMaze = false;
		ateGhost = false;
		atePower = false;
		illegalPP = false;
		died = false;

		// First follow the path to the position represented by the node
		followingPath = true;
		int i = 0, steps = 0, destination;
		tempGame = null;
		tempDGame = null;
		treePhase = 0;
		for (i = pathMoves.length - 1; i >= 0; i--) {
			// Execute the first path-move to determine a direction
			pacMove = pathMoves[i];
			//
			advanceGame();
			treePhase++;
			maxSimulations--;
			//
			if (died || nextMaze || ateGhost || atePower || illegalPP) {
				break;
			}
			//
			if (i < 0) {
				destination = dGame.getPacHeading();
			} else {
				destination = pacLocations[i];
			}
			//
			steps++;
			//
			if (steps > minSteps) {
				tempPills = pillsEaten;
				tempGhosts = ghostsEaten;
				tempMaxSim = maxSimulations;
				tempDGame = dGame.copy();
				tempGame = gameState.copy();
			}
			// If there is a power-pill on the current edge, stop following path
			if (dGame.getCurrentPacmanEdge() != null && selectionType == SelectionType.GhostScore) {
				if (dGame.getCurrentPacmanEdge().powerPill) {
					if (gameState
							.isPowerPillStillAvailable(dGame.getCurrentPacmanEdge().powerPillIndex)) {
						break;
					}
				}
			}
			// Follow the chosen path!
			while (pacLocation != destination) {
				pacMove = gameState
						.getPossibleMoves(pacLocation, gameState.getPacmanLastMoveMade())[0];
				//
				advanceGame();
				maxSimulations--;
				//
				if (died || nextMaze || ateGhost || atePower || illegalPP) {
					break;
				}
			}
			//
			if (died || nextMaze || ateGhost || atePower || illegalPP) {
				break;
			}
		}
		//
		if (died && steps > minSteps) {
			maxSimulations = tempMaxSim;
			pillsEaten = tempPills;
			ghostsEaten = tempGhosts;
			currentEdgesVisited = tempDGame.getVisitedEdgeCount();
			pacLocation = tempGame.getPacmanCurrentNodeIndex();
			//
			this.gameState = tempGame;
			this.dGame = tempDGame;
			//
			died = false;
			pacManMover = new PacManMover(gameState, dGame);
			ghostMover = new PinchGhostMover(gameState, dGame);
			CauseOfDeath.redo++;
			//
		} else if (died) {
			CauseOfDeath.tree++;
		}

		// Playout phase
		if (!died && !nextMaze && !illegalPP) {
			followingPath = false;
			for (int j = 0; j < maxSimulations; j++) {
				advanceGame();
				//
				if (nextMaze || died || illegalPP) {
					break;
				}
			}
		}
		//
		if (!died) {
			//
			// If pacman is trapped in the current path, death.
			died = pacManTrapped();
			if (died) {
				CauseOfDeath.trapped++;
			}
			//
		} else if (!followingPath) {
			//
			CauseOfDeath.simulation++;
		}
		//
		if (died) {
			deathCount++;
			//
			lastGoodTG = true;
			lastGoodTv = true;
		} else {
			lastGoodTG = false;
			lastGoodTv = false;
		}

		//
		pillNorm = .0;
		ghostNorm = .0;
		// Determine the pill-score
		if (nextMaze && gameState.getCurrentLevelTime() < Constants.LEVEL_LIMIT) {
			pillNorm = 1.;
		} else if (pwrPillsBefore <= gameState.getNumberOfActivePowerPills()
				&& (pillsEaten > 0 || edgePillsEaten > 0)
				&& selectionType != SelectionType.GhostScore) {
			//
			pillNorm = Math.max(pillsEaten, edgePillsEaten) / pillsBefore;
		}
		// Determine the ghost score
		if (ghostsEaten > 0) {
			if (ghostDivisor == 0) {
				// Mathemagics!!
				ghostDivisor = Constants.NUM_GHOSTS
						* (Constants.EDIBLE_TIME * (Math.pow(Constants.EDIBLE_TIME_REDUCTION,
								gameState.getCurrentLevel() % Constants.LEVEL_RESET_REDUCTION)));
			}
			//
			ghostNorm = ghostsEaten / ghostDivisor;
		}
		return new MCTResult(pillNorm, ghostNorm, !died);
	}
}
