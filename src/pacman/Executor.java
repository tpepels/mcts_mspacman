package pacman;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Random;
import pacman.controllers.*;
import pacman.controllers.examples.Legacy2TheReckoning;
import pacman.entries.pacman.MyPacMan;
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
	 * @param args the command line arguments
	 */
	public static void main(String[] args) {
		Executor exec = new Executor();

		/*
		 * //run multiple games in batch mode - good for testing. int numTrials=10; 
		 * exec.runExperiment(new RandomPacMan(),new RandomGhosts(),numTrials);
		 */

		/*
		 * //run a game in synchronous mode: game waits until controllers respond. int delay=5; boolean visual=true;
		 * exec.runGame(new RandomPacMan(),new RandomGhosts(),visual,delay);
		 */

		// /*
		// run the game in asynchronous mode.
		boolean visual = true;
		// exec.runGameTimed(new NearestPillPacMan(),new
		// AggressiveGhosts(),visual);
		exec.runGame(new MyPacMan(), new Legacy2TheReckoning(), visual, Constants.DELAY);
		// exec.runGameTimedSpeedOptimised(new MyPacMan(), new
		// Legacy2TheReckoning(), false, true);
		// exec.runGameTimedSpeedOptimised(new StarterPacMan(), new
		// PinchGhostMover(), false, true);
		// exec.runGame(new StarterPacMan(), new MyGhosts(), visual,
		// Constants.DELAY);
		// exec.runGameTimed(new HumanController(new KeyBoardInput()),new
		// StarterGhosts(),visual);
		// */

		/*
		 * //run the game in asynchronous mode but advance as soon as both controllers are ready - this is the mode of
		 * the competition. //time limit of DELAY ms still applies. boolean visual=true; boolean fixedTime=false;
		 * exec.runGameTimedSpeedOptimised(new RandomPacMan(),new RandomGhosts(),fixedTime,visual);
		 */

		/*
		 * //run game in asynchronous mode and record it to file for replay at a later stage. boolean visual=true;
		 * String fileName="replay.txt"; exec.runGameTimedRecorded(new HumanController(new KeyBoardInput()),new
		 * RandomGhosts(),visual,fileName); //exec.replayGame(fileName,visual);
		 */
	}

	/**
	 * For running multiple games without visuals. This is useful to get a good idea of how well a controller plays
	 * against a chosen opponent: the random nature of the game means that performance can vary from game to game.
	 * Running many games and looking at the average score (and standard deviation/error) helps to get a better idea of
	 * how well the controller is likely to do in the competition.
	 * 
	 * @param pacManController
	 *            The Pac-Man controller
	 * @param ghostController
	 *            The Ghosts controller
	 * @param trials
	 *            The number of trials to be executed
	 */
	public void runExperiment(Controller<MOVE> pacManController,
			Controller<EnumMap<GHOST, MOVE>> ghostController, int trials) {
		double avgScore = 0;

		Random rnd = new Random(0);
		Game game;

		for (int i = 0; i < trials; i++) {
			game = new Game(rnd.nextLong());

			while (!game.gameOver()) {
				game.advanceGame(
						pacManController.getMove(game.copy(), System.currentTimeMillis() + DELAY),
						ghostController.getMove(game.copy(), System.currentTimeMillis() + DELAY));
			}

			avgScore += game.getScore();
			System.out.println(i + "\t" + game.getScore());
		}

		System.out.println(avgScore / trials);
	}

	/**
	 * Run a game in asynchronous mode: the game waits until a move is returned. In order to slow thing down in case the
	 * controllers return very quickly, a time limit can be used. If fasted gameplay is required, this delay should be
	 * put as 0.
	 * 
	 * @param pacManController
	 *            The Pac-Man controller
	 * @param ghostController
	 *            The Ghosts controller
	 * @param visual
	 *            Indicates whether or not to use visuals
	 * @param delay
	 *            The delay between time-steps
	 */
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

	/**
	 * Run the game with time limit (asynchronous mode). This is how it will be done in the competition. Can be played
	 * with and without visual display of game states.
	 * 
	 * @param pacManController
	 *            The Pac-Man controller
	 * @param ghostController
	 *            The Ghosts controller
	 * @param visual
	 *            Indicates whether or not to use visuals
	 */
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

	/**
	 * Run the game in asynchronous mode but proceed as soon as both controllers replied. The time limit still applies
	 * so so the game will proceed after 40ms regardless of whether the controllers managed to calculate a turn.
	 * 
	 * @param pacManController
	 *            The Pac-Man controller
	 * @param ghostController
	 *            The Ghosts controller
	 * @param fixedTime
	 *            Whether or not to wait until 40ms are up even if both controllers already responded
	 * @param visual
	 *            Indicates whether or not to use visuals
	 */
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