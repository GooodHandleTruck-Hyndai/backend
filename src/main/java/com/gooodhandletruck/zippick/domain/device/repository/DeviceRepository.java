package com.gooodhandletruck.zippick.domain.device.repository;

import com.gooodhandletruck.zippick.domain.device.entity.Device;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<Device, Long> {

    Optional<Device> findByAnonymousId(String anonymousId);
}
