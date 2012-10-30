package pacman.entries.pacman.unimaas.framework;

/**
 * 
 * @author Tom Pepels, Maastricht University
 */
public class Edge {
	public final int uniqueId;
	public final int[] nodes;
	public final int length, pillCount;
	public final boolean powerPill;
	public int powerPillIndex = -1;

	public Edge(int id, int[] nodes, int length, int pillCount, boolean powerPill) {
		this.uniqueId = id;
		this.length = length;
		this.nodes = nodes;
		this.pillCount = pillCount;
		this.powerPill = powerPill;
	}
	
	public void setPowerpillIndex(int index) {
		this.powerPillIndex = index;
	}
}
