package pacman.entries.pacman.unimaas.framework;

import java.text.DecimalFormat;

import pacman.entries.pacman.unimaas.selection.UCTSelection;
import pacman.entries.pacman.unimaas.simulation.StrategySimulation;
import pacman.game.Constants.MOVE;
import pacman.game.Game;

/**
 * @author Tom Pepels, Maastricht University
 */
public abstract class MCTNode {
	public static final int PILL_I = 0, GHOST_I = 1, SURV_I = 2;
	public static boolean noalpha = false, no_surv_reuse = false;
	// Gamestates
	protected Game gameState;
	private DiscreteGame dGame;
	// Move information
	public MOVE pathDirection;
	public Edge edge;
	public int depth = 0, pathLength, junctionIndex;
	// Scores [pill, ghost, survival]
	public double[] oldScores, oldMaxScores, newScores, newMaxScores;
	public double oldVisitCount, oldMaxVisitCount, newVisitCount, newMaxVisitCount;
	// Tree fields
	protected MCTNode[] children;
	private MCTNode parent;
	// Move fields used for expansion
	private MCTNode selectedNode = this;

	/**
	 * Constructor for root node
	 * 
	 * @param gameState Gamestate of the node
	 */
	public MCTNode(DiscreteGame dGame, Game gameState) {
		this.depth = 0;
		this.gameState = gameState;
		this.dGame = dGame;
		//
		this.parent = null;
		int current = gameState.getPacmanCurrentNodeIndex();

		if (gameState.isJunction(current)) {
			this.junctionIndex = current;
			edge = null;
		} else {
			this.junctionIndex = -1;
		}
		// At first any node will not have child nodes
		children = null;
		//
		oldMaxScores = new double[3];
		oldScores = new double[3];
		newScores = new double[3];
		newMaxScores = new double[3];
	}

	/**
	 * Constructor for MCT node
	 * 
	 * @param game StateThe gamestate of the node
	 * @param parent The parent of the node.
	 * @param playerNode True if this is a node for a player-move, false if opponent move
	 */
	public MCTNode(Game gameState, MCTNode parent, MOVE pathDir, Edge edge, int junctionI,
			int pathLength) {
		this.gameState = gameState;
		this.parent = parent;
		this.pathDirection = pathDir;
		this.junctionIndex = junctionI;
		this.depth = parent.depth + 1;
		this.edge = edge;
		this.pathLength = pathLength;
		// At first any node will not have child nodes
		children = null;
		//
		oldMaxScores = new double[3];
		oldScores = new double[3];
		newScores = new double[3];
		newMaxScores = new double[3];
	}

	// The children of a node should represent at least this part of the node"s visits.
	public static double minChildVisitRate = .5;

	public void backPropagate(MCTResult result, SelectionType selectionType, int treePhaseDepth) {
		MCTNode node = this;
		if (node.depth > treePhaseDepth) {
			node.newVisitCount--;
		} else {
			node.addValue(result);
		}
		node = node.getParent();
		while (node != null) {
			if (node.depth > treePhaseDepth) {
				node.newVisitCount--;
				node = node.getParent();
				continue;
			}
			// Update the node"s values (Average Back-propagation)
			node.addValue(result);
			// Maximum back-propagate the values of the child with the highest score
			MCTNode topChild = null;
			double childrenVCount = 0.;
			double topRate = Double.NEGATIVE_INFINITY;// , myRate = Double.NEGATIVE_INFINITY;
			for (MCTNode child : node.getChildren()) {
				childrenVCount += child.newVisitCount;
				//
				if (selectionType == SelectionType.SurvivalRate) {
					// myRate = node.getAlphaSurvivalScore(false);
					if (child.getAlphaSurvivalScore(true) > topRate) {
						topChild = child;
						topRate = topChild.getAlphaSurvivalScore(true);
					}
				} else if (selectionType == SelectionType.PillScore) {
					// myRate = node.getAlphaPillScore(false);
					if (child.getAlphaPillScore(true) > topRate) {
						topChild = child;
						topRate = child.getAlphaPillScore(true);
					}
				} else if (selectionType == SelectionType.GhostScore) {
					// myRate = node.getAlphaGhostScore(false);
					if (child.getAlphaGhostScore(true) > topRate) {
						topChild = child;
						topRate = child.getAlphaGhostScore(true);
					}
				}
			}
			//
			if (childrenVCount / node.newVisitCount < minChildVisitRate || topChild == null) {
				node.oldMaxScores = node.oldScores;
				node.newMaxScores = node.newScores;
				//
				node.oldMaxVisitCount = node.oldVisitCount;
				node.newMaxVisitCount = node.newVisitCount;
				node = node.getParent();
				continue;
			}

			node.oldMaxScores = topChild.oldMaxScores;
			node.newMaxScores = topChild.newMaxScores;
			//
			node.oldMaxVisitCount = topChild.oldMaxVisitCount;
			node.newMaxVisitCount = topChild.newMaxVisitCount;
			//
			node = node.getParent();
		}
	}

