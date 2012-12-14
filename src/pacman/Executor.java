package pacman;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Random;
import pacman.controllers.*;
import pacman.controllers.examples.Legacy2TheReckoning;
import pacman.entries.pacman.MyPacMan;
import pacman.entries.pacman.unimaas.framework.XSRandom;
import pacman.game.Constants;
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
	/**
	 * The main method. Several options are listed - simply remove comments to use the option you want.
	 * 
	 * @param args
	 *            the command line arguments
	 */
	public static void main(String[] args) {
		Executor exec = new Executor();
		//
		if (args.length == 0) {
			exec.runGame(new MyPacMan(), new Legacy2TheReckoning(), true, Constants.DELAY);
			return;
		} else if (args[0].equals("?")) {
			System.out.println("Usage: \n java -jar MsPacMan.jar <output file> <numTrials> <test>");
			return;
		}
		//
		int numTrials = Integer.parseInt(args[1]);
		//
		outFile = args[0];
		try {
			logFile = new FileWriter(outFile, true);
			out = new PrintWriter(outFile);
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		//
		Controller<EnumMap<GHOST, MOVE>> ghosts = new Legacy2TheReckoning();
		MyPacMan pacman = new MyPacMan();
		//
		if (args[2].equals("alpha_ps")) {
			writeOutput("alpha_ps");
			for (double u = .0; u <= 1.; u += .1) {
				writeOutput(":: Alpha_ps: " + u);
				pacman.setAlpha_ps(u);
				exec.runExperiment(pacman, ghosts, numTrials);
			}
		}
		//
		if (args[2].equals("alpha_g")) {
			writeOutput("alpha_g");
			for (double u = .0; u <= 1.; u += .1) {
				writeOutput(":: Alpha_g: " + u);
				pacman.setAlpha_g(u);
				exec.runExperiment(pacman, ghosts, numTrials);
			}
		}
		//
		if (args[2].equals("uct")) {
			writeOutput("UCT Constant");
			for (double u = .1; u <= 2.; u += .2) {
				writeOutput(":: UCT Constant: " + u);
				pacman.setUCTC(u);
				exec.runExperiment(pacman, ghosts, numTrials);
			}
		}
		//
		if (args[2].equals("gamma")) {
			writeOutput("gamma");
			for (double u = .0; u <= 1.; u += .1) {
				writeOutput(":: Decay factor gamma: " + u);
				pacman.setGamma(u);
				exec.runExperiment(pacman, ghosts, numTrials);
			}
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

	public void runExperiment(Controller<MOVE> pacManController,
			Controller<EnumMap<GHOST, MOVE>> ghostController, int trials) {
		double avgScore = 0, maxScore = 0, minScore = Double.POSITIVE_INFINITY, S;
		int[] values = new int[trials];
		long due;
		Random rnd = new Random(0);
		XSRandom.r.setSeed(0);
		Game game;
		//
		int i = 0, realTrials = 0, avgLives = 0, avgMaze = 0;
		writeOutput(":: Running " + trials + " games");
		writeOutput("Score \t Lives \t Final level");
		//
		for (i = 0; i < trials; i++) {
			game = new Game(rnd.nextLong());
			//
			try {
				while (!game.gameOver()) {
					game.advanceGame(pacManController.getMove(game.copy(),
							System.currentTimeMillis() + DELAY), ghostController.getMove(
							game.copy(), System.currentTimeMillis() + DELAY));
				}
			} catch (Exception ex) {
				System.err.println("Exception caught, running next game. " + ex.getMessage());
			}

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
		writeOutput(":: Std dev: " + Math.sqrt(stdSum / (double) realTrials));
		writeOutput("-------------------========-----------------------");
	}

	public void runGame(Controller<MOVE> pacManController,
			Controller<EnumMap<GHOST, MOVE>> ghostController, boolean visual, int delay) {
		Game game = new Game(0), debugGame = new Game(0);

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
		Game game = new Game(0);

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
		Game game = new Game(0);

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
	 * @param pacManController
	 *            The Pac-Man controller
	 * @param ghostController
	 *            The Ghosts controller
	 * @param visual
	 *            Whether to run the game with visuals
	 * @param fileName
	 *            The file name of the file that saves the replay
	 */
	public void runGameTimedRecorded(Controller<MOVE> pacManController,
			Controller<EnumMap<GHOST, MOVE>> ghostController, boolean visual, String fileName) {
		StringBuilder replay = new StringBuilder();

		Game game = new Game(0);

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
	 * @param fileName
	 *            The file name of the game to be played
	 * @param visual
	 *            Indicates whether or not to use visuals
	 */
	public void replayGame(String fileName, boolean visual) {
		ArrayList<String> timeSteps = loadReplay(fileName);

		Game game = new Game(0);

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