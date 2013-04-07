package pacman.entries.pacman.unimaas.framework;

import java.util.ArrayList;

import pacman.game.Constants;
import pacman.game.Constants.GHOST;
import pacman.game.Constants.MOVE;
import pacman.game.Game;

/**
 * 
 * @author Tom Pepels, Maastricht University
 */
public class DiscreteGame {
	public static Edge[][][] mazeGraphs;
	public static Edge[][] currentEdges;
	public static int[] initialEdges;

	/**
	 * Creates a graph from the current maze
	 * 
	 * @param gameState The game state of the current game
	 * @return The current maze graph
	 */
	private static Edge[][] createGraph(Game gameState) {
		int[] junctionIndices = gameState.getJunctionIndices();
		int highestJunction = junctionIndices[junctionIndices.length - 1];
		int[] junctions = new int[highestJunction + 1];
		ArrayList<Edge> allEdges = new ArrayList<Edge>(400);
		Edge[][] jn = new Edge[junctions.length][4];

		int l = 0; // Index for junctionIndices array.
		int id = 0, ppIndex = 0;
		boolean pp = false, initialEdge = false;
		for (int i = 0; i < junctions.length; i++) {
			// If the current index is a junction-index
			if (i == junctionIndices[l]) {
				for (MOVE j : MOVE.values()) {
					pp = false;
					initialEdge = false;
					ppIndex = 0;
					if (j == MOVE.NEUTRAL) {
						continue;
					}
					if (jn[i][j.ordinal()] != null) {
						continue;
					}
					int n = gameState.getNeighbour(i, j);

					MOVE lastDir = j;
					int length = 0, pills = 0;

					while (n != -1 && !gameState.isJunction(n)) {
						if (gameState.getGhostInitialNodeIndex() == n) {
							initialEdge = true;
						}
						//
						length++;
						if (gameState.getPillIndex(n) >= 0) {
							pills++;
						}
						if (gameState.getPowerPillIndex(n) >= 0) {
							pp = true;
							ppIndex = gameState.getPowerPillIndex(n);
						}
						//
						for (MOVE m : MOVE.values()) {
							if (m == lastDir.opposite() || m == MOVE.NEUTRAL) {
								continue;
							}

							int newN = gameState.getNeighbour(n, m);

							if (newN != -1) {
								n = newN;
								lastDir = m;
								break;
							}
						}
					}

					if (n != -1) {
						if (jn[i][j.ordinal()] == null
								&& jn[n][lastDir.opposite().ordinal()] == null) {
							int[] nodes = new int[] { i, n };
							Edge edge = new Edge(id, nodes, length, pills, pp);

							if (initialEdge)
								initialEdges[gameState.getMazeIndex()] = id;
							if (pp)
								edge.setPowerpillIndex(ppIndex);
							allEdges.add(edge);
							jn[i][j.ordinal()] = edge;
							jn[n][lastDir.opposite().ordinal()] = edge;
							id++;
						}
					}
				}
				l++;
			}
		}
		highestEdgeId[gameState.getMazeIndex()] = id;
		currentEdges[gameState.getMazeIndex()] = new Edge[id];
		currentEdges[gameState.getMazeIndex()] = allEdges.toArray(currentEdges[gameState.getMazeIndex()]);
		return jn;
	}

	/**
	 * Creates the current maze-graph if is does not exist
	 * 
	 * @param gameState
	 *            The game state of the current game
	 * @return the current maze graph
	 */
	private static Edge[][] setCurrentGraph(Game gameState) {
		int maze = gameState.getMazeIndex();
		if (mazeGraphs[maze] == null) {
			mazeGraphs[maze] = createGraph(gameState);
		}
		return mazeGraphs[maze];
	}

	//
	// The highest edge-id for the current maze
	private static int[] highestEdgeId;
	//
	private int pacHeading = -1, pacRear = -1, currentMaze = -1, lastPacManJunction = -1,
			visitedEdgeCount = 0;
	private Edge currentPacManEdge;
	private MOVE lastPacManMove;
	private boolean[] pacmanEdgesVisited;
	private int timeOnCurrentEdge = 0;
	private int[] ghostJunctions, ghostEdges, ghostHeadings, pillsEatenEdge;
	private MOVE[] ghostMoves;

