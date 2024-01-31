public class SemError extends RuntimeException{
	private static final long serialVersionUID = 1L;

	public SemError(String message, int row) {
        super("\u001B[31m\033[1mSem Error: " + message + " [ line:" + row  + " ]\u001B[0m");
	}
}