	public void propagateMaxValues(SelectionType selectionType) {
		if (!isLeaf()) {
			double childrenVCount = 0.;
			//
			for (MCTNode c : children) {
				childrenVCount += c.newVisitCount;
				if (c.isLeaf()) {
					continue;
				}
				c.propagateMaxValues(selectionType);
			}
			// // The children didn"t have enough visits compared to this node.
			if (childrenVCount / newVisitCount < minChildVisitRate) {
				oldMaxScores = oldScores;
				newMaxScores = newScores;
				//
				oldMaxVisitCount = oldVisitCount;
				newMaxVisitCount = newVisitCount;
				return;
			}
			//
			MCTNode topChild = null;
			double topRate = Double.NEGATIVE_INFINITY;// , myRate = Double.NEGATIVE_INFINITY;
			//
			for (MCTNode child : children) {
				// Do not use values of unvisited children
				if (child.newVisitCount < UCTSelection.minVisits) {
					continue;
				}
				if (selectionType == SelectionType.SurvivalRate) {
					// myRate = getAlphaSurvivalScore(false);
					if (child.getAlphaSurvivalScore(true) > topRate) {
						topChild = child;
						topRate = topChild.getAlphaSurvivalScore(true);
					}
				} else if (selectionType == SelectionType.PillScore) {
					// myRate = getAlphaPillScore(false);
					if (child.getAlphaPillScore(true) > topRate) {
						topChild = child;
						topRate = child.getAlphaPillScore(true);
					}
				} else if (selectionType == SelectionType.GhostScore) {
					// myRate = getAlphaGhostScore(false);
					if (child.getAlphaGhostScore(true) > topRate) {
						topChild = child;
						topRate = child.getAlphaGhostScore(true);
					}
				}
			}
			if (topChild == null) {
				oldMaxScores = oldScores;
				newMaxScores = newScores;
				//
				oldMaxVisitCount = oldVisitCount;
				newMaxVisitCount = newVisitCount;
				return;
			}
			//
			oldMaxScores = topChild.oldMaxScores;
			newMaxScores = topChild.newMaxScores;
			//
			oldMaxVisitCount = topChild.oldMaxVisitCount;
			newMaxVisitCount = topChild.newMaxVisitCount;
		}
	}

	public void propagateMaxValues(SelectionType selectionType, double minRate) {
		if (!isLeaf()) {
			double childrenVCount = 0.;
			for (MCTNode c : children) {
				childrenVCount += c.newVisitCount;
				if (c.isLeaf()) {
					continue;
				}
				c.propagateMaxValues(selectionType, minRate);
			}
			// The children didn"t have enough visits compared to this node.
			if (childrenVCount / newVisitCount < minChildVisitRate) {
				oldMaxScores = oldScores;
				newMaxScores = newScores;
				//
				oldMaxVisitCount = oldVisitCount;
				newMaxVisitCount = newVisitCount;
				return;
			}
			//
			MCTNode topChild = null;
			double topRate = Double.NEGATIVE_INFINITY;// , myRate = Double.NEGATIVE_INFINITY;
			//
			for (MCTNode child : children) {
				// Do not use values of unvisited children
				if (child.newVisitCount < UCTSelection.minVisits
						|| getNewMaxValue(SURV_I) < minRate) {
					continue;
				}
				if (selectionType == SelectionType.SurvivalRate) {
					// myRate = getAlphaSurvivalScore(false);
					if (child.getAlphaSurvivalScore(true) > topRate) {
						topChild = child;
						topRate = topChild.getAlphaSurvivalScore(true);
					}
				} else if (selectionType == SelectionType.PillScore) {
					// myRate = getAlphaPillScore(false);
					if (child.getAlphaPillScore(true) > topRate) {
						topChild = child;
						topRate = child.getAlphaPillScore(true);
					}
				} else if (selectionType == SelectionType.GhostScore) {
					// myRate = getAlphaGhostScore(false);
					if (child.getAlphaGhostScore(true) > topRate) {
						topChild = child;
						topRate = child.getAlphaGhostScore(true);
					}
				}
			}
			//
			if (topChild == null) {
				oldMaxScores = oldScores;
				newMaxScores = newScores;
				//
				oldMaxVisitCount = oldVisitCount;
				newMaxVisitCount = newVisitCount;
				return;
			}
			//
			oldMaxScores = topChild.oldMaxScores;
			newMaxScores = topChild.newMaxScores;
			//
			oldMaxVisitCount = topChild.oldMaxVisitCount;
			newMaxVisitCount = topChild.newMaxVisitCount;
		}
	}

