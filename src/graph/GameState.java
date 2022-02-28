package graph;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import diver.McDiver;
import gui.GUI;

public class GameState implements FindState, FleeState {

	private enum Phase {
		FIND, FLEE;
	}

	@SuppressWarnings("serial")
	private static class OutOfTimeException extends RuntimeException {}

	static boolean shouldPrint= true;

	/** minimum and maximum number of rows */
	public static final int MIN_ROWS= 8, MAX_ROWS= 25;

	/** minimum and maximum number of columns */
	public static final int MIN_COLS= 12, MAX_COLS= 40;

	/** Time-out time for find and flee phases */
	public static final long FIND_TIMEOUT= 10, FLEE_TIMEOUT= 15;

	/** Minimum and maximum bonuses */
	public static final double MIN_BONUS= 1.0, MAX_BONUS= 1.3;

	/** extra time factor. bigger is nicer - addition to total multiplier */
	private static final double EXTRA_TIME_FACTOR= 0.3;

	private static final double NO_BONUS_LENGTH= 3;

	/** The find- and flee- sewers */
	private final Sewers findSewer, fleeSewer;

	private final SewerDiver sewerDiver;

	private final Optional<GUI> gui;

	private final long seed;

	private Node position;

	/** steps taken so far, steps left, and coins collected */
	private int stepsTaken, stepsToGo, coinsCollected;

	private Phase phase;
	private boolean findSucceeded= false;
	private boolean fleeSucceeded= false;
	private boolean findErred= false;
	private boolean fleeErred= false;
	private boolean findTimedOut= false;
	private boolean fleeTimedOut= false;

	private int minFindDistance;
	private int minFleeDistance;

	private int findStepsLeft= 0;
	private int fleeStepsLeft= 0;

	private int minFindSteps;

	/** = "flee succeeded" */
	public boolean fleeSucceeded() {
		return fleeSucceeded;
	}

	/** Constructor: a new GameState object for sewerDiver sd. <br>
	 * This constructor takes a path to files storing serialized sewers <br>
	 * and simply loads these sewers. */
	GameState(Path findSewerPath, Path fleeSewerPath, SewerDiver sd)
		throws IOException {
		findSewer= Sewers.deserialize(Files.readAllLines(findSewerPath));
		minFindSteps= findSewer.minPathLengthToRing(findSewer.entrance());
		fleeSewer= Sewers.deserialize(Files.readAllLines(fleeSewerPath));

		sewerDiver= sd;

		position= findSewer.entrance();
		stepsTaken= 0;
		stepsToGo= Integer.MAX_VALUE;
		coinsCollected= 0;

		seed= -1;

		phase= Phase.FIND;
		gui= Optional.of(new GUI(findSewer, position.getTile().row(),
			position.getTile().column(), 0, this));
	}

	/** Constructor: a new random game instance with or without a GUI. */
	private GameState(boolean useGui, SewerDiver sd) {
		this(new Random().nextLong(), useGui, sd);
	}

	/** Constructor: a new game instance using seed seed with or without a GUI, <br>
	 * and with sewerDiver sd used to solve the game. */
	/* package */ GameState(long seed, boolean useGui, SewerDiver sd) {
		Random rand= new Random(seed);
		int ROWS= rand.nextInt(MAX_ROWS - MIN_ROWS + 1) + MIN_ROWS;
		int COLS= rand.nextInt(MAX_COLS - MIN_COLS + 1) + MIN_COLS;
		findSewer= Sewers.digExploreSewer(ROWS, COLS, rand);
		minFindSteps= findSewer.minPathLengthToRing(findSewer.entrance());
		Tile ringTile= findSewer.ring().getTile();
		fleeSewer= Sewers.digGetOutSewer(ROWS, COLS, ringTile.row(), ringTile.column(), rand);

		position= findSewer.entrance();
		stepsTaken= 0;
		stepsToGo= Integer.MAX_VALUE;
		coinsCollected= 0;

		sewerDiver= sd;
		phase= Phase.FIND;

		this.seed= seed;

		if (useGui) {
			gui= Optional.of(new GUI(findSewer, position.getTile().row(),
				position.getTile().column(), seed, this));
		} else {
			gui= Optional.empty();
		}
	}

