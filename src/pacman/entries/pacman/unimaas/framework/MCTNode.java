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
	// Gamestates
	protected Game gameState;
	private DiscreteGame dGame;
	// Move information
	public MOVE pathDirection;
	public Edge edge;
	public int depth = 0, pathLength, junctionIndex;
	// Scores [pill, ghost, survival]
	public double[] meanScores, maxScores, currentScores, maxCurrentScores;
	public double totalVisitCount, maxTotalVisitCount, currentVisitCount, maxCurrentVisitCount;
	// Tree fields
	protected MCTNode[] children;
	private MCTNode parent;
	// Move fields used for expansion
	private MCTNode selectedNode = this;

	/**
	 * Constructor for root node
	 * 
	 * @param gameState
	 *            Gamestate of the node
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
		maxScores = new double[3];
		meanScores = new double[3];
		currentScores = new double[3];
		maxCurrentScores = new double[3];
	}

	/**
	 * Constructor for MCT node
	 * 
	 * @param game
	 *            StateThe gamestate of the node
	 * @param parent
	 *            The parent of the node.
	 * @param playerNode
	 *            True if this is a node for a player-move, false if opponent move
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
		maxScores = new double[3];
		meanScores = new double[3];
		currentScores = new double[3];
		maxCurrentScores = new double[3];
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
		//
		meanScores[0] *= discount;
		meanScores[1] *= discount;
		meanScores[2] *= discount;
		//
		totalVisitCount *= discount;
		// Reset the maximum scores, they should be reset later.
		maxScores = new double[3];
		maxTotalVisitCount = 0;
		//
		currentVisitCount = 0;
		maxCurrentVisitCount = 0;
		//
		currentScores = new double[3];
		maxCurrentScores = new double[3];
		//
		if (children != null) {
			for (MCTNode c : children) {
				c.discountValues(discount);
			}
		}
	}

	public void addValue(MCTResult result) {
		meanScores[0] += result.pillScore;
		currentScores[0] += result.pillScore;
		meanScores[1] += result.ghostScore;
		currentScores[1] += result.ghostScore;
		//
		if (result.target) {
			meanScores[2]++;
			currentScores[2]++;
		}
		// Leafnodes have maxvalue = value
		if (isLeaf()) {
			maxScores = meanScores;
			maxCurrentScores = currentScores;
			//
			maxTotalVisitCount = totalVisitCount;
			maxCurrentVisitCount = currentVisitCount;
		}
	}
	
	/**
	 * Validate the tree using the gamestate to check all junctions
	 * @param game
	 */
	public void validate(Game game) {
		if(isRoot() && children != null) {
			if(junctionIndex != -1) {
				// Check if this is actually pacman's position
				if(game.getPacmanCurrentNodeIndex() != junctionIndex) {
					System.err.println("Error at root, pacman is not on junction.");
				}
				// Check if the number of children match the possible options
				if(game.getPossibleMoves(junctionIndex).length != children.length) {
					System.err.println("Error at root, on junction.");
					System.err.println("Number of children: " + children.length);
					System.err.println("Should be: " + game.getPossibleMoves(junctionIndex).length);
				}
			} else {
				// Check if the number of children match the possible options
				if(game.getPossibleMoves(game.getPacmanCurrentNodeIndex()).length != children.length) {
					System.err.println("Error at root, on edge.");
					System.err.println("Number of children: " + children.length);
					System.err.println("Should be: " + game.getPossibleMoves(game.getPacmanCurrentNodeIndex()).length);
				}
			}
			//
			for (MCTNode c : children) {
				c.validate(game);
			}
		} else if(children != null) {
			// Check if the number of children match the possible options -1 for reverse
			if(game.getPossibleMoves(junctionIndex).length - 1 != children.length) {
				System.err.println("Error at child, on junction.");
				System.err.println("Number of children: " + children.length);
				System.err.println("Should be: " + (game.getPossibleMoves(junctionIndex).length - 1));
			}
			//
			for (MCTNode c : children) {
				c.validate(game);
			}
		}
	}

	/**
	 * Adds a visit to the visit counter
	 */
	public void addVisit() {
		// This is the number of visits for the current turn.
		currentVisitCount++;
		totalVisitCount++;
	}

	// The children of a node should represent at least this part of the node's visits.
	private double minChildVisitRate = .6;

	public void backPropagate(MCTResult result, SelectionType selectionType, int treePhaseDepth) {
		MCTNode node = this;
		if (node.depth > treePhaseDepth) {
			node.currentVisitCount--;
			node.totalVisitCount--;
		} else {
			node.addValue(result);
		}
		node = node.getParent();

		while (node != null) {
			// Update the node's values (Average Back-propagation)
			if (node.depth > treePhaseDepth) {
				node.currentVisitCount--;
				node.totalVisitCount--;
				node = node.getParent();
				continue;
			}
			node.addValue(result);
			// Maximum back-propagate the values of the child with the highest score
			MCTNode topChild = null;
			double topRate = Double.NEGATIVE_INFINITY;
			double childrenVCount = 0.;
			for (MCTNode child : node.getChildren()) {
				childrenVCount += child.getVisitCount();
				//
				if (selectionType == SelectionType.SurvivalRate) {
					if (child.getAlphaSurvivalScore() > topRate) {
						topChild = child;
						topRate = topChild.getAlphaSurvivalScore();
					}
				} else if (selectionType == SelectionType.PillScore) {
					if (child.getAlphaPillScore() > topRate) {
						topChild = child;
						topRate = child.getAlphaPillScore();
					}
				} else if (selectionType == SelectionType.GhostScore) {
					if (child.getAlphaGhostScore() > topRate) {
						topChild = child;
						topRate = child.getAlphaGhostScore();
					}
				}
			}
			// The children didn't have enough visits compared to this node,
			// therefore don't use their values to back-propagate
			if ((childrenVCount / getVisitCount()) < minChildVisitRate) {
				node.maxScores = node.meanScores;
				node.maxCurrentScores = node.currentScores;
				//
				node.maxTotalVisitCount = node.totalVisitCount;
				node.maxCurrentVisitCount = node.currentVisitCount;
				node = node.getParent();
				continue;
			}

			node.maxScores = topChild.maxScores;
			node.maxCurrentScores = topChild.maxCurrentScores;
			//
			node.maxTotalVisitCount = topChild.maxTotalVisitCount;
			node.maxCurrentVisitCount = topChild.maxCurrentVisitCount;
			//
			node = node.getParent();
		}
	}

	public void propagateMaxValues(SelectionType selectionType) {
		if (!isLeaf()) {
			double childrenVCount = 0.;
			//
			for (MCTNode c : children) {
				childrenVCount += c.getVisitCount();
				if (c.isLeaf()) {
					continue;
				}
				c.propagateMaxValues(selectionType);
			}
			// The children didn't have enough visits compared to this node.
			if (childrenVCount / getVisitCount() < minChildVisitRate) {
				maxScores = meanScores;
				maxCurrentScores = currentScores;
				//
				maxTotalVisitCount = totalVisitCount;
				maxCurrentVisitCount = currentVisitCount;
				return;
			}
			//
			MCTNode topChild = null;
			double topRate = Double.NEGATIVE_INFINITY;
			//
			for (MCTNode child : children) {
				// Do not use values of unvisited children
				if (child.currentVisitCount < UCTSelection.minVisits) {
					continue;
				}
				if (selectionType == SelectionType.SurvivalRate) {
					if (child.getAlphaSurvivalScore() > topRate) {
						topChild = child;
						topRate = topChild.getAlphaSurvivalScore();
					}
				} else if (selectionType == SelectionType.PillScore) {
					if (child.getAlphaPillScore() > topRate) {
						topChild = child;
						topRate = child.getAlphaPillScore();
					}
				} else if (selectionType == SelectionType.GhostScore) {
					if (child.getAlphaGhostScore() > topRate) {
						topChild = child;
						topRate = child.getAlphaGhostScore();
					}
				}
			}
			if (topChild == null) {
				maxScores = meanScores;
				maxCurrentScores = currentScores;
				//
				maxTotalVisitCount = totalVisitCount;
				maxCurrentVisitCount = currentVisitCount;
				return;
			}
			//
			maxScores = topChild.maxScores;
			maxCurrentScores = topChild.maxCurrentScores;
			//
			maxTotalVisitCount = topChild.maxTotalVisitCount;
			maxCurrentVisitCount = topChild.maxCurrentVisitCount;
		}
	}

	public void propagateMaxValues(SelectionType selectionType, double minRate) {
		if (!isLeaf()) {
			//
			double childrenVCount = 0.;
			for (MCTNode c : children) {
				childrenVCount += c.getVisitCount();
				if (c.isLeaf()) {
					continue;
				}
				c.propagateMaxValues(selectionType, minRate);
			}
			// The children didn't have enough visits compared to this node.
			if (childrenVCount / getVisitCount() < minChildVisitRate) {
				maxScores = meanScores;
				maxCurrentScores = currentScores;
				//
				maxTotalVisitCount = totalVisitCount;
				maxCurrentVisitCount = currentVisitCount;
				return;
			}
			//
			MCTNode topChild = null;
			double topRate = Double.NEGATIVE_INFINITY;
			//
			for (MCTNode child : children) {
				// Do not use values of unvisited children
				if (child.currentVisitCount < UCTSelection.minVisits
						|| child.getAlphaSurvivalScore() < minRate) {
					continue;
				}
				if (selectionType == SelectionType.SurvivalRate) {
					if (child.getAlphaSurvivalScore() > topRate) {
						topChild = child;
						topRate = topChild.getAlphaSurvivalScore();
					}
				} else if (selectionType == SelectionType.PillScore) {
					if (child.getAlphaPillScore() > topRate) {
						topChild = child;
						topRate = child.getAlphaPillScore();
					}
				} else if (selectionType == SelectionType.GhostScore) {
					if (child.getAlphaGhostScore() > topRate) {
						topChild = child;
						topRate = child.getAlphaGhostScore();
					}
				}
			}
			//
			if (topChild == null) {
				maxScores = meanScores;
				maxCurrentScores = currentScores;
				//
				maxTotalVisitCount = totalVisitCount;
				maxCurrentVisitCount = currentVisitCount;
				return;
			}
			//
			maxScores = topChild.maxScores;
			maxCurrentScores = topChild.maxCurrentScores;
			//
			maxTotalVisitCount = topChild.maxTotalVisitCount;
			maxCurrentVisitCount = topChild.maxCurrentVisitCount;
		}
	}

	/**
	 * Check based on some simple rules to determine if this node should be expanded
	 * 
	 * @param maxPathLength
	 *            The maximum path-length for paths in the tree
	 * @return true if this node can be expanded, false otherwise
	 */
	public boolean canExpand(int maxPathLength, boolean variable_depth, int maxNodeDepth) {
		if (!isRoot()) {
			if (variable_depth && getPathLength() <= maxPathLength) {
				// Don't expand nodes that have not been simulated
				if (currentVisitCount < UCTSelection.minVisits) {
					return false;
				}
			} else if (!variable_depth && depth <= maxNodeDepth) {
				// Don't expand nodes that have not been simulated
				if (currentVisitCount < UCTSelection.minVisits) {
					return false;
				}
			} else {
				return false;
			}
		}
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
	 * Get the children array of this node, only contains children in positions which have been expanded.
	 * 
	 * @return Children array
	 */
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

	/**
	 * @return the edgeId of this node's edge to it's parent
	 */
	public int getEdgeId() {
		if (edge != null)
			return edge.uniqueId;
		else
			return -1;
	}

	/**
	 * Gets the gamestate of this node
	 * 
	 * @return Gamestate represented by this node
	 */
	public Game getGameState() {
		return gameState;
	}

	/**
	 * @return the junctionIndex
	 */
	public int getJunctionIndex() {
		return junctionIndex;
	}

	/**
	 * Returns the parent node for this node
	 * 
	 * @return The MCTNode that is the parent of this node
	 */
	public MCTNode getParent() {
		return parent;
	}

	/**
	 * @return The direction pacman went from the previous node to reach this node
	 */
	public MOVE getPathDirection() {
		return pathDirection;
	}

	/**
	 * @return the pathLength
	 */
	public int getPathLength() {
		return pathLength;
	}

	public double getTotalMaxVisitCount() {
		return maxTotalVisitCount;
	}

	public double getVisitCount() {
		return totalVisitCount;
	}

	public double getCurrentVisitCount() {
		return currentVisitCount;
	}

	/**
	 * Checks if this node has any children
	 * 
	 * @return True if this node is a leaf node
	 */
	public boolean isLeaf() {
		if (children == null)
			return true;
		else
			return children.length == 0;
	}

	/**
	 * @return True of this is the rootnode of the MC tree
	 */
	public boolean isRoot() {
		return parent == null;
	}

	/**
	 * Selects a leafnode in the tree for expansion according to the given selection method.
	 * 
	 * @param selection
	 *            The class containing the selection method
	 * @return The selected leafnode.
	 */
	public MCTNode selection(MCTSelection selection, boolean maxSelection) {
		MCTNode node = this;

		while (!node.isLeaf()) {
			node = selection.selectNode(node, maxSelection);
		}
		return node;
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

	public void copyStats(MCTNode node2) {
		System.arraycopy(node2.maxScores, 0, maxScores, 0, maxScores.length);
		System.arraycopy(node2.meanScores, 0, meanScores, 0, meanScores.length);
		//
		maxTotalVisitCount = node2.maxTotalVisitCount;
		totalVisitCount = node2.totalVisitCount;
	}

	public void substractStats(MCTNode node2) {
		// Substract the stats from the node.
		totalVisitCount -= node2.totalVisitCount;
		meanScores[0] -= node2.meanScores[0];
		meanScores[1] -= node2.meanScores[1];
		meanScores[2] -= node2.meanScores[2];
	}

	public void addStats(MCTNode node2) {
		// Add the stats from the node.
		totalVisitCount += node2.totalVisitCount;
		meanScores[0] += node2.meanScores[0];
		meanScores[1] += node2.meanScores[1];
		meanScores[2] += node2.meanScores[2];
	}

	/**
	 * @param pathLength
	 *            the pathLength to set
	 */
	public void setPathLength(int pathLength) {
		this.pathLength = pathLength;
	}

	int[] pacLocations;
	MOVE[] moves;

	/**
	 * Does a simulation using the rootnode's game state followed by actions up to this node's position
	 * 
	 * @param simulation
	 *            The simulation-class to use for simulating the playout
	 */
	public MCTResult simulate(StrategySimulation simulation, int simCount,
			SelectionType selectionType, int target, boolean strategic) {
		// Do a simulation starting at the root's game state
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
		// Get the root's game states
		Game interState = selectedNode.getGameState().copy();
		DiscreteGame disGame = selectedNode.getdGame().copy();
		//
		return simulation.playout(disGame, interState, moves, pacLocations, simCount,
				selectionType, strategic);
	}

	@Override
	public String toString() {
		DecimalFormat df2 = new DecimalFormat("#,###,###,##0.000");
		// meanScores, maxScores, currentScores, maxCurrentScores;
		String intro = String.format("Moves %s:", pathDirection);
		String mean = String.format("Total MEAN\t [%s, %s, %s]\t %d visits.",
				df2.format(getMeanValue(0)), df2.format(getMeanValue(1)),
				df2.format(getMeanValue(2)), (int) totalVisitCount);
		String max = String.format("Total MAX\t [%s, %s, %s]\t %d visits.",
				df2.format(getMaxValue(0)), df2.format(getMaxValue(1)), df2.format(getMaxValue(2)),
				(int) maxTotalVisitCount);
		String c_mean = String.format("Current MEAN\t [%s, %s, %s]\t %d visits.",
				df2.format(getCurrentMeanValue(0)), df2.format(getCurrentMeanValue(1)),
				df2.format(getCurrentMeanValue(2)), (int) currentVisitCount);
		String c_max = String.format("Current MAX\t [%s, %s, %s]\t %d visits.",
				df2.format(getCurrentMaxValue(0)), df2.format(getCurrentMaxValue(1)),
				df2.format(getCurrentMaxValue(2)), (int) maxCurrentVisitCount);

		return String.format(
				"%s\n\t%s\n\t%s\n\t%s\n\t%s\n----------------------------------------", intro,
				mean, max, c_mean, c_max);
	}

	public double getAlphaSurvivalScore() {
		return (UCTSelection.alpha_ps * getMaxSurvivalRate() + (1. - UCTSelection.alpha_ps)
				* getCurrentMaxSurvivals());
	}

	public double getAlphaPillScore() {
		return UCTSelection.alpha_ps * (getMaxPillScore() * getMaxSurvivalRate())
				+ (1. - UCTSelection.alpha_ps)
				* (getCurrentMaxPillScore() * getCurrentMaxSurvivals());
	}

	public double getAlphaGhostScore() {
		return UCTSelection.alpha_g * (getMaxGhostScore()) + (1. - UCTSelection.alpha_g)
				* (getCurrentMaxGhostScore()) * getAlphaSurvivalScore();
	}

	public double getPillScore() {
		if (getVisitCount() > 0) {
			return meanScores[0] / getVisitCount();
		} else {
			return 0;
		}
	}

	public double getGhostScore() {
		if (getVisitCount() > 0) {
			return meanScores[1] / getVisitCount();
		} else {
			return 0;
		}
	}

	public double getSurvivalRate() {
		if (getVisitCount() > 0) {
			return meanScores[2] / getVisitCount();
		} else {
			return 0;
		}
	}

	public double getMaxPillScore() {
		if (getTotalMaxVisitCount() > 0) {
			return (maxScores[0] / getTotalMaxVisitCount());
		} else {
			return 0;
		}
	}

	public double getMaxGhostScore() {
		if (getTotalMaxVisitCount() > 0) {

			return (maxScores[1] / getTotalMaxVisitCount());
		} else {
			return 0;
		}
	}

	public double getMaxSurvivalRate() {
		if (getTotalMaxVisitCount() > 0) {
			return maxScores[2] / getTotalMaxVisitCount();
		} else {
			return 0;
		}
	}

	public double getCurrentMeanPillScore() {
		if (currentVisitCount > 0) {
			return currentScores[0] / currentVisitCount;
		} else {
			return 0.;
		}
	}

	public double getCurrentMeanGhostScore() {
		if (currentVisitCount > 0) {
			return currentScores[1] / currentVisitCount;
		} else {
			return 0.;
		}
	}

	public double getCurrentMeanSurvivals() {
		if (currentVisitCount > 0) {
			return currentScores[2] / currentVisitCount;
		} else {
			return 0.;
		}
	}

	public double getCurrentMaxPillScore() {
		if (maxCurrentVisitCount > 0) {
			return maxCurrentScores[0] / maxCurrentVisitCount;
		} else {
			return 0.;
		}
	}

	public double getCurrentMaxGhostScore() {
		if (maxCurrentVisitCount > 0) {
			return maxCurrentScores[1] / maxCurrentVisitCount;
		} else {
			return 0.;
		}
	}

	public double getCurrentMaxSurvivals() {
		if (maxCurrentVisitCount > 0) {
			return maxCurrentScores[2] / maxCurrentVisitCount;
		} else {
			return 0.;
		}
	}

	// These are helper functions to get info from the reward vectors by index
	private double getCurrentMeanValue(int i) {
		if (currentVisitCount > 0) {
			return currentScores[i] / currentVisitCount;
		} else {
			return 0.;
		}
	}

	private double getCurrentMaxValue(int i) {
		if (maxCurrentVisitCount > 0) {
			return maxCurrentScores[i] / maxCurrentVisitCount;
		} else {
			return 0.;
		}
	}

	private double getMaxValue(int i) {
		if (maxTotalVisitCount > 0) {
			return maxScores[i] / maxTotalVisitCount;
		} else {
			return 0.;
		}
	}

	private double getMeanValue(int i) {
		if (totalVisitCount > 0) {
			return meanScores[i] / totalVisitCount;
		} else {
			return 0.;
		}
	}
}
