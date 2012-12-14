package pacman.entries.pacman;

import pacman.controllers.Controller;
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
	private final StrategySimulation simulation = new StrategySimulation();
	private final MCTSelection selection = new UCTSelection();
	// At this time, start eating ghosts a.s.a.p.
	private final int endGameTime = Constants.MAX_TIME - 3000;
	private final int maxPathLength = 60, maxSimulations = 110;
	// penalties and discounts
	private double reversePenalty = .8; // The reward penalty for selecting a reverse move
	private double discount = .5; // Decay factor for the tree decay
	// Set some slacktime for the search to ensure on time return of move
	private int slackTime = 2; // Slack on simulations
	private final int finalSlackTime = 1; // Total slack time
	// Safety and minimum ghost score parameters.
	private double safetyT = .75, ghostSelectScore = .4;
	private double hardSafetyT = .85, hardGhostSelectScore = .3;
	private double easySafetyT = .7, easyGhostSelectScore = .5;
	//
	private boolean atJunction = false, prevLocationWasJunction = false;
	private final boolean[] ghostsAtJunctions = new boolean[4], ghostsAtInitial = new boolean[4];
	private final int[] ghostJunctions = new int[4];
	// Counters etc..
	private int earlyCount = 0, lastJunction = -1, currentMaze = -1,
			pacLives = Constants.NUM_LIVES, currentTarget = -1, timesDiedInFirstMaze = 0;
	private double prevMoveSurvivalRate = 0., simulations = 0;
	private MOVE move, lastTurnMove, lastJunctionMove;
	// Gamestate
	private Edge[][] graph = null;
	private DiscreteGame dGame = null;
	private Game gameState;
	private SinglePlayerNode root;
	private Edge currentPacmanEdge;
	private SelectionType selectionType;

	@Override
	public MOVE getMove(Game game, long timeDue) {
		//
		gameState = game;
		updateDiscreteGamePreMove();
		CauseOfDeath.reset();
		setSelectionType();
		root = setupTree();
		// Run the simulations
		runSimulations(timeDue - slackTime);
		//
		MCTNode selectedChild = null;
		MCTNode[] children = root.getChildren();
		// No suitable moves could be found using MCTS
		if (children == null || children.length == 0) {
			move = fastMove();
		} else {
			root.propagateMaxValues(SelectionType.SurvivalRate);
			// Remember the previous safety rate
			prevMoveSurvivalRate = root.getAlphaSurvivalScore();
			// Check if safe enough to make a rewarding move.
			if (prevMoveSurvivalRate < safetyT) {
				selectionType = SelectionType.SurvivalRate;
			} else {
				root.propagateMaxValues(SelectionType.GhostScore, safetyT);
				for (MCTNode c : root.getChildren()) {
					if (c.getAlphaGhostScore() > ghostSelectScore) {
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
			// Get the path-move from the selected childnode
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
		}
		// Reset the prevlocation value
		if (!atJunction) {
			prevLocationWasJunction = false;
		}

		// This makes sure moves are always returned on time
		if (System.currentTimeMillis() > (timeDue - finalSlackTime)
				&& gameState.getCurrentLevelTime() > 10) {
			slackTime++;
			// if (DEBUG)
			// System.err.println("Late! new slacktime: " + slackTime);
		} else if (System.currentTimeMillis() < (timeDue - finalSlackTime)
				&& gameState.getCurrentLevelTime() > 10) {
			this.earlyCount++;
			if (earlyCount >= 10) {
				if (slackTime > 2) {
					slackTime--;
				}
				earlyCount = 0;
			}
		}
		//
		return move;
	}

	/**
	 * Should be called AFTER updateDiscreteGamePreMove() and setSelectionType()
	 * @return The root-node of the tree to be used by search
	 */
	private SinglePlayerNode setupTree() {
		// re-use the game tree
		// The discrete rulles for tossing the tree:
		// 1. Pac-man was eaten or gone to next level
		// 2. There was a global reversal event
		// 3. Pac-man ate a ghost
		// 4. Pac-man ate a power-pill
		if (gameState.wasPacManEaten() || gameState.getCurrentLevelTime() == 0 || root == null
				|| gameState.getTimeOfLastGlobalReversal() == (gameState.getTotalTime() - 1)
				|| gameState.getNumGhostsEaten() > 0 || gameState.wasPowerPillEaten()) {
			root = new SinglePlayerNode(dGame, gameState);
		} else if (root != null && root.getChildren() == null) {
			root = new SinglePlayerNode(dGame, gameState);
		} else if (atJunction) {
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
			root.setChildren(newRootChildren);
			root.addStats(extraChild);
			root.setNodeDepth(0);
			//
			root.setGameState(gameState.copy());
			root.setdGame(dGame.copy());
			root.discountValues(discount);
			root.propagateMaxValues(selectionType);
			//
		} else if (prevLocationWasJunction) {
			MCTNode forwardChild = null;
			// Select the new root based on the last move made.
			for (MCTNode c : root.getChildren()) {
				if (c.getPathDirection() == lastTurnMove) {
					forwardChild = c;
					break;
				}
			}
			if (forwardChild == null) {
				System.out.println("no forward child found!");
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
			MCTNode[] newReverseChildren = new MCTNode[newChildren[1].getChildren().length - 1];
			int i = 0, k = 0;
			// This node still has the forwardChild as one of its children.
			while (k < newReverseChildren.length) {
				// Get the children sans forwardChild
				if (newChildren[1].getChildren()[i] != forwardChild) {
					newReverseChildren[k] = newChildren[1].getChildren()[i];
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
			root.copyStats(newChildren[1]);
			// Substract the stats from the old root.
			newChildren[1].substractStats(newChildren[0]);
			root.setNodeDepth(0);
			//
			root.setGameState(gameState.copy());
			root.setdGame(dGame.copy());
			root.discountValues(discount);
			// Correct the max-values
			root.propagateMaxValues(selectionType);
		} else {
			//
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
			root.discountValues(discount);
			// Correct the max-values
			root.propagateMaxValues(selectionType);
		}
		root.setEdge(currentPacmanEdge);
		return root;
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
	 * Returns true if there are edible ghosts in pacman's range
	 * 
	 * @return true if edible ghost in range
	 */
	private boolean feasableBlueGhost() {
		int pacLoc = gameState.getPacmanCurrentNodeIndex();
		boolean feasableGhost = false;
		for (GHOST g : GHOST.values()) {
			if (gameState.isGhostEdible(g) && gameState.getGhostLairTime(g) == 0) {
				//
				int dist = gameState.getShortestPathDistance(pacLoc,
						gameState.getGhostCurrentNodeIndex(g))
						- (Constants.EAT_DISTANCE * 2);
				feasableGhost = (dist <= gameState.getGhostEdibleTime(g));
				if (feasableGhost)
					break;
			}
		}

		return feasableGhost;
	}

	double maxScore = 0.f;

	private MCTNode getBestChild(MCTNode[] children, SelectionType selectionType) {
		maxScore = 0.f;
		MCTNode selectedChild = null;
		if (DEBUG)
			System.out.println("[2] Selectiontype: " + selectionType);
		for (MCTNode c : children) {
			if (DEBUG) {
				System.out.println(c);
			}
			//
			double score = 0.f;
			if (selectionType == SelectionType.GhostScore) {
				// Don't consider unsafe children
				if (c.getAlphaSurvivalScore() < safetyT) {
					continue;
				}
				score = c.getAlphaGhostScore();
			} else if (selectionType == SelectionType.PillScore) {
				// Don't consider unsafe children
				if (c.getAlphaSurvivalScore() < safetyT) {
					continue;
				}
				score = c.getAlphaPillScore();
			}
			// Give a penalty to the score if it reverses ms pac-man
			if (c.getPathDirection().opposite() == gameState.getPacmanLastMoveMade()
					|| c.getPathDirection() == gameState.getPacmanLastMoveMade().opposite()) {
				score *= reversePenalty;
			}
			// Don't punish survival-based selection, even if reversed
			if (selectionType == SelectionType.SurvivalRate) {
				// For maximum selection
				score = c.getAlphaSurvivalScore();
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
	 * Returns the forward move if pacman is on an edge!
	 * 
	 * @return
	 */
	private MOVE getEdgeForwardMove() {
		return gameState.getPossibleMoves(gameState.getPacmanCurrentNodeIndex(),
				gameState.getPacmanLastMoveMade())[0];
	}

	/**
	 * Returns the reverse move if pac-man is on an edge
	 * 
	 * @return
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

	/**
	 * Runs monte carlo search tree until the current time == timeDue
	 * 
	 * @param timeDue
	 *            The time at which the algorithm should stop
	 */
	private void runSimulations(long timeDue) {
		MCTNode expandNode = null, simulationNode = null;
		simulations = 0;
		while (System.currentTimeMillis() < timeDue) {
			simulations++;
			//
			root.addVisit();
			expandNode = root.selection(selection, true);
			simulationNode = expandNode;

			// Check if the expandnode can be expanded
			if (expandNode.canExpand(maxPathLength)) {
				expandNode.expand(root.getdGame(), root);
				simulationNode = expandNode.selection(selection, true);
			}
			// Start a simulation using the path from the root.
			MCTResult result;
			result = simulationNode.simulate(simulation, maxSimulations + maxPathLength,
					selectionType, currentTarget);
			// Propagate the result from the expanded child to the root
			simulationNode.backPropagate(result, selectionType, simulation.getTreePhaseSteps());
		}
	}

	public void setGhostEpsilon(double value) {
		PinchGhostMover.greedyP = value;
	}

	public void setSafetyT(double safetyT) {
		this.safetyT = safetyT;
	}

	public void setAlpha_ps(double alpha) {
		UCTSelection.alpha_ps = alpha;
	}

	public void setAlpha_g(double alpha) {
		UCTSelection.alpha_g = alpha;
	}
	
	public void setUCTC(double UCTC) {
		UCTSelection.C = UCTC;
	}

	public void setGamma(double gamma) {
		discount = gamma;
	}

	/**
	 * Sets the selection type based on the current state of the game.
	 */
	private void setSelectionType() {
		if (prevMoveSurvivalRate >= safetyT) {
			//
			boolean ghostSelection = false;
			for (GHOST g : GHOST.values()) {
				if (gameState.isGhostEdible(g) && gameState.getGhostLairTime(g) == 0) {
					ghostSelection = true;
					break;
				}
			}
			//
			if (ghostSelection) {
				if (feasableBlueGhost()) {
					selectionType = SelectionType.GhostScore;
				} else {
					selectionType = SelectionType.PillScore;
				}
			} else {
				selectionType = SelectionType.PillScore;
			}
		} else {
			selectionType = SelectionType.SurvivalRate;
		}
		selection.setSelectionType(selectionType);
	}

	/**
	 * Updates the discrete gamestate based on the current gamestate before move-selection
	 */
	private void updateDiscreteGamePreMove() {
		// First call should create a new discrete gamestate each game
		if (gameState.getTotalTime() == 0 || dGame == null) {
			dGame = new DiscreteGame(gameState);
			this.timesDiedInFirstMaze = 0;
		}
		// Either the game just started or pacman entered a new maze
		if (gameState.getMazeIndex() != currentMaze || gameState.getTotalTime() == 0) {
			dGame.setCurrentMaze(gameState);
			graph = dGame.getGraph();
			//
			currentMaze = gameState.getMazeIndex();
			lastJunction = -1;
			currentPacmanEdge = null;
			lastJunctionMove = MOVE.NEUTRAL;
			lastTurnMove = MOVE.NEUTRAL;
			pacLives = gameState.getPacmanNumberOfLivesRemaining();
			currentTarget = -1;
			prevLocationWasJunction = false;
			simulations = 0;
			simulation.gameCount = 0.;
			simulation.deathCount = 0.;
		}
		// For easier ghosts
		if (gameState.getMazeIndex() == 2 && gameState.getPacmanNumberOfLivesRemaining() >= 2) {
			ghostSelectScore = this.easyGhostSelectScore;
			safetyT = this.easySafetyT;
			simulation.setEasyMinGhostNorm();
		}

		// Pacman died
		if (gameState.wasPacManEaten()) {
			lastJunction = -1;
			currentPacmanEdge = null;
			lastJunctionMove = MOVE.NEUTRAL;
			lastTurnMove = MOVE.NEUTRAL;
			dGame.pacmanDied();
			pacLives = gameState.getPacmanNumberOfLivesRemaining();
			prevLocationWasJunction = false;

			// When the opponent is strong, go for ghosts sooner.
			if (gameState.getCurrentLevel() < 1) {
				this.timesDiedInFirstMaze++;
				if (this.timesDiedInFirstMaze == 1) {
					this.ghostSelectScore = hardGhostSelectScore;
					this.simulation.setDecreasedMinGhostNorm();
					this.safetyT = hardSafetyT;
				}
			}

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
			// PinchGhostMover.LGR.clear();
			// PacManMover.LGR.clear();

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
		// DEBUG = atJunction;
		//
		// if (nextMoveTargetSelection && atJunction) {
		// currentTarget = getTarget();
		// }
		//
		if (atJunction) {
			//
			lastJunction = gameState.getPacmanCurrentNodeIndex();
			prevLocationWasJunction = true;
		} else if (lastJunction > -1) {
			// The root node is not at a junction, assign the current edge-id
			// to the root
			currentPacmanEdge = graph[lastJunction][lastJunctionMove.ordinal()];
		}

		if (gameState.getTimeOfLastGlobalReversal() == (gameState.getTotalTime() - 1)
				|| gameState.wasPowerPillEaten()) {
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

			if (ghostsAtJunctions[g.ordinal()] && !gameState.wasGhostEaten(g)) {
				try {
					if (dGame.getGraph()[ghostJunctions[g.ordinal()]][gameState.getGhostLastMoveMade(g).ordinal()] != null) {
						dGame.setGhostMove(g.ordinal(), ghostJunctions[g.ordinal()],
								gameState.getGhostLastMoveMade(g));
					}
				} catch (Exception ex) {
					//System.err.println("Ghost on wrong edge!");
				}
			}
			// Reset the ghost statuses
			if (gameState.isJunction(gameState.getGhostCurrentNodeIndex(g))) {
				this.ghostsAtJunctions[g.ordinal()] = true;
				this.ghostJunctions[g.ordinal()] = gameState.getGhostCurrentNodeIndex(g);
			} else {
				this.ghostsAtJunctions[g.ordinal()] = false;
			}
		}

		// At the end of the game, make sure to eat as many ghosts as possible.
		if (gameState.getTotalTime() >= this.endGameTime) {
			this.ghostSelectScore = this.hardGhostSelectScore;
			simulation.setDecreasedMinGhostNorm();
		}
	}
}