	/** Run through the game, one step at a time. <br>
	 * Will run flee() only if find() succeeds. <br>
	 * Will fail in case of timeout. */
	void runWithTimeLimit() {
		findWithTimeLimit();
		if (!findSucceeded) {
			findStepsLeft= findSewer.minPathLengthToRing(position);
			fleeStepsLeft= fleeSewer.minPathLengthToRing(fleeSewer.entrance());
		} else {
			fleeWithTimeLimit();
			if (!fleeSucceeded) {
				fleeStepsLeft= fleeSewer.minPathLengthToRing(position);
			}
		}
	}

	/** Run through the game, one step at a time. <br>
	 * Will run flee() only if find() succeeds. <br>
	 * Does not use a timeout and will wait as long as necessary. */
	void run() {
		find();
		if (!findSucceeded) {
			findStepsLeft= findSewer.minPathLengthToRing(position);
			fleeStepsLeft= fleeSewer.minPathLengthToRing(fleeSewer.entrance());
		} else {
			flee();
			if (!fleeSucceeded) {
				fleeStepsLeft= fleeSewer.minPathLengthToRing(position);
			}
		}
	}

	/** Run only the find phase. Uses timeout. */
	void runFindWithTimeout() {
		findWithTimeLimit();
		if (!findSucceeded) {
			findStepsLeft= findSewer.minPathLengthToRing(position);
		}
	}

	/** Run only the flee phase. Uses timeout. */
	void runFleeWithTimeout() {
		fleeWithTimeLimit();
		if (!fleeSucceeded) {
			fleeStepsLeft= fleeSewer.minPathLengthToRing(position);
		}
	}

	@SuppressWarnings("deprecation")
	/** Wrap a call find() with the timeout functionality. */
	private void findWithTimeLimit() {
		FutureTask<Void> ft= new FutureTask<>(new Callable<Void>() {
			@Override
			public Void call() {
				find();
				return null;
			}
		});

		Thread t= new Thread(ft);
		t.start();
		try {
			ft.get(FIND_TIMEOUT, TimeUnit.SECONDS);
		} catch (TimeoutException e) {
			t.stop();
			findTimedOut= true;
		} catch (InterruptedException | ExecutionException e) {
			System.err.println("ERROR");
			// Shouldn't happen
		}
	}

	/** Run the sewerDiver's find() function with no timeout. */
	/* package */ void find() {
		phase= Phase.FIND;
		stepsTaken= 0;
		findSucceeded= false;
		position= findSewer.entrance();
		minFindDistance= findSewer.minPathLengthToRing(position);
		gui.ifPresent((g) -> g.setLighting(false));
		gui.ifPresent((g) -> g.updateSewer(findSewer, 0));
		gui.ifPresent((g) -> g.moveTo(position));

		try {
			sewerDiver.find(this);
			// Verify that we returned at the correct location
			if (position.equals(findSewer.ring())) {
				findSucceeded= true;
			} else {
				errPrintln("find(...) returned at the wrong location.");
				gui.ifPresent(
					(g) -> g.displayError("find(f..) returned at the wrong location."));
			}
		} catch (Throwable t) {
			if (t instanceof ThreadDeath) return;
			errPrintln("find(...) threw an exception.");
			errPrintln("Here is the output.");
			t.printStackTrace();
			gui.ifPresent((g) -> g.displayError(
				"find(...) threw an exception. See the console output."));
			findErred= true;
		}
	}

