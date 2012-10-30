package pacman.entries.pacman.unimaas.framework;

import java.util.EnumMap;

import pacman.game.Constants.GHOST;
import pacman.game.Constants.MOVE;

/**
 * 
 * @author Tom Pepels, Maastricht University
 */
public interface GhostMoveGenerator {
	public EnumMap<GHOST, MOVE> generateGhostMoves();
}
