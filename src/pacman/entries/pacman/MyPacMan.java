package pacman.entries.pacman;

import pacman.controllers.Controller;
import pacman.entries.pacman.unimaas.Settings;
import pacman.entries.pacman.unimaas.SinglePlayerNode;
import pacman.entries.pacman.unimaas.framework.*;
import pacman.entries.pacman.unimaas.ghosts.PinchGhostMover;
import pacman.entries.pacman.unimaas.pacman.PacManMover;
import pacman.entries.pacman.unimaas.selection.UCTSelection;
import pacman.entries.pacman.unimaas.simulation.StrategySimulation;
import pacman.game.Constants;
import pacman.game.Constants.GHOST;
import pacman.game.Game;
import pacman.game.Constants.*;

public class MyPacMan extends Controller<MOVE> {
	// Set true for debugging output
	private final boolean DEBUG = false;

	// To use different simulation strategy or selection, set them here.
	private StrategySimulation simulation = new StrategySimulation();
	private final MCTSelection selection = new UCTSelection();
	// Maximum length of a tree-path and maximum simulation steps in simulation phase
	public double maxPathLength, maxSimulations, initMaxPathLength;
	// Safety and minimum ghost score parameters.
	public double safetyT, ghostSelectScore, reversePenalty, discount; // Decay factor for the tree decay
	//
	public boolean reuse = true, decay = true, var_depth = true, strategic_playout = true,
			max_selection = true;
	public int maxNodeDepth = 5; // For fixed node depth tests
	private double depthIncrease = 0;
	// Counters etc..
	private boolean atJunction = false, prevLocationWasJunction = false;
	private final boolean[] ghostsAtJunctions = new boolean[4], ghostsAtInitial = new boolean[4];
	private final int[] ghostJunctions = new int[4];
	private int lastJunction = -1, currentMaze = -1, pacLives = Constants.NUM_LIVES;
	private double prevMoveSurvivalRate = 0., simulations = 0;
	private MOVE move, lastTurnMove, lastJunctionMove;
	// Gamestate
	private Edge[][] graph = null;
	private DiscreteGame dGame = null;
	private Game gameState;
	private SinglePlayerNode root; // , lastJRoot;
	private Edge currentPacmanEdge;
	private SelectionType selectionType;

	@Override
	public MOVE getMove(Game game, long timeDue) {
		gameState = game;
		//
		// DEBUG = gameState.isJunction(gameState.getPacmanCurrentNodeIndex());
		// if ((move != null && lastTurnMove != null)) {
		// if (move.opposite() == lastTurnMove || move == lastTurnMove.opposite()) {
		// System.out.println("reversed!!");
		// }
		// }
		updateDiscreteGamePreMove();
		CauseOfDeath.reset();
		setSelectionType();
		setupTree();
		// if(atJunction)
		// lastJRoot = root;
		// root.validate(game);
		// Run the simulations
		runSimulations(timeDue - 1);
		MCTNode selectedChild = null;
		MCTNode[] children = root.getChildren();
		// No suitable moves could be found using MCTS
		if (children == null || children.length == 0) {
			move = fastMove();
		} else {
			root.propagateMaxValues(SelectionType.SurvivalRate);
			// Remember the previous safety rate
			prevMoveSurvivalRate = root.getNewMaxValue(MCTNode.SURV_I);
			// Check if safe enough to make a rewarding move.
			if (prevMoveSurvivalRate < safetyT) {
				selectionType = SelectionType.SurvivalRate;
			} else {
				// Check if we should go for the ghosts
				root.propagateMaxValues(SelectionType.GhostScore, safetyT);
				for (MCTNode c : root.getChildren()) {
					if (c.getAlphaGhostScore(max_selection) >= ghostSelectScore) {
						selectionType = SelectionType.GhostScore;
					}
				}
				root.propagateMaxValues(selectionType, safetyT);
			}
			//
			while (selectedChild == null) {
				selectedChild = getBestChild(children, selectionType);
				//
				if (selectedChild == null) {
					// If no child was found to be the best based on the current
					// selection type, choose a different method of selection
					if (selectionType == SelectionType.GhostScore) {
						selectionType = SelectionType.PillScore;
						root.propagateMaxValues(selectionType, safetyT);
					} else if (selectionType == SelectionType.PillScore) {
						selectionType = SelectionType.SurvivalRate;
						root.propagateMaxValues(selectionType);
					} else {
						// No move could be selected based on any selection type
						// (should not happen)
						break;
					}
				}
			}
			// Get the path-move from the selected child
			if (selectedChild != null) {
				move = selectedChild.getPathDirection();
			} else { // No child could be selected, return a fast move.
				move = fastMove();
			}
		}
		// TODO DEBUG
		if (DEBUG) {
			System.out.println("Did " + simulations + " simulations");
			System.out.println("Selected child: " + selectedChild + "\n\n");
			CauseOfDeath.print();
			System.out.println("Max path length: " + maxPathLength);
		}
		// Reset the prevlocation value
		if (!atJunction) {
			prevLocationWasJunction = false;
		}
		return move;
	}

