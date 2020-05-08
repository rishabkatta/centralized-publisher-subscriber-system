package edu.rit.cs;

public interface Publisher {
	/*
	 * publish an event of a specific topic with title and content
	 */
	public void publish(Event event, String qos, int retrails);
	
	/*
	 * advertise new topic
	 */
	public void advertise(Topic newTopic);
}
