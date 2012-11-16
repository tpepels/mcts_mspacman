package pacman.entries.pacman.unimaas.framework;

import java.text.DecimalFormat;

import pacman.entries.pacman.unimaas.selection.UCTSelection;
import pacman.game.Constants.MOVE;
import pacman.game.Game;

/**
 * 
 * @author Tom Pepels, Maastricht University
 */
public abstract class MCTNode {
	// Gamestates
	protected Game gameState;
	private DiscreteGame dGame;
	// Move information
	public MOVE pathDirection;
	public Edge edge;
	//
	public int depth = 0, pathLength, junctionIndex;
	// Scores
	public double maxPillScore, maxGhostScore, maxSurvivals, maxVisitCount, maxTurnVisits;
	public double visitCount, turnVisitCount, pillScore, ghostScore, survivals;
	private double currentSurvivals = 0, maxCurrentSurvivals = 0;

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
	}

	/**
	 * Constructor for MCT node
	 * 
	 * @param gameState The gamestate of the node
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
		maxPillScore *= discount;
		maxGhostScore *= discount;
		maxSurvivals *= discount;
		maxVisitCount *= discount;
		pillScore *= discount;
		ghostScore *= discount;
		survivals *= discount;
		visitCount *= discount;
		//
		turnVisitCount = 0;
		maxTurnVisits = 0;
		currentSurvivals = 0;
		//
		if (children != null) {
			for (MCTNode c : children) {
				c.discountValues(discount);
			}
		}
	}

	public void addValue(MCTResult result) {
		pillScore += result.pillScore;
		ghostScore += result.ghostScore;
		//
		if (result.target) {
			survivals++;
			currentSurvivals++;
		}
		// Leafnodes have maxvalue = value
		if (isLeaf()) {
			maxPillScore = pillScore;
			maxGhostScore = ghostScore;
			maxSurvivals = survivals;
			//
			maxVisitCount = visitCount;
			maxTurnVisits = turnVisitCount;
			maxCurrentSurvivals = currentSurvivals;
		}
	}

	/**
	 * Adds a visit to the visitcounter
	 */
	public void addVisit() {
		// This is the number of visits for the current turn.
		turnVisitCount++;
		visitCount++;
	}

	public void backPropagate(MCTResult result, SelectionType selectionType) {
		MCTNode node = this;
		node.addValue(result);
		node = node.getParentNode();

		while (node != null) {
			// Update the node's values (Average Back-propagation)
			node.addValue(result);

			// Maximum back-propagate the values of the child with the highest
			// score
			MCTNode topChild = null;
			double topRate = Double.NEGATIVE_INFINITY;

			for (MCTNode child : node.getChildren()) {
				// Do not use values of pruned children
				if (selectionType == SelectionType.TargetRate) {
					if (child.getMaxSurvivalRate() > topRate) {
						topChild = child;
						topRate = topChild.getMaxSurvivalRate();
					}
				} else if (selectionType == SelectionType.PillScore) {
					if (child.getMaxPillScore() * child.getMaxSurvivalRate() > topRate) {
						topChild = child;
						topRate = child.getMaxPillScore() * child.getMaxSurvivalRate();
					}
				} else if (selectionType == SelectionType.GhostScore) {
					if (child.getMaxGhostScore() * child.getMaxSurvivalRate() > topRate) {
						topChild = child;
						topRate = child.getMaxGhostScore() * child.getMaxSurvivalRate();
					}
				}
			}
			//
			node.maxSurvivals = topChild.maxSurvivals;
			node.maxPillScore = topChild.maxPillScore;
			node.maxGhostScore = topChild.maxGhostScore;
			//
			node.maxVisitCount = topChild.maxVisitCount;
			node.maxTurnVisits = topChild.maxTurnVisits;
			node.maxCurrentSurvivals = topChild.maxCurrentSurvivals;
			//
			node = node.getParentNode();
		}
	}

	public boolean canExpand(int maxPathLength) {
		if (!isRoot()) {
			if (getPathLength() <= maxPathLength) {
				// Don't expand nodes that have not been simulated
				if (turnVisitCount < UCTSelection.minVisits) {
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
	 * the number of possible childnodes (if not initialized)
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

	public double getGhostScore() {
		if (getVisitCount() > 0) {
			return ghostScore / getVisitCount();
		} else {
			return 0;
		}
	}

	/**
	 * @return the junctionIndex
	 */
	public int getJunctionIndex() {
		return junctionIndex;
	}

	public double getMaxGhostScore() {
		if (getTotalMaxVisitCount() > 0) {

			return (maxGhostScore / getTotalMaxVisitCount());
		} else {
			return 0;
		}
	}

	public double getMaxPillScore() {
		if (getTotalMaxVisitCount() > 0) {
			return (maxPillScore / getTotalMaxVisitCount());
		} else {
			return 0;
		}
	}

	public double getMaxSurvivalRate() {
		if (getTotalMaxVisitCount() > 0) {
			return maxSurvivals / getTotalMaxVisitCount();
		} else {
			return 0;
		}
	}

	public double getMaxVisitCount() {
		return maxVisitCount;
	}

	/**
	 * Returns the parent node for this node
	 * 
	 * @return The MCTNode that is the parent of this node
	 */
	public MCTNode getParentNode() {
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

	public double getPillScore() {
		if (getVisitCount() > 0) {
			return pillScore / getVisitCount();
		} else {
			return 0;
		}
	}

	public double getSurvivalRate() {
		if (getVisitCount() > 0) {
			return survivals / getVisitCount();
		} else {
			return 0;
		}
	}

	public double getTotalMaxVisitCount() {
		return maxVisitCount;
	}

	public double getVisitCount() {
		return visitCount;
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

	public void propagateMaxValues(SelectionType selectionType) {
		if (!isLeaf()) {
			//
			for (MCTNode c : children) {
				if (c.isLeaf()) {
					continue;
				}
				c.propagateMaxValues(selectionType);
			}
			//
			MCTNode topChild = null;
			double topScore = Double.NEGATIVE_INFINITY;
			//
			for (MCTNode child : children) {
				// Do not use values of unvisited children
				if (child.turnVisitCount < UCTSelection.minVisits) {
					continue;
				}
				if (selectionType == SelectionType.TargetRate) {
					if (child.getMaxSurvivalRate() > topScore) {
						topChild = child;
						topScore = topChild.getMaxSurvivalRate();
					}
				} else if (selectionType == SelectionType.PillScore) {
					if (child.getMaxPillScore() * child.getMaxSurvivalRate() > topScore) {
						topChild = child;
						topScore = child.getMaxPillScore() * child.getMaxSurvivalRate();
					}
				} else if (selectionType == SelectionType.GhostScore) {
					if (child.getMaxGhostScore() * child.getMaxSurvivalRate() > topScore) {
						topChild = child;
						topScore = child.getMaxGhostScore() * child.getMaxSurvivalRate();
					}
				}
			}
			if (topChild == null) {
				maxSurvivals = survivals;
				maxPillScore = pillScore;
				maxGhostScore = ghostScore;
				//
				maxVisitCount = visitCount;
				maxCurrentSurvivals = currentSurvivals;
				maxTurnVisits = turnVisitCount;
				return;
			}
			//
			maxSurvivals = topChild.maxSurvivals;
			maxPillScore = topChild.maxPillScore;
			maxGhostScore = topChild.maxGhostScore;
			//
			maxVisitCount = topChild.maxVisitCount;
			maxTurnVisits = topChild.maxTurnVisits;
			maxCurrentSurvivals = topChild.maxCurrentSurvivals;
		}
	}

	public void propagateMaxValues(SelectionType selectionType, double minRate) {
		if (!isLeaf()) {
			//
			for (MCTNode c : children) {
				if (c.isLeaf()) {
					continue;
				}
				c.propagateMaxValues(selectionType, minRate);
			}
			//
			MCTNode topChild = null;
			double topScore = Double.NEGATIVE_INFINITY;
			//
			for (MCTNode child : children) {
				// Do not use values of unvisited children
				if (child.turnVisitCount < UCTSelection.minVisits
						|| child.getMaxSurvivalRate() < minRate) {
					continue;
				}
				if (selectionType == SelectionType.TargetRate) {
					if (child.getMaxSurvivalRate() > topScore) {
						topChild = child;
						topScore = topChild.getMaxSurvivalRate();
					}
				} else if (selectionType == SelectionType.PillScore) {
					if (child.getMaxPillScore() * child.getMaxSurvivalRate() > topScore) {
						topChild = child;
						topScore = child.getMaxPillScore() * child.getMaxSurvivalRate();
					}
				} else if (selectionType == SelectionType.GhostScore) {
					if (child.getMaxGhostScore() * child.getMaxSurvivalRate() > topScore) {
						topChild = child;
						topScore = child.getMaxGhostScore() * child.getMaxSurvivalRate();
					}
				}
			}
			if (topChild == null) {
				maxSurvivals = survivals;
				maxPillScore = pillScore;
				maxGhostScore = ghostScore;
				//
				maxVisitCount = visitCount;
				maxCurrentSurvivals = currentSurvivals;
				maxTurnVisits = turnVisitCount;
				return;
			}
			//
			maxSurvivals = topChild.maxSurvivals;
			maxPillScore = topChild.maxPillScore;
			maxGhostScore = topChild.maxGhostScore;
			//
			maxVisitCount = topChild.maxVisitCount;
			maxCurrentSurvivals = topChild.maxCurrentSurvivals;
			maxTurnVisits = topChild.maxTurnVisits;
		}
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

	/**
	 * @param edgeId
	 *            the edgeId to set
	 */
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
		//
		maxVisitCount = node2.maxVisitCount;
		maxPillScore = node2.maxPillScore;
		maxSurvivals = node2.maxSurvivals;
		maxGhostScore = node2.maxGhostScore;
		//
		visitCount = node2.visitCount;
		pillScore = node2.pillScore;
		ghostScore = node2.ghostScore;
		survivals = node2.survivals;
	}

	public void substractStats(MCTNode node2) {
		// Substract the stats from the node.
		visitCount -= node2.visitCount;
		pillScore -= node2.pillScore;
		ghostScore -= node2.ghostScore;
		survivals -= node2.survivals;
	}

	public void addStats(MCTNode node2) {
		// Add the stats from the node.
		visitCount += node2.visitCount;
		pillScore += node2.pillScore;
		ghostScore += node2.ghostScore;
		survivals += node2.survivals;
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
	public MCTResult simulate(MCTSimulation simulation, int simCount, SelectionType selectionType,
			int target) {
		// Do a simulation starting at the root's game state
		selectedNode = this;
		pacLocations = new int[selectedNode.depth + 1];
		moves = new MOVE[selectedNode.depth];
		int i = 0;
		while (!selectedNode.isRoot()) {
			moves[i] = selectedNode.getPathDirection();
			pacLocations[i] = selectedNode.getJunctionIndex();
			i++;
			selectedNode = selectedNode.getParentNode();
		}
		if (selectedNode.junctionIndex > -1) {
			pacLocations[i] = selectedNode.getJunctionIndex();
		}
		// Get the root's game states
		Game interState = selectedNode.getGameState().copy();
		DiscreteGame disGame = selectedNode.getdGame().copy();
		// 
		return simulation
				.playout(disGame, interState, moves, pacLocations, simCount, selectionType);
	}

	@Override
	public String toString() {
		DecimalFormat df2 = new DecimalFormat("#,###,###,##0.0000");

		String string = "node| j:" + getJunctionIndex() + "\t | parent j: "
				+ parent.getJunctionIndex() + "\t | move: " + pathDirection
				+ "\t | [p,g,s,vt,vc,sc]: [" + df2.format(getPillScore()) + ", "
				+ df2.format(getGhostScore()) + ", " + df2.format(getSurvivalRate()) + ", "
				+ (int) getVisitCount() + ", " + turnVisitCount + ", "
				+ df2.format(currentSurvivals / turnVisitCount) + "]\t | MAX[p,g,s,vt,vc,sc]: ["
				+ df2.format(getMaxPillScore()) + ", " + df2.format(getMaxGhostScore()) + ", "
				+ df2.format(getMaxSurvivalRate()) + ", " + (int) getMaxVisitCount() + ", "
				+ maxTurnVisits + ", " + df2.format(maxCurrentSurvivals / maxTurnVisits) + "]"
				+ "\t | pathlength: " + getPathLength() + "\t | depth: " + getDepth();

		return string;
	}
}