	/**
	 * Check based on some simple rules to determine if this node should be expanded
	 * 
	 * @param maxPathLength The maximum path-length for paths in the tree
	 * @return true if this node can be expanded, false otherwise
	 */
	public boolean canExpand(int maxPathLength, boolean variable_depth, int maxNodeDepth) {
		if (!isRoot()) {
			// make sure the node has sufficient simulations
			if (newVisitCount < UCTSelection.minVisits) {
				return false;
			}
			// Enforce the max path length rule
			if (variable_depth && getPathLength() > maxPathLength) {
				return false;
			}
			// Max node depth
			if (!variable_depth && depth > maxNodeDepth) {
				return false;
			}
			return isLeaf();
		}
		// The root-node can always be expanded
		return true;
	}

	/**
	 * The implementation of this method should do the following: 1. Initialize the children array with size equal to
	 * the number of possible child nodes (if not initialized)
	 * 
	 * @return The position of the first expanded child
	 */
	public abstract void expand(DiscreteGame dGame, MCTNode rootNode);

	/**
	 * Selects a leafnode in the tree for expansion according to the given selection method.
	 * 
	 * @param selection The class containing the selection method
	 * @return The selected leafnode.
	 */
	public MCTNode selection(MCTSelection selection, boolean maxSelection) {
		MCTNode node = this;

		while (!node.isLeaf()) {
			node = selection.selectNode(node, maxSelection);
		}
		return node;
	}

	/**
	 * Selects a leafnode in the tree for expansion according to the given selection method.
	 * 
	 * @param selection The class containing the selection method
	 * @return The selected leafnode.
	 */
	public MCTNode selection(MCTSelection selection, boolean maxSelection, int maxPathLength) {
		MCTNode node = this;

		while (!node.isLeaf() && node.pathLength <= maxPathLength) {
			node = selection.selectNode(node, maxSelection);
		}
		return node;
	}

	public void addVisit() {
		// This is the number of visits for the current turn.
		newVisitCount++;
	}

	public void addValue(MCTResult result) {
		newScores[PILL_I] += result.pillScore;
		newScores[GHOST_I] += result.ghostScore;
		//
		if (result.target) {
			newScores[SURV_I]++;
		}
		// Leafnodes have maxvalue = value
		if (isLeaf()) {
			oldMaxScores = oldScores;
			newMaxScores = newScores;
			//
			oldMaxVisitCount = oldVisitCount;
			newMaxVisitCount = newVisitCount;
		}
	}

	public void addDistance(int dist) {
		this.pathLength += dist;
		if (children != null) {
			for (MCTNode c : children) {
				c.addDistance(dist);
			}
		}
	}

	public void setNodeDepth(int depth) {
		this.depth = depth;
		if (children != null) {
			for (MCTNode c : children) {
				c.setNodeDepth(depth + 1);
			}
		}
	}

