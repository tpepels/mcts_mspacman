package pacman.entries.pacman.unimaas.selection;

import pacman.entries.pacman.unimaas.framework.MCTNode;
import pacman.entries.pacman.unimaas.framework.MCTSelection;
import pacman.entries.pacman.unimaas.framework.SelectionType;
import pacman.entries.pacman.unimaas.framework.XSRandom;

public class UCTSelection implements MCTSelection {

	public static double C = .9, alpha_ps = .5, alpha_g = .5;
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
			double uctValue = 0., val_old = 0., val_new = 0.;

			if (maxSelection) {
				// Use the max values
				if (selectionType == SelectionType.GhostScore) {
					val_old = c.getOldMaxValue(MCTNode.GHOST_I) * c.getOldMaxValue(MCTNode.SURV_I);
					val_new = c.getNewMaxValue(MCTNode.GHOST_I) * c.getNewMaxValue(MCTNode.SURV_I);
				} else if (selectionType == SelectionType.PillScore) {
					val_old = c.getOldMaxValue(MCTNode.PILL_I) * c.getOldMaxValue(MCTNode.SURV_I);
					val_new = c.getNewMaxValue(MCTNode.PILL_I) * c.getNewMaxValue(MCTNode.SURV_I);
				} else {
					val_old = c.getOldMaxValue(MCTNode.SURV_I);
					val_new = c.getNewMaxValue(MCTNode.SURV_I);
				}
			} else {
				// Use the mean values
				if (selectionType == SelectionType.GhostScore) {
					val_old = c.getOldMeanValue(MCTNode.GHOST_I)
							* c.getOldMeanValue(MCTNode.SURV_I);
					val_new = c.getNewMeanValue(MCTNode.GHOST_I)
							* c.getNewMeanValue(MCTNode.SURV_I);
				} else if (selectionType == SelectionType.PillScore) {
					val_old = c.getOldMeanValue(MCTNode.PILL_I) * c.getOldMeanValue(MCTNode.SURV_I);
					val_new = c.getNewMeanValue(MCTNode.PILL_I) * c.getNewMeanValue(MCTNode.SURV_I);
				} else {
					val_old = c.getOldMeanValue(MCTNode.SURV_I);
					val_new = c.getNewMeanValue(MCTNode.SURV_I);
				}
			}
			
			// check the number of visits PER TURN!
			if (c.newVisitCount < minVisits) {
				// Give an unvisited node a high value s.t. it is selected.
				uctValue = 100.0 + (XSRandom.r.nextDouble() * 10.0);
			} else if (c.oldVisitCount >= 1.) {
				uctValue = alpha
						* (val_old + C * Math.sqrt(Math.log(P.oldVisitCount) / c.oldVisitCount))
						+ (1. - alpha)
						* (val_new + C * Math.sqrt(Math.log(P.newVisitCount) / c.newVisitCount));
			} else {
				// we did not see this node in previous searches, use normal uct.
				uctValue = val_new + C * Math.sqrt(Math.log(P.newVisitCount) / c.newVisitCount);
			}
			if(Double.isNaN(uctValue))
				System.err.println("wtf");
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