	// This is true if the pacman has reversed course on her last path
	private boolean pacManReversed = false;

	private boolean pacManReversalStatus = false; // False if pacman did not
													// reverse, true if she did

	static {
		mazeGraphs = new Edge[Constants.NUM_MAZES][][];
		currentEdges = new Edge[Constants.NUM_MAZES][];
		highestEdgeId = new int[Constants.NUM_MAZES];
		initialEdges = new int[Constants.NUM_MAZES];
	}

	/**
	 * Empty constructor for the copy() method
	 */
	private DiscreteGame() {
		ghostJunctions = new int[] { -1, -1, -1, -1 };
		ghostMoves = new MOVE[Constants.NUM_GHOSTS];
		ghostEdges = new int[] { -1, -1, -1, -1 };
		ghostHeadings = new int[] { -1, -1, -1, -1 };
	}

	/**
	 * Creates a discrete game state for the current game state
	 * 
	 * @param gameState
	 *            The current game state
	 */
	public DiscreteGame(Game gameState) {
		setCurrentMaze(gameState);
	}

	/**
	 * Copies the discrete game state to a new object
	 * 
	 * @return
	 */
	public DiscreteGame copy() {

		DiscreteGame newGame = new DiscreteGame();

		// Copy the visited edges array
		int n = pacmanEdgesVisited.length;
		newGame.pacmanEdgesVisited = new boolean[n];
		newGame.pillsEatenEdge = new int[n];
		System.arraycopy(this.pacmanEdgesVisited, 0, newGame.pacmanEdgesVisited, 0, n);
		System.arraycopy(this.pillsEatenEdge, 0, newGame.pillsEatenEdge, 0, n);

		// Copy the lists of ghost moves and visited junctions
		n = Constants.NUM_GHOSTS;
		System.arraycopy(this.ghostJunctions, 0, newGame.ghostJunctions, 0, n);
		System.arraycopy(this.ghostMoves, 0, newGame.ghostMoves, 0, n);
		System.arraycopy(this.ghostEdges, 0, newGame.ghostEdges, 0, n);
		System.arraycopy(this.ghostHeadings, 0, newGame.ghostHeadings, 0, n);
		// Copy ALL THE VARIABLES
		newGame.timeOnCurrentEdge = this.timeOnCurrentEdge;
		newGame.lastPacManJunction = this.lastPacManJunction;
		newGame.lastPacManMove = this.lastPacManMove;
		newGame.pacHeading = this.pacHeading;
		newGame.pacRear = this.pacRear;
		newGame.currentPacManEdge = this.currentPacManEdge;
		newGame.currentMaze = this.currentMaze;
		newGame.pacManReversed = this.pacManReversed;
		newGame.visitedEdgeCount = this.visitedEdgeCount;
		newGame.pacManReversalStatus = this.pacManReversalStatus;
		//
		return newGame;
	}

	public void eatPill() {
		try {
		if (currentPacManEdge != null) {
			if (!pacmanEdgesVisited[currentPacManEdge.uniqueId]) {
				int maxPills = currentEdges[currentMaze][currentPacManEdge.uniqueId].pillCount;

				pillsEatenEdge[currentPacManEdge.uniqueId]++;

				if (pillsEatenEdge[currentPacManEdge.uniqueId] == maxPills) {
					setEdgeVisited(currentPacManEdge.uniqueId);
				}
				if (pillsEatenEdge[currentPacManEdge.uniqueId] > maxPills) {
					setEdgeVisited(currentPacManEdge.uniqueId);
				}
			}
		}
		} catch (Exception ex) {
			
		}
	}

	public void eatPill(int number) {
		if (currentPacManEdge != null) {
			if (!pacmanEdgesVisited[currentPacManEdge.uniqueId]) {
				int maxPills = currentEdges[currentMaze][currentPacManEdge.uniqueId].pillCount;

				pillsEatenEdge[currentPacManEdge.uniqueId] += number;

				if (pillsEatenEdge[currentPacManEdge.uniqueId] >= maxPills) {
					setEdgeVisited(currentPacManEdge.uniqueId);
				}
			}
		}
	}