	public void discountValues(double discount) {
		oldScores[PILL_I] += newScores[PILL_I];
		oldScores[GHOST_I] += newScores[GHOST_I];
		oldScores[SURV_I] += newScores[SURV_I];
		oldVisitCount += newVisitCount;
		//
		oldScores[PILL_I] *= discount;
		oldScores[GHOST_I] *= discount;
		oldScores[SURV_I] *= discount;
		oldVisitCount *= discount;
		// Reset the maximum scores, they should be reset later.
		oldMaxScores = new double[3];
		oldMaxVisitCount = 0;
		//
		newVisitCount = 0;
		newMaxVisitCount = 0;
		//
		newScores = new double[3];
		newMaxScores = new double[3];
		//
		if (children != null) {
			for (MCTNode c : children) {
				c.discountValues(discount);
			}
		}
	}

	public void copyStats(MCTNode node2) {
		System.arraycopy(node2.newMaxScores, 0, newMaxScores, 0, newMaxScores.length);
		System.arraycopy(node2.newScores, 0, newScores, 0, newScores.length);
		System.arraycopy(node2.oldMaxScores, 0, oldMaxScores, 0, oldMaxScores.length);
		System.arraycopy(node2.oldScores, 0, oldScores, 0, oldScores.length);
		//
		oldMaxVisitCount = node2.oldMaxVisitCount;
		oldVisitCount = node2.oldVisitCount;
		//
		newMaxVisitCount = node2.newMaxVisitCount;
		newVisitCount = node2.newVisitCount;
	}

	public void clearStats() {
		// Clear the stats.
		oldVisitCount = 0;
		newVisitCount = 0;
		oldMaxVisitCount = 0;
		newMaxVisitCount = 0;
		//
		oldScores = new double[3];
		newScores = new double[3];
		oldMaxScores = new double[3];
		newMaxScores = new double[3];
		//
		if (!isLeaf()) {
			for (MCTNode c : children) {
				c.clearStats();
			}
		}
	}

	public void substractStats(MCTNode node2) {
		// Substract the stats from the node.
		oldVisitCount -= node2.oldVisitCount;
		newVisitCount -= node2.newVisitCount;
		//
		oldScores[PILL_I] -= node2.oldScores[PILL_I];
		oldScores[GHOST_I] -= node2.oldScores[GHOST_I];
		oldScores[SURV_I] -= node2.oldScores[SURV_I];
		//
		newScores[PILL_I] -= node2.newScores[PILL_I];
		newScores[GHOST_I] -= node2.newScores[GHOST_I];
		newScores[SURV_I] -= node2.newScores[SURV_I];
	}

	public void addStats(MCTNode node2) {
		// Add the stats from the node.
		oldVisitCount += node2.oldVisitCount;
		newVisitCount += node2.newVisitCount;
		//
		oldScores[PILL_I] += node2.oldScores[PILL_I];
		oldScores[GHOST_I] += node2.oldScores[GHOST_I];
		oldScores[SURV_I] += node2.oldScores[SURV_I];
		//
		newScores[PILL_I] += node2.newScores[PILL_I];
		newScores[GHOST_I] += node2.newScores[GHOST_I];
		newScores[SURV_I] += node2.newScores[SURV_I];
	}

	int[] pacLocations;
	MOVE[] moves;

	/**
	 * Does a simulation using the rootnode"s game state followed by actions up to this node"s position
	 * 
	 * @param simulation The simulation-class to use for simulating the playout
	 */
	public MCTResult simulate(StrategySimulation simulation, int simCount, int pathLength,
			SelectionType selectionType, boolean strategic) {
		// Do a simulation starting at the root"s game state
		selectedNode = this;
		pacLocations = new int[selectedNode.depth + 1];
		moves = new MOVE[selectedNode.depth];
		int i = 0;
		while (!selectedNode.isRoot()) {
			moves[i] = selectedNode.getPathDirection();
			pacLocations[i] = selectedNode.getJunctionIndex();
			i++;
			selectedNode = selectedNode.getParent();
		}
		if (selectedNode.junctionIndex > -1) {
			pacLocations[i] = selectedNode.getJunctionIndex();
		}
		// Get the root"s game states
		Game interState = selectedNode.getGameState().copy();
		DiscreteGame disGame = selectedNode.getdGame().copy();
		//
		return simulation.playout(disGame, interState, moves, pacLocations, simCount, pathLength,
				selectionType, strategic);
	}

