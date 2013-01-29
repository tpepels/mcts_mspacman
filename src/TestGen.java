import java.io.IOException;
import java.io.PrintWriter;

public class TestGen {

	private static String[] args1 = { "alpha_ps", "alpha_g", "uct", "gamma", "path", "simulations",
			"reuse", "decay", "var_depth", "strat_playout", "eiisolver", "ghostbuster", "memetix" };
	private static final String java = "java -jar MsPacMan.jar ";
	private static final int numTrials = 25;
	private static PrintWriter out;

	/**
	 * @param args
	 */
	public static void main(String[] args) {

		for (int j = 0; j < args1.length; j++) {
			createOutFile(args1[j]);
			out.print(java + args1[j] + ".txt " + numTrials + " " + args1[j]);
			out.flush();
			if (out != null)
				out.close();
		}
	}

	private static void createOutFile(String fileName) {
		try {
			// FileWriter logFile = new FileWriter(fileName, true);
			out = new PrintWriter("C:\\Users\\Tom\\Desktop\\Tests\\" + fileName);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
