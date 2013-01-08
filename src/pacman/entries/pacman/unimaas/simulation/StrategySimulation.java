package pacman.entries.pacman.unimaas.simulation;

import java.util.EnumMap;

import pacman.entries.pacman.unimaas.framework.CauseOfDeath;
import pacman.entries.pacman.unimaas.framework.DiscreteGame;
import pacman.entries.pacman.unimaas.framework.MCTResult;
import pacman.entries.pacman.unimaas.framework.MCTSimulation;
import pacman.entries.pacman.unimaas.framework.SelectionType;
import pacman.entries.pacman.unimaas.framework.XSRandom;
import pacman.entries.pacman.unimaas.ghosts.PinchGhostMover;
import pacman.entries.pacman.unimaas.pacman.PacManMover;
import pacman.game.Constants;
import pacman.game.Constants.GHOST;
import pacman.game.Constants.MOVE;
import pacman.game.Game;

/**
 * 
 * @author Tom Pepels, Maastricht University
 */
public class StrategySimulation implements MCTSimulation {
	// DEBUG
	// private GameView gv;
	// private final boolean DEBUG = false;
	// private Game debugGameState;
	// private DiscreteGame debugDGameState;
	// !DEBUG
	//
	public static int minSteps = 1;
	public static double pillPower = 1.3;
	public static boolean trailGhost = false;
	public static double sminGhostNorm = .5, sdecreasedMinGhostNorm = .4, easyMinGhostNorm = .6;
	//
	private PinchGhostMover ghostMover;
	private PacManMover pacManMover;
	private DiscreteGame dGame;
	private SelectionType selectionType;
	private Game gameState;
	//
	private boolean targetSelection = false;
	private int maxSimulations;
	private int lastGoodTrailGhost = -1;
	private boolean lastGoodTv = false, lastGoodTG = false;
	//
	private int pillsBefore, pacLocation, tempMaxSim;
	private boolean nextMaze, followingPath;
	private double pillNorm, ghostNorm, pillsEaten, edgePillsEaten, ghostsEaten, tempPills,
			tempGhosts, minGhostNorm;
	//
	// Fields used for determining if pacman was trapped.
	boolean frontBlocked, rearBlocked;
	int pacHeading, pacRear, pacEdge, ghostJ, ghostEdge, ghostHeading, treePhase;
	//
	private int mazeBefore, pwrPillsBefore, currentEdgesVisited;
	private boolean died, targetReached, atePower, ateGhost, illegalPP;
	private boolean[] ghostVector = new boolean[Constants.NUM_GHOSTS],
			ghostsAtInitial = new boolean[Constants.NUM_GHOSTS];
	private int[] edibleTimes = new int[Constants.NUM_GHOSTS];
	private MOVE pacMove, lastMove;
	//
	private Game tempGame;
	private DiscreteGame tempDGame;
	//
	public double gameCount, deathCount;

	//
	public StrategySimulation() {
		this.minGhostNorm = StrategySimulation.sminGhostNorm;
	}

	public void setDecreasedMinGhostNorm() {
		this.minGhostNorm = StrategySimulation.sdecreasedMinGhostNorm;
	}

	public void setEasyMinGhostNorm() {
		this.minGhostNorm = StrategySimulation.easyMinGhostNorm;
	}

	/**
	 * The number of steps that were made in the tree-phase.
	 */
	public int getTreePhaseSteps() {
		return treePhase;
	}

	boolean[] ghostsAtJunctions = new boolean[Constants.NUM_GHOSTS];
	int[] ghostJunctions = new int[Constants.NUM_GHOSTS];
	private EnumMap<GHOST, MOVE> ghostMoves = new EnumMap<GHOST, MOVE>(GHOST.class);

