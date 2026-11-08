package com.gooodhandletruck.zippick.domain.interest.repository;

import com.gooodhandletruck.zippick.domain.interest.entity.InterestCondition;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterestConditionRepository extends JpaRepository<InterestCondition, Long> {

    Optional<InterestCondition> findByDeviceId(Long deviceId);
}