	@SuppressWarnings("deprecation")
	/** Wrap a call flee() with the timeout functionality. */
	private void fleeWithTimeLimit() {
		FutureTask<Void> ft= new FutureTask<>(new Callable<Void>() {
			@Override
			public Void call() {
				flee();
				return null;
			}
		});

		Thread t= new Thread(ft);
		t.start();
		try {
			ft.get(FLEE_TIMEOUT, TimeUnit.SECONDS);
		} catch (TimeoutException e) {
			t.stop();
			fleeTimedOut= true;
		} catch (InterruptedException | ExecutionException e) {
			System.err.println("ERROR"); // Shouldn't happen
		}
	}

	/** Handle the logic for running the sewerDiver's flee() procedure with no timeout. */
	/* package */ void flee() {
		phase= Phase.FLEE;
		Tile ringTile= findSewer.ring().getTile();
		position= fleeSewer.nodeAt(ringTile.row(), ringTile.column());
		minFleeDistance= fleeSewer.minPathLengthToRing(position);
		stepsToGo= computeStepsToFlee();
		gui.ifPresent((g) -> g.getOptionsPanel().changePhaseLabel("Flee phase"));
		gui.ifPresent((g) -> g.setLighting(true));
		gui.ifPresent((g) -> g.updateSewer(fleeSewer, stepsToGo));

		// Pick up coins on start phase (if any)
		Node cn= currentNode();
		int coins= cn.getTile().coins();
		if (coins > 0) {
			grabCoins();
		}

		try {
			sewerDiver.flee(this);
			// Verify that the diver returned at the correct location
			if (!position.equals(fleeSewer.ring())) {
				errPrintln("flee(..) returned at the wrong location.");
				gui.ifPresent((g) -> g
					.displayError("flee(...) returned at the wrong location."));
				return;
			}

			fleeSucceeded= true;
			gui.ifPresent((g) -> g.getOptionsPanel().changePhaseLabel("Flee done!"));
			System.out.println("Flee Succeeded!");
			// Since the exit has been reached, turn off painting the
			GUI g= gui.isPresent() ? gui.get() : null;
			gui.MazePanel mp= g == null ? null : g.getMazePanel();
			if (mp != null) mp.repaint();

		} catch (OutOfTimeException e) {
			errPrintln("flee(...) ran out of steps before returning!");
			gui.ifPresent((g) -> g
				.displayError("flee(...) ran out of steps before returning!"));
		} catch (Throwable t) {
			if (t instanceof ThreadDeath) return;
			errPrintln("flee(...) threw an exception:");
			t.printStackTrace();
			gui.ifPresent((g) -> g.displayError(
				"flee(...) threw an exception. See the console output."));
			fleeErred= true;
		}

		outPrintln("Coins collected   : " + getCoinsCollected());
		DecimalFormat df= new DecimalFormat("#.##");
		outPrintln("Bonus multiplier : " + df.format(computeBonusFactor()));
		outPrintln("Score            : " + getScore());
	}

	/** Making sure the sewerDiver always has the minimum steps needed to get out, <br>
	 * add a factor of extra steps proportional to the size of the sewer. */
	private int computeStepsToFlee() {
		int minStepsToFlee= fleeSewer.minPathLengthToRing(position);
		return (int) (minStepsToFlee + EXTRA_TIME_FACTOR *
			(Sewers.MAX_EDGE_WEIGHT + 1) * fleeSewer.numOpenTiles() / 2);

	}

	/** Compare the sewerDiver's performance on the flee() phase to the <br>
	 * theoretical minimum, compute their bonus factor on a call from <br>
	 * MIN_BONUS to MAX_BONUS. <br>
	 * Bonus should be minimum if take longer than NO_BONUS_LENGTH times optimal. */
	private double computeBonusFactor() {
		double findDiff= (stepsTaken - minFindSteps) / (double) minFindSteps;
		if (findDiff <= 0) return MAX_BONUS;
		double multDiff= MAX_BONUS - MIN_BONUS;
		return Math.max(MIN_BONUS, MAX_BONUS - findDiff / NO_BONUS_LENGTH * multDiff);
	}

