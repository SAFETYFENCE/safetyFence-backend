package com.project.safetyFence.link;

import com.project.safetyFence.link.domain.Link;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LinkRepository extends JpaRepository<Link, Long> {
    boolean existsByUser_NumberAndUserNumber(String userNumber, String linkedUserNumber);

    /**
     * 피보호자 번호(user_number)에 매핑된 보호자 링크들을 조회한다.
     */
    List<Link> findByUserNumber(String userNumber);
}
