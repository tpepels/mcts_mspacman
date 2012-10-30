package pacman.entries.pacman.unimaas.framework;

/**
 *
 * @author Tom Pepels, Maastricht University
 */
public interface MCTSelection {
    /**
     * Should calculate a node for exansion/exploration
     * @return MCT Node to be expanded
     */
    public MCTNode selectNode(MCTNode rootNode, boolean maxSelection);
    public void setSelectionType(SelectionType type);
}
