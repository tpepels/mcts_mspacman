package pacman.entries.pacman.unimaas.ghosts;

import java.util.EnumMap;

import pacman.entries.pacman.unimaas.framework.DiscreteGame;
import pacman.entries.pacman.unimaas.framework.Edge;
import pacman.entries.pacman.unimaas.framework.GhostMoveGenerator;
import pacman.entries.pacman.unimaas.framework.XSRandom;
import pacman.game.Constants.DM;
import pacman.game.Constants.GHOST;
import pacman.game.Constants.MOVE;
import pacman.game.Constants;
import pacman.game.Game;

/**
 * 
 * @author Tom Pepels, Maastricht University
 */
public class PinchGhostMover implements GhostMoveGenerator {

	// The per-ghost probabilities of making a greedy move.
	public static double epsilon = .8;
	public static int pacDistT = 6;
	private DiscreteGame dGame;
	private int trailGhost = -1, current;
	private Edge[][] graph;
	private Game gameState;
	public boolean debug = false;
	private boolean[] targetVector = { false, false, false, false };
	//
	private EnumMap<GHOST, MOVE> LGRMoves = new EnumMap<GHOST, MOVE>(GHOST.class);
	private EnumMap<GHOST, MOVE> ghostMoves = new EnumMap<GHOST, MOVE>(GHOST.class);
	//
	private MOVE[] dirs;
	private MOVE lastMove;
	private int pacLoc, pacRear, pacFront, pacDistance, pacFrontDist;

	int pacEdge, ghostJ, ghostEdge, ghostHeading, moveO;

	boolean frontBlocked = false, rearBlocked = false;

	public PinchGhostMover(Game gameState, DiscreteGame dGame) {
		//
		this.gameState = gameState;
		this.dGame = dGame;
		this.graph = dGame.getGraph();
	}

