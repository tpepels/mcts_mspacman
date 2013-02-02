package pacman.entries.pacman.unimaas;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class Settings {
	public double[] maxPathLength, maxSimulations;
	// Safety and minimum ghost score parameters.
	public double[] safetyT, ghostSelectScore;
	// Penalty and discount
	public double[] reversePenalty, discount;
	// UCT Constant
	public double[] uctC;
	// The minimum number of visits for UCT
	public double[] minVisits;
	// PowerPill penalties (to few ghosts, no ghosts)
	public double[] ppPenalty1, ppPenalty2;
	// Epsilon-greedy values for pacman and ghosts
	public double[] pacEpsilon, ghostEpsilon;
	// Alpha values for re-use
	public double[] alpha_pill, alpha_ghosts;

	// On/off settings for disabling certain enhancements
	public boolean tree_reuse, tree_decay, tree_var_depth, strategic_playout, last_good_config,
			enable_trailghost;
	// Different opponents
	public String opponent;
	//
	public ArrayList<double[]> properties;

	public Settings() {
		maxPathLength = new double[1];
		maxSimulations = new double[1];
		//
		safetyT = new double[1];
		ghostSelectScore = new double[1];
		reversePenalty = new double[1];
		discount = new double[1];
		//
		uctC = new double[1];
		minVisits = new double[1];
		//
		ppPenalty1 = new double[1];
		ppPenalty2 = new double[1];
		//
		pacEpsilon = new double[1];
		ghostEpsilon = new double[1];
		//
		alpha_pill = new double[1];
		alpha_ghosts = new double[1];
	}
	
	public void setPropertiesList() {
		properties = new ArrayList<double[]>();
		properties.add(maxPathLength);
		properties.add(maxSimulations);
		properties.add(safetyT);
		properties.add(ghostSelectScore);
		properties.add(reversePenalty);
		properties.add(discount);
		properties.add(uctC);
		properties.add(minVisits);
		properties.add(ppPenalty1);
		properties.add(ppPenalty2);
		properties.add(pacEpsilon);
		properties.add(ghostEpsilon);
		properties.add(alpha_pill);
		properties.add(alpha_ghosts);
	}

	public static Settings getDefaultSetting() {
		Settings defSettings = new Settings();
		//
		defSettings.maxPathLength[0] = 80;
		defSettings.maxSimulations[0] = 40;
		//
		defSettings.safetyT[0] = .75;
		defSettings.ghostSelectScore[0] = .45;
		defSettings.reversePenalty[0] = .8;
		defSettings.discount[0] = .5;
		//
		defSettings.uctC[0] = .9;
		defSettings.minVisits[0] = 10;
		//
		defSettings.ppPenalty1[0] = .5;
		defSettings.ppPenalty2[0] = .2;
		//
		defSettings.pacEpsilon[0] = .7;
		defSettings.ghostEpsilon[0] = .7;
		//
		defSettings.alpha_pill[0] = 1.;
		defSettings.alpha_ghosts[0] = 1.;
		//
		defSettings.tree_reuse = true;
		defSettings.tree_decay = true;
		defSettings.tree_var_depth = true;
		defSettings.strategic_playout = true;
		defSettings.last_good_config = true;
		defSettings.enable_trailghost = true;
		//
		defSettings.opponent = "pacman.controllers.examples.Legacy2TheReckoning";
		return defSettings;
	}

	public static Settings deserializeSettings(String file) throws IOException {
		BufferedReader br = new BufferedReader(new FileReader(file));
		String line, json = "";
		while ((line = br.readLine()) != null) {
			json += line;
		}
		br.close();
		// Convert to a settings object
		Gson gson = new Gson();
		JsonObject object = (new JsonParser()).parse(json).getAsJsonObject();
		Settings settings = gson.fromJson(object, Settings.class);
		return settings;
	}

	public static void serializeSettings(String file, Settings settings) throws IOException {
		Gson gson = new Gson();
		String json = gson.toJson(settings);
		BufferedWriter bw = new BufferedWriter(new FileWriter(file));
		bw.write(json);
		bw.close();
	}
}
