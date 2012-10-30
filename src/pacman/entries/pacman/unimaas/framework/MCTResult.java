package pacman.entries.pacman.unimaas.framework;

/**
 *
 * @author Tom Pepels, Maastricht University
 */
public class MCTResult {
    public double pillScore = 0.;
    public double ghostScore = 0.;
    public boolean target = false;

    public MCTResult(double pillScore, double ghostScore, boolean target) {
        this.pillScore = pillScore;
        this.ghostScore = ghostScore;
        this.target = target;
    }
    
    @Override
    public String toString() {
        return "MCTResult: pillScore: " + pillScore + ", ghostScore: " + ghostScore + ", target: " + target;
    }
}