	@Override
	public EnumMap<GHOST, MOVE> generateGhostMoves() {
		ghostMoves.clear();
		LGRMoves.clear();

		pacFront = dGame.getPacHeading();
		pacRear = dGame.getPacRear();
		pacLoc = gameState.getPacmanCurrentNodeIndex();
		// If pacman reversed, don"t switch the headings of the ghosts around
		if (dGame.isPacmanReversed()) {
			pacFront = pacRear;
			pacRear = dGame.getPacHeading();
		}
		boolean actionRequired = false;
		for (GHOST g : GHOST.values()) {
			current = gameState.getGhostCurrentNodeIndex(g);
			dirs = gameState.getPossibleMoves(current, gameState.getGhostLastMoveMade(g));
			if (dirs.length > 1) {
				actionRequired = true;
				break;
			}
		}

		if (!actionRequired) {
			return ghostMoves;
		}

		if (pacRear < 1 || pacFront < 1) {
			// The rear and front of pacman are not yet known, make random moves.
			for (GHOST g : GHOST.values()) {
				if (gameState.doesGhostRequireAction(g)) {
					try {
						ghostMoves.put(g, getRandomMoveToTarget(g, pacLoc));
					} catch (Exception ex) {
						// Fails sometimes :(
					}
				}
			}
			// Return the random moves.
			return ghostMoves;
		}
		//
		if (dGame.getCurrentPacmanEdge() != null) {
			pacFrontDist = dGame.pacManDistanceToHeading() + 1;
		} else {
			pacFrontDist = 0;
		}
		//
		for (GHOST g : GHOST.values()) {
			current = gameState.getGhostCurrentNodeIndex(g);
			lastMove = gameState.getGhostLastMoveMade(g);
			dirs = gameState.getPossibleMoves(current, lastMove);
			//
			if (dirs.length > 1) {
				boolean pacClose = false;
				MOVE move = MOVE.NEUTRAL;
				// Check if this ghost can make a killing move
				if (!gameState.isGhostEdible(g) && gameState.isJunction(current)) {
					//
					for (int j = 0; j < dirs.length; j++) {
						// This move makes it impossible for Pacman to choose a new direction
						if (pacManTrapped(g, current, dirs[j])) {
							// System.out.println(g + " " + dirs[j] + " TRAP");
							move = dirs[j];
							pacClose = true;
							break;
						}
					}
					pacDistance = gameState.getShortestPathDistance(current, pacLoc, lastMove)
							- Constants.EAT_DISTANCE;
					// Pac-Man is very close to the ghost, move to pacs direction
					if (pacDistance <= pacDistT && !pacClose) {
						move = gameState.getApproximateNextMoveTowardsTarget(current, pacLoc,
								lastMove, DM.PATH);
						// System.out.println(g + " " + move + " PAC CLOSE");
						if (!isDoubleMove(g, move)) {
							pacClose = true;
						}
					}
					//
					if (!pacClose && g.ordinal() == trailGhost || targetVector[g.ordinal()]) {
						// Ghost is at the junction at the rear of pacman
						if (current == pacRear) {
							// Move in the direction of the edge that pacman is on
							for (int j = 0; j < dirs.length; j++) {
								if (dGame.getCurrentPacmanEdgeId() == graph[current][dirs[j]
										.ordinal()].uniqueId) {
									// System.out.println(g + " " + dirs[j] + " AT PAC REAR");
									move = dirs[j];
									// If another ghost is already on the path, ignore and continue
									if (!isDoubleMove(g, move)) {
										pacClose = true;
									}
									break;
								}
							}
						}
					} else if (current == pacFront) {
						for (int j = 0; j < dirs.length; j++) {
							// Junction in front of pacman, try to block it off
							if (dGame.getCurrentPacmanEdgeId() == graph[current][dirs[j].ordinal()].uniqueId) {
								// System.out.println(g + " " + dirs[j] + " AT PAC FRONT");
								move = dirs[j];
								if (!isDoubleMove(g, move)) {
									pacClose = true;
								}
								break;
							}
						}
					}
				}
				//
				if (!pacClose && XSRandom.r.nextDouble() < epsilon) {
					if (gameState.isGhostEdible(g)) {
						// Edible => run away!.
						move = gameState.getApproximateNextMoveAwayFromTarget(current, pacLoc,
								lastMove, DM.PATH);
						move = getNonDoubleMove(g, move);
						// System.out.println(g + " " + move + " EDIBLE");
					} else {
						// Try to corner the pacman by moving to its front or rear
						if (g.ordinal() == trailGhost || targetVector[g.ordinal()]) {
							// Get closer to the rear position of pacman
							move = gameState.getApproximateNextMoveTowardsTarget(current, pacRear,
									lastMove, DM.PATH);
						} else {
							if (gameState.isJunction(current)) {
								// Try to block off the front of pacman
								for (int j = 0; j < dirs.length; j++) {
									Edge e = graph[current][dirs[j].ordinal()];
									if (e.nodes[0] == pacFront || e.nodes[1] == pacFront) {
										move = dirs[j];
										if (!isDoubleMove(g, move)) {
											// System.out.println(g + " " + move + " BLOCKING FRONT");
											pacClose = true;
										}
										break;
									}
								}
							}

							if (!pacClose) {
								// Get closer to the front of pacman
								if (gameState.getShortestPathDistance(current, pacFront, lastMove)
										- Constants.EAT_DISTANCE <= pacFrontDist) {
									//
									move = gameState.getApproximateNextMoveTowardsTarget(current,
											pacFront, lastMove, DM.PATH);
									// System.out.println(g + " " + move + " CLOSE TO FRONT");
								} else {
									move = gameState.getApproximateNextMoveTowardsTarget(current,
											pacFront, lastMove, DM.PATH);
									//
									if (isDoubleMove(g, move)) {
										move = getRandomMoveToTarget(g, pacFront);
									}
									// System.out.println(g + " " + move + " TO FRONT SOMEWHERE");
								}
							}
						}
					}

				} else if (!pacClose) {
					if (g.ordinal() == trailGhost || targetVector[g.ordinal()]) {
						move = getRandomMoveToTarget(g, pacRear);
					} else {
						move = getRandomMoveToTarget(g, pacFront);
					}
					move = getNonDoubleMove(g, move);
				}
				//
				ghostMoves.put(g, move);
			}
		}
		//
		return ghostMoves;
	}

	private boolean isDoubleMove(GHOST g, MOVE move) {
		//
		for (GHOST f : GHOST.values()) {
			if (g.equals(f)) {
				continue;
			}
			if (gameState.isGhostEdible(f) == gameState.isGhostEdible(g)) {
				if (current == gameState.getGhostCurrentNodeIndex(f)) {
					if (ghostMoves.get(f) != null && ghostMoves.get(f) == move)
						return true;
				} else if (current == dGame.getLastGhostJunction(f.ordinal())) {
					if (move == dGame.getLastGhostMove(f.ordinal()))
						return true;
				}
			}
		}
		//
		return false;
	}

	private MOVE getNonDoubleMove(GHOST g, MOVE move) {
		//
		for (GHOST f : GHOST.values()) {
			if (g.equals(f)) {
				continue;
			}
			if (gameState.isGhostEdible(f) == gameState.isGhostEdible(g)) {
				if (current == gameState.getGhostCurrentNodeIndex(f)) {
					if (ghostMoves.get(f) != null) {
						while (ghostMoves.get(f) == move) {
							move = dirs[XSRandom.r.nextInt(dirs.length)];
						}
						break;
					}
				} else if (current == dGame.getLastGhostJunction(f.ordinal())) {
					while (move == dGame.getLastGhostMove(f.ordinal())) {
						move = dirs[XSRandom.r.nextInt(dirs.length)];
					}
					break;
				}
			}
		}
		//
		return move;
	}

