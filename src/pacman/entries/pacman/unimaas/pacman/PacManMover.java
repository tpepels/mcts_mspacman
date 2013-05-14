package pacman.entries.pacman.unimaas.pacman;

import pacman.entries.pacman.unimaas.framework.DiscreteGame;
import pacman.entries.pacman.unimaas.framework.Edge;
import pacman.entries.pacman.unimaas.framework.PacManMoveGenerator;
import pacman.entries.pacman.unimaas.framework.XSRandom;
import pacman.game.Constants;
import pacman.game.Constants.DM;
import pacman.game.Constants.GHOST;
import pacman.game.Constants.MOVE;
import pacman.game.Game;

public class PacManMover implements PacManMoveGenerator {
	//
	public static int ghostDistT = 6;
	public static double epsilon = .8;
	//
	private MOVE[] safeMoves, safeMoves2, moves, pacMoves, directionsRev;
	private Edge[] junction;
	private Edge[][] graph;
	private MOVE reverse;
	private DiscreteGame dGame;
	public Game gameState;
	private int pacLocation, curDist, nextDist, nextLoc;
	private int[] powerPills;
	public int safety, lastJSafety = 11;
	boolean reversedOnPath = false;

	private final int[] blues = new int[Constants.NUM_GHOSTS];

	int pacHeading, ghostJ, ghostHeading, pacDistance, ghostLoc, ghostDistance, distance,
			nextLocation, nextDistance, nextGhostLocation, nextGhostDist;

	MOVE ghostDir, nextMove, nextGhostMove;

	int nextGhostLoc, origDist, nextMoveDist;

	public PacManMover(Game gameState, DiscreteGame dGame) {
		this.dGame = dGame;
		this.gameState = gameState;
		this.graph = dGame.getGraph();
		//
	}

	private boolean angryGhostNear() {
		return angryGhostNear(
				gameState.getPossibleMoves(pacLocation, gameState.getPacmanLastMoveMade())[0],
				ghostDistT, false);
	}

	private boolean angryGhostNear(MOVE nextMove, int dist, boolean moveTwice) {
		for (GHOST g : GHOST.values()) {
			//
			ghostDir = gameState.getGhostLastMoveMade(g);
			ghostLoc = gameState.getGhostCurrentNodeIndex(g);
			//
			if (gameState.getGhostLairTime(g) == 0) {
				distance = gameState.getShortestPathDistance(ghostLoc, pacLocation, ghostDir);
				if ((gameState.getGhostEdibleTime(g) + Constants.EAT_DISTANCE) > distance)
					distance = gameState.getGhostEdibleTime(g);
			} else {
				ghostLoc = gameState.getGhostInitialNodeIndex();
				distance = gameState.getShortestPathDistance(gameState.getGhostInitialNodeIndex(),
						pacLocation, ghostDir) + (gameState.getGhostLairTime(g) - 1);
			}
			//
			if (distance <= dist) {
				if ((distance - Constants.EAT_DISTANCE) > dGame.pacManDistanceToHeading() + 1
						&& !moveTwice) {
					continue;
				}
				// The ghost is near the pacman
				nextLocation = gameState.getNeighbour(pacLocation, nextMove);
				if (moveTwice) {
					nextLocation = gameState.getNeighbour(nextLocation,
							gameState.getPossibleMoves(nextLocation, nextMove)[0]);
				}
				if (gameState.getGhostEdibleTime(g) > 0) {
					nextDistance = gameState.getShortestPathDistance(nextLocation, ghostLoc);
					if (gameState.getGhostEdibleTime(g) + 1 > nextDistance)
						nextDistance = gameState.getGhostEdibleTime(g);
				} else {
					nextDistance = gameState.getShortestPathDistance(nextLocation, ghostLoc);
				}

				//
				if (nextDistance < distance) {
					// Pac-Man is moving toward the ghost
					nextGhostMove = gameState.getPossibleMoves(ghostLoc, ghostDir)[0];
					nextGhostLocation = gameState.getNeighbour(ghostLoc, nextGhostMove);
					//
					if (gameState.getGhostLairTime(g) == 0) {
						nextGhostDist = gameState.getShortestPathDistance(nextGhostLocation,
								pacLocation, nextGhostMove);
						if (gameState.getGhostEdibleTime(g) > nextGhostDist)
							nextGhostDist = gameState.getGhostEdibleTime(g);
					} else {
						nextGhostDist = gameState.getShortestPathDistance(
								gameState.getGhostInitialNodeIndex(), pacLocation, nextGhostMove)
								+ (gameState.getGhostLairTime(g) - 1);
					}
					// The ghost is moving towards the pacman
					if (nextGhostDist < distance && !nearPowerPill(pacLocation)) {
						// System.out.println("Angry ghost near!");
						return true;
					}
				}
			}
		}
		return false;
	}