	/** See moveTo(Node&lt;TileData&gt; n)
	 *
	 * @param id The Id of the neighboring Node to move to */
	@Override
	public void moveTo(long id) {
		if (phase != Phase.FIND) {
			throw new IllegalStateException("moveTo(ID) can only be called while fleeing!");
		}

		for (Node n : position.getNeighbors()) {
			if (n.getId() == id) {
				position= n;
				stepsTaken++ ;
				gui.ifPresent((g) -> g.updateBonus(computeBonusFactor()));
				gui.ifPresent((g) -> g.moveTo(n));
				return;
			}
		}
		throw new IllegalArgumentException("moveTo: Node must be adjacent to position");
	}

	/** Return the unique id of the current location. */
	@Override
	public long currentLocation() {
		if (phase != Phase.FIND) {
			throw new IllegalStateException("getLocation() can be called only while fleeing!");
		}

		return position.getId();
	}

	/** Return a collection of NodeStatus objects that contain the unique ID of the node and the
	 * distance from that node to the ring. */
	@Override
	public Collection<NodeStatus> neighbors() {
		if (phase != Phase.FIND) {
			throw new IllegalStateException("getNeighbors() can be called only while fleeing!");
		}

		Collection<NodeStatus> options= new ArrayList<>();
		for (Node n : position.getNeighbors()) {
			int distance= computeDistanceToRing(n.getTile().row(), n.getTile().column());
			options.add(new NodeStatus(n.getId(), distance));
		}
		return options;
	}

	/** Return the Manhattan distance from (row, col) to the ring */
	private int computeDistanceToRing(int row, int col) {
		return Math.abs(row - findSewer.ring().getTile().row()) +
			Math.abs(col - findSewer.ring().getTile().column());
	}

	/** Return the Manhattan distance from the current location <br>
	 * to the ring location on the map. */
	@Override
	public int distanceToRing() {
		if (phase != Phase.FIND) {
			throw new IllegalStateException(
				"distanceToRing() can be called only while fleeing!");
		}

		return computeDistanceToRing(position.getTile().row(), position.getTile().column());
	}

	@Override
	public Node currentNode() {
		if (phase != Phase.FLEE) {
			throw new IllegalStateException("getCurrentNode: Error, " +
				"current Node may not be accessed unless fleeing");
		}
		return position;
	}

	@Override
	public Node exit() {
		if (phase != Phase.FLEE) {
			throw new IllegalStateException("getEntrance: Error, " +
				"current Node may not be accessed unless fleeing");
		}
		return fleeSewer.ring();
	}

	@Override
	public Collection<Node> allNodes() {
		if (phase != Phase.FLEE) {
			throw new IllegalStateException("getVertices: Error, " +
				"Vertices may not be accessed unless fleeing");
		}
		return Collections.unmodifiableSet(fleeSewer.graph());
	}

	/** Attempt to move the sewerDiver from the current position to the<br>
	 * <tt>Node</tt> <tt>n</tt>. Throw an <tt>IllegalArgumentException</tt> <br>
	 * if <tt>n</tt> is not neighboring. <br>
	 * Increment the steps taken if successful. */
	@Override
	public void moveTo(Node n) {
		if (phase != Phase.FLEE) {
			throw new IllegalStateException("Call moveTo(Node) only when fleeing!");
		}
		int distance= position.getEdge(n).length;
		if (stepsToGo - distance < 0) throw new OutOfTimeException();

		if (!position.getNeighbors().contains(n))
			throw new IllegalArgumentException("moveTo: Node must be adjacent to position");
		position= n;
		stepsToGo-= distance;
		gui.ifPresent((g) -> g.updateStepsToGo(stepsToGo));
		gui.ifPresent((g) -> { g.moveTo(n); });
		grabCoins();
	}

