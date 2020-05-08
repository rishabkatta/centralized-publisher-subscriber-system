/**
 * @author-name: Rishab Katta
 *
 * Topic class is used to hold all the variables related to a topic object. It has constructors and getters/setters.
 */
package edu.rit.cs;

import java.io.Serializable;
import java.util.List;

public class Topic implements Serializable {
	private String id;
	private List<String> keywords;
	private String name;
	private static int counter = 0;

	public Topic(String id, List<String> keywords, String name) {
		this.id = id;
		this.keywords = keywords;
		this.name = name;
		incrementCounter();
	}

	public Topic(List<String> keywords, String name) {
		this.id = "t" + counter;
		this.keywords = keywords;
		this.name = name;
		incrementCounter();
	}
	/*
	 * incrementCounter function is used to generate a unique serial id.
	 */
	private void incrementCounter(){
		counter += 1;
	}

	public String getId() {
		return id;
	}

	public List<String> getKeywords() {
		return keywords;
	}

	public String getName() {
		return name;
	}
}
