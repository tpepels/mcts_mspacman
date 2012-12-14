package pacman.entries.pacman.unimaas.selection;

import pacman.entries.pacman.unimaas.framework.MCTNode;
import pacman.entries.pacman.unimaas.framework.MCTSelection;
import pacman.entries.pacman.unimaas.framework.SelectionType;
import pacman.entries.pacman.unimaas.framework.XSRandom;

public class UCTSelection implements MCTSelection {

	public static double C = .95, alpha_ps = 0.6, alpha_g = 0.2;
	public static int minVisits = 10;
	private SelectionType selectionType = SelectionType.SurvivalRate;

	@Override
	public MCTNode selectNode(MCTNode P, boolean maxSelection) {

		MCTNode selectedNode = null;
		double bestValue = Double.NEGATIVE_INFINITY;
		MCTNode[] children = P.getChildren();
		double alpha = (selectionType == SelectionType.GhostScore) ? alpha_g : alpha_ps;
		//
		for (MCTNode c : children) {
			double uctValue = 0., Vt = 0., Vc = 0.;

			if (maxSelection) {
				if (selectionType == SelectionType.GhostScore) {
					Vt = c.getMaxGhostScore() * c.getMaxSurvivalRate();
					Vc = c.getCurrentMaxGhostScore() * c.getCurrentMaxSurvivals();
				} else if (selectionType == SelectionType.PillScore) {
					Vt = c.getMaxPillScore() * c.getMaxSurvivalRate();
					Vc = c.getCurrentMaxPillScore() * c.getCurrentMaxSurvivals();
				} else {
					Vt = c.getMaxSurvivalRate();
					Vc = c.getCurrentMaxSurvivals();
				}
			} else {
				if (selectionType == SelectionType.GhostScore) {
					Vt = c.getGhostScore() * c.getSurvivalRate();
					Vc = c.getCurrentMeanGhostScore() * c.getCurrentMeanSurvivals();
				} else if (selectionType == SelectionType.PillScore) {
					Vt = c.getPillScore() * c.getSurvivalRate();
					Vc = c.getCurrentMeanPillScore() * c.getCurrentMeanSurvivals();
				} else {
					Vt = c.getSurvivalRate();
					Vc = c.getCurrentMeanSurvivals();
				}
			}

			// check the number of visits PER TURN!
			if (c.currentVisitCount < minVisits) {
				// Give an unvisited node a high value s.t. it is selected.
				uctValue = 100.0 + (XSRandom.r.nextDouble() * 10.0);
			} else {
				uctValue = alpha
						* (Vt + (C * Math.sqrt(Math.log(P.getVisitCount()) / c.getVisitCount())))
						+ ((1 - alpha) * (Vc + (C * Math.sqrt(Math.log(P.getCurrentVisitCount())
								/ c.getCurrentVisitCount()))));
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

	@Override
	public void setSelectionType(SelectionType selectionType) {
		this.selectionType = selectionType;
	}
}
