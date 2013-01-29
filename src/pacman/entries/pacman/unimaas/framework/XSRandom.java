package pacman.entries.pacman.unimaas.framework;

import java.util.Random;

/**
 * A subclass of java.util.random that implements the 
 * Xorshift random number generator
 * Source: http://demesos.blogspot.com/2011/09/replacing-java-random-generator.html
 */
public class XSRandom extends Random {
	private static final long serialVersionUID = 4376922116562734652L;
	public static XSRandom r = new XSRandom();
	private long seed;
	public XSRandom() {
        this(System.currentTimeMillis());
    }

    public XSRandom(long seed) {
        this.seed = seed;
    }

	@Override
	protected int next(int nbits) {
		long x = seed;
		x ^= (x << 21);
		x ^= (x >>> 35);
		x ^= (x << 4);
		seed = x;
		x &= ((1L << nbits) - 1);
		return (int) x;
	}
}