	/**
	 * Runs monte carlo search tree until the current time == timeDue
	 * 
	 * @param timeDue The time at which the algorithm should stop
	 */
	private void runSimulations(long timeDue) {
		MCTNode expandNode = null, simulationNode = null;
		simulations = 0;
		while (System.currentTimeMillis() < timeDue) {
			simulations++;
			//
			root.addVisit();
			expandNode = root.selection(selection, max_selection, (int) maxPathLength);
			simulationNode = expandNode;

			// Check if the expandnode can be expanded
			if (expandNode.canExpand((int) maxPathLength, var_depth, maxNodeDepth)) {
				expandNode.expand(root.getdGame(), root);
				simulationNode = expandNode.selection(selection, true);
			}
			// Start a simulation using the path from the root.
			MCTResult result;
			result = simulationNode.simulate(simulation, (int) maxSimulations, (int) maxPathLength,
					selectionType, strategic_playout);
			// Propagate the result from the expanded child to the root
			simulationNode.backPropagate(result, selectionType, simulation.getTreePhaseSteps());
		}
	}

	/**
	 * Sets the selection type based on the current state of the game.
	 */
	private void setSelectionType() {
		if (prevMoveSurvivalRate >= safetyT) {
			selectionType = SelectionType.PillScore;
			//
			boolean ghostSelection = false;
			for (GHOST g : GHOST.values()) {
				if (gameState.isGhostEdible(g) && gameState.getGhostLairTime(g) == 0) {
					ghostSelection = true;
					break;
				}
			}
			if (ghostSelection && feasableBlueGhost()) {
				selectionType = SelectionType.GhostScore;
			}
		} else {
			selectionType = SelectionType.SurvivalRate;
		}
		selection.setSelectionType(selectionType);
	}

	private MCTNode getBestChild(MCTNode[] children, SelectionType selectionType) {
		double maxScore = 0.f;
		MCTNode selectedChild = null;
		if (DEBUG)
			System.out.println("[2] Selectiontype: " + selectionType);
		for (MCTNode c : children) {
			if (DEBUG) {
				System.out.println(c);
			}
			if (c.getNewMaxValue(MCTNode.SURV_I) == .0)
				continue;
			//
			double score = 0.f;
			if (selectionType == SelectionType.GhostScore) {
				// Don"t consider unsafe children
				if (c.getNewMaxValue(MCTNode.SURV_I) < safetyT) {
					continue;
				}
				score = c.getAlphaGhostScore(max_selection);
			} else if (selectionType == SelectionType.PillScore) {
				// Don"t consider unsafe children
				if (c.getNewMaxValue(MCTNode.SURV_I) < safetyT) {
					continue;
				}
				score = c.getAlphaPillScore(max_selection);
			}
			// Give a penalty to the score if it reverses ms pac-man
			if (c.getPathDirection().opposite() == gameState.getPacmanLastMoveMade()
					|| c.getPathDirection() == gameState.getPacmanLastMoveMade().opposite()) {
				score *= reversePenalty;
			}
			// Don"t punish survival-based selection, even if reversed
			if (selectionType == SelectionType.SurvivalRate) {
				// For maximum selection
				score = c.getAlphaSurvivalScore(max_selection);
			}
			// Remember the highest score seen and the node that came with it
			if (score > maxScore) {
				maxScore = score;
				selectedChild = c;
			}
		}
		if (DEBUG) {
			System.out.println("[2] Max score: " + maxScore);
		}
		return selectedChild;
	}

