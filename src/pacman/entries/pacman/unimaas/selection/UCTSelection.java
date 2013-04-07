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
		double alpha = (selectionType == SelectionType.PillScore) ? alpha_ps : alpha_g;
		//
		for (MCTNode c : children) {
			double uctValue = 0., val_old = 0., val_new = 0., old_surv, val_no_alpha = 0.;
			if (maxSelection) {
				if (c.getNewMaxValue(MCTNode.SURV_I) == 0.) {
					old_surv = 0.;
				} else {
					old_surv = c.getOldMaxValue(MCTNode.SURV_I);
				}
				// Use the max values
				if (selectionType == SelectionType.GhostScore) {
					val_no_alpha = c.getAlphaGhostScore(true);
					val_old = c.getOldMaxValue(MCTNode.GHOST_I) * old_surv;
					val_new = c.getNewMaxValue(MCTNode.GHOST_I) * c.getNewMaxValue(MCTNode.SURV_I);
				} else if (selectionType == SelectionType.PillScore) {
					val_no_alpha = c.getAlphaPillScore(true);
					val_old = c.getOldMaxValue(MCTNode.PILL_I) * old_surv;
					val_new = c.getNewMaxValue(MCTNode.PILL_I) * c.getNewMaxValue(MCTNode.SURV_I);
				} else {
					val_no_alpha = c.getAlphaSurvivalScore(true);
					val_old = old_surv;
					val_new = c.getNewMaxValue(MCTNode.SURV_I);
				}
			} else {
				if (c.getNewMeanValue(MCTNode.SURV_I) == 0.) {
					old_surv = 0.;
				} else {
					old_surv = c.getOldMeanValue(MCTNode.SURV_I);
				}
				// Use the mean values
				if (selectionType == SelectionType.GhostScore) {
					val_no_alpha = c.getAlphaGhostScore(false);
					val_old = c.getOldMeanValue(MCTNode.GHOST_I) * old_surv;
					val_new = c.getNewMeanValue(MCTNode.GHOST_I)
							* c.getNewMeanValue(MCTNode.SURV_I);
				} else if (selectionType == SelectionType.PillScore) {
					val_no_alpha = c.getAlphaPillScore(false);
					val_old = c.getOldMeanValue(MCTNode.PILL_I) * old_surv;
					val_new = c.getNewMeanValue(MCTNode.PILL_I) * c.getNewMeanValue(MCTNode.SURV_I);
				} else {
					val_no_alpha = c.getAlphaSurvivalScore(false);
					val_old = old_surv;
					val_new = c.getNewMeanValue(MCTNode.SURV_I);
				}
			}
			//
			if (c.newVisitCount == 0) {
				// Give an unvisited node a high value s.t. it is selected.
				uctValue = 100.0 + (XSRandom.r.nextDouble() * 10.0);
			} else if (c.newVisitCount < minVisits) {
				// Give an unvisited node a high value s.t. it is selected.
				uctValue = 10.0 + (XSRandom.r.nextDouble() * 10.0);
			} else if (c.oldVisitCount > minVisits) {
				if (!MCTNode.noalpha) {
					uctValue = alpha
							* (val_old + C * Math.sqrt(Math.log(P.oldVisitCount) / c.oldVisitCount))
							+ (1. - alpha)
							* (val_new + C * Math.sqrt(Math.log(P.newVisitCount) / c.newVisitCount));
				} else {
					uctValue = val_no_alpha
							+ C
							* Math.sqrt(Math.log(P.newVisitCount + P.oldVisitCount)
									/ (c.newVisitCount + c.oldVisitCount));
				}
			} else {
				// we did not see this node in previous searches, use normal uct.
				uctValue = val_new + C * Math.sqrt(Math.log(P.newVisitCount) / c.newVisitCount);
			}

			if (Double.isNaN(uctValue)) {
				System.err.println("NaN value in selection.");
				uctValue = XSRandom.r.nextDouble();
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