	public double getAlphaSurvivalScore(boolean max) {
		if (no_surv_reuse) {
			if (max) {
				if (newMaxVisitCount > 0) {
					return newMaxScores[SURV_I] / newMaxVisitCount;
				}
			} else {
				if (newVisitCount > 0) {
					return newScores[SURV_I] / newVisitCount;
				}
			}
		} else {
			if (max) {
				if (oldMaxVisitCount + newMaxVisitCount > 0) {
					return (oldMaxScores[SURV_I] + newMaxScores[SURV_I])
							/ (oldMaxVisitCount + newMaxVisitCount);
				}
			} else {
				if (oldVisitCount + newVisitCount > 0) {
					return (oldScores[SURV_I] + newScores[SURV_I])
							/ (oldVisitCount + newVisitCount);
				}
			}
		}
		return 0;
	}

	public double getAlphaPillScore(boolean max) {
		if (max) {
			if (oldMaxVisitCount + newMaxVisitCount > 0) {
				return ((oldMaxScores[PILL_I] + newMaxScores[PILL_I]) / (oldMaxVisitCount + newMaxVisitCount))
						* getAlphaSurvivalScore(max);
			}
		} else {
			if (oldVisitCount + newVisitCount > 0) {
				return ((oldScores[PILL_I] + newScores[PILL_I]) / (oldVisitCount + newVisitCount))
						* getAlphaSurvivalScore(max);
			}
		}

		return 0;
	}

	public double getAlphaGhostScore(boolean max) {
		if (max) {
			if (newMaxVisitCount > 0) {
				return (newMaxScores[GHOST_I] / newMaxVisitCount) * getAlphaSurvivalScore(max);
			}
		} else {
			if (newMaxVisitCount > 0) {
				return (newScores[GHOST_I] / newVisitCount) * getAlphaSurvivalScore(max);
			}
		}
		return 0;
		// if (max) {
		// if (oldMaxVisitCount + newMaxVisitCount > 0) {
		// return ((oldMaxScores[GHOST_I] + newMaxScores[GHOST_I]) / (oldMaxVisitCount + newMaxVisitCount))
		// * getAlphaSurvivalScore(max);
		// }
		// } else {
		// if (oldVisitCount + newVisitCount > 0) {
		// return ((oldScores[GHOST_I] + newScores[GHOST_I]) / (oldVisitCount + newVisitCount))
		// * getAlphaSurvivalScore(max);
		// }
		// }
		// return 0;
	}

	// These are helper functions to get info from the reward vectors by index
	public double getNewMeanValue(int i) {
		if (newVisitCount > 0) {
			return newScores[i] / newVisitCount;
		} else {
			return 0.;
		}
	}

	public double getNewMaxValue(int i) {
		if (newMaxVisitCount > 0) {
			return newMaxScores[i] / newMaxVisitCount;
		} else {
			return 0.;
		}
	}

	public double getOldMaxValue(int i) {
		if (oldMaxVisitCount > 0) {
			return oldMaxScores[i] / oldMaxVisitCount;
		} else {
			return 0.;
		}
	}

	public double getOldMeanValue(int i) {
		if (oldVisitCount > 0) {
			return oldScores[i] / oldVisitCount;
		} else {
			return 0.;
		}
	}

	public MCTNode[] getChildren() {
		return children;
	}

	public int getDepth() {
		return depth;
	}

	public DiscreteGame getdGame() {
		return dGame;
	}

	public Edge getEdge() {
		return edge;
	}

	public int getEdgeId() {
		if (edge != null)
			return edge.uniqueId;
		else
			return -1;
	}

	public Game getGameState() {
		return gameState;
	}

	public int getJunctionIndex() {
		return junctionIndex;
	}

	public MCTNode getParent() {
		return parent;
	}

	public MOVE getPathDirection() {
		return pathDirection;
	}

	public int getPathLength() {
		return pathLength;
	}

	public boolean isLeaf() {
		if (children == null)
			return true;
		else
			return children.length == 0;
	}

	public boolean isRoot() {
		return parent == null;
	}

	public void setdGame(DiscreteGame dGame) {
		this.dGame = dGame;
	}

	public void setEdge(Edge edge) {
		this.edge = edge;
	}

