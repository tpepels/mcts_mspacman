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

	public static double uctConstant = 1., alpha = 0.4;
	private static double current_alpha = alpha;
	public static int minVisits = 10;
	private SelectionType selectionType = SelectionType.TargetRate;
	private final double eps = 0.0001;

	@Override
	public MCTNode selectNode(MCTNode parent, boolean maxSelection) {

		MCTNode selectedNode = null;
		double bestValue = Double.NEGATIVE_INFINITY;
		MCTNode[] children = parent.getChildren();
		//
		if(selectionType == SelectionType.GhostScore)
			current_alpha = 0.;
		else
			current_alpha = 0.4;
		//
		for (MCTNode c : children) {

			double uctValue = 0., nodeScore = 0., currentNodeScore = 0.;

			if (maxSelection) {
				if (selectionType == SelectionType.GhostScore) {
					nodeScore = c.getMaxGhostScore() * c.getMaxSurvivalRate();
					currentNodeScore = c.getCurrentMaxGhostScore() * c.getCurrentMaxSurvivals();
				} else if (selectionType == SelectionType.PillScore) {
					nodeScore = c.getMaxPillScore() * c.getMaxSurvivalRate();
					currentNodeScore = c.getCurrentMaxPillScore() * c.getCurrentMaxSurvivals();
				} else {
					nodeScore = c.getMaxSurvivalRate();
					currentNodeScore = c.getCurrentMaxSurvivals();
				}
			} else {
				if (selectionType == SelectionType.GhostScore) {
					nodeScore = c.getGhostScore() * c.getSurvivalRate();
					currentNodeScore = c.getCurrentMeanGhostScore() * c.getCurrentMeanSurvivals();
				} else if (selectionType == SelectionType.PillScore) {
					nodeScore = c.getPillScore() * c.getSurvivalRate();
					currentNodeScore = c.getCurrentMeanPillScore() * c.getCurrentMeanSurvivals();
				} else {
					nodeScore = c.getSurvivalRate();
					currentNodeScore = c.getCurrentMeanSurvivals();
				}
			}

			// check the number of visits PER TURN!
			if (c.currentVisitCount < minVisits) {
				// Give an unvisited node a high value s.t. it is selected.
				uctValue = 100.0 + (XSRandom.r.nextDouble() * 10.0);
			} else {
				uctValue = current_alpha
						* (nodeScore + (uctConstant * Math.sqrt(Math.log(parent.getVisitCount())
								/ c.getVisitCount())))
						+ ((1 - current_alpha) 
								* (currentNodeScore + (uctConstant * Math.sqrt(Math
								.log(parent.getCurrentVisitCount()) / c.getCurrentVisitCount()))))
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
