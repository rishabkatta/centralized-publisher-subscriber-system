package edu.rit.cs;

import java.io.FileNotFoundException;
import java.io.IOException;

public interface Subscriber {
	/*
	 * subscribe to a topic
	 */
	public void subscribe(Topic topic, String subscriberID);

	
	/*
	 * unsubscribe from a topic 
	 */
	public void unsubscribe(Topic topic, String subscriberID) throws IOException;
	
	/*
	 * unsubscribe to all subscribed topics
	 */
	public void unsubscribe(String subscriberID) throws IOException;
	
	/*
	 * show the list of topics current subscribed to 
	 */
	public void listSubscribedTopics();
	
}
