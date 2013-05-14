import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

public class TestGen {
	private static int numTrials = 200;
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
			//
			if(shortName.toLowerCase().contains("legacy"))
				numTrials = 100;
			else if (shortName.toLowerCase().contains("wilsh") || shortName.toLowerCase().contains("flamedragon"))
				numTrials = 200;
			else
				numTrials = 400;
			//
			out.print("java -jar MsPacMan.jar " + shortName + ".txt " + numTrials + " config/" + fullName + " true");
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