	private MOVE getRandomMoveToTarget(GHOST g, int location) {
		boolean edible = gameState.isGhostEdible(g);
		MOVE forbiddenMove = MOVE.NEUTRAL;
		if (edible) {
			forbiddenMove = gameState.getApproximateNextMoveTowardsTarget(current, location,
					lastMove, DM.PATH);
		} else {
			forbiddenMove = gameState.getApproximateNextMoveAwayFromTarget(current, location,
					lastMove, DM.PATH);
		}
		MOVE move = dirs[XSRandom.r.nextInt(dirs.length)];
		while (move.equals(forbiddenMove)) {
			move = dirs[XSRandom.r.nextInt(dirs.length)];
		}
		return move;
	}

	Edge[] nextEdges;
	int[] nextNodes = new int[3];
	int nextNodeCount = 0;

	// private int[] getNextNodes(int location, int currentEdge) {
	// nextEdges = dGame.getGraph()[location];
	// nextNodeCount = 0;
	// //
	// for (Edge edge : nextEdges) {
	// if (edge == null)
	// continue;
	// // PacMan is currently on this edge.
	// if (edge.uniqueId == currentEdge)
	// continue;
	// // Get the end of the next edge
	// if (edge.nodes[0] == location)
	// nextNodes[nextNodeCount] = edge.nodes[1];
	// else
	// nextNodes[nextNodeCount] = edge.nodes[0];
	// //
	// nextNodeCount++;
	// }
	// //
	// if (nextNodeCount < 3) {
	// int[] nodes = new int[nextNodeCount];
	// System.arraycopy(nextNodes, 0, nodes, 0, nextNodeCount);
	// return nodes;
	// } else {
	// return nextNodes;
	// }
	// }
	//
	// private int getRandomNextNode(int location, int currentEdge) {
	// nextEdges = dGame.getGraph()[location];
	// nextNodeCount = 0;
	// //
	// for (Edge edge : nextEdges) {
	// if (edge == null)
	// continue;
	// // PacMan is currently on this edge.
	// if (edge.uniqueId == currentEdge)
	// continue;
	// // Get the end of the next edge
	// if (edge.nodes[0] == location)
	// nextNodes[nextNodeCount] = edge.nodes[1];
	// else
	// nextNodes[nextNodeCount] = edge.nodes[0];
	// //
	// nextNodeCount++;
	// }
	// return nextNodes[XSRandom.r.nextInt(nextNodeCount)];
	// }

	private boolean pacManTrapped(GHOST ghost, int ghostJunction, MOVE ghostMove) {
		frontBlocked = false;
		rearBlocked = false;

		if (dGame.getCurrentPacmanEdge() == null) {
			return false;
		}
		pacEdge = dGame.getCurrentPacmanEdgeId();

		for (GHOST g : GHOST.values()) {
			int i = g.ordinal();
			if (!gameState.isGhostEdible(g) && gameState.getGhostLairTime(g) == 0) {
				//
				if (g.equals(ghost)) {
					ghostJ = ghostJunction;
					moveO = ghostMove.ordinal();
					ghostEdge = dGame.getGraph()[ghostJ][moveO].uniqueId;

					ghostHeading = graph[ghostJ][moveO].nodes[1];
					if (ghostHeading == ghostJ) {
						ghostHeading = graph[ghostJ][moveO].nodes[0];
					}
				} else if (gameState.isJunction(gameState.getGhostCurrentNodeIndex(g)) && ghostMoves.get(g) != null) {
					ghostJ =gameState.getGhostCurrentNodeIndex(g);
					moveO = ghostMoves.get(g).ordinal();
					ghostEdge = dGame.getGraph()[ghostJ][moveO].uniqueId;
					
					ghostHeading = graph[ghostJ][moveO].nodes[1];
					if (ghostHeading == ghostJ) {
						ghostHeading = graph[ghostJ][moveO].nodes[0];
					}
				} else {
					ghostJ = dGame.getLastGhostJunction(i);
					ghostEdge = dGame.getGhostEdgeId(i);
					ghostHeading = dGame.getGhostHeading(i);
				}

				if (ghostJ > 0) {
					if (ghostEdge == pacEdge) {
						// The ghost is on the same edge as pacman
						if (ghostHeading == pacFront) {
							frontBlocked = true;
							if (frontBlocked && rearBlocked) {
								return true;
							}
						} else if (ghostHeading == pacRear) {

							rearBlocked = true;
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

	/*
	 * Boolean vector to determine which ghost chases
	 */
	public void setTargetVector(boolean[] targetVector) {
		this.targetVector = targetVector;
	}

	public void setTrailGhost(int ghost) {
		this.trailGhost = ghost;
	}
}