	/**
	 * Should be called AFTER updateDiscreteGamePreMove() and setSelectionType()
	 * 
	 * @return The root-node of the tree to be used by search
	 */
	private void setupTree() {
		if (!reuse) {
			root = new SinglePlayerNode(dGame, gameState);
			root.setEdge(currentPacmanEdge);
			maxPathLength = initMaxPathLength;
			return;
		}

		// re-use the game tree
		// The discrete rulles for tossing the tree:
		// 1. Pac-man was eaten or gone to next level
		// 2. There was a global reversal event
		// 3. Pac-man ate a ghost
		// 4. Pac-man ate a power-pill
		if (gameState.wasPacManEaten() || gameState.getCurrentLevelTime() < 2 || root == null
				|| gameState.getTimeOfLastGlobalReversal() == (gameState.getTotalTime() - 1)
				|| gameState.getNumGhostsEaten() > 0 || gameState.wasPowerPillEaten()) {
			root = new SinglePlayerNode(dGame, gameState);
			root.setEdge(currentPacmanEdge);
			maxPathLength = initMaxPathLength;
			return;
		} else if (root != null && root.getChildren() == null) {
			root = new SinglePlayerNode(dGame, gameState);
			root.setEdge(currentPacmanEdge);
			maxPathLength = initMaxPathLength;
			return;
		}
		// Here be tree reuse!
		try {
			clearBadChildren(root);
			//
			if (atJunction) {
				MCTNode newRoot = null;
				MCTNode extraChild = null;
				// Select the new root based on the last move made.
				for (MCTNode c : root.getChildren()) {
					if (c.getPathDirection() == lastTurnMove) {
						newRoot = c;
					} else {
						extraChild = c;
					}
				}
				if (newRoot == null || extraChild == null)
					System.err.println("No new root or extraChild");
				//
				root = (SinglePlayerNode) newRoot;
				root.setParent(null);
				int newLen = 1;
				if (root.getChildren() != null) {
					newLen += root.getChildren().length;
				}
				// Add the new child to the rootnode
				MCTNode[] newRootChildren = new MCTNode[newLen];
				for (int i = 0; i < (newLen - 1); i++) {
					newRootChildren[i] = root.getChildren()[i];
				}
				//
				newRootChildren[newRootChildren.length - 1] = extraChild;
				extraChild.setParent(root);
				root.addStats(extraChild);
				root.setChildren(newRootChildren);
				//
				if (gameState.getPossibleMoves(root.getJunctionIndex()).length != root
						.getChildren().length) {
					// Happens on very rare occasions, reason unknown...
					root = new SinglePlayerNode(dGame, gameState);
					root.setEdge(currentPacmanEdge);
					return;
				}
				root.setNodeDepth(0);
				//
				root.setGameState(gameState.copy());
				root.setdGame(dGame.copy());
				if (decay)
					root.discountValues(discount);
				root.propagateMaxValues(selectionType);
			} else if (prevLocationWasJunction) {
				maxPathLength = initMaxPathLength;
				MCTNode forwardChild = null;
				// Select the new root based on the last move made.
				for (MCTNode c : root.getChildren()) {
					if (c.getPathDirection() == lastTurnMove) {
						forwardChild = c;
						break;
					}
				}
				if (forwardChild == null) {
					System.err.println("no forward child found!");
				}
				MCTNode[] newChildren = new MCTNode[2];
				//
				newChildren[0] = forwardChild;
				newChildren[0].pathDirection = getEdgeForwardMove();
				newChildren[0].edge = dGame.getCurrentPacmanEdge();
				newChildren[0].pathLength = dGame.getCurrentPacmanEdge().length;
				newChildren[0].addDistance(-1);
				//
				newChildren[1] = root;
				newChildren[1].pathDirection = getEdgeReverseMove();
				newChildren[1].edge = dGame.getCurrentPacmanEdge();
				newChildren[1].pathLength = 0;
				newChildren[1].addDistance(1);
				// Array to temporarily store the new children for the old root in.
				MCTNode[] newReverseChildren = new MCTNode[root.getChildren().length - 1];
				int i = 0, k = 0;
				// This node still has the forwardChild as one of its children.
				while (k < newReverseChildren.length) {
					// Get the children sans forwardChild
					if (root.getChildren()[i] != forwardChild) {
						newReverseChildren[k] = root.getChildren()[i];
						k++;
					}
					i++;
				}
				newChildren[1].setChildren(newReverseChildren);
				// We are on an edge, hence need a new root
				root = new SinglePlayerNode(dGame.copy(), gameState.copy());
				newChildren[1].setParent(root);
				newChildren[0].setParent(root);
				root.setChildren(newChildren);
				// Copy the stats from the old root
				root.copyStats(newChildren[1]);
				// Substract the stats of the forward node from the old root.
				newChildren[1].substractStats(newChildren[0]);
				root.setNodeDepth(0);
				//
				root.setGameState(gameState.copy());
				root.setdGame(dGame.copy());
				if (decay)
					root.discountValues(discount);
				// Correct the max-values
				root.propagateMaxValues(selectionType);
			} else {
				if (maxPathLength < initMaxPathLength * 1.7)
					maxPathLength += depthIncrease;
				if (root.getChildren() != null) {
					for (MCTNode c : root.getChildren()) {
						if (lastTurnMove.equals(c.getPathDirection())) {
							// Change the move-direction to the available
							// forward-move
							c.pathDirection = getEdgeForwardMove();
							// We moved in this direction, decrease the pathlength.
							if (c.getPathLength() > 0) {
								c.addDistance(-1);
							}
						} else {
							c.pathDirection = getEdgeReverseMove();
							c.addDistance(1);
						}
					}
				}
				root.setGameState(gameState.copy());
				root.setdGame(dGame.copy());
				if (decay)
					root.discountValues(discount);
				// Correct the max-values
				root.propagateMaxValues(selectionType);
			}
		} catch (Exception ex) {
			System.err.println("error in tree reuse.");
			// If something goes wrong, just make a new tree.
			root = new SinglePlayerNode(dGame, gameState);
			root.setEdge(currentPacmanEdge);
			maxPathLength = initMaxPathLength;
			return;
		}
		root.setEdge(currentPacmanEdge);
	}

