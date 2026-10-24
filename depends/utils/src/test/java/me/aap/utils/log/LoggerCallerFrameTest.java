package me.aap.utils.log;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class LoggerCallerFrameTest {

	@Test
	public void callerFrameIsTheCodeThatLogged() {
		CapturingLogger logger = new CapturingLogger();
		logger.w();
		assertEquals("callerFrameIsTheCodeThatLogged", logger.frame.getMethodName());
		assertEquals(LoggerCallerFrameTest.class.getName(), logger.frame.getClassName());
	}

	/** {@link #w()} plays the role of {@link Log#w(Object...)}: one frame between the caller and the logger. */
	private static final class CapturingLogger extends PrintStreamLogger {
		StackTraceElement frame;

		CapturingLogger() {
			super(System.out);
		}

		void w() {
			logWarn(new StringBuilder());
		}

		@Override
		public void logWarn(StringBuilder msg) {
			frame = callerFrame(1);
		}
	}
}
