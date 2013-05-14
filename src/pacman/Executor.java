package pacman;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Random;
import pacman.controllers.*;
import pacman.controllers.examples.Legacy2TheReckoning;
import pacman.entries.pacman.MyPacMan;
import pacman.entries.pacman.SimulationPacMan;
import pacman.entries.pacman.unimaas.Settings;
import pacman.entries.pacman.unimaas.framework.*;
import pacman.game.Constants;
import pacman.game.Constants.GHOST;
import pacman.game.Constants.MOVE;
import pacman.game.Game;
import pacman.game.GameView;

import static pacman.game.Constants.*;

/**
 * This class may be used to execute the game in timed or un-timed modes, with or without visuals. Competitors should
 * implement their controllers in game.entries.ghosts and game.entries.pacman respectively. The skeleton classes are
 * already provided. The package structure should not be changed (although you may create sub-packages in these
 * packages).
 */
@SuppressWarnings("unused")
public class Executor {
	private static MyPacMan pacman = null;
	private static Controller<EnumMap<GHOST, MOVE>> ghosts = null;
	private static Class<?> ghostClass;
	private static int numTrials = 0;
	private static boolean printall = false;
	private static Settings setting = null;
	private static long seed = -1;

	//

	/**
	 * The main method. Several options are listed - simply remove comments to use the option you want.
	 * 
	 * @param args the command line arguments
	 */
	@SuppressWarnings({ "unchecked" })
	public static void main(String[] args) {
		Executor exec = new Executor();

//		runExperiment(new SimulationPacMan(), new pacman.opponents.Ghosts.wilsh.MyGhosts(),
//				400, true);

		if (args.length == 0) {
			setting = Settings.getDefaultSetting();
			MyPacMan pm = new MyPacMan();
			pm.loadSettings(setting);
			try {
				// Super safety ...
				if (setting.opponent
						.equalsIgnoreCase("pacman.controllers.examples.legacy2thereckoning")
						|| setting.opponent
								.equalsIgnoreCase("pacman.opponents.ghosts.ghostbuster.myghosts")
						|| setting.opponent
								.equalsIgnoreCase("pacman.opponents.ghosts.memetix.myghosts")
						|| setting.opponent
								.equalsIgnoreCase("pacman.opponents.ghosts.eiisolver.myghosts")
						|| setting.opponent
								.equalsIgnoreCase("pacman.opponents.Ghosts.flamedragon.myghosts")
						|| setting.opponent
								.equalsIgnoreCase("pacman.opponents.Ghosts.wilsh.myghosts")) {
					System.out.println("Opponent: " + setting.opponent);
					ghostClass = Class.forName(setting.opponent);
					ghosts = (Controller<EnumMap<GHOST, MOVE>>) Class.forName(setting.opponent)
							.newInstance();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			exec.runGame(pm, ghosts, true, Constants.DELAY);
			return;
		} else if (args.length == 1) {
			try {
				setting = Settings.deserializeSettings(args[0]);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			MyPacMan pm = new MyPacMan();
			pm.loadSettings(setting);
			exec.runGame(pm, new Legacy2TheReckoning(), true, Constants.DELAY);
			return;
		} else if (args[0].equals("?")) {
			System.out
					.println("Usage: \n java -jar MsPacMan.jar <output_file> <num_trials> <settings_file> <verbose_output> [seed]");
			return;
		}
		//
		numTrials = Integer.parseInt(args[1]);
		writeOutput("Running " + numTrials + " games");
		printall = Boolean.parseBoolean(args[3]);

		if (args.length >= 5) {
			try {
				Integer.parseInt(args[4]);
			} catch (NumberFormatException ex) {
				// No seed
				seed = -1;
			}
		}
		//
		outFile = args[0];
		System.out.println("Output goes to: " + outFile);
		try {
			logFile = new FileWriter(outFile, true);
			out = new PrintWriter(outFile);
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}

		try {
			setting = Settings.deserializeSettings(args[2]);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		//
		try {
			// Super safety ...
			if (setting.opponent
					.equalsIgnoreCase("pacman.controllers.examples.legacy2thereckoning")
					|| setting.opponent
							.equalsIgnoreCase("pacman.opponents.ghosts.ghostbuster.myghosts")
					|| setting.opponent
							.equalsIgnoreCase("pacman.opponents.ghosts.memetix.myghosts")
					|| setting.opponent
							.equalsIgnoreCase("pacman.opponents.ghosts.eiisolver.myghosts")
					|| setting.opponent
							.equalsIgnoreCase("pacman.opponents.Ghosts.flamedragon.myghosts")
					|| setting.opponent.equalsIgnoreCase("pacman.opponents.Ghosts.wilsh.myghosts")) {
				System.out.println("Opponent: " + setting.opponent);
				ghostClass = Class.forName(setting.opponent);
				ghosts = (Controller<EnumMap<GHOST, MOVE>>) ghostClass.newInstance();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		pacman = new MyPacMan();
		setting.setPropertiesList();
		loopNextProperty(setting.properties, 0);
		if (noLoop) { // There was no list of values, we just run the number of tests with the given setting
			pacman.loadSettings(setting);
			runExperiment(pacman, ghosts, numTrials, printall);
		}
	}

	public static boolean noLoop = true;

	public static boolean loopNextProperty(ArrayList<double[]> properties, int index) {
		// Check for the next property to loop over
		while (index < properties.size() && properties.get(index).length <= 1) {
			index++;
		}
		// Check if we"re not at the end
		if (index < properties.size()) {
			double[] prop = properties.get(index);
			// Needed to reset it later
			double firstVal = prop[0];
			String name = Settings.class.getFields()[index].getName();
			for (int i = 0; i < prop.length; i++) {
				// Always place the property to test at the start of the list
				prop[0] = prop[i];
				writeOutput(name + " value: " + prop[i]);
				//
				if (!loopNextProperty(properties, index + 1)) {
					pacman = new MyPacMan();
					pacman.loadSettings(setting);
					runExperiment(pacman, ghosts, numTrials, printall);
					noLoop = false;
				}
			}
			prop[0] = firstVal;
			return true;
		} else {
			// We"ve reached the end of the list
			return false;
		}
	}

	private static String outFile;
	private static PrintWriter out;
	private static FileWriter logFile;

	private static void writeOutput(String output) {
		if (out != null) { // Write output to file
			out.println(output);
			out.flush();
		}
		// Also write it to default output in case writing to file fails.
		System.out.println(output);
	}

	@SuppressWarnings("unchecked")
	public static void runExperiment(Controller<MOVE> pacManController,
			Controller<EnumMap<GHOST, MOVE>> ghostController, int trials, boolean printall) {
		double avgScore = 0, maxScore = 0, minScore = Double.POSITIVE_INFINITY, S;
		int[] values = new int[trials];
		long due;
		Game game;
		//
		int i = 0, realTrials = 0, avgLives = 0, avgMaze = 0, errorCount = 0;
		writeOutput(":: Running " + trials + " games");
		if (printall)
			writeOutput("Score \t Lives \t Final level");
		//
		for (i = 0; i < trials; i++) {
			long mySeed = System.currentTimeMillis();
			if (seed >= 0) {
				mySeed = seed;
			}
			XSRandom.r.setSeed(mySeed);
			game = new Game(mySeed);
			errorCount = 0;
			try {
				ghosts = (Controller<EnumMap<GHOST, MOVE>>) ghostClass.newInstance();
			} catch (Exception e) {
				e.printStackTrace();
			}
			//
			while (!game.gameOver()) {
				try {
					game.advanceGame(pacManController.getMove(game.copy(),
							System.currentTimeMillis() + DELAY), ghostController.getMove(
							game.copy(), System.currentTimeMillis() + DELAY));
					errorCount = 0;
				} catch (Exception ex) {
					errorCount++;
					System.err.println("Error count: " + errorCount);
					ex.printStackTrace();
					// too many errors, skip the game
					if (errorCount >= 5) {
						System.err.println("Too many errors, ending game.");
						writeOutput("game skipped due to errors.");
						break;
					}
				}
			}

			if (printall)
				writeOutput(game.getScore() + "\t" + game.getPacmanNumberOfLivesRemaining() + "\t"
						+ game.getCurrentLevel());

			if (game.getScore() < minScore) {
				minScore = game.getScore();
			}
			if (game.getScore() > maxScore) {
				maxScore = game.getScore();
			}
			values[realTrials] = game.getScore();
			//
			avgScore += game.getScore();
			avgLives += game.getPacmanNumberOfLivesRemaining();
			avgMaze += game.getCurrentLevel();
			realTrials++;
		}
		//
		double stdSum = 0., mean = avgScore / (double) realTrials;
		for (i = 0; i < realTrials; i++) {
			stdSum += Math.pow(values[i] - mean, 2);
		}
		//
		writeOutput(":: Ran " + realTrials + " trials");
		writeOutput(":: Average score: " + mean);
		writeOutput(":: Average maze reached: " + ((double) avgMaze / (double) realTrials));
		writeOutput(":: Agerage lives remaining: " + ((double) avgLives / (double) realTrials));
		writeOutput(":: Maximum score: " + maxScore);
		writeOutput(":: Minimum score: " + minScore);
		double stdDev = Math.sqrt(stdSum / (double) realTrials - 1);
		double conf = 1.96 * stdDev / Math.sqrt(realTrials - 1);
		writeOutput(":: Std dev: " + stdDev);
		writeOutput(":: 95% conf.:" + conf);
		writeOutput(mean + "\t" + conf + "\t" + ((double) avgLives / (double) realTrials) + "\t"
				+ ((double) avgMaze / (double) realTrials));
	}

	public void runGame(Controller<MOVE> pacManController,
			Controller<EnumMap<GHOST, MOVE>> ghostController, boolean visual, int delay) {
		Game game = new Game(System.currentTimeMillis(), 0), debugGame = new Game(
				System.currentTimeMillis());

		GameView gv = null;

		if (visual)
			gv = new GameView(game).showGame();
		while (!game.gameOver()) {
			game.advanceGame(
					pacManController.getMove(game.copy(), System.currentTimeMillis() + delay),
					ghostController.getMove(game.copy(), System.currentTimeMillis() + delay));

			// try{Thread.sleep(delay);}catch(Exception e){}
			if (game.isJunction(game.getPacmanCurrentNodeIndex())) {
				debugGame = game.copy();
			}

			if (visual)
				gv.repaint();
		}
		GameView gv2 = new GameView(debugGame).showGame();
		System.out.println("Game over!");
	}

	public void runGameTimed(Controller<MOVE> pacManController,
			Controller<EnumMap<GHOST, MOVE>> ghostController, boolean visual) {
		Game game = new Game(System.currentTimeMillis());

		GameView gv = null;

		if (visual)
			gv = new GameView(game).showGame();

		if (pacManController instanceof HumanController)
			gv.getFrame().addKeyListener(((HumanController) pacManController).getKeyboardInput());

		new Thread(pacManController).start();
		new Thread(ghostController).start();

		while (!game.gameOver()) {
			pacManController.update(game.copy(), System.currentTimeMillis() + DELAY);
			ghostController.update(game.copy(), System.currentTimeMillis() + DELAY);

			try {
				Thread.sleep(DELAY);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			game.advanceGame(pacManController.getMove(), ghostController.getMove());

			if (visual)
				gv.repaint();
		}

		pacManController.terminate();
		ghostController.terminate();
	}

	public void runGameTimedSpeedOptimised(Controller<MOVE> pacManController,
			Controller<EnumMap<GHOST, MOVE>> ghostController, boolean fixedTime, boolean visual) {
		Game game = new Game(System.currentTimeMillis());

		GameView gv = null;

		if (visual)
			gv = new GameView(game).showGame();

		if (pacManController instanceof HumanController)
			gv.getFrame().addKeyListener(((HumanController) pacManController).getKeyboardInput());

		new Thread(pacManController).start();
		new Thread(ghostController).start();

		while (!game.gameOver()) {
			pacManController.update(game.copy(), System.currentTimeMillis() + DELAY);
			ghostController.update(game.copy(), System.currentTimeMillis() + DELAY);

			try {
				int waited = DELAY / INTERVAL_WAIT;

				for (int j = 0; j < DELAY / INTERVAL_WAIT; j++) {
					Thread.sleep(INTERVAL_WAIT);

					if (pacManController.hasComputed() && ghostController.hasComputed()) {
						waited = j;
						break;
					}
				}

				if (fixedTime)
					Thread.sleep(((DELAY / INTERVAL_WAIT) - waited) * INTERVAL_WAIT);

				game.advanceGame(pacManController.getMove(), ghostController.getMove());
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			if (visual)
				gv.repaint();
		}

		pacManController.terminate();
		ghostController.terminate();
	}

	/**
	 * Run a game in asynchronous mode and recorded.
	 * 
	 * @param pacManController The Pac-Man controller
	 * @param ghostController The Ghosts controller
	 * @param visual Whether to run the game with visuals
	 * @param fileName The file name of the file that saves the replay
	 */
	public void runGameTimedRecorded(Controller<MOVE> pacManController,
			Controller<EnumMap<GHOST, MOVE>> ghostController, boolean visual, String fileName) {
		StringBuilder replay = new StringBuilder();

		Game game = new Game(System.currentTimeMillis());

		GameView gv = null;

		if (visual) {
			gv = new GameView(game).showGame();

			if (pacManController instanceof HumanController)
				gv.getFrame().addKeyListener(
						((HumanController) pacManController).getKeyboardInput());
		}

		new Thread(pacManController).start();
		new Thread(ghostController).start();

		while (!game.gameOver()) {
			pacManController.update(game.copy(), System.currentTimeMillis() + DELAY);
			ghostController.update(game.copy(), System.currentTimeMillis() + DELAY);

			try {
				Thread.sleep(DELAY);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			game.advanceGame(pacManController.getMove(), ghostController.getMove());

			if (visual)
				gv.repaint();

			replay.append(game.getGameState() + "\n");
		}

		pacManController.terminate();
		ghostController.terminate();

		saveToFile(replay.toString(), fileName, false);
	}

	/**
	 * Replay a previously saved game.
	 * 
	 * @param fileName The file name of the game to be played
	 * @param visual Indicates whether or not to use visuals
	 */
	public void replayGame(String fileName, boolean visual) {
		ArrayList<String> timeSteps = loadReplay(fileName);

		Game game = new Game(System.currentTimeMillis());

		GameView gv = null;

		if (visual)
			gv = new GameView(game).showGame();

		for (int j = 0; j < timeSteps.size(); j++) {
			game.setGameState(timeSteps.get(j));

			try {
				Thread.sleep(DELAY);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			if (visual)
				gv.repaint();
		}
	}

	// save file for replays
	public static void saveToFile(String data, String name, boolean append) {
		try {
			FileOutputStream outS = new FileOutputStream(name, append);
			PrintWriter pw = new PrintWriter(outS);

			pw.println(data);
			pw.flush();
			outS.close();

		} catch (IOException e) {
			System.out.println("Could not save data!");
		}
	}

	// load a replay
	private static ArrayList<String> loadReplay(String fileName) {
		ArrayList<String> replay = new ArrayList<String>();

		try {
			@SuppressWarnings("resource")
			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(
					fileName)));
			String input = br.readLine();

			while (input != null) {
				if (!input.equals(""))
					replay.add(input);

				input = br.readLine();
			}
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}

		return replay;
	}
}