	private void clearBadChildren(MCTNode root) {
		for (MCTNode c : root.getChildren()) {
			if (c.getNewMeanValue(MCTNode.SURV_I) == .0) {
				root.substractStats(c);
				c.clearStats();
			}
		}
	}

	public void loadSettings(Settings setting) {
		initMaxPathLength = (int) setting.maxPathLength[0];
		maxPathLength = initMaxPathLength;
		maxSimulations = (int) setting.maxSimulations[0];
		//
		safetyT = setting.safetyT[0];
		ghostSelectScore = setting.ghostSelectScore[0];
		reversePenalty = setting.reversePenalty[0];
		discount = setting.discount[0];
		//
		UCTSelection.C = setting.uctC[0];
		UCTSelection.minVisits = (int) setting.minVisits[0];
		//
		PacManMover.epsilon = setting.pacEpsilon[0];
		PinchGhostMover.epsilon = setting.ghostEpsilon[0];
		//
		MCTNode.minChildVisitRate = setting.minChildVisitRate[0];
		depthIncrease = setting.depthIncrease[0];
		//
		StrategySimulation.longTerm = !setting.no_ltg;
		StrategySimulation.trailGhost = setting.enable_trailghost;
		StrategySimulation.minSteps = (int) setting.minSteps[0];
		StrategySimulation.pillPower = setting.pillPower[0];
		simulation.last_good_config = setting.last_good_config;
		reuse = setting.tree_reuse;
		var_depth = setting.tree_var_depth;
		strategic_playout = setting.strategic_playout;
		max_selection = setting.max_selection;
		MCTNode.noalpha = setting.no_alpha;
		MCTNode.no_surv_reuse = setting.no_surv_reuse;
	}