	public void setGameState(Game gameState) {
		this.gameState = gameState;
	}

	public void setParent(MCTNode parent) {
		this.parent = parent;
	}

	public void setChildren(MCTNode[] children) {
		this.children = children;
	}

	public void setPathLength(int pathLength) {
		this.pathLength = pathLength;
	}

	@Override
	public String toString() {
		DecimalFormat df2 = new DecimalFormat("#,###,###,##0.000");
		// meanScores, maxScores, currentScores, maxCurrentScores;
		String intro = String.format("Moves %s:", pathDirection);
		String mean = String.format("Old MEAN\t [%s, %s, %s]\t %d visits.",
				df2.format(getOldMeanValue(0)), df2.format(getOldMeanValue(1)),
				df2.format(getOldMeanValue(2)), (int) oldVisitCount);
		String max = String.format("Old MAX \t [%s, %s, %s]\t %d visits.",
				df2.format(getOldMaxValue(0)), df2.format(getOldMaxValue(1)),
				df2.format(getOldMaxValue(2)), (int) oldMaxVisitCount);
		String c_mean = String.format("Current MEAN\t [%s, %s, %s]\t %d visits.",
				df2.format(getNewMeanValue(0)), df2.format(getNewMeanValue(1)),
				df2.format(getNewMeanValue(2)), (int) newVisitCount);
		String c_max = String.format("Current MAX\t [%s, %s, %s]\t %d visits.",
				df2.format(getNewMaxValue(0)), df2.format(getNewMaxValue(1)),
				df2.format(getNewMaxValue(2)), (int) newMaxVisitCount);
		String alpha_s = String.format("Max alpha\t [%s, %s, %s]",
				df2.format(getAlphaPillScore(true)), df2.format(getAlphaGhostScore(true)),
				df2.format(getAlphaSurvivalScore(true)));
		String m_alpha_s = String.format("Mean alpha\t [%s, %s, %s]",
				df2.format(getAlphaPillScore(false)), df2.format(getAlphaGhostScore(false)),
				df2.format(getAlphaSurvivalScore(false)));

		return String
				.format("%s\n\t%s\n\t%s\n\t%s\n\t%s\n\n\t%s\n\t%s\n------------------------------------------------------",
						intro, mean, max, c_mean, c_max, alpha_s, m_alpha_s);
	}

	/**
	 * Validate the tree using the gamestate to check all junctions
	 * 
	 * @param game
	 */
	public void validate(Game game) {
		if (isRoot() && children != null) {
			if (junctionIndex != -1) {
				// Check if this is actually pacman"s position
				if (game.getPacmanCurrentNodeIndex() != junctionIndex) {
					System.err.println("Error at root, pacman is not on junction.");
				}
				// Check if the number of children match the possible options
				if (game.getPossibleMoves(junctionIndex).length != children.length) {
					System.err.println("Error at root, on junction.");
					System.err.println("Number of children: " + children.length);
					System.err.println("Should be: " + game.getPossibleMoves(junctionIndex).length);
				}
			} else {
				// Check if the number of children match the possible options
				if (game.getPossibleMoves(game.getPacmanCurrentNodeIndex()).length != children.length) {
					System.err.println("Error at root, on edge.");
					System.err.println("Number of children: " + children.length);
					System.err.println("Should be: "
							+ game.getPossibleMoves(game.getPacmanCurrentNodeIndex()).length);
				}
			}
			//
			for (MCTNode c : children) {
				c.validate(game);
			}
		} else if (children != null && children.length > 0) {
			if (oldVisitCount > parent.oldVisitCount) {
				System.err.println("My visitcount is higher than parents, pathlength: "
						+ pathLength);
				if (parent.isRoot())
					System.err.println("Parent is root");
				if (parent.junctionIndex > -1)
					System.err.println("Parent is junction");
			}
			// Check if the number of children match the possible options -1 for reverse
			if (game.getPossibleMoves(junctionIndex).length - 1 != children.length) {
				System.err.println("Error at child, on junction.");
				System.err.println("Number of children: " + children.length);
				System.err.println("Should be: "
						+ (game.getPossibleMoves(junctionIndex).length - 1));
			}
			//
			for (MCTNode c : children) {
				c.validate(game);
			}
		}
	}
}