	private boolean angryGhostOnCrossPath(int pacJunction, MOVE pacMove) {
		pacHeading = graph[pacJunction][pacMove.ordinal()].nodes[1];
		if (pacHeading == pacJunction) {
			pacHeading = graph[pacJunction][pacMove.ordinal()].nodes[0];
		}
		//
		if (pacJunction >= 0 && pacMove != MOVE.NEUTRAL) {
			for (GHOST g : GHOST.values()) {
				if (gameState.getGhostEdibleTime(g) - 1 > 0) {
					int distanceFromPacToGhost = gameState.getShortestPathDistance(
							gameState.getNeighbour(pacJunction, pacMove),
							gameState.getGhostCurrentNodeIndex(g), pacMove) + 1;
					if (distanceFromPacToGhost - Constants.EAT_DISTANCE <= gameState
							.getGhostEdibleTime(g) - 1) {
						continue;
					}
				}

				if (dGame.getGhostEdgeId(g.ordinal()) == graph[pacJunction][pacMove.ordinal()].uniqueId) {
					//
					if (gameState.getGhostLairTime(g) == 0) {
						ghostHeading = dGame.getGhostHeading(g.ordinal());
					} else {
						ghostHeading = pacJunction;
					}
					// The ghost is heading for pacman.
					if (ghostHeading == pacJunction) {
						if (graph[pacJunction][pacMove.ordinal()].powerPill) {
							int ppindex = gameState.getPowerPillIndices()[graph[pacJunction][pacMove
									.ordinal()].powerPillIndex];
							if (gameState.isPowerPillStillAvailable(graph[pacJunction][pacMove
									.ordinal()].powerPillIndex)) {
								//
								int pacPillDist = gameState.getShortestPathDistance(
										gameState.getNeighbour(pacJunction, pacMove), ppindex,
										pacMove) + 1;
								//
								int ghostPillDist = gameState.getShortestPathDistance(
										gameState.getGhostCurrentNodeIndex(g), ppindex,
										gameState.getGhostLastMoveMade(g));
								//
								if (pacPillDist < ghostPillDist) {
									int pacGhostDist = gameState.getShortestPathDistance(
											pacJunction, gameState.getGhostCurrentNodeIndex(g));
									//
									if (pacPillDist < pacGhostDist) {
										continue;
									}

								}
							}
						}
						return true;
					}
					if (gameState.getShortestPathDistance(gameState.getGhostCurrentNodeIndex(g),
							gameState.getPacmanCurrentNodeIndex(),
							gameState.getGhostLastMoveMade(g)) < graph[pacJunction][pacMove
							.ordinal()].length + 1) {
						return true;
					}
				} else if (gameState.getGhostCurrentNodeIndex(g) == pacHeading) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean angryGhostOnJunction(int pacJunction, MOVE pacMove) {
		pacHeading = graph[pacJunction][pacMove.ordinal()].nodes[1];
		if (pacHeading == pacJunction) {
			pacHeading = graph[pacJunction][pacMove.ordinal()].nodes[0];
		}

		int target = pacHeading;
		pacDistance = graph[pacJunction][pacMove.ordinal()].length + 1;
		// If there is a power pill on the edge, the target to measure is that powerpill"s location
		if (graph[pacJunction][pacMove.ordinal()].powerPill) {
			if (gameState
					.isPowerPillStillAvailable(graph[pacJunction][pacMove.ordinal()].powerPillIndex)) {
				target = gameState.getPowerPillIndices()[graph[pacJunction][pacMove.ordinal()].powerPillIndex];
				pacDistance = gameState.getShortestPathDistance(
						gameState.getNeighbour(pacJunction, pacMove), target, pacMove) + 1;
			}
		}

		for (GHOST g : GHOST.values()) {
			int i = g.ordinal();

			if (dGame.getGhostEdgeId(i) != graph[pacJunction][pacMove.ordinal()].uniqueId) {
				ghostLoc = gameState.getGhostCurrentNodeIndex(g);
				ghostDir = gameState.getGhostLastMoveMade(g);
				//
				if (gameState.getGhostLairTime(g) > 0) {
					origDist = gameState.getGhostLairTime(g)
							+ gameState.getShortestPathDistance(
									gameState.getGhostInitialNodeIndex(), target);
				} else {
					if (ghostLoc == gameState.getGhostInitialNodeIndex()
							&& ghostDir.equals(MOVE.NEUTRAL)) {
						origDist = gameState.getShortestPathDistance(ghostLoc, target);
					} else {
						origDist = gameState.getShortestPathDistance(ghostLoc, target, ghostDir);
						if (gameState.getGhostEdibleTime(g) > origDist)
							origDist = gameState.getGhostEdibleTime(g);
					}
				}
				if (origDist - (Constants.EAT_DISTANCE) <= pacDistance) {
					// Ghost is closer to the junction than pac man
					return true;
				}
			}
		}
		return false;
	}

	public int closestBlueGhost() {
		// Blue ghosts are active --> target is the closest one
		int k = 0;
		int[] blue = new int[Constants.NUM_GHOSTS];
		for (GHOST g : GHOST.values()) {
			if (gameState.isGhostEdible(g) && gameState.getGhostLairTime(g) == 0) {
				if (gameState.getShortestPathDistance(pacLocation,
						gameState.getGhostCurrentNodeIndex(g)) <= gameState.getGhostEdibleTime(g)
						- (Constants.EAT_DISTANCE)) {
					blue[k] = gameState.getGhostCurrentNodeIndex(g);
					k++;
				}
			}
		}
		//
		if (k > 0) {
			int[] targets = new int[k];
			System.arraycopy(blue, 0, targets, 0, k);
			return gameState.getClosestNodeIndexFromNodeIndex(
					gameState.getPacmanCurrentNodeIndex(), targets, DM.PATH);
		} else {
			return -1;
		}
	}

	public MOVE generatePacManMove() {
		safety = -1;
		pacLocation = gameState.getPacmanCurrentNodeIndex();
		if (gameState.isJunction(pacLocation)) { // More than 2 choices can be
			lastJSafety = -1;
			// made
			// System.out.println("----=====----");
			reversedOnPath = false;
			reverse = gameState.getPacmanLastMoveMade().opposite();
			junction = graph[pacLocation];

			safeMoves = new MOVE[4];
			int k = 0;

			// Check for angry ghosts on all paths & unavailable moves
			for (MOVE m : MOVE.values()) {
				if (m == MOVE.NEUTRAL) {
					continue;
				}
				int i = m.ordinal();
				if (junction[i] != null) {
					if (!angryGhostOnCrossPath(pacLocation, m)) {
						safeMoves[k] = m;
						k++;
					}
				}
			}

			safeMoves2 = new MOVE[k];
			//
			int blueGhost = -1;
			MOVE closerToBlue = MOVE.NEUTRAL;
			// Epsilon greedy ghost move
			blueGhost = closestBlueGhost();
			if (blueGhost > -1) {
				closerToBlue = gameState.getNextMoveTowardsTarget(pacLocation, blueGhost, DM.PATH);
			}
			//
			int l = 0;
			for (int i = 0; i < k; i++) {
				if (!angryGhostOnJunction(pacLocation, safeMoves[i])) {
					// if (safeMoves[i].equals(closerToBlue)) {
					// // System.out.println(safeMoves2[j] +
					// " closer to ghost");
					// return safeMoves[i];
					// } ""
					safeMoves2[l] = safeMoves[i];
					l++;
				}
			}
			// System.out.println();
			moves = new MOVE[l];
			int m = 0;
			// Check if the paths with no ghosts were not visited before
			for (int j = 0; j < l; j++) {
				if (safeMoves2[j].equals(closerToBlue)) {
					lastJSafety = 5;
					// System.out.println(safeMoves2[j] + " closer to ghost");

					// debugGameState = gameState.copy();
					// debugDGameState = dGame.copy();
					return safeMoves2[j];
				}
				// Don"t visit an edge twice unless it is unavoidable
				try {
					if (dGame.getPacmanEdgeVisited(junction[safeMoves2[j].ordinal()].uniqueId)
							|| safeMoves2[j] == reverse) {
						continue;
					}
				} catch (Exception e) {
					// This sometimes goes wrong, no problem.
				}

				moves[m] = safeMoves2[j];
				m++;
			}

			MOVE nextDir = MOVE.NEUTRAL;
			if (m > 0 && XSRandom.r.nextDouble() < epsilon) {
				// There is a completely safe path, that was not visited
				// before
				nextDir = moves[XSRandom.r.nextInt(m)];
				lastJSafety = 1;
				// TODO DEBUG
				// debugGameState = gameState.copy();
				// debugDGameState = dGame.copy();
				// System.out.println("[1] Selected " + nextDir + " T: " + gameState.getCurrentLevelTime() + " L: "
				// + gameState.getPacmanCurrentNodeIndex());
			} else if (l > 0) {
				// No safe path that was not visited before,
				// but safe path available that was visited before
				// Try to go for the nearest safe pill in this case
				nextDir = safeMoves2[XSRandom.r.nextInt(l)];
				lastJSafety = 2;
				// TODO DEBUG
				// debugGameState = gameState.copy();
				// debugDGameState = dGame.copy();
				// System.out.println("[2] Selected " + nextDir + " T: " + gameState.getCurrentLevelTime() + " L: "
				// + gameState.getPacmanCurrentNodeIndex());
			} else if (k > 0) {
				nextDir = safeMoves[XSRandom.r.nextInt(k)];
				lastJSafety = 12;
				// System.out.println("[3] Selected " + nextDir + " T: " + gameState.getCurrentLevelTime() + " L: "
				// + gameState.getPacmanCurrentNodeIndex());
			} else {
				pacMoves = gameState.getPossibleMoves(pacLocation,
						gameState.getPacmanLastMoveMade());
				nextDir = pacMoves[XSRandom.r.nextInt(pacMoves.length)];
				lastJSafety = 13;
				// System.out.println("[4] Random " + nextDir + " T: " + gameState.getCurrentLevelTime() + " L: "
				// + gameState.getPacmanCurrentNodeIndex());
			}
			return nextDir;

		} else {
			// Pacman is not at a location where she has more than 2 choices
			if (!reversedOnPath) {
				if (lastJSafety == 11) {
					// Pacman is allowed to reverse after when there is an angry ghost near
					if (angryGhostNear()) {
						// This contains both forward and backward directions
						directionsRev = gameState.getPossibleMoves(pacLocation);
						// This is always the forward direction
						MOVE forward = gameState.getPossibleMoves(pacLocation,
								gameState.getPacmanLastMoveMade())[0];
						MOVE reverse = MOVE.NEUTRAL;

						for (int i = directionsRev.length - 1; i >= 0; i--) {
							if (directionsRev[i] != forward) {
								reverse = directionsRev[i];
								break;
							}
						}
						reversedOnPath = true; // Reverse only once
						dGame.reversePacMan();
						safety = 7;
						return reverse;
					}
				}

				// Pacman is allowed to reverse after eating a powerpill
				if (gameState.wasPowerPillEaten()) {
					//
					for (GHOST g : GHOST.values()) {
						blues[g.ordinal()] = gameState.getGhostCurrentNodeIndex(g);
					}

					MOVE move = gameState
							.getNextMoveTowardsTarget(pacLocation, gameState
									.getClosestNodeIndexFromNodeIndex(pacLocation, blues, DM.PATH),
									DM.PATH);
					if (move == gameState.getPacmanLastMoveMade().opposite()
							|| move.opposite() == gameState.getPacmanLastMoveMade()) {
						dGame.reversePacMan();
						reversedOnPath = true;
						// System.out.println("Powerpill eaten, reversed!");
					}
					safety = 7;
					return move;
				}
			}
			safety = 0;
			// Just go forward!
			return gameState.getPossibleMoves(pacLocation, gameState.getPacmanLastMoveMade())[0];
		}
	}

	private boolean nearPowerPill(int pacLoc) {
		if (dGame.getCurrentPacmanEdge() != null && dGame.getCurrentPacmanEdge().powerPill) {
			powerPills = gameState.getActivePowerPillsIndices();
			if (powerPills.length > 0) {
				for (int i = 0; i < powerPills.length; i++) {
					curDist = gameState.getShortestPathDistance(pacLoc, powerPills[i]);
					if (curDist <= (Constants.EAT_DISTANCE + 1)) {
						nextLoc = gameState.getNeighbour(
								pacLoc,
								gameState.getPossibleMoves(pacLoc,
										gameState.getPacmanLastMoveMade())[0]);
						nextDist = gameState.getShortestPathDistance(nextLoc, powerPills[i]);
						if (nextDist < curDist) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}
}
