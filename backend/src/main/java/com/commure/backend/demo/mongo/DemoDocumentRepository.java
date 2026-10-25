package com.commure.backend.demo.mongo;

import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.repository.MongoRepository;

@Profile("mongo")
public interface DemoDocumentRepository extends MongoRepository<DemoDocument, String> {
}
