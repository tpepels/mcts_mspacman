package pacman.entries.pacman.unimaas;

import java.util.ArrayList;

import pacman.entries.pacman.unimaas.framework.DiscreteGame;
import pacman.entries.pacman.unimaas.framework.Edge;
import pacman.entries.pacman.unimaas.framework.GhostMoveGenerator;
import pacman.entries.pacman.unimaas.framework.MCTNode;
import pacman.entries.pacman.unimaas.framework.PathFollower;
import pacman.entries.pacman.unimaas.ghosts.AggressiveGhosts;
import pacman.game.Constants.DM;
import pacman.game.Constants.MOVE;
import pacman.game.Game;

/**
 * 
 * @author Tom Pepels, Maastricht University
 */
public class SinglePlayerNode extends MCTNode {

	private final int retries = 5; // How many times to retry if death in path
									// to follow

	public SinglePlayerNode(DiscreteGame dGame, Game gameState) {
		super(dGame, gameState);
	}

	public SinglePlayerNode(Game gameState, MCTNode parent, MOVE pathDirection, Edge edge,
			int junctionIndex, int pathLength) {
		super(gameState, parent, pathDirection, edge, junctionIndex, pathLength);
	}

	@Override
	public void expand(DiscreteGame dGame, MCTNode rootNode) {

		if (children != null) {
			return;
		}

		int childCount = 0, pacLoc = gameState.getPacmanCurrentNodeIndex();
		ArrayList<MCTNode> tempChildren = new ArrayList<MCTNode>(4);

		if (getJunctionIndex() != -1) {
			Edge[] nextEdges = dGame.getGraph()[getJunctionIndex()];
			MCTNode childNode = null;
			for (int i = 0; i < nextEdges.length; i++) {

				if (nextEdges[i] == null) {
					continue;
				}

				// The order of the nodes in the list may differ.
				int nextJunction = nextEdges[i].nodes[1];
				if (nextJunction == getJunctionIndex()) {
					nextJunction = nextEdges[i].nodes[0];
				}

				if (!isRoot()) {
					if (nextEdges[i].uniqueId != getEdgeId()) {
						childNode = new SinglePlayerNode(gameState, this, MOVE.values()[i],
								nextEdges[i], nextJunction, getPathLength() + nextEdges[i].length);
						tempChildren.add(childNode);
						childCount++;
					}
				} else {
					childNode = new SinglePlayerNode(gameState, this, MOVE.values()[i],
							nextEdges[i], nextJunction, nextEdges[i].length);
					tempChildren.add(childNode);
					childCount++;
				}

			}
			// Pacman is not at a junction in the gamestate,
			// hence find the junctions connected to pacman.
		} else if (this.getEdgeId() != -1) {
			// TODO DEBUG
			// if (dGame.getCurrentPacmanEdgeId() != this.getEdgeId() ||
			// !isRoot()) {
			// System.out.println("Verkeerde edge in expand");
			// }
			// !DEBUG

			MOVE[] allMoves = gameState.getPossibleMoves(pacLoc);
			MOVE forward = gameState.getPossibleMoves(pacLoc, gameState.getPacmanLastMoveMade())[0];
			MOVE reverse = MOVE.NEUTRAL;
			for (int i = 0; i < allMoves.length; i++) {
				if (allMoves[i] != forward) {
					reverse = allMoves[i];
				}
			}

			MCTNode forwardNode = new SinglePlayerNode(gameState, this, forward,
					dGame.getCurrentPacmanEdge(), dGame.getPacHeading(),
					dGame.pacManDistanceToHeading());

			MCTNode reverseNode = new SinglePlayerNode(gameState, this, reverse,
					dGame.getCurrentPacmanEdge(), dGame.getPacRear(), dGame.pacManDistanceToRear());
			//
			tempChildren.add(forwardNode);
			tempChildren.add(reverseNode);
			//
			childCount += 2;

		} else {
			// TODO DEBUG
			// if (!isRoot()) {
			// System.err.println("Strange!");
			// } // !DEBUG
			MOVE[] pacManMoves = gameState.getPossibleMoves(pacLoc);
			PathFollower pf = new PathFollower();

			for (int i = 0; i < pacManMoves.length; i++) {
				if (pacManMoves[i] == MOVE.NEUTRAL)
					continue;
				Game newState = null;
				boolean died = true, nextMaze = false;
				int tries = 0;
				int currentMaze = gameState.getMazeIndex();
				// If pac-man dies on a path, try this path again
				while (died && tries < retries) {
					newState = gameState.copy();
					//
					GhostMoveGenerator ghostMover = new AggressiveGhosts(newState);
					MOVE[] newMoves = pf.followPath(newState, ghostMover, pacManMoves[i]);
					//
					died = (newMoves.length == 0);
					if (currentMaze != newState.getMazeIndex()) {
						// Don"t generate any children, a fast heuristic move
						// should be made.
						nextMaze = true;
						break;
					}
					tries++;
				}
				if (nextMaze) {
					break;
				}

				// If after retries attempts the path could not be followed,
				// discard the path
				if (!died && !nextMaze) {
					MCTNode childNode = new SinglePlayerNode(gameState, this, pacManMoves[i],
							dGame.getGraph()[newState.getPacmanCurrentNodeIndex()][pacManMoves[i]
									.opposite().ordinal()], newState.getPacmanCurrentNodeIndex(),
							(int) gameState.getDistance(pacLoc,
									newState.getPacmanCurrentNodeIndex(), DM.PATH));
					tempChildren.add(childNode);
					childCount++;
				}
			}
		}

		if (childCount > 0) {
			MCTNode[] t = new MCTNode[0];
			MCTNode[] tempChildNodes = tempChildren.toArray(t);

			children = tempChildNodes;
		}
	}
}