	/**
	 * Advances the current game-state by 1 time-unit.
	 */
	private void advanceGame() {
		// Store the edible time for each edible ghost.
		for (GHOST g : GHOST.values()) {
			// Reset ghosts that were at initial index
			if (ghostsAtInitial[g.ordinal()]) {
				dGame.setGhostEdgeToInitial(g.ordinal(), gameState.getGhostLastMoveMade(g));
			}
			// Remember the decisions that ghosts made at junctions
			if (ghostsAtJunctions[g.ordinal()] && !gameState.wasGhostEaten(g)) {
				dGame.setGhostMove(g.ordinal(), ghostJunctions[g.ordinal()],
						ghostMoves.get(g));
			}
			// Reset the ghost statuses
			if (gameState.isJunction(gameState.getGhostCurrentNodeIndex(g))) {
				ghostsAtJunctions[g.ordinal()] = true;
				ghostJunctions[g.ordinal()] = gameState.getGhostCurrentNodeIndex(g);
			} else {
				ghostsAtJunctions[g.ordinal()] = false;
			}
			// Check if any ghosts are at the initial ghost-index
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
		ghostMoves.clear();
		//
		if (!followingPath) { // If this is the case, pacmove is predetermined
			pacMove = pacManMover.generatePacManMove(selectionType);
		}
		//
		lastMove = gameState.getPacmanLastMoveMade();
		currentEdgesVisited = dGame.getVisitedEdgeCount();
		ghostMoves = ghostMover.generateGhostMoves(false);
		gameState.advanceGameWithPowerPillReverseOnly(pacMove, ghostMoves);

		// Check if pacman died this turn
		died = gameState.wasPacManEaten();
		//
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
		if (gameState.isJunction(pacLocation)) {
			//
			dGame.pacMove(pacLocation, pacMove);
		} else if (lastMove == pacMove.opposite() || lastMove.opposite() == pacMove) {
			// Pacman reversed on the current edge
			dGame.reversePacMan();
		} else {
			// Increase the time pacman has spent on the current edge,
			// this is used for distance measurements.
			dGame.increaseTimeCurrentEdge();
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
		pacLocation = gameState.getPacmanCurrentNodeIndex();
		//
		if (gameState.wasPillEaten()) {
			dGame.eatPill();
			// pillsEaten += Math.pow(.999, maxSimulations);
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
	}

	/**
	 * Determines if pacman is trapped in the current path.
	 * 
	 * @return
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

	@Override
	public MCTResult playout(DiscreteGame discreteGame, Game state, MOVE[] pathMoves,
			int[] pacLocations, int maxSims, SelectionType selectionType) {
		//
		this.selectionType = selectionType;
		this.gameState = state;
		this.dGame = discreteGame;
		this.maxSimulations = maxSims;
		ghostsAtJunctions = new boolean[Constants.NUM_GHOSTS];
		ghostJunctions = new int[Constants.NUM_GHOSTS];
		ghostMoves.clear();
		gameCount++;
		// debugGameState = null;
		// debugDGameState = null;
		tempDGame = null;
		tempGame = null;

		// if (XSRandom.r.nextInt(1000) <= 2) {
		// gv = new GameView(gameState).showGame();
		// }
		pacLocation = gameState.getPacmanCurrentNodeIndex();
		ghostMover = new PinchGhostMover(gameState, dGame);
		// Set the targets for the ghosts
		if (trailGhost) {
			if (!lastGoodTG)
				lastGoodTrailGhost = XSRandom.r.nextInt(Constants.NUM_GHOSTS);
			((PinchGhostMover) ghostMover).setTrailGhost(lastGoodTrailGhost);
		} else {
			if (!lastGoodTv) {
				for (int i = 0; i < Constants.NUM_GHOSTS; i++) {
					ghostVector[i] = XSRandom.r.nextBoolean();
				}
			}
			((PinchGhostMover) ghostMover).setTargetVector(ghostVector);
		}
		//
		pacManMover = new PacManMover(gameState, dGame);
		// Set the pre-game variables, these will be used for scoring later
		pillsBefore = gameState.getNumberOfActivePills();
		mazeBefore = gameState.getMazeIndex();
		pwrPillsBefore = gameState.getNumberOfActivePowerPills();

		// Reset the scoring values
		nextMaze = false;
		pillsEaten = 0;
		edgePillsEaten = 0;
		tempPills = 0;
		tempGhosts = 0;
		tempMaxSim = 0;
		ghostsEaten = 0;
		ateGhost = false;
		atePower = false;
		illegalPP = false;
		targetReached = false;
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
			try {
				advanceGame();
				treePhase++;
			} catch (Exception e) {
				System.err.println("Nullpointer :(");
				break;
			}
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
				// clearedEdgeOnPath = edgeCleared;
				//
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

		if (maxSimulations < 20) {
			System.err.println("almost not simulations left!");
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
			// //
			// if (!followingPath && !nextMaze && !illegalPP && debugGameState != null) {
			// pacManMover = new PacManMover(debugGameState, debugDGameState);
			// MOVE mv = pacManMover.generatePacManMove(selectionType);
			// if (pacManMover.lastJSafety < 10) {
			// new GameView(gameState).showGame();
			// new GameView(debugGameState).showGame();
			// System.err.println("Dgame: " + dGame.getLastPacManJunction() + " debugDGame: "
			// + debugDGameState.getLastPacManJunction());
			// System.err.println("Would not do again, did: " + lastJMove + " should be: " + mv + " safety: "
			// + pacManMover.safety);
			// pacManMover.generatePacManMove(selectionType);
			// }
			// if (lastJTime != debugGameState.getCurrentLevelTime()) {
			// System.err.println("Wrong time! " + pacManMover.safety);
			// }
			// }
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
		if (targetReached && targetSelection || pillsBefore == 0
				|| (nextMaze && gameState.getCurrentLevelTime() < Constants.LEVEL_LIMIT)) {
			pillNorm = 1.;
		} else {
			//
			if ((pillsEaten > 0 || edgePillsEaten > 0) && selectionType != SelectionType.GhostScore) {
				pillNorm = Math.max(pillsEaten, edgePillsEaten) / pillsBefore;
			}
		}
		// Determine the ghost score
		if (ghostsEaten > 0) {
			//
			double ghostDivisor = Constants.NUM_GHOSTS
					* (Constants.EDIBLE_TIME * (Math.pow(Constants.EDIBLE_TIME_REDUCTION,
							gameState.getCurrentLevel() % Constants.LEVEL_RESET_REDUCTION)));
			ghostNorm = ghostsEaten / ghostDivisor;
			//
			if (pwrPillsBefore > gameState.getNumberOfActivePowerPills()
					&& ghostNorm < minGhostNorm) {
				// Penalty is a lower pill score when pp was eaten, but not enough ghosts
				pillNorm /= 5.;
				ghostNorm /= 5.;
			}
			// else {
			// pillNorm += (ghostNorm * .4);
			// }
		} else if (pwrPillsBefore > gameState.getNumberOfActivePowerPills()) {
			// Penalty is a lower pill score when pp was eaten, but no ghosts
			pillNorm /= 10.;
		}
		return new MCTResult(pillNorm, ghostNorm, !died);
	}

	@Override
	public MCTResult playout(DiscreteGame dGame, Game state, MOVE[] pathMoves, int[] pacLocations,
			int maxSimulations, SelectionType selectionType, int target) {
		MCTResult result = playout(dGame, state, pathMoves, pacLocations, maxSimulations,
				selectionType);
		return result;
	}
}
