/**
 * @author-name: Rishab Katta
 *
 * Event class for instantiating event object. An event can be thought of as a message that publisher sends to all topic
 * subscribers.
 */
package edu.rit.cs;

import java.io.Serializable;

/*
Event class just consists of constructors and getters/setters.
 */
public class Event implements Serializable {
	private String id;
	private Topic topic;
	private String title;
	private String content;
	private static int counter = 0;

	public Event(String id, Topic topic, String title, String content){
		this.id = id;
		this.topic = topic;
		this.title = title;
		this.content = content;
		incrementCounter();
	}

	public Event(Topic topic, String title, String content) {
		this.id = "e" + counter;
		this.topic = topic;
		this.title = title;
		this.content = content;
		incrementCounter();
	}

	private void incrementCounter(){
		counter += 1;
	}


	public String getId() {
		return id;
	}

	public Topic getTopic() {
		return topic;
	}

	public String getTitle() {
		return title;
	}

	public String getContent() {
		return content;
	}
}
