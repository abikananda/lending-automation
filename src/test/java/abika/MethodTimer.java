package abika;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MethodTimer class to track execution time of methods
 */
public class MethodTimer {
    private static final Logger logger = LoggerFactory.getLogger(MethodTimer.class);
    private final String methodName;
    private final long startTime;

    public MethodTimer(String methodName) {
        this.methodName = methodName;
        this.startTime = System.currentTimeMillis();
    }

    public void end() {
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        logTime(duration);
    }

    public long getElapsedMillis() {
        long endTime = System.currentTimeMillis();
        return endTime - startTime;
    }

    private void logTime(long duration) {
        if (duration < 1000) {
//            logger.info("⏱️  [{}] took {} ms", methodName, duration);
        } else {
//            logger.info("⏱️  [{}] took {} seconds ({} ms)", methodName, String.format("%.2f", duration / 1000.0), duration);
        }
    }
}

