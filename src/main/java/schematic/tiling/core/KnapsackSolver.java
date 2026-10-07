package schematic.tiling.core;

import java.util.Arrays;

public class KnapsackSolver {

    public static class KnapsackSolutionNotFound extends IllegalArgumentException {
        private final int remainder;

        public KnapsackSolutionNotFound(String message, int remainder) {
            super(message);
            this.remainder = remainder;
        }

        public int getRemainder() {
            return this.remainder;
        }
    }

    /**
     * Given an array of positive integers of size `m` sorted in descending order,
     * and a natural number `n`, finds `m` integers `c[0], c[1], ...`
     * such that `c[0] numbers[0] + c[1] numbers[1] + ... >= n` and this sum is minimal.
     *
     * Port of knapsack_solver from schem_gen_v1.2.py.
     */
    public static int[] solve(int[] numbers, int n, boolean strict) {
        if (n <= 0) {
            try {
                solve(numbers, 1, strict);
            } catch (KnapsackSolutionNotFound err) {
                throw new KnapsackSolutionNotFound("This width is too small.", err.getRemainder() + n - 1);
            }
            throw new KnapsackSolutionNotFound("This width is too small.", n - 1);
        }

        if (numbers == null || numbers.length == 0) {
            throw new IllegalArgumentException("Numbers array cannot be null or empty");
        }

        for (int i = 0; i < numbers.length - 1; i++) {
            if (numbers[i] <= numbers[i + 1] || numbers[i + 1] <= 0) {
                throw new IllegalArgumentException("Numbers are not in strictly descending order or not positive: " + Arrays.toString(numbers));
            }
        }
        if (numbers[numbers.length - 1] <= 0) {
            throw new IllegalArgumentException("Numbers are not positive: " + Arrays.toString(numbers));
        }

        int m = numbers.length;
        int num0 = numbers[0];

        // first guess
        int[] c = new int[m];
        c[0] = (int) Math.ceil((double) n / num0);

        int[] bestAns = new int[m + 1];
        bestAns[0] = n - c[0] * num0; // <= 0
        bestAns[1] = c[0];
        for (int i = 1; i < m; i++) {
            bestAns[1 + i] = 0;
        }

        if (bestAns[0] == 0) {
            return Arrays.copyOfRange(bestAns, 1, bestAns.length);
        }

        if (m > 1) {
            int[] coefs = new int[m - 1]; // coefs for numbers[1..m-1]

            while (true) {
                int sumRest = 0;
                for (int i = 0; i < m - 1; i++) {
                    sumRest += coefs[i] * numbers[i + 1];
                }
                int r = n - sumRest;
                if (r >= 0) {
                    int c0 = (int) Math.ceil((double) r / num0);
                    int remainder = -(r % num0);
                    if (remainder < 0) {
                        remainder += num0;
                    }
                    int candFirst = -remainder;

                    if (compareCandidate(candFirst, c0, coefs, bestAns) > 0) {
                        bestAns[0] = candFirst;
                        bestAns[1] = c0;
                        for (int i = 0; i < m - 1; i++) {
                            bestAns[2 + i] = coefs[i];
                        }
                    }
                }

                // increment odometer for coefs
                int idx = m - 2;
                while (idx >= 0) {
                    coefs[idx]++;
                    if (coefs[idx] < num0) {
                        break;
                    }
                    coefs[idx] = 0;
                    idx--;
                }
                if (idx < 0) {
                    break;
                }
            }
        }

        if (strict && bestAns[0] != 0) {
            throw new KnapsackSolutionNotFound(
                    "Unable to find a solution for n=" + n + " and widths=" + Arrays.toString(numbers) + ".",
                    bestAns[0]
            );
        }

        return Arrays.copyOfRange(bestAns, 1, bestAns.length);
    }

    private static int compareCandidate(int candFirst, int c0, int[] coefs, int[] bestAns) {
        if (candFirst != bestAns[0]) {
            return Integer.compare(candFirst, bestAns[0]);
        }
        if (c0 != bestAns[1]) {
            return Integer.compare(c0, bestAns[1]);
        }
        for (int i = 0; i < coefs.length; i++) {
            if (coefs[i] != bestAns[2 + i]) {
                return Integer.compare(coefs[i], bestAns[2 + i]);
            }
        }
        return 0;
    }
}

