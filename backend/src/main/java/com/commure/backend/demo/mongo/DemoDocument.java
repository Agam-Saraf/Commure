package com.commure.backend.demo.mongo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("demo_documents")
public class DemoDocument {

	@Id
	private String id;

	private String value;

	protected DemoDocument() {
	}

	public DemoDocument(String value) {
		this.value = value;
	}

	public String getId() {
		return id;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}
}