	public Edge getCurrentPacmanEdge() {
		return currentPacManEdge;
	}

	public int getCurrentPacmanEdgeId() {
		if (currentPacManEdge != null)
			return currentPacManEdge.uniqueId;
		else
			return -1;
	}

	public Edge[] getEdgeList() {
		return currentEdges[currentMaze];
	}

	public int getGhostEdgeId(int ghost) {
		return ghostEdges[ghost];
	}

	public int getGhostHeading(int ghost) {
		return ghostHeadings[ghost];
	}

	/**
	 * Gets the current maze graph
	 * 
	 * @return the current maze graph
	 */
	public Edge[][] getGraph() {
		return mazeGraphs[currentMaze];
	}

	public int getLastGhostJunction(int ghost) {
		return ghostJunctions[ghost];
	}

	public MOVE getLastGhostMove(int ghost) {
		return ghostMoves[ghost];
	}

	/**
	 * @return the lastPacManJunction
	 */
	public int getLastPacManJunction() {
		return lastPacManJunction;
	}

	/**
	 * @return the pacHeading
	 */
	public int getPacHeading() {
		return pacHeading;
	}

	public boolean getPacmanEdgeVisited(int edgeId) {
		return pacmanEdgesVisited[edgeId] || (currentEdges[currentMaze][edgeId].pillCount == 0);
	}

	/**
	 * @return the pacRear
	 */
	public int getPacRear() {
		return pacRear;
	}

	public int getVisitedEdgeCount() {
		return visitedEdgeCount;
	}

	public void ghostEaten(int ghost) {
		// System.out.println("Ghost " + ghost + " was eaten");
		ghostMoves[ghost] = MOVE.NEUTRAL;
		ghostJunctions[ghost] = -1;
		ghostEdges[ghost] = -1;
		ghostHeadings[ghost] = -1;
	}

	public void increaseTimeCurrentEdge() {
		// If pacman reversed, increase the distance traveled,
		// otherwise decrease (going backwards)
		if (!pacManReversalStatus)
			this.timeOnCurrentEdge++;
		else
			this.timeOnCurrentEdge--;
	}

	/**
	 * @return the pacManReversed
	 */
	public boolean isPacmanReversed() {
		return pacManReversed;
	}

	public void pacmanDied() {
		lastPacManJunction = -1;
		lastPacManMove = MOVE.NEUTRAL;
		pacHeading = -1;
		pacRear = -1;
		currentPacManEdge = null;
		pacManReversed = false;
		// Reset the ghost status
		ghostJunctions = new int[] { -1, -1, -1, -1 };
		ghostMoves = new MOVE[Constants.NUM_GHOSTS];
		ghostEdges = new int[] { -1, -1, -1, -1 };
		ghostHeadings = new int[] { -1, -1, -1, -1 };
	}

	public int pacManDistanceToHeading() {
		if (!pacManReversalStatus && currentPacManEdge != null)
			return currentPacManEdge.length - timeOnCurrentEdge;
		else
			return timeOnCurrentEdge;
	}

	public int pacManDistanceToRear() {
		if (pacManReversalStatus)
			return currentPacManEdge.length - timeOnCurrentEdge;
		else
			return timeOnCurrentEdge;
	}

	// public void setPacmanEdgeVisited(int edgeId) {
	// if (!pacmanEdgesVisited[edgeId]) {
	// visitedEdgeCount++;
	// }
	// pacmanEdgesVisited[edgeId] = true;
	// }

