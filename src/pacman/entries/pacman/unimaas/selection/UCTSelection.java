package pacman.entries.pacman.unimaas.selection;

import pacman.entries.pacman.unimaas.framework.MCTNode;
import pacman.entries.pacman.unimaas.framework.MCTSelection;
import pacman.entries.pacman.unimaas.framework.SelectionType;
import pacman.entries.pacman.unimaas.framework.XSRandom;

/**
 * 
 * @author Tom Pepels, Maastricht University
 */
public class UCTSelection implements MCTSelection {

	public static double uctConstant = 1.6;
	public static int minVisits = 10;
	private SelectionType selectionType = SelectionType.TargetRate;
	private final double eps = 0.0001;
	
	@Override
	public MCTNode selectNode(MCTNode parent, boolean maxSelection) {

		MCTNode selectedNode = null;
		double bestValue = Double.NEGATIVE_INFINITY;
		MCTNode[] children = parent.getChildren();

		for (MCTNode c : children) {

			double uctValue = 0., nodeScore = 0.;

			if (maxSelection) {
				if (selectionType == SelectionType.GhostScore) {
					nodeScore = c.getMaxGhostScore() * c.getMaxSurvivalRate();
				} else if (selectionType == SelectionType.PillScore) {
					nodeScore = c.getMaxPillScore() * c.getMaxSurvivalRate();
				} else {
					nodeScore = c.getMaxSurvivalRate();
				}
			} else {
				if (selectionType == SelectionType.GhostScore) {
					nodeScore = c.getGhostScore() * c.getSurvivalRate();
				} else if (selectionType == SelectionType.PillScore) {
					nodeScore = c.getPillScore() * c.getSurvivalRate();
				} else {
					nodeScore = c.getSurvivalRate();
				}
			}
			
			// check the number of visits PER TURN!
			if (c.turnVisitCount < minVisits) {
				// Give an unvisited node a high value s.t. it is selected.
				uctValue = 100.0 + (XSRandom.r.nextDouble() * 10.0);
			} else {
				uctValue = nodeScore + (uctConstant * 
						Math.sqrt(Math.log(parent.getVisitCount()) / c.getVisitCount())) 
						+ (XSRandom.r.nextDouble() * eps); // Tie breaker
			}
			
			// Select the highest value
			if (uctValue > bestValue) {
				selectedNode = c;
				bestValue = uctValue;
			}
		}
		//
		selectedNode.addVisit();
		//
		return selectedNode;
	}

	/**
	 * @param minVisits
	 *            The minimum number of visits before UCT starts
	 */
	public void setMinVisits(int minVisits) {
		UCTSelection.minVisits = minVisits;
	}

	@Override
	public void setSelectionType(SelectionType selectionType) {
		this.selectionType = selectionType;
	}
}
