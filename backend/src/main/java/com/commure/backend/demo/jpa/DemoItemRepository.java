package com.commure.backend.demo.jpa;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

@Profile("jpa")
public interface DemoItemRepository extends JpaRepository<DemoItem, Long> {
}
