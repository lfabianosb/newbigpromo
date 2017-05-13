import job.FlightSearchJob;

public class WorkerProcess {

	public static void main(String[] args) {
		Thread job1 = new Thread(new FlightSearchJob());
		job1.start();
	}
}