	/**
	 * Move strategy which is used when no suitable move could be determined by MCT selection
	 * 
	 * @return Move that maximizes the distance from the nearest angry ghost
	 */
	private MOVE fastMove() {
		int[] reds = new int[Constants.NUM_GHOSTS];
		int r = 0;
		for (GHOST g : GHOST.values()) {
			if (!gameState.isGhostEdible(g)) {
				reds[r] = gameState.getGhostCurrentNodeIndex(g);
				r++;
			}
		}
		int[] ghosts = new int[r];
		System.arraycopy(reds, 0, ghosts, 0, r);

		int target = gameState.getClosestNodeIndexFromNodeIndex(
				gameState.getPacmanCurrentNodeIndex(), ghosts, DM.PATH);
		return gameState.getNextMoveAwayFromTarget(gameState.getPacmanCurrentNodeIndex(), target,
				DM.PATH);
	}

	/**
	 * Returns true if there are edible ghosts in pacman"s range
	 * 
	 * @return true if edible ghost in range
	 */
	private boolean feasableBlueGhost() {
		int pacLoc = gameState.getPacmanCurrentNodeIndex();
		boolean feasableGhost = false;
		for (GHOST g : GHOST.values()) {
			if (gameState.isGhostEdible(g) && gameState.getGhostLairTime(g) == 0) {
				// Assume we are moving toward the ghost and the ghost toward us.
				int dist = gameState.getShortestPathDistance(pacLoc,
						gameState.getGhostCurrentNodeIndex(g))
						- (Constants.EAT_DISTANCE * 3);
				feasableGhost = (dist <= gameState.getGhostEdibleTime(g));
				if (feasableGhost)
					break;
			}
		}

		return feasableGhost;
	}

	/**
	 * Returns the forward move if pacman is on an edge!
	 */
	private MOVE getEdgeForwardMove() {
		return gameState.getPossibleMoves(gameState.getPacmanCurrentNodeIndex(),
				gameState.getPacmanLastMoveMade())[0];
	}

	/**
	 * Returns the reverse move if pac-man is on an edge
	 */
	private MOVE getEdgeReverseMove() {
		MOVE forward = gameState.getPossibleMoves(gameState.getPacmanCurrentNodeIndex(),
				gameState.getPacmanLastMoveMade())[0];
		MOVE[] allMoves = gameState.getPossibleMoves(gameState.getPacmanCurrentNodeIndex());
		for (int i = 0; i < allMoves.length; i++) {
			if (allMoves[i] != forward) {
				return allMoves[i];
			}
		}
		return null;
	}

	long lastPilltime = 0;
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
			graph = dGame.getGraph();
			//
			currentMaze = gameState.getMazeIndex();
			lastJunction = -1;
			currentPacmanEdge = null;
			lastJunctionMove = MOVE.NEUTRAL;
			lastTurnMove = MOVE.NEUTRAL;
			pacLives = gameState.getPacmanNumberOfLivesRemaining();
			prevLocationWasJunction = false;
			simulations = 0;
			simulation.gameCount = 0.;
			simulation.deathCount = 0.;
		}

		// Pacman died
		if (gameState.wasPacManEaten()) {
			if (DEBUG)
				System.out.println("I Died here !!!=======================================");
			lastJunction = -1;
			currentPacmanEdge = null;
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
		} else if (lastJunction > -1) {
			// The root node is not at a junction, assign the current edge-id
			// to the root
			currentPacmanEdge = graph[lastJunction][lastJunctionMove.ordinal()];
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