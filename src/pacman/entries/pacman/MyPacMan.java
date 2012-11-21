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
	private final boolean DEBUG = true;
	// To use different simulation strategy or selection, set them here.
	private final StrategySimulation simulation = new StrategySimulation();
	private final MCTSelection selection = new UCTSelection();
	// Search parameters
	private final int endGameTime = Constants.MAX_TIME - 3000; // At this time,
																// start eating
																// ghosts asap
	private final int initialMaxPathLength = 40, reuseMaxPathLength = 60, maxSimulations = 100;
	private int maxPathLength = initialMaxPathLength;
	// penalties and discounts
	private double reversePenalty = .7; // The reward penalty for selecting a
										// reverse move
	private double discount = .8; // Decay factor for the tree decay
	// Set some slacktime for the search to ensure on time return of move
	private int slackTime = 5; // Slack on simulations
	private final int finalSlackTime = 1; // Total slack time
	// Safety and minimum ghost score parameters.
	private double safetyT = .75, ghostSelectScore = .4;
	private double hardSafetyT = .85, hardGhostSelectScore = .3;
	private double easySafetyT = .7, easyGhostSelectScore = .5;
	//
	private boolean atJunction = false, prevLocationWasJunction = false,
			nextMoveTargetSelection = false;
	//
	private final boolean[] ghostsAtJunctions = new boolean[4], ghostsAtInitial = new boolean[4];
	private final int[] ghostJunctions = new int[4];

	// Counters etc..
	private int earlyCount = 0, lastJunction = -1, currentMaze = -1,
			pacLives = Constants.NUM_LIVES, noTarget = 0, currentTarget = -1,
			currentEdgesVisited = 0, timesDiedInFirstMaze = 0;
	private double prevMoveSurvivalRate = 0., simulations = 0, moves = 0;
	private MOVE move, lastTurnMove, lastJunctionMove;
	private MOVE[] prevAvailMoves, availMoves;
	// Gamestate
	private Edge[][] graph = null;
	private DiscreteGame dGame = null;
	private Game gameState;
	private SinglePlayerNode root, lastJunctionRoot;
	private Edge currentPacmanEdge;
	private SelectionType selectionType;

	@Override
	public MOVE getMove(Game game, long timeDue) {
		//
		gameState = game;
		updateDiscreteGamePreMove();
		CauseOfDeath.reset();
		// boolean ghostAtJunction = false;
		// if (dGame.getCurrentPacmanEdge() != null) {
		// for (GHOST g : GHOST.values()) {
		// ghostAtJunction =
		// gameState.isJunction(gameState.getGhostCurrentNodeIndex(g));
		//
		// if (ghostAtJunction) {
		//
		// if
		// (gameState.getShortestPathDistance(gameState.getPacmanCurrentNodeIndex(),
		// gameState.getGhostCurrentNodeIndex(g),
		// gameState.getPacmanLastMoveMade()) <= maxPathLength) {
		// ghostAtJunction = true;
		// break;
		// } else {
		// ghostAtJunction = false;
		// }
		// }
		// }
		// }
		//
		// // Setup the root
		// if (edgeCleared || gameState.getNumGhostsEaten() > 0 || atJunction ||
		// prevLocationWasJunction || !sameMoves
		// || gameState.getCurrentLevelTime() == 0 ||
		// gameState.wasPowerPillEaten() || gameState.wasPacManEaten()
		// || gameState.getTimeOfLastGlobalReversal() ==
		// (gameState.getTotalTime() - 1) || ghostAtJunction
		// || angryGhostNear()) {
		// //
		// root = new SinglePlayerNode(dGame.copy(), gameState.copy());
		// maxPathLength = initialMaxPathLength;
		// } else {
		// // Increase the maximum path length
		// maxPathLength = reuseMaxPathLength;
		// //
		// // System.err.println(": resused!");
		// root.setdGame(dGame.copy());
		// root.setGameState(gameState.copy());
		//
		// if (root.getChildren() != null) {
		// for (MCTNode c : root.getChildren()) {
		// if (gameState.getPacmanLastMoveMade().equals(c.getPathDirection()) &&
		// c.getPathLength() > 0) {
		// c.addDistance(-1);
		// } else {
		// c.addDistance(1);
		// }
		// }
		// }
		// }
		//
		setSelectionType();
		maxPathLength = reuseMaxPathLength;
		//
//		root = new SinglePlayerNode(dGame, gameState);
		// re-use the game tree
		if (gameState.wasPacManEaten() || gameState.getCurrentLevelTime() == 0 || root == null) {
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
			newRootChildren[newRootChildren.length - 1] = extraChild;
			extraChild.setParent(root);
			root.setChildren(newRootChildren);
			root.addStats(extraChild);
			root.propagateMaxValues(selectionType);
			root.setNodeDepth(0);
			//
			root.setGameState(gameState.copy());
			root.setdGame(dGame.copy());
			root.discountValues(discount);
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
			// Correct the max-values
			root.propagateMaxValues(selectionType);
			//
			root.setGameState(gameState.copy());
			root.setdGame(dGame.copy());
			root.discountValues(discount);
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
		}
		root.setEdge(currentPacmanEdge);
		// Run the simulations
		runSimulations(timeDue - slackTime);
		//
		MCTNode selectedChild = null;
		MCTNode[] children = root.getChildren();
		// No suitable moves could be found using MCTS
		if (children.length == 0) {
			move = fastMove();
		} else {
			root.propagateMaxValues(SelectionType.TargetRate);
			// Remember the previous safety rate
			prevMoveSurvivalRate = root.getMaxSurvivalRate();
			// Check if safe enough to make a rewarding move.
			if (prevMoveSurvivalRate < safetyT) {
				selectionType = SelectionType.TargetRate;
			} else {
				root.propagateMaxValues(SelectionType.GhostScore, safetyT);
				for (MCTNode c : root.getChildren()) {
					if (c.getMaxGhostScore() > ghostSelectScore) {
						selectionType = SelectionType.GhostScore;
						// System.err.println("Going for ghosts!: " +
						// (c.getMaxGhostScore() * c.getMaxSurvivalRate()));
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
						selectionType = SelectionType.TargetRate;
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
			//
			// if (!atJunction
			// && (move.opposite() == gameState.getPacmanLastMoveMade() || move
			// == gameState
			// .getPacmanLastMoveMade().opposite())) {
			// System.out.println("Reversed!");
			// }
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
		if(root != null && gameState.isJunction(gameState.getPacmanCurrentNodeIndex()))
			lastJunctionRoot = root;
		//
		return move;
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
				if (c.getMaxSurvivalRate() < safetyT) {
					continue;
				}
				if (c.getPathDirection().opposite() == gameState.getPacmanLastMoveMade()
						|| c.getPathDirection() == gameState.getPacmanLastMoveMade().opposite()) {
					score = c.getMaxGhostScore() * reversePenalty * c.getMaxSurvivalRate();
				} else {
					score = c.getMaxGhostScore() * c.getMaxSurvivalRate();
				}
			} else if (selectionType == SelectionType.PillScore) {
				// Don't consider unsafe children
				if (c.getMaxSurvivalRate() < safetyT) {
					continue;
				}
				if (c.getPathDirection().opposite() == gameState.getPacmanLastMoveMade()
						|| c.getPathDirection() == gameState.getPacmanLastMoveMade().opposite()) {
					score = c.getMaxPillScore() * reversePenalty * c.getMaxSurvivalRate();
				} else {
					score = c.getMaxPillScore() * c.getMaxSurvivalRate();
				}
			} else {
				// For maximum selection
				score = c.getMaxSurvivalRate();
			}
			//
			if (score > maxScore) {
				maxScore = score;
				selectedChild = c;
			}
		}
		//
		if (DEBUG) {
			System.out.println("[2] Max score: " + maxScore);
		}
		moves++;
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

	private boolean angryGhostNear() {
		int pacLocation = gameState.getPacmanCurrentNodeIndex();
		for (GHOST g : GHOST.values()) {
			//
			MOVE ghostDir = gameState.getGhostLastMoveMade(g);
			int ghostLoc = gameState.getGhostCurrentNodeIndex(g);

			int distance = 0;
			if (gameState.getGhostLairTime(g) == 0) {
				distance = gameState.getShortestPathDistance(ghostLoc, pacLocation, ghostDir)
						+ gameState.getGhostEdibleTime(g);
			} else {
				ghostLoc = gameState.getGhostInitialNodeIndex();
				distance = gameState.getShortestPathDistance(gameState.getGhostInitialNodeIndex(),
						pacLocation, ghostDir) + gameState.getGhostEdibleTime(g);
			}
			//
			if (distance <= 10) {
				if ((distance - Constants.EAT_DISTANCE) > dGame.pacManDistanceToHeading() + 1) {
					// System.out.println("Closer to junction than ghost to me.");
					continue;
				}
				// The ghost is near the pacman
				MOVE nextMove = gameState.getPossibleMoves(pacLocation,
						gameState.getPacmanLastMoveMade())[0];
				int nextLocation = gameState.getNeighbour(pacLocation, nextMove);
				int nextDistance = 0;
				if (gameState.getGhostEdibleTime(g) > 0) {
					nextDistance = gameState.getShortestPathDistance(nextLocation, ghostLoc)
							+ gameState.getGhostEdibleTime(g) - 1;
				} else {
					nextDistance = gameState.getShortestPathDistance(nextLocation, ghostLoc);
				}

				//
				if (nextDistance < distance) {
					// Pac-Man is moving toward the ghost
					MOVE nextGhostMove = gameState.getPossibleMoves(ghostLoc, ghostDir)[0];
					int nextGhostLocation = gameState.getNeighbour(ghostLoc, nextGhostMove);
					//
					int nextGhostDist = 0;
					if (gameState.getGhostLairTime(g) == 0) {
						nextGhostDist = gameState.getShortestPathDistance(nextGhostLocation,
								pacLocation, nextGhostMove) + gameState.getGhostEdibleTime(g);
					} else {
						if (gameState.getGhostEdibleTime(g) > 0) {
							nextGhostDist = gameState.getShortestPathDistance(nextGhostLocation,
									pacLocation, nextGhostMove)
									+ gameState.getGhostEdibleTime(g)
									- 1;
						} else {
							nextGhostDist = gameState.getShortestPathDistance(nextGhostLocation,
									pacLocation, nextGhostMove);
						}
					}

					// The ghost is moving towards the pacman
					if (nextGhostDist < distance) {
						if (DEBUG)
							System.out.println("Angry ghost near!");
						return true;
					}
				}
			}
		}
		return false;
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

	public void setDistT(int value) {
		PinchGhostMover.pacDistT = value;
		PacManMover.ghostDistT = value;
	}

	public void setGhostEpsilon(double value) {
		PinchGhostMover.greedyP = value;
	}

	public void setSafetyT(double safetyT) {
		this.safetyT = safetyT;
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
			selectionType = SelectionType.TargetRate;
		}
		selection.setSelectionType(selectionType);
	}

	public void setUCTC(double UCTC) {
		UCTSelection.uctConstant = UCTC;
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
			nextMoveTargetSelection = false;
			prevLocationWasJunction = false;
			//
			// System.out.println("Death rate: " + (simulation.deathCount /
			// simulation.gameCount));
			// System.out.println("Simulations per move: " + (simulations /
			// moves));
			simulations = 0;
			moves = 0;
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
			nextMoveTargetSelection = false;
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
		currentEdgesVisited = dGame.getVisitedEdgeCount();
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
					if (dGame.getGraph()[ghostJunctions[g.ordinal()]][gameState
							.getGhostLastMoveMade(g).ordinal()] != null) {
						dGame.setGhostMove(g.ordinal(), ghostJunctions[g.ordinal()],
								gameState.getGhostLastMoveMade(g));
					}
				} catch (Exception ex) {
					System.err.println("Ghost nullpointer");
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