	/** Pick up coins. <br>
	 * Coins on a Node n are picked up automatically when the flee phase starts and<br>
	 * when a call moveTo(n) is executed. */
	void grabCoins() {
		if (phase != Phase.FLEE) {
			throw new IllegalStateException("Call grabCoins() only when fleeing!");
		}
		coinsCollected+= position.getTile().takeCoins();
		gui.ifPresent((g) -> g.updateCoins(coinsCollected, getScore()));
	}

	@Override
	/** Return the number of steps remaining to flee. */
	public int stepsToGo() {
		if (phase != Phase.FLEE) {
			throw new IllegalStateException(
				"stepsToGo() can be called only while fleeing!");
		}
		return stepsToGo;
	}

	/* package */ int getCoinsCollected() {
		return coinsCollected;
	}

	/** Return the player's current score. */
	/* package */ int getScore() {
		return (int) (computeBonusFactor() * coinsCollected);
	}

	/* package */ boolean getFindSucceeded() {
		return findSucceeded;
	}

	/* package */ boolean getFleeSucceeded() {
		return fleeSucceeded;
	}

	/* package */ boolean getFindErrored() {
		return findErred;
	}

	/* package */ boolean getFleeErrored() {
		return fleeErred;
	}

	/* package */ boolean getFindTimeout() {
		return findTimedOut;
	}

	/* package */ boolean getFleeTimeout() {
		return fleeTimedOut;
	}

	/* package */ int getMinFindDistance() {
		return minFindDistance;
	}

	/* package */ int getMinFleeDistance() {
		return minFleeDistance;
	}

	/* package */ int getFindStepsLeft() {
		return findStepsLeft;
	}

	/* package */ int getFleeStepsLeft() {
		return fleeStepsLeft;
	}

	/** Given seed, whether or not to use the GUI, and an instance of <br>
	 * a solution to use, run the game. */
	public static int runNewGame(long seed, boolean useGui, SewerDiver solution) {
		GameState state;
		if (seed != 0) {
			state= new GameState(seed, useGui, solution);
		} else {
			state= new GameState(useGui, solution);
		}
		outPrintln("Seed : " + state.seed);
		state.run();
		return state.getScore();
	}

	/** Execute find and flee on a random seed, except that: <br>
	 * (1) If there is a parameter -s <seed>, run on that seed OR <br>
	 * (2) If there is a parameter -n <count>, run count times on random seeds. */
	public static void main(String[] args) throws IOException {
		List<String> argList= new ArrayList<>(Arrays.asList(args));
		int repeatNumberIndex= argList.indexOf("-n");
		int numTimesToRun= 1;
		if (repeatNumberIndex >= 0) {
			try {
				numTimesToRun= Math.max(Integer.parseInt(argList.get(repeatNumberIndex + 1)), 1);
			} catch (Exception e) {
				// numTimesToRun = 1
			}
		}
		int seedIndex= argList.indexOf("-s");
		long seed= 0;
		if (seedIndex >= 0) {
			try {
				seed= Long.parseLong(argList.get(seedIndex + 1));
			} catch (NumberFormatException e) {
				errPrintln("Error, -s must be followed by a numerical seed");
				return;
			} catch (ArrayIndexOutOfBoundsException e) {
				errPrintln("Error, -s must be followed by a seed");
				return;
			}
		}

		int totalScore= 0;
		for (int i= 0; i < numTimesToRun; i++ ) {
			totalScore+= runNewGame(seed, false, new McDiver());
			if (seed != 0) seed= new Random(seed).nextLong();
			outPrintln("");
		}

		outPrintln("Average score : " + totalScore / numTimesToRun);
	}

	static void outPrintln(String s) {
		if (shouldPrint) System.out.println(s);
	}

	static void errPrintln(String s) {
		if (shouldPrint) System.err.println(s);
	}
}
