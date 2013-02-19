import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

public class TestGen {
	private static final int numTrials = 25;
	private static PrintWriter out;

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		File folder = new File("C:\\Users\\Tom\\Desktop\\config\\");
		File[] listOfFiles = folder.listFiles();
		
		for (int j = 0; j < listOfFiles.length; j++) {
			String fullName = listOfFiles[j].getName();
			String shortName = fullName.substring(0, fullName.length() - 5);
			createOutFile(shortName);
			out.print("java -jar MsPacMan.jar " + shortName + ".txt " + numTrials + " config/" + fullName + " false");
			out.flush();
			if (out != null)
				out.close();
		}
	}

	private static void createOutFile(String fileName) {
		try {
			// FileWriter logFile = new FileWriter(fileName, true);
			out = new PrintWriter("C:\\Users\\Tom\\Desktop\\scripts\\" + fileName);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