	/**
	 * Registers a move made by pacman at a junction
	 * 
	 * @param junction
	 *            The location of the current junction
	 * @param move
	 *            The move pacman made at the current junction
	 */
	public void pacMove(int junction, MOVE move) {
		// Reset the pacman"s reversed status
		this.pacManReversalStatus = false;
		this.timeOnCurrentEdge = 0;
		this.pacManReversed = false;
		this.lastPacManJunction = junction;
		this.lastPacManMove = move;
		// Set the edge to visited
		try {
			Edge edge = getGraph()[junction][move.ordinal()];
			currentPacManEdge = edge;
			pacHeading = edge.nodes[1];
			//
			if (pacHeading == junction)
				pacHeading = edge.nodes[0];
			//
			pacRear = junction;
		} catch (Exception ex) {
			System.err.println("Pacmove nullpointer");
		}
	}

	public void reverseGhosts() {
		for (GHOST g : GHOST.values()) {
			if (ghostJunctions[g.ordinal()] > -1) {
				int temp = ghostHeadings[g.ordinal()];
				ghostHeadings[g.ordinal()] = ghostJunctions[g.ordinal()];
				ghostJunctions[g.ordinal()] = temp;
			}
		}
	}

	/**
	 * Call this method when pacman reverses on a path (not at a junction)
	 */
	public void reversePacMan() {
		int temp = pacHeading;
		pacHeading = pacRear;
		pacRear = temp;
		pacManReversed = true;
		pacManReversalStatus = !pacManReversalStatus;
	}

	/**
	 * Call this method when pacman enters a new maze
	 * 
	 * @param gameState
	 *            The current gamestate
	 */
	public void setCurrentMaze(Game gameState) {
		int newMaze = gameState.getMazeIndex();

		if (newMaze != currentMaze) {
			currentMaze = newMaze;
			// Make sure the new maze graph exists
			setCurrentGraph(gameState);
			// Reset the game"s history
			pacmanEdgesVisited = new boolean[highestEdgeId[currentMaze]];
			pillsEatenEdge = new int[highestEdgeId[currentMaze]];
			ghostJunctions = new int[] { -1, -1, -1, -1 };
			ghostMoves = new MOVE[Constants.NUM_GHOSTS];
			ghostEdges = new int[] { initialEdges[newMaze], initialEdges[newMaze],
					initialEdges[newMaze], initialEdges[newMaze] };
			ghostHeadings = new int[] { -1, -1, -1, -1 };
			//
			timeOnCurrentEdge = 0;
			lastPacManJunction = -1;
			lastPacManMove = MOVE.NEUTRAL;
			//
			currentPacManEdge = null;
			pacHeading = -1;
			pacRear = -1;
			//
			visitedEdgeCount = 0;
			pacManReversed = false;
			pacManReversalStatus = false;
		}
	}

	public int getHighestEdgeId() {
		return highestEdgeId[currentMaze];
	}

	public void setEdgeVisited(int edgeId) {
		pacmanEdgesVisited[edgeId] = true;
		visitedEdgeCount++;
	}

	public void setGhostEdgeToInitial(int ghost, MOVE move) {
		ghostMoves[ghost] = move;
		Edge edge = DiscreteGame.currentEdges[currentMaze][initialEdges[currentMaze]];
		ghostEdges[ghost] = edge.uniqueId;
		if (move == MOVE.LEFT) {
			ghostJunctions[ghost] = edge.nodes[1];
			ghostHeadings[ghost] = edge.nodes[0];
		} else {
			ghostJunctions[ghost] = edge.nodes[0];
			ghostHeadings[ghost] = edge.nodes[1];
		}
	}

	public void setGhostMove(int ghost, int junction, MOVE move) {
		if (move == MOVE.NEUTRAL)
			return;
		ghostMoves[ghost] = move;
		ghostJunctions[ghost] = junction;
		//
//		try {
			Edge edge = getGraph()[junction][move.ordinal()];
			ghostEdges[ghost] = edge.uniqueId;
			//
			ghostHeadings[ghost] = edge.nodes[1];
			if (ghostHeadings[ghost] == junction) {
				ghostHeadings[ghost] = edge.nodes[0];
			}
//		} catch (Exception ex) {
//			System.err.println(String.format("GhostMove nullpointer, j: %d, m: %d, g: %d",
//					junction, move.ordinal(), ghost));
//		}
	}

	public int[] getPillsEatenEdge() {
		return pillsEatenEdge;
	